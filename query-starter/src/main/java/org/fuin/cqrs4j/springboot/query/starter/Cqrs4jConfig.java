package org.fuin.cqrs4j.springboot.query.starter;

import org.fuin.cqrs4j.core.QueryExecutionContextProvider;
import org.fuin.cqrs4j.core.TenantIdsSupplier;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.core.ViewRegistry;
import org.fuin.cqrs4j.esc.ConverterRegistration;
import org.fuin.cqrs4j.esc.ProjectionLeaseService;
import org.fuin.cqrs4j.esc.ProjectionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.fuin.cqrs4j.springboot.query.core.QueryExceptionHandlers;
import org.fuin.cqrs4j.springboot.query.core.SecurityContextQueryExecutionContextProvider;
import org.fuin.cqrs4j.springboot.query.core.base.EventstoreConfig;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionFreshnessService;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionLagMetrics;
import org.fuin.cqrs4j.springboot.query.core.view.SpringViewManager;
import org.fuin.cqrs4j.springboot.query.core.view.ProjectionConfig;
import org.fuin.cqrs4j.springboot.query.core.view.QryProjectionLeaseService;
import org.fuin.cqrs4j.springboot.query.core.view.QryProjectionService;
import org.fuin.cqrs4j.springboot.query.core.view.SpringViewRegistry;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.WritableTenantContext;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.api.SubscribableEventStoreAsync;
import org.fuin.objects4j.common.ThreadSafe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Configures the necessary beans.
 */
@ThreadSafe
@AutoConfigureBefore(JpaRepositoriesAutoConfiguration.class)
@EnableJpaRepositories(basePackages = {"org.fuin.cqrs4j.springboot.query.core.view"})
@EnableConfigurationProperties({EventstoreConfig.class, ProjectionConfig.class})
// The three annotated classes of that package are registered explicitly instead of being
// discovered: they are all injected by type, so their bean names are irrelevant, and an
// application no longer has to have this package inside its own component scan.
@Import({ProjectionFreshnessService.class, QryProjectionService.class, QryProjectionLeaseService.class})
@EntityScan("org.fuin.cqrs4j.jpa.query")
public class Cqrs4jConfig {

    /**
     * Creates the provider that derives the caller of a query from Spring Security's context.
     *
     * @param tenant          Tenant name reported for every request.
     * @param anonymousUserId User id reported for an unauthenticated request.
     *
     * @return Execution context provider.
     */
    @Bean
    @ConditionalOnMissingBean(QueryExecutionContextProvider.class)
    // Referenced by name: Spring Security is an optional dependency, so the class may be absent.
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    public QueryExecutionContextProvider queryExecutionContextProvider(
            @Value("${org.fuin.cqrs4j.query.tenant:default}") final String tenant,
            @Value("${org.fuin.cqrs4j.query.anonymous-user:anonymous}") final String anonymousUserId) {
        return new SecurityContextQueryExecutionContextProvider(new TenantId(tenant), anonymousUserId);
    }

    /**
     * Maps a refused read to HTTP 403.
     * <p>
     * Registered here rather than left to a component scan, because a query server deployed on its own has
     * no {@code command-core} on the classpath and would otherwise answer 500 for every refused read.
     *
     * @return Exception handlers.
     */
    @Bean
    @ConditionalOnMissingBean(QueryExceptionHandlers.class)
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.ControllerAdvice")
    public QueryExceptionHandlers queryExceptionHandlers() {
        return new QueryExceptionHandlers();
    }

    /**
     * The entity manager the read model is written to and queried through, under a name views and view
     * services can ask for by qualifier.
     * <p>
     * With one datasource this is simply the shared entity manager of the primary factory, and naming it
     * changes nothing. It exists for the application that has <em>two</em>: a read model whose durability
     * differs from the rest of its persistence - an in-memory read model rebuilt from the event store at
     * every start, beside process-manager state that has to survive a restart. There, an unqualified
     * injection point cannot say which of the two it means, and the read side is the half that must not
     * silently get the other one. Such an application declares its own bean of this name and the default
     * below backs off.
     *
     * @param entityManagerFactory Factory to create the shared entity manager from.
     *
     * @return Entity manager of the read model.
     */
    @Bean
    @ConditionalOnMissingBean(name = "readModelEntityManager")
    public EntityManager readModelEntityManager(final EntityManagerFactory entityManagerFactory) {
        return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewRegistry viewRegistry(ConfigurableBeanFactory beanFactory, List<View> views) {
        return new SpringViewRegistry(beanFactory, views);
    }

    /**
     * Collects the application-provided event up-caster registrations into a converter registry used to
     * decorate the event store's deserialization (so projections and replay upcast old-version events). Empty
     * when the application registers none, in which case events pass through unchanged.
     *
     * @param registrations Converter registrations contributed by the application (may be empty).
     * @return Converter registry.
     */
    @Bean
    @ConditionalOnMissingBean
    public ConverterRegistry converterRegistry(final List<ConverterRegistration> registrations) {
        return ConverterRegistration.toRegistry(registrations);
    }

    /**
     * The single view manager, for the ordinary case of one read model in one database.
     * <p>
     * It backs off when the application declares its own, which is what an application does when its views
     * do not all share one durability: a read model rebuilt from the event store at every start needs its
     * chunk transaction and its checkpoint on the disposable side, while process managers need theirs on
     * the durable one, and a manager holds exactly one of each.
     */
    @Bean
    @Order(0)
    @ConditionalOnMissingBean(SpringViewManager.class)
    public SpringViewManager viewManager(final ScheduledAnnotationBeanPostProcessor postProcessor,
                                         final ViewRegistry viewRegistry,
                                         final EventStore eventstore,
                                         final ProjectionAdminEventStore admin,
                                         final ProjectionService projectionService,
                                         final PlatformTransactionManager transactionManager,
                                         final ConfigurableBeanFactory beanFactory,
                                         @Value("${org.fuin.cqrs4j.multitenancy:false}") boolean multitenancy,
                                         final Optional<WritableTenantContext> tenantContext,
                                         final Optional<TenantIdsSupplier> tenantIdsSupplier,
                                         final ProjectionLeaseService leaseService,
                                         @Value("${org.fuin.cqrs4j.projection.ha.enabled:false}") boolean haEnabled,
                                         @Value("${org.fuin.cqrs4j.projection.ha.owner:}") String ownerProperty,
                                         @Value("${org.fuin.cqrs4j.projection.ha.ttl:60000}") long leaseTtlMillis,
                                         final Optional<SubscribableEventStoreAsync> subscribableEventStore,
                                         @Value("${org.fuin.cqrs4j.projection.mode:poll}") String projectionMode,
                                         final ProjectionConfig projectionConfig) {

        final String owner = ownerProperty.isBlank() ? UUID.randomUUID().toString() : ownerProperty;
        final boolean pushEnabled = "push".equalsIgnoreCase(projectionMode);
        return new SpringViewManager(postProcessor, viewRegistry, eventstore, admin,
                projectionService, transactionManager, beanFactory,
                multitenancy, tenantContext.orElse(null),
                tenantIdsSupplier.orElse(null),
                leaseService, haEnabled, owner, leaseTtlMillis,
                subscribableEventStore.orElse(null), pushEnabled, projectionConfig);

    }

    /**
     * Registers the projection-lag metrics binder, but only when Micrometer is on the classpath.
     * Applications that do not use Micrometer are unaffected.
     */
    @ThreadSafe
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    public static class ProjectionMetricsConfig {

        /**
         * Binds a projection-lag gauge (tagged with the view name) per registered view.
         *
         * @param viewRegistry      Registered views.
         * @param eventstore        Event store instance to use.
         * @param projectionService Service holding the projection checkpoints.
         * @param multitenancy      Determines if multitenancy is enabled.
         * @param tenantContext     Tenant context.
         * @param tenantIdsSupplier Supplies the known tenant ids.
         * @return Meter binder registered with the application's meter registries.
         */
        @Bean
        @ConditionalOnMissingBean
        public ProjectionLagMetrics projectionLagMetrics(final ViewRegistry viewRegistry,
                                                         final EventStore eventstore,
                                                         final ProjectionService projectionService,
                                                         @Value("${org.fuin.cqrs4j.multitenancy:false}") boolean multitenancy,
                                                         final Optional<WritableTenantContext> tenantContext,
                                                         final Optional<TenantIdsSupplier> tenantIdsSupplier) {
            return new ProjectionLagMetrics(viewRegistry, eventstore, projectionService,
                    multitenancy ? tenantContext.orElse(null) : null,
                    multitenancy ? tenantIdsSupplier.orElse(null) : null);
        }

    }

}

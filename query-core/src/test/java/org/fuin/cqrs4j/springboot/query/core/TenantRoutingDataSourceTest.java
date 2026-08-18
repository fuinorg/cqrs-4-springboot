/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.cqrs4j.springboot.query.core;

import org.fuin.cqrs4j.core.TenantRepository;
import org.fuin.ddd4j.core.TenantContext;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TenantRoutingDataSource}.
 */
public class TenantRoutingDataSourceTest {

    @Test
    public void testDefaultLookupKeyWhenNoTenant() {
        final TenantRepository tenantRepository = Stream::empty;
        final TenantContext context = Optional::empty;

        final TenantRoutingDataSource testee = new TenantRoutingDataSource(
                "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1", "sa", "", "org.hsqldb.jdbc.JDBCDriver", "main",
                Optional.of(context), tenantRepository);

        // No tenant in the context -> the default schema is used as the routing key
        assertThat(testee.determineCurrentLookupKey()).isEqualTo("main");
    }

    @Test
    public void testATenantRoutesToItsOwnDataSource() {

        // The routing key and the keys the data sources were registered under have to be the same type,
        // or every tenant silently falls through to the default data source: AbstractRoutingDataSource
        // looks the key up in a map and, finding nothing, uses the default one rather than failing.
        // Sharing one schema between all tenants is precisely what this class exists to prevent, and it
        // would report nothing at all.
        final TenantId acme = new TenantId("acme");
        final TenantRepository tenantRepository = () -> Stream.of(acme);
        final TenantContext context = () -> Optional.of(acme);

        final TenantRoutingDataSource testee = new TenantRoutingDataSource(
                "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1", "sa", "", "org.hsqldb.jdbc.JDBCDriver", "main",
                Optional.of(context), tenantRepository);
        testee.afterPropertiesSet();

        assertThat(testee.getResolvedDataSources()).containsKey(testee.determineCurrentLookupKey());
    }

    @Test
    public void testATenantAddedAtRuntimeIsRoutedToo() {

        // Spring copies the target map when the data source is initialised, so putting an entry into the
        // backing map afterwards changes nothing until the resolution is redone. A tenant provisioned while
        // the application runs would otherwise be accepted by the token validation and then served the
        // wrong schema.
        final TenantId beta = new TenantId("beta");
        final TenantRepository tenantRepository = Stream::empty;
        final TenantContext context = () -> Optional.of(beta);

        final TenantRoutingDataSource testee = new TenantRoutingDataSource(
                "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1", "sa", "", "org.hsqldb.jdbc.JDBCDriver", "main",
                Optional.of(context), tenantRepository);
        testee.afterPropertiesSet();
        assertThat(testee.getResolvedDataSources()).doesNotContainKey("beta");

        testee.handleEvent(new TenantAddedEvent(() -> beta));

        assertThat(testee.getResolvedDataSources()).containsKey("beta");
    }

    /** Exposes the routing decision, which Spring keeps protected in another package. */
    private static final class Exposed extends TenantRoutingDataSource {
        private Exposed(final String jdbcUrl, final String username, final String password,
                        final String driverClassName, final String defaultSchemaName,
                        final Optional<TenantContext> context, final TenantRepository tenantRepository) {
            super(jdbcUrl, username, password, driverClassName, defaultSchemaName, context, tenantRepository);
        }

        private javax.sql.DataSource target() {
            return determineTargetDataSource();
        }
    }

    @Test
    public void testAnUnknownTenantIsRefusedRatherThanServedTheDefaultSchema() {

        // A tenant the repository does not know - added to the identity provider but not here yet, or
        // removed - must not quietly read and write the schema everyone else shares.
        final TenantRepository tenantRepository = Stream::empty;
        final TenantContext context = () -> Optional.of(new TenantId("stranger"));

        final Exposed testee = new Exposed(
                "jdbc:hsqldb:mem:main;DB_CLOSE_DELAY=-1", "sa", "", "org.hsqldb.jdbc.JDBCDriver", "main",
                Optional.of(context), tenantRepository);
        testee.afterPropertiesSet();

        assertThatThrownBy(testee::target)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stranger");
    }

}

package org.fuin.cqrs4j.springboot.command.starter;

import org.fuin.cqrs4j.springboot.command.core.CommandExceptionHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link CommandEndpointAutoConfiguration}.
 */
class CommandEndpointAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommandEndpointAutoConfiguration.class));

    @Test
    void testTheExceptionHandlersAreContributed() {
        // An application that only depends on the starter never scans the package the advice lives in.
        // Without this bean every failed command answers 500 with Spring's default error body, and the
        // mapping to 400 / 403 / 404 / 409 the advice declares is dead code.
        runner.run(context -> assertThat(context).hasSingleBean(CommandExceptionHandlers.class));
    }

    @Test
    void testAnApplicationCanSupplyItsOwn() {
        runner.withBean(CommandExceptionHandlers.class, MyHandlers::new)
                .run(context -> assertThat(context.getBean(CommandExceptionHandlers.class))
                        .isInstanceOf(MyHandlers.class));
    }

    /** Stands in for an application that maps some failure of its own. */
    private static final class MyHandlers extends CommandExceptionHandlers {
    }

}

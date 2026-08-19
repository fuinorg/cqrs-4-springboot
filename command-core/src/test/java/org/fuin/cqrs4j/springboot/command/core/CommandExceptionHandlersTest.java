package org.fuin.cqrs4j.springboot.command.core;

import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.core.ResultType;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.ddd4j.core.AggregateVersionConflictException;
import org.fuin.objects4j.common.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CommandExceptionHandlers}.
 * <p>
 * Every case goes through the unchecked wrapper rather than the checked failure, because that is the
 * only thing {@link CommandRestController} ever throws - a mapping that only works on the unwrapped
 * exception is a mapping that never runs.
 */
class CommandExceptionHandlersTest {

    private final CommandExceptionHandlers testee = new CommandExceptionHandlers();

    @Test
    void testItIsAControllerAdvice() {
        assertThat(CommandExceptionHandlers.class.getAnnotation(ControllerAdvice.class)).isNotNull();
    }

    @Test
    void testAMissingAggregateIsNotFound() {
        final ResponseEntity<Result<?>> response = testee.handleCommandExecutionFailed(
                wrapped(new AggregateNotFoundException("MODULE_SETTINGS", "1234")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(response).getType()).isEqualTo(ResultType.ERROR);
    }

    @Test
    void testAVersionConflictIsAConflict() {
        final ResponseEntity<Result<?>> response = testee.handleCommandExecutionFailed(
                wrapped(new AggregateVersionConflictException("MODULE_SETTINGS", "1234", 2, 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void testAnInvalidArgumentIsABadRequest() {
        // What the caller sent cannot be worked with, so it is answered like anything else that fails
        // validation - not as a server error the caller can do nothing about.
        final ResponseEntity<Result<?>> response = testee.handleCommandExecutionFailed(
                wrapped(new ConstraintViolationException("The argument 'moduleId' cannot be null")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).getMessage()).contains("moduleId");
    }

    @Test
    void testAnythingElseIsAServerError() {
        final ResponseEntity<Result<?>> response = testee.handleCommandExecutionFailed(
                wrapped(new IllegalStateException("Boom")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testTheCheckedFailureIsMappedTheSameWay() {
        // Both are accepted: the endpoint wraps, but a caller inside the application may not.
        final ResponseEntity<Result<?>> response = testee.handleCommandExecutionFailed(
                new CommandExecutionFailedException(new AggregateNotFoundException("MODULE_SETTINGS", "1234")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static CommandExecutionRuntimeException wrapped(final Exception cause) {
        return new CommandExecutionRuntimeException(new CommandExecutionFailedException(cause));
    }

    private static Result<?> body(final ResponseEntity<Result<?>> response) {
        final Result<?> body = response.getBody();
        assertThat(body).describedAs("every failure needs a result body").isNotNull();
        return body;
    }

}

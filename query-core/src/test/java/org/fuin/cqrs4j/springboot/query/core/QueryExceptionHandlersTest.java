package org.fuin.cqrs4j.springboot.query.core;

import org.fuin.ddd4j.core.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link QueryExceptionHandlers}.
 */
public class QueryExceptionHandlersTest {

    @Test
    public void testIsAControllerAdviceSoItAppliesToEveryGeneratedViewController() {
        // There is no single query endpoint to attach this to - one controller method per view method - so
        // it has to be an advice rather than something a controller opts into.
        assertThat(QueryExceptionHandlers.class.getAnnotation(ControllerAdvice.class))
                .isNotNull();
    }

    @Test
    public void testMapsUnauthorizedToForbidden() throws NoSuchMethodException {
        // A refused read must not surface as 500: that reads as a broken query server rather than as a
        // permissions problem, and gets diagnosed as one.
        final Method method = QueryExceptionHandlers.class.getMethod("handleForbidden", UnauthorizedException.class);

        final ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
        assertThat(handler).isNotNull();
        assertThat(handler.value()).containsExactly(UnauthorizedException.class);

        final ResponseStatus status = method.getAnnotation(ResponseStatus.class);
        assertThat(status).isNotNull();
        assertThat(status.value()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void testHandlerReturnsNoBody() throws NoSuchMethodException {
        // A command reports its outcome as a result object; a refused read has no result to report, and the
        // reason belongs in the log rather than in the answer.
        assertThat(QueryExceptionHandlers.class.getMethod("handleForbidden", UnauthorizedException.class)
                .getReturnType()).isEqualTo(void.class);
    }

    @Test
    public void testHandlingDoesNotThrow() {
        assertThatCode(() -> new QueryExceptionHandlers().handleForbidden(new UnauthorizedException()))
                .doesNotThrowAnyException();
    }

}

package org.fuin.cqrs4j.springboot.query.core;

import org.fuin.ddd4j.core.UnauthorizedException;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Translates a refused read into HTTP {@code 403 Forbidden}.
 * <p>
 * The command side has an equivalent in {@code CommandExceptionHandlers}, but that class lives in
 * {@code command-core} and nothing on the query side depends on it. In a combined deployment it happens to
 * be present; in a query server deployed on its own it is not, and without this advice a refused read would
 * surface as {@code 500} - which reads as a broken query server rather than as a permissions problem, and
 * would be diagnosed as one.
 * <p>
 * No response body. A command reports its outcome as a result object, but a read that was refused has no
 * result to report, and the reason for the refusal belongs in the log rather than in the answer. Where both
 * advices are present, either may handle the exception; both answer {@code 403}, which is the part that is
 * contractual.
 */
@ThreadSafe
@ControllerAdvice
public class QueryExceptionHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(QueryExceptionHandlers.class);

    /**
     * Handles a refused read and maps it to HTTP status {@code 403 Forbidden}.
     *
     * @param ex Exception that occurred.
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(UnauthorizedException.class)
    public void handleForbidden(final UnauthorizedException ex) {
        // The exception carries no message of its own; the authorizer logged which permission was missing.
        LOG.debug("Refused a read the caller is not authorized for");
    }

}

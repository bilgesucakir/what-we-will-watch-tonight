package com.whatwewillwatchtonight.controller.error;

/**
 * Base for request problems the API reports as a {@code 400} with a plain,
 * user-facing message. Each subclass owns the wording for one kind of problem;
 * {@link ApiExceptionHandler} turns any of them into the response.
 */
public abstract class ApiException extends RuntimeException {

    protected ApiException(String message) {
        super(message);
    }
}

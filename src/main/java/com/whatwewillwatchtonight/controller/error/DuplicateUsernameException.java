package com.whatwewillwatchtonight.controller.error;

/**
 * Thrown when the same Letterboxd username is given in more than one field.
 */
public class DuplicateUsernameException extends ApiException {

    public DuplicateUsernameException() {
        super("Enter a different username in each field.");
    }
}

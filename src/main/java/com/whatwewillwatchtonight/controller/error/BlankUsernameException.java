package com.whatwewillwatchtonight.controller.error;

/**
 * Thrown when a required username parameter is present but empty or whitespace.
 */
public class BlankUsernameException extends ApiException {

    public BlankUsernameException() {
        super("Fill in every username.");
    }
}

package com.whatwewillwatchtonight.controller.error;

/**
 * Thrown when {@code /api/intersect} is called with fewer or more usernames
 * than it supports.
 */
public class InvalidUsernameCountException extends ApiException {

    public InvalidUsernameCountException(int min, int max) {
        super("Enter between " + min + " and " + max + " usernames.");
    }
}

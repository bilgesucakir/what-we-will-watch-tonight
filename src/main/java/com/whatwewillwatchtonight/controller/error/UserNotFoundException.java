package com.whatwewillwatchtonight.controller.error;

import java.util.List;

/**
 * Thrown when one or more of the given usernames don't exist on Letterboxd.
 */
public class UserNotFoundException extends ApiException {

    public UserNotFoundException(List<String> usernames) {
        super(usernames.size() == 1
                ? "There's no Letterboxd user named '" + usernames.get(0) + "'."
                : "No Letterboxd users named: " + String.join(", ", usernames) + ".");
    }
}

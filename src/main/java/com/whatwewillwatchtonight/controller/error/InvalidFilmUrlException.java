package com.whatwewillwatchtonight.controller.error;

/**
 * Thrown when a poster request names something other than a Letterboxd film
 * URL, or leaves out a film's title.
 */
public class InvalidFilmUrlException extends ApiException {

    public InvalidFilmUrlException(String url) {
        super("Not a Letterboxd film: " + url + ".");
    }
}

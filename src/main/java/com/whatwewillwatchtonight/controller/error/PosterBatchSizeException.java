package com.whatwewillwatchtonight.controller.error;

/**
 * Thrown when a poster request is empty or asks for more films than one page.
 */
public class PosterBatchSizeException extends ApiException {

    public PosterBatchSizeException(int max) {
        super("Ask for between 1 and " + max + " posters at a time.");
    }
}

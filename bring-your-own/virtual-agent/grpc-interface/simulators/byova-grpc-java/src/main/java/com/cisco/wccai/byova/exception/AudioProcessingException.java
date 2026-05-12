package com.cisco.wccai.byova.exception;

import java.io.Serial;

/** Thrown when audio data cannot be read, decoded, or persisted. */
public class AudioProcessingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AudioProcessingException(String message) {
        super(message);
    }

    public AudioProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

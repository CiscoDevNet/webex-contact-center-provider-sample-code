package com.cisco.wccai.common;

import java.io.Serial;

public class AudioProcessingException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 5646651237007609861L;

    public AudioProcessingException(String message) {
        super(message);
    }

    public AudioProcessingException(String message, Throwable throwable) {
        super(message, throwable);
    }
}

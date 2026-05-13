package com.cisco.wccai.forking.grpc;

import com.cisco.wcc.ccai.media.v1.Conversationaudioforking.ConversationAudioForkingRequest;
import com.cisco.wcc.ccai.media.v1.Conversationaudioforking.ConversationAudioForkingResponse;
import com.cisco.wccai.forking.service.ConversationAudioProcessor;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-RPC observer that handles a single Conversation Audio Forking stream.
 *
 * <p>The wire contract is: the client sends one {@link ConversationAudioForkingRequest} per
 * audio frame, and the server replies with a single {@link ConversationAudioForkingResponse}
 * carrying {@code status_message="SUCCESS"} when the client half-closes the stream. Errors are
 * propagated to the response observer so the client's {@code onError} callback is invoked.
 */
@Slf4j
public class ConversationAudioRequestObserver
        implements StreamObserver<ConversationAudioForkingRequest> {

    private final StreamObserver<ConversationAudioForkingResponse> responseObserver;
    private final ConversationAudioProcessor processor;

    private volatile String conversationId;

    public ConversationAudioRequestObserver(
            StreamObserver<ConversationAudioForkingResponse> responseObserver,
            ConversationAudioProcessor processor) {
        this.responseObserver = responseObserver;
        this.processor = processor;
    }

    @Override
    public void onNext(ConversationAudioForkingRequest request) {
        conversationId = request.getConversationId();
        try {
            processor.process(request);
        } catch (RuntimeException e) {
            log.error("Failed to process forked audio for conversationId={}: {}",
                    conversationId, e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("Conversation Audio Forking stream error for conversationId={}: {}",
                conversationId, throwable.getMessage(), throwable);
        if (conversationId != null) {
            processor.onConversationCompleted(conversationId);
        }
        responseObserver.onError(throwable);
    }

    @Override
    public void onCompleted() {
        log.info("Conversation Audio Forking stream completed for conversationId={}", conversationId);
        if (conversationId != null) {
            processor.onConversationCompleted(conversationId);
        }
        responseObserver.onNext(
                ConversationAudioForkingResponse.newBuilder().setStatusMessage("SUCCESS").build());
        responseObserver.onCompleted();
    }
}

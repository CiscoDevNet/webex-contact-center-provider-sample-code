package com.cisco.wccai.forking.grpc;

import com.cisco.wcc.ccai.media.v1.ConversationAudioGrpc;
import com.cisco.wcc.ccai.media.v1.Conversationaudioforking.ConversationAudioForkingRequest;
import com.cisco.wcc.ccai.media.v1.Conversationaudioforking.ConversationAudioForkingResponse;
import com.cisco.wccai.forking.service.ConversationAudioProcessor;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Entry point for the {@code ConversationAudio} gRPC service. Each bidirectional streaming call
 * gets its own {@link ConversationAudioRequestObserver} so per-call state (counters, file
 * handles) is isolated between concurrent conversations.
 */
@Slf4j
@Component
public class ConversationAudioGrpcService extends ConversationAudioGrpc.ConversationAudioImplBase {

    private final ConversationAudioProcessor processor;

    public ConversationAudioGrpcService(ConversationAudioProcessor processor) {
        this.processor = processor;
    }

    @Override
    public StreamObserver<ConversationAudioForkingRequest> streamConversationAudio(
            StreamObserver<ConversationAudioForkingResponse> responseObserver) {
        return new ConversationAudioRequestObserver(responseObserver, processor);
    }
}

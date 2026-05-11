package com.cisco.wccai.byova.grpc;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wcc.ccai.media.v1.VoiceVirtualAgentGrpc;
import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.byova.config.VoiceVaProperties;
import com.cisco.wccai.byova.service.SilenceDetector;
import com.cisco.wccai.byova.service.VoiceVAResponseBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Entry point for the {@code VoiceVirtualAgent} gRPC service. Each bidirectional streaming call
 * gets its own {@link VoiceVARequestObserver} so per-call state (audio buffers, DTMF flags) is
 * isolated between concurrent conversations.
 */
@Slf4j
@Component
public class VoiceVAGrpcService extends VoiceVirtualAgentGrpc.VoiceVirtualAgentImplBase {

    private final VoiceVAResponseBuilder responseBuilder;
    private final SilenceDetector silenceDetector;
    private final VoiceVaProperties properties;

    public VoiceVAGrpcService(
            VoiceVAResponseBuilder responseBuilder,
            SilenceDetector silenceDetector,
            VoiceVaProperties properties) {
        this.responseBuilder = responseBuilder;
        this.silenceDetector = silenceDetector;
        this.properties = properties;
    }

    @Override
    public StreamObserver<Voicevirtualagent.VoiceVARequest> processCallerInput(
            StreamObserver<Voicevirtualagent.VoiceVAResponse> responseObserver) {
        return new VoiceVARequestObserver(responseObserver, responseBuilder, silenceDetector, properties);
    }

    @Override
    public void listVirtualAgents(
            ByovaCommon.ListVARequest request, StreamObserver<ByovaCommon.ListVAResponse> responseObserver) {
        log.info("ListVA request received for orgId: {}", request.getCustomerOrgId());
        responseObserver.onNext(responseBuilder.sampleVirtualAgents());
        responseObserver.onCompleted();
    }
}

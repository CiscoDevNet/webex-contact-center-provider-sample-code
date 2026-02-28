package com.cisco.wccai.grpc.server;

import com.cisco.wcc.ccai.media.v1.ConversationAudioGrpc;
import com.cisco.wcc.ccai.media.v1.Conversationaudioforking;
// NEW: Import the common types which include the AudioEncoding enum.
import com.cisco.wcc.ccai.media.v1.MediaServiceCommon;
import com.cisco.wcc.ccai.media.v1.MediaServiceCommon.AudioEncoding;

import com.google.cloud.storage.*;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static com.cisco.wcc.ccai.media.v1.MediaServiceCommon.AudioEncoding.LINEAR16_VALUE;
import static com.cisco.wcc.ccai.media.v1.MediaServiceCommon.AudioEncoding.MULAW_VALUE;

public class ConversationAudioForkServiceImpl extends ConversationAudioGrpc.ConversationAudioImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationAudioForkServiceImpl.class);

    private final Storage storage;
    private final String bucketName;

    /**
     * Constructor initializes the Google Cloud Storage client and gets the bucket name.
     */
    public ConversationAudioForkServiceImpl() {
        LOGGER.info("ConversationAudioForkServiceImpl constructor called");
        this.storage = StorageOptions.getDefaultInstance().getService();

        if (this.storage == null) {
            LOGGER.error("Could not get default storage instance. This service will not be able to save audio.");
        }

        this.bucketName = System.getenv("GCS_BUCKET_NAME");
        if (this.bucketName == null || this.bucketName.isEmpty()) {
            LOGGER.error("GCS_BUCKET_NAME environment variable not set. Audio will not be saved.");
        } else {
            LOGGER.info("GCS_BUCKET_NAME environment variable set to {}", bucketName);
        }
    }

    @Override
    public StreamObserver<Conversationaudioforking.ConversationAudioForkingRequest> streamConversationAudio(
            StreamObserver<Conversationaudioforking.ConversationAudioForkingResponse> responseObserver) {

        if (bucketName == null || storage == null) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription("Server is not configured to save audio streams.")
                            .asRuntimeException()
            );
            return new NoOpStreamObserver();
        }
        return new AudioStreamToGcsHandler(responseObserver, storage, bucketName);
    }

    /**
     * A dedicated handler that buffers audio streams and saves them as WAV files in GCS.
     */
    private static class AudioStreamToGcsHandler implements StreamObserver<Conversationaudioforking.ConversationAudioForkingRequest> {
        private static final int NUM_CHANNELS = 1; // Each roleId is treated as a separate mono channel.

        private final StreamObserver<Conversationaudioforking.ConversationAudioForkingResponse> responseObserver;
        private final Storage storage;
        private final String bucketName;

        private final Map<String, ByteArrayOutputStream> audioBuffers = new HashMap<>();
        private String conversationId;

        // MODIFIED: These fields will be populated dynamically from the first request.
        private int sampleRate;
        private int bitsPerSample;
        private int audioFormatCode; // e.g., 1 for PCM, 7 for u-law

        public AudioStreamToGcsHandler(StreamObserver<Conversationaudioforking.ConversationAudioForkingResponse> responseObserver, Storage storage, String bucketName) {
            this.responseObserver = responseObserver;
            this.storage = storage;
            this.bucketName = bucketName;
            LOGGER.info("AudioStreamToGcsHandler initialized");
        }

        @Override
        public void onNext(Conversationaudioforking.ConversationAudioForkingRequest request) {
            // On the first message, capture the stream's format from the proto message.
            if (this.conversationId == null) {
                this.conversationId = request.getConversationId();
                Conversationaudioforking.AudioStream audio = request.getAudio();
                this.sampleRate = audio.getSampleRateHertz();

                // MODIFIED: Determine format based on the protobuf enum.
                // This makes the handler robust and able to handle multiple encodings.
                switch (audio.getEncoding()) {
                    case MULAW:
                        this.audioFormatCode = 7; // WAV format code for G.711 u-law
                        this.bitsPerSample = 8;
                        break;
                    case LINEAR16:
                        this.audioFormatCode = 1; // WAV format code for PCM
                        this.bitsPerSample = 16;
                        break;
                    default:
                        LOGGER.error("Unsupported audio encoding received: {}", audio.getEncoding());
                        responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("Unsupported audio encoding: " + audio.getEncoding())
                                .asRuntimeException());
                        cleanup();
                        return; // Stop processing
                }
                LOGGER.info("Detected audio stream format: {} Hz, {} bits, format code {}. Ready to receive audio.",
                        this.sampleRate, this.bitsPerSample, this.audioFormatCode);
            }

            String roleId = request.getAudio().getRoleId();
            ByteString audioData = request.getAudio().getAudioData();

            ByteArrayOutputStream buffer = audioBuffers.computeIfAbsent(roleId, k -> new ByteArrayOutputStream());
            try {
                if (audioData != null && !audioData.isEmpty()) {
                    audioData.writeTo(buffer);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write to in-memory audio buffer for roleId: {}", roleId, e);
                responseObserver.onError(Status.INTERNAL.withDescription("Failed to buffer audio data.").asRuntimeException());
                cleanup();
            }

            responseObserver.onNext(Conversationaudioforking.ConversationAudioForkingResponse.newBuilder()
                    .setStatusMessage("Processed chunk for conversationId: " + request.getConversationId())
                    .build());
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.error("Client stream produced an error", t);
            cleanup();
        }

        @Override
        public void onCompleted() {
            LOGGER.info("Client has finished sending audio. Finalizing WAV files for conversationId: {}", conversationId);
            finalizeAndUpload();
            responseObserver.onCompleted();
        }

        private void finalizeAndUpload() {
            if (conversationId == null) {
                LOGGER.warn("onCompleted called but no requests were received; nothing to upload.");
                return;
            }

            for (Map.Entry<String, ByteArrayOutputStream> entry : audioBuffers.entrySet()) {
                String roleId = entry.getKey();
                byte[] audioData = entry.getValue().toByteArray();

                if (audioData.length == 0) {
                    LOGGER.info("Skipping upload for roleId '{}' as no audio data was received.", roleId);
                    continue;
                }

                try {
                    // 1. Create the WAV header with the dynamically determined format.
                    byte[] header = createWavHeader(audioData.length, this.sampleRate, this.bitsPerSample, this.audioFormatCode);

                    // 2. Combine header and audio data.
                    byte[] wavFileBytes = new byte[header.length + audioData.length];
                    System.arraycopy(header, 0, wavFileBytes, 0, header.length);
                    System.arraycopy(audioData, 0, wavFileBytes, header.length, audioData.length);

                    // 3. Define the GCS object and upload the complete WAV file.
                    String gcsObjectName = String.format("audio/%s-%s.wav", conversationId, roleId);
                    BlobId blobId = BlobId.of(bucketName, gcsObjectName);
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                            .setContentType("audio/wav")
                            .build();

                    storage.create(blobInfo, wavFileBytes);
                    LOGGER.info("Successfully uploaded gs://{}/{}", bucketName, gcsObjectName);

                } catch (Exception e) {
                    LOGGER.error("Failed to create or upload WAV file for roleId: {}", roleId, e);
                }
            }
            cleanup();
        }

        private void cleanup() {
            audioBuffers.clear();
        }

        /**
         * Creates a 44-byte WAV file header for a given audio format.
         *
         * @param audioDataLength The length of the raw audio data in bytes.
         * @param sampleRate      The sample rate (e.g., 8000).
         * @param bitsPerSample   The number of bits per sample (e.g., 8 for u-law, 16 for PCM).
         * @param formatCode      The WAV format code (1 for PCM, 7 for u-law).
         * @return A byte array containing the WAV header.
         */
        private byte[] createWavHeader(long audioDataLength, int sampleRate, int bitsPerSample, int formatCode) {
            long totalDataLen = audioDataLength + 36;
            long byteRate = (long) sampleRate * NUM_CHANNELS * bitsPerSample / 8;
            int blockAlign = NUM_CHANNELS * bitsPerSample / 8;

            ByteBuffer buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);

            buffer.put("RIFF".getBytes());
            buffer.putInt((int) totalDataLen);
            buffer.put("WAVE".getBytes());
            buffer.put("fmt ".getBytes());
            buffer.putInt(16); // Sub-chunk size for PCM
            buffer.putShort((short) formatCode);
            buffer.putShort((short) NUM_CHANNELS);
            buffer.putInt(sampleRate);
            buffer.putInt((int) byteRate);
            buffer.putShort((short) blockAlign);
            buffer.putShort((short) bitsPerSample);
            buffer.put("data".getBytes());
            buffer.putInt((int) audioDataLength);

            return buffer.array();
        }
    }

    /**
     * A simple no-op observer to handle cases where the service can't start.
     */
    private static class NoOpStreamObserver implements StreamObserver<Conversationaudioforking.ConversationAudioForkingRequest> {
        @Override
        public void onNext(Conversationaudioforking.ConversationAudioForkingRequest value) {}
        @Override
        public void onError(Throwable t) {}
        @Override
        public void onCompleted() {}
    }
}

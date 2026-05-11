package com.cisco.wccai.util;

import com.cisco.wccai.common.AudioConstant;
import com.cisco.wccai.common.AudioProcessingException;
import com.google.protobuf.ByteString;
import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

/**
 * Utility class for loading audio asset bytes from the classpath and optionally writing captured
 * caller audio to local WAV files for debugging. Works in terms of raw bytes / ByteString so it
 * plugs directly into protobuf messages such as {@code VoiceInput.caller_audio} and
 * {@code Prompt.audio_content}.
 */
@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class AudioFileUtil {
    private static final String AUDIO_FILE_OUTPUT_DIR = String.format("%s%srecorded-audio",
            System.getProperty("user.home"),
            File.separator);
    private static boolean isDirectoryCreated = false;

    @Getter
    private static final byte[] muLawWavHeader = new byte[]
            {
                    0x52, 0x49, 0x46, 0x46, // Group id: "RIFF"
                    0x00, 0x00, 0x00, 0x00, // Remainder Length: to be calculated
                    0x57, 0x41, 0x56, 0x45, // Riff Type: "WAVE"
                    0x66, 0x6D, 0x74, 0x20, // Chunk id: "fmt "
                    0x12, 0x00, 0x00, 0x00, // Sub-chunk Size: 18 bytes
                    0x07, 0x00, // Format Tag: ITU G.711 µ-law
                    0x01, 0x00, // Channels: mono
                    0x40, 0x1F, 0x00, 0x00, // Samples Per Second: 8000
                    0x40, 0x1F, 0x00, 0x00, // Average Bytes Per Second: 8000
                    0x01, 0x00, // Block Align
                    0x08, 0x00, // Bits Per Sample: 8 bits
                    0x00, 0x00, // Extra Format Size: none
                    0x66, 0x61, 0x63, 0x74, // Chunk id: "fact"
                    0x04, 0x00, 0x00, 0x00, // Sub-chunk Size: 4 bytes
                    0x00, 0x00, 0x00, 0x00, // samples in "data": to be calculated
                    0x64, 0x61, 0x74, 0x61, // Chunk id: "data
                    (byte) 0xFF, (byte) 0xFF, // Sub-chunk 3ID
                    (byte) 0xFF, (byte) 0xFF, // Sub-chunk 3: to be calculated
            };

    public static void writeWavWithMuLaw(byte[] audioData) {
        AudioFormat muLawFormat = new AudioFormat(
                AudioFormat.Encoding.ULAW, // Encoding
                8000,                       // Sample Rate
                8,                         // Sample Size in Bits
                1,                         // Channels (mono)
                1,                         // Frame Size (bytes per frame for ULAW 8-bit mono)
                8000,                      // Frame Rate
                false                      // Big Endian (not critical for 8-bit, but standard)
        );

        String filePath = Paths.get(AUDIO_FILE_OUTPUT_DIR, UUID.randomUUID() + "mu-law.wav").toString();
        writeWavFile(audioData, muLawFormat, filePath);
    }

    public static void writeWavWithMuLaw(ByteString audioData) {
        writeWavWithMuLaw(audioData.toByteArray());
    }

    private static void writeWavFile(byte[] audioData, AudioFormat format, String filePath) {
        validateInputs(audioData, format, filePath);

        try (AudioInputStream audioInputStream = createAudioInputStream(audioData, format)) {
            writeToFile(audioInputStream, filePath);
            log.info("Successfully created WAV file: {}", filePath);
        } catch (IOException e) {
            log.error("Error creating WAV file {}", filePath, e);
        }
    }

    /**
     * Validates the input parameters for WAV file creation
     */
    private static void validateInputs(byte[] audioData, AudioFormat format, String filePath) {
        if (Objects.isNull(audioData) || audioData.length == 0) {
            throw new IllegalArgumentException("Audio data cannot be null or empty");
        }
        if (Objects.isNull(format)) {
            throw new IllegalArgumentException("Audio format cannot be null");
        }
        if (Objects.isNull(filePath) || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
    }

    /**
     * Creates an AudioInputStream from the raw audio data
     */
    private static AudioInputStream createAudioInputStream(byte[] audioData, AudioFormat format) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
        long frameLength = audioData.length / format.getFrameSize();
        return new AudioInputStream(byteArrayInputStream, format, frameLength);
    }

    /**
     * Writes the AudioInputStream to a WAV file
     */
    private static void writeToFile(AudioInputStream ais, String filePath) throws IOException {
        File outputFile = new File(filePath);
        if (isDirectoryCreated) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
        } else {
            createDirectory(outputFile);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
        }
    }

    /**
     * Ensures the parent directory exists
     */
    private static void createDirectory(File file) {
        File parent = file.getParentFile();
        if (Objects.nonNull(parent) && Boolean.FALSE.equals(parent.exists()) && Boolean.FALSE.equals(parent.mkdirs())) {
            log.error("Failed to create directory for file: {}", file.getAbsolutePath());
        } else {
            isDirectoryCreated = true;
            log.info("Directory created successfully for file: {}", file.getAbsolutePath());
        }
    }

    /**
     * Loads an audio resource (e.g. {@code "call-start.wav"}) from {@code src/main/resources/audio/}
     * and returns it as a protobuf {@link ByteString} suitable for assigning directly to
     * {@code Prompt.audio_content}.
     */
    public static ByteString audioContentFromResources(String fileName) {
        return ByteString.copyFrom(audioBytesFromResources(fileName));
    }

    public static byte[] audioBytesFromResources(String fileName) {
        return audioToByteArray(String.format("%s%s", AudioConstant.BASE_PATH, fileName));
    }

    public static byte[] audioToByteArray(String path) {
        if (StringUtils.isBlank(path)) {
            throw new IllegalArgumentException("Audio file path cannot be null or empty");
        }

        try (InputStream inputStream = AudioFileUtil.class.getClassLoader().getResourceAsStream(path)) {
            if (Objects.isNull(inputStream)) {
                throw new IllegalArgumentException("Audio file resource not found: " + path);
            }
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new AudioProcessingException(String.format("Error reading audio file %s.", path), e);
        }
    }
}

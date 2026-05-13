package com.cisco.wccai.util;

import com.cisco.wccai.common.AudioConstant;
import com.cisco.wccai.common.AudioProcessingException;
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
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

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

    public static void writeWavWithPcm(short[] pcmSamples) {
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, // Encoding
                8000,                            // Sample Rate
                16,                              // Sample Size in Bits
                1,                               // Channels (mono)
                2,                               // Frame Size (2 bytes for 16-bit mono PCM)
                8000,                            // Frame Rate
                false                            // Big Endian (PCM data in WAV is usually little-endian,
                // but AudioSystem handles this based on format.
                // ByteBuffer ensures little-endian for the byte array itself)
        );

        byte[] audioData = AudioFormatUtil.convertPCMShortsToBytes(pcmSamples);
        String base64EncodedAudio = Base64.getEncoder().encodeToString(muLawWavHeader).concat(Base64.getEncoder().encodeToString(audioData));
        String filePath = Paths.get(AUDIO_FILE_OUTPUT_DIR, UUID.randomUUID() + "pcm.wav").toString();
        writeWavFile(Base64.getDecoder().decode(base64EncodedAudio), pcmFormat, filePath);
    }

    public static void writeWavWithMuLaw(String base64Audio) {
        writeWavWithMuLaw(Base64.getDecoder().decode(base64Audio));
    }

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

    public static String audioContentFromResources(String fileName) {
        return audioToBase64String(String.format("%s%s", AudioConstant.BASE_PATH, fileName));
    }

    public static String audioToBase64String(String path) {
        if (StringUtils.isBlank(path)) {
            throw new IllegalArgumentException("Audio file path cannot be null or empty");
        }
        try (InputStream inputStream = AudioFileUtil.class.getClassLoader().getResourceAsStream(path)) {
            if (Objects.isNull(inputStream)) {
                throw new IllegalArgumentException("Audio file resource not found: " + path);
            }
            byte[] audioBytes = inputStream.readAllBytes();
            return Base64.getEncoder().encodeToString(audioBytes);
        } catch (Exception e) {
            throw new AudioProcessingException(String.format("Error reading audio file %s.", path), e);
        }
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

    public static String audioContentWithoutHeader(String fileName) {
        return audioToByteStringWithoutHeader(String.format("%s%s", AudioConstant.BASE_PATH, fileName));
    }

    public static String audioToByteStringWithoutHeader(String path) {
        if (StringUtils.isBlank(path)) {
            throw new IllegalArgumentException("Audio file path to read without header cannot be null or empty");
        }

        InputStream resourceStream = AudioFileUtil.class.getClassLoader().getResourceAsStream(path);
        if (Objects.isNull(resourceStream)) {
            throw new IllegalArgumentException("Audio file resource not found: " + path);
        }
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(resourceStream)) {
            // Get total frames and frame size
            long frameLength = audioInputStream.getFrameLength();
            int frameSize = audioInputStream.getFormat().getFrameSize();

            // Calculate data size
            byte[] audioData = new byte[(int) (frameLength * frameSize)];

            // Read the actual audio data
            audioInputStream.read(audioData);
            return Base64.getEncoder().encodeToString(audioData);
        } catch (Exception e) {
            throw new AudioProcessingException(String.format("Error reading without header audio file %s.", path), e);
        }
    }
}

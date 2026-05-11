package com.cisco.wccai.byova.audio;

import com.cisco.wccai.byova.exception.AudioProcessingException;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads audio assets bundled on the classpath and (optionally) writes captured caller audio to
 * WAV files for debugging. All methods are static since no per-instance state is required.
 */
@Slf4j
public final class AudioFileLoader {

    /**
     * µ-law WAV header (58 bytes) for 8 kHz / mono / 8-bit audio. The size fields (RIFF remainder,
     * sample count, data chunk size) are intentionally left as {@code 0xFFFF} / {@code 0x00000000}
     * placeholders: the sample streams µ-law bytes as they arrive, so the absolute length is not
     * known at header-write time. Most players handle the unspecified-length case correctly.
     */
    private static final byte[] MU_LAW_WAV_HEADER = {
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            0x00, 0x00, 0x00, 0x00, // Remainder length (unspecified)
            0x57, 0x41, 0x56, 0x45, // "WAVE"
            0x66, 0x6D, 0x74, 0x20, // "fmt "
            0x12, 0x00, 0x00, 0x00, // sub-chunk size: 18 bytes
            0x07, 0x00,             // format tag: ITU G.711 µ-law
            0x01, 0x00,             // channels: mono
            0x40, 0x1F, 0x00, 0x00, // samples/sec: 8000
            0x40, 0x1F, 0x00, 0x00, // avg bytes/sec: 8000
            0x01, 0x00,             // block align
            0x08, 0x00,             // bits/sample: 8
            0x00, 0x00,             // extra format size
            0x66, 0x61, 0x63, 0x74, // "fact"
            0x04, 0x00, 0x00, 0x00, // sub-chunk size: 4 bytes
            0x00, 0x00, 0x00, 0x00, // samples count (unspecified)
            0x64, 0x61, 0x74, 0x61, // "data"
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF // data chunk size (unspecified)
    };

    private static final Path AUDIO_OUTPUT_DIR =
            Paths.get(System.getProperty("user.home"), "recorded-audio");

    private AudioFileLoader() {
    }

    /** Returns a defensive copy of the canned µ-law WAV header. */
    public static byte[] getMuLawWavHeader() {
        return MU_LAW_WAV_HEADER.clone();
    }

    /**
     * Loads an audio asset (e.g. {@code "call-start.wav"}) from the classpath
     * {@code audio/} folder and returns its full content.
     */
    public static ByteString audioContentFromResources(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Audio file name cannot be null or empty");
        }
        return audioToByteStringFromResources(AudioConstants.AUDIO_RESOURCE_PREFIX + fileName);
    }

    /**
     * Reads an arbitrary classpath resource (e.g. {@code "audio/call-start.wav"}) into memory.
     */
    public static ByteString audioToByteStringFromResources(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Audio resource path cannot be null or empty");
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = AudioFileLoader.class.getClassLoader();
        }
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new AudioProcessingException("Audio resource not found on classpath: " + resourcePath);
            }
            return ByteString.readFrom(in);
        } catch (IOException e) {
            throw new AudioProcessingException("Error reading audio resource " + resourcePath, e);
        }
    }

    /**
     * Persists the supplied µ-law audio bytes to a timestamped WAV file under
     * {@code ~/recorded-audio/}. Intended only for local debugging flows (controlled by
     * {@code voice.va.audio.write-to-file}).
     */
    public static void writeWavWithMuLaw(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            log.warn("Skipping WAV write: audio data is null or empty");
            return;
        }
        AudioFormat muLawFormat = new AudioFormat(
                AudioFormat.Encoding.ULAW, 8_000f, 8, 1, 1, 8_000f, false);

        Path outputFile = AUDIO_OUTPUT_DIR.resolve(UUID.randomUUID() + "-mu-law.wav");
        try {
            Files.createDirectories(AUDIO_OUTPUT_DIR);
        } catch (IOException e) {
            log.error("Failed to create audio output directory {}", AUDIO_OUTPUT_DIR, e);
            return;
        }
        long frames = audioData.length / (long) muLawFormat.getFrameSize();
        try (AudioInputStream ais =
                     new AudioInputStream(new ByteArrayInputStream(audioData), muLawFormat, frames)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile.toFile());
            log.info("Wrote captured caller audio to {}", outputFile);
        } catch (IOException e) {
            log.error("Failed to write WAV file {}", outputFile, e);
        }
    }

    /** Convenience overload that writes the contents of a ByteString to WAV. */
    public static void writeWavWithMuLaw(ByteString audio) {
        writeWavWithMuLaw(audio.toByteArray());
    }

    /** Convenience accessor used in tests that prefer working with {@link File}. */
    static Path audioOutputDir() {
        return AUDIO_OUTPUT_DIR;
    }
}

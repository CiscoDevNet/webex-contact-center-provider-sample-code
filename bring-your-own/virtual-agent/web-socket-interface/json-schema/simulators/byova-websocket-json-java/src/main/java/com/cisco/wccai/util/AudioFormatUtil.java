package com.cisco.wccai.util;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Utility class for converting between µ-law encoded audio and linear PCM samples.
 * This implementation follows the G.711 µ-law standard.
 */
@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class AudioFormatUtil {

    private static final int SIGN_BIT = 0x80;
    private static final int QUANT_MASK = 0x0F;
    private static final int SEG_SHIFT = 4;
    private static final int BIAS = 0x84; // or 33, standard is 0x84 (132) for G.711 µ-law
    private static final int MAX_PCM_VALUE = 8031; // Max value for µ-law expanded to 14-bit linear

    // Precomputed tables for faster linear2MuLaw conversion. For 16-bit signed shorts (-32768 to 32767).
    private static final byte[] muLawLookupTable = new byte[65536];

    static {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            muLawLookupTable[i - Short.MIN_VALUE] = linearToMuLawSample((short) i);
        }
    }

    /**
     * Decodes a chunk of µ-law bytes into an array of linear PCM shorts
     * using the standard decoder.
     */
    public static short[] muLawToLinear(byte[] muLawChunk) {
        if (muLawChunk == null) {
            return new short[0];
        }
        short[] pcmChunk = new short[muLawChunk.length];
        for (int i = 0; i < muLawChunk.length; i++) {
            // Pass the byte as an int 0-255 to standardMuLawToLinear
            pcmChunk[i] = standardMuLawToLinear(muLawChunk[i] & 0xFF);
        }
        return pcmChunk;
    }

    /**
     * Decodes a single 8-bit µ-law sample to a 16-bit linear PCM sample.
     * This is a standard G.711 µ-law decoding algorithm.
     *
     * @param ulawVal The 8-bit µ-law encoded sample (as an int 0-255 for convenience).
     * @return The 16-bit linear PCM sample.
     */
    public static short standardMuLawToLinear(int ulawVal) {
        ulawVal = ~ulawVal; // Invert all bits (standard part of G.711 µ-law decoding)

        int sign = (ulawVal & 0x80);
        int exponent = (ulawVal >> 4) & 0x07;
        int mantissa = ulawVal & 0x0F;

        // Calculate magnitude
        // ( (mantissa << 1) + 33 ) is because the steps are mid-riser
        // << exponent is applying the segment scaling
        int magnitude = ((mantissa << 1) + 33) << exponent;
        magnitude -= 33; // Subtract bias

        return (short) (sign == 0 ? magnitude : -magnitude);
    }

    /**
     * Encodes a single 16-bit linear PCM sample to an 8-bit µ-law byte using lookup.
     */
    public static byte linearToMuLaw(short pcmSample) {
        return muLawLookupTable[pcmSample - Short.MIN_VALUE];
    }

    /**
     * Encodes a single 16-bit linear PCM sample to an 8-bit µ-law byte (direct calculation).
     * This is the G.711 µ-law encoding algorithm.
     */
    private static byte linearToMuLawSample(short pcmVal) {
        int sign;
        int exponent;
        int mantissa;
        byte muLawByte;
        int absPcm = Math.abs(pcmVal);

        // Clamp to 14-bit range effectively, as µ-law covers this.
        // G.711 µ-law is defined for a 13-bit magnitude, but often implemented with 14-bit input.
        if (absPcm > MAX_PCM_VALUE) {
            absPcm = MAX_PCM_VALUE;
        }
        sign = (pcmVal < 0) ? 0x00 : SIGN_BIT; // Sign bit is 0 for negative in G.711 µ-law after inversion

        // Add bias
        absPcm += BIAS;

        // Find exponent and mantissa
        if (absPcm <= 0xFF) { // <= 255
            exponent = 0;
            mantissa = (absPcm - 33) >> 1; // Approximation
        } else if (absPcm <= 0x1FF) { // <= 511
            exponent = 1;
            mantissa = (absPcm - 256 - 33) >> 2;
        } else if (absPcm <= 0x3FF) { // <= 1023
            exponent = 2;
            mantissa = (absPcm - 512 - 33) >> 3;
        } else if (absPcm <= 0x7FF) { // <= 2047
            exponent = 3;
            mantissa = (absPcm - 1024 - 33) >> 4;
        } else if (absPcm <= 0xFFF) { // <= 4095
            exponent = 4;
            mantissa = (absPcm - 2048 - 33) >> 5;
        } else if (absPcm <= 0x1FFF) { // <= 8191
            exponent = 5;
            mantissa = (absPcm - 4096 - 33) >> 6;
        } else if (absPcm <= 0x3FFF) { // <= 16383
            exponent = 6;
            mantissa = (absPcm - 8192 - 33) >> 7;
        } else { // > 16383
            exponent = 7;
            mantissa = (absPcm - 16384 - 33) >> 8;
            if (mantissa > QUANT_MASK) mantissa = QUANT_MASK; // Clip mantissa
        }
        if (mantissa < 0) mantissa = 0; // Ensure mantissa is not negative

        muLawByte = (byte) (sign | (exponent << SEG_SHIFT) | mantissa);
        return (byte) ~muLawByte; // Invert all bits for final G.711 µ-law byte
    }

    /**
     * Encodes an array of 16-bit linear PCM samples to µ-law bytes.
     */
    public static byte[] linearToMuLaw(short[] pcmSamples) {
        if (pcmSamples == null) {
            return new byte[0];
        }
        byte[] muLawChunk = new byte[pcmSamples.length];
        for (int i = 0; i < pcmSamples.length; i++) {
            muLawChunk[i] = linearToMuLaw(pcmSamples[i]);
        }
        return muLawChunk;
    }

    /**
     * Converts an array of 16-bit PCM shorts to a byte array (little-endian).
     * This is necessary for creating a WAV file with 16-bit PCM data.
     *
     * @param pcmSamples The array of 16-bit PCM samples.
     * @return A byte array representing the PCM data in little-endian format.
     */
    public static byte[] convertPCMShortsToBytes(short[] pcmSamples) {
        if (pcmSamples == null) {
            return new byte[0];
        }
        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(pcmSamples.length * 2);
        byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (short sample : pcmSamples) {
            byteBuffer.putShort(sample);
        }
        return byteBuffer.array();
    }
}

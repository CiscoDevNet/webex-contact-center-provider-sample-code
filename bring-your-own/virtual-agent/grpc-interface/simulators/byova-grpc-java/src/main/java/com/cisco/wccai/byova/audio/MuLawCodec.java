package com.cisco.wccai.byova.audio;

/**
 * Utility for converting between G.711 µ-law encoded audio and linear PCM samples.
 *
 * <p>Follows the ITU-T G.711 µ-law standard. Only decoding is exercised by the sample, but the
 * encoder and PCM-to-bytes helpers are retained to simplify extensions (e.g., writing PCM WAV
 * output).
 */
public final class MuLawCodec {

    private static final int SIGN_BIT = 0x80;
    private static final int QUANT_MASK = 0x0F;
    private static final int SEG_SHIFT = 4;
    private static final int BIAS = 0x84;
    // G.711 µ-law decodes to a 14-bit linear range; this is the positive magnitude cap.
    private static final int MAX_PCM_VALUE = 8_031;

    private MuLawCodec() {
    }

    /** Decodes a chunk of µ-law bytes into 16-bit linear PCM samples. */
    public static short[] muLawToLinear(byte[] muLawChunk) {
        if (muLawChunk == null) {
            return new short[0];
        }
        short[] pcm = new short[muLawChunk.length];
        for (int i = 0; i < muLawChunk.length; i++) {
            pcm[i] = muLawToLinear(muLawChunk[i] & 0xFF);
        }
        return pcm;
    }

    /** Decodes a single 8-bit µ-law sample (0-255) to a 16-bit linear PCM sample. */
    public static short muLawToLinear(int ulawVal) {
        int inverted = ~ulawVal;
        int sign = inverted & 0x80;
        int exponent = (inverted >> 4) & 0x07;
        int mantissa = inverted & 0x0F;

        int magnitude = ((mantissa << 1) + 33) << exponent;
        magnitude -= 33;

        return (short) (sign == 0 ? magnitude : -magnitude);
    }

    /** Encodes a single 16-bit linear PCM sample to an 8-bit µ-law byte. */
    public static byte linearToMuLaw(short pcmVal) {
        int absPcm = Math.min(Math.abs((int) pcmVal), MAX_PCM_VALUE);
        int sign = (pcmVal < 0) ? 0x00 : SIGN_BIT;
        absPcm += BIAS;

        int exponent;
        int mantissa;
        if (absPcm <= 0xFF) {
            exponent = 0;
            mantissa = (absPcm - 33) >> 1;
        } else if (absPcm <= 0x1FF) {
            exponent = 1;
            mantissa = (absPcm - 256 - 33) >> 2;
        } else if (absPcm <= 0x3FF) {
            exponent = 2;
            mantissa = (absPcm - 512 - 33) >> 3;
        } else if (absPcm <= 0x7FF) {
            exponent = 3;
            mantissa = (absPcm - 1024 - 33) >> 4;
        } else if (absPcm <= 0xFFF) {
            exponent = 4;
            mantissa = (absPcm - 2048 - 33) >> 5;
        } else if (absPcm <= 0x1FFF) {
            exponent = 5;
            mantissa = (absPcm - 4096 - 33) >> 6;
        } else if (absPcm <= 0x3FFF) {
            exponent = 6;
            mantissa = (absPcm - 8192 - 33) >> 7;
        } else {
            exponent = 7;
            mantissa = (absPcm - 16384 - 33) >> 8;
            if (mantissa > QUANT_MASK) {
                mantissa = QUANT_MASK;
            }
        }
        if (mantissa < 0) {
            mantissa = 0;
        }
        byte muLawByte = (byte) (sign | (exponent << SEG_SHIFT) | mantissa);
        return (byte) ~muLawByte;
    }
}

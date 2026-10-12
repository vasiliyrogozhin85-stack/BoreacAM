package com.boreac.am.modem;

public final class Protocol321 {
    private Protocol321() {}

    public static final int SAMPLE_RATE = 48_000;
    public static final int USEFUL = 80;
    public static final int CYCLIC_PREFIX = 16;
    public static final int SYMBOL_SAMPLES = 96;

    public static final int TOTAL_CARRIERS = 16;
    public static final int DATA_CARRIERS = 14;
    public static final int BYTES_PER_SYMBOL = 7;
    public static final double FIRST_HZ = 1800.0;
    public static final double SPACING_HZ = 600.0;
    public static final double AMPLITUDE = 22_000.0;

    public static final int PAYLOAD_MAX = 1024;
    public static final int HEADER_BYTES = 36;
    public static final int HEADER_SYMBOLS = 6;
    public static final int PREAMBLE_SYMBOLS = 8;
    public static final int SYNC_SYMBOLS = 4;

    public static final int LEAD_SILENCE_MS = 300;
    public static final int FRAME_GAP_MS = 70;
    public static final int DATA_REPEATS = 2;

    public static final int ENERGY_WINDOW = 64;
    public static final int ENERGY_STEP = 16;
    public static final double MIN_ENERGY_RMS = 650.0;
    public static final int ALIGN_RANGE = 48;

    public static final int FRAME_TRAIN = 1;
    public static final int FRAME_CAPS = 2;
    public static final int FRAME_DATA = 3;
    public static final int FRAME_ACK = 4;
    public static final int FRAME_NACK = 5;
    public static final int FRAME_END = 6;
    public static final int FRAME_CANCEL = 7;

    public static final int MODE_SAFE = 0;
    public static final int MODE_NORMAL = 1;
    public static final int MODE_FAST = 2;
    public static final int MODE_TURBO = 3;

    public static double frequency(int carrier) {
        return FIRST_HZ + carrier * SPACING_HZ;
    }

    public static boolean isPilot(int carrier) {
        return carrier == 3 || carrier == 12;
    }

    public static int gapSamples() {
        return SAMPLE_RATE * FRAME_GAP_MS / 1000;
    }

    public static int leadSamples() {
        return SAMPLE_RATE * LEAD_SILENCE_MS / 1000;
    }

    public static int payloadSymbols(int payloadLength) {
        return payloadLength <= 0 ? 0 : (payloadLength + BYTES_PER_SYMBOL - 1) / BYTES_PER_SYMBOL;
    }

    public static int frameSamples(int payloadLength) {
        return (PREAMBLE_SYMBOLS + SYNC_SYMBOLS + HEADER_SYMBOLS + payloadSymbols(payloadLength))
                * SYMBOL_SAMPLES;
    }
}

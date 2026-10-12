package com.boreac.am.modem;

import java.util.Arrays;

public final class QamCodec {
    private static final double[][] COS = new double[Protocol321.TOTAL_CARRIERS][Protocol321.USEFUL];
    private static final double[][] SIN = new double[Protocol321.TOTAL_CARRIERS][Protocol321.USEFUL];

    static {
        for (int c = 0; c < Protocol321.TOTAL_CARRIERS; c++) {
            double w = 2.0 * Math.PI * Protocol321.frequency(c) / Protocol321.SAMPLE_RATE;
            for (int n = 0; n < Protocol321.USEFUL; n++) {
                COS[c][n] = Math.cos(w * n);
                SIN[c][n] = Math.sin(w * n);
            }
        }
    }

    private QamCodec() {}

    private static double level(int value) {
        return switch (value & 3) {
            case 0 -> -3.0;
            case 1 -> -1.0;
            case 3 -> 1.0;
            default -> 3.0;
        };
    }

    private static int bits(double x) {
        if (x < -2.0) return 0;
        if (x < 0.0) return 1;
        if (x < 2.0) return 3;
        return 2;
    }

    private static int nibble(double i, double q) {
        return bits(i) * 4 + bits(q);
    }

    public static short[] buildSymbol(byte[] sevenBytes, int pilotPhase) {
        if (sevenBytes.length < Protocol321.BYTES_PER_SYMBOL) {
            throw new IllegalArgumentException("A symbol requires 7 bytes");
        }

        double[] useful = new double[Protocol321.USEFUL];
        double scale = Protocol321.AMPLITUDE / 52.0;
        int dataIndex = 0;

        for (int carrier = 0; carrier < Protocol321.TOTAL_CARRIERS; carrier++) {
            double i;
            double q;

            if (Protocol321.isPilot(carrier)) {
                i = (pilotPhase & 1) == 0 ? 3.0 : -3.0;
                q = 0.0;
            } else {
                int b = sevenBytes[dataIndex / 2] & 0xff;
                int nib = (dataIndex & 1) == 0 ? ((b >>> 4) & 0x0f) : (b & 0x0f);
                i = level((nib >>> 2) & 3);
                q = level(nib & 3);
                dataIndex++;
            }

            for (int n = 0; n < Protocol321.USEFUL; n++) {
                useful[n] += i * COS[carrier][n] - q * SIN[carrier][n];
            }
        }

        short[] out = new short[Protocol321.SYMBOL_SAMPLES];
        for (int n = 0; n < Protocol321.CYCLIC_PREFIX; n++) {
            int source = Protocol321.USEFUL - Protocol321.CYCLIC_PREFIX + n;
            out[n] = clipToShort(useful[source] * scale);
        }
        for (int n = 0; n < Protocol321.USEFUL; n++) {
            out[Protocol321.CYCLIC_PREFIX + n] = clipToShort(useful[n] * scale);
        }
        return out;
    }

    public static byte[] decodeSymbol(short[] samples, int start, int pilotPhase) {
        if (start < 0 || start + Protocol321.SYMBOL_SAMPLES > samples.length) {
            throw new IllegalArgumentException("Incomplete QAM symbol");
        }

        double[] ri = new double[Protocol321.TOTAL_CARRIERS];
        double[] rq = new double[Protocol321.TOTAL_CARRIERS];

        for (int carrier = 0; carrier < Protocol321.TOTAL_CARRIERS; carrier++) {
            double ia = 0.0;
            double qa = 0.0;
            for (int n = 0; n < Protocol321.USEFUL; n++) {
                double sample = samples[start + Protocol321.CYCLIC_PREFIX + n];
                ia += sample * COS[carrier][n];
                qa -= sample * SIN[carrier][n];
            }

            double scale = Protocol321.AMPLITUDE / 52.0;
            ri[carrier] = (2.0 * ia / Protocol321.USEFUL) / scale;
            rq[carrier] = (2.0 * qa / Protocol321.USEFUL) / scale;
        }

        double expectedPilot = (pilotPhase & 1) == 0 ? 3.0 : -3.0;
        double hi = ((ri[3] / expectedPilot) + (ri[12] / expectedPilot)) / 2.0;
        double hq = ((rq[3] / expectedPilot) + (rq[12] / expectedPilot)) / 2.0;
        double den = hi * hi + hq * hq;

        if (den < 0.04) {
            hi = 1.0;
            hq = 0.0;
            den = 1.0;
        }

        byte[] out = new byte[Protocol321.BYTES_PER_SYMBOL];
        int dataIndex = 0;

        for (int carrier = 0; carrier < Protocol321.TOTAL_CARRIERS; carrier++) {
            if (Protocol321.isPilot(carrier)) continue;

            double i = ri[carrier];
            double q = rq[carrier];
            double eqI = (i * hi + q * hq) / den;
            double eqQ = (q * hi - i * hq) / den;
            int nib = nibble(eqI, eqQ);

            int byteIndex = dataIndex / 2;
            if ((dataIndex & 1) == 0) {
                out[byteIndex] = (byte) (nib << 4);
            } else {
                out[byteIndex] = (byte) ((out[byteIndex] & 0xf0) | nib);
            }
            dataIndex++;
        }
        return out;
    }

    public static short[] trainingSymbol(int index) {
        byte[] b = new byte[7];
        Arrays.fill(b, (byte) (((index & 1) == 0) ? 0x22 : 0xdd));
        return buildSymbol(b, index);
    }

    public static short[] syncSymbol(int index) {
        byte[] b = switch (index & 3) {
            case 0 -> new byte[] {(byte) 0xa5, 0x5a, 0x3c, (byte) 0xc3, (byte) 0x96, 0x69, (byte) 0xf0};
            case 1 -> new byte[] {0x1e, (byte) 0xe1, (byte) 0x87, 0x78, 0x4b, (byte) 0xb4, 0x2d};
            case 2 -> new byte[] {(byte) 0xcc, 0x33, (byte) 0xf0, 0x0f, (byte) 0xaa, 0x55, (byte) 0x99};
            default -> new byte[] {0x69, (byte) 0x96, 0x5a, (byte) 0xa5, (byte) 0xc3, 0x3c, 0x0f};
        };
        return buildSymbol(b, index);
    }

    public static boolean selfTest() {
        byte[] original = new byte[] {0x00, 0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc};
        short[] symbol = buildSymbol(original, 0);
        return Arrays.equals(original, decodeSymbol(symbol, 0, 0));
    }

    private static short clipToShort(double value) {
        if (value > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (value < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) Math.round(value);
    }
}

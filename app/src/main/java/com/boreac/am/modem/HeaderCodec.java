package com.boreac.am.modem;

public final class HeaderCodec {
    private HeaderCodec() {}

    public static byte[] encode(FrameHeader h) {
        byte[] b = new byte[Protocol321.HEADER_BYTES];
        b[0] = 'V';
        b[1] = '3';
        b[2] = 2;
        b[3] = (byte) h.frameType;
        b[4] = (byte) h.mode;

        putInt(b, 5, h.sessionId);
        putInt(b, 9, h.packetNo);
        putInt(b, 13, h.totalPackets);
        putU16(b, 17, h.payloadLength);
        putInt(b, 19, h.fileSize);
        putInt(b, 23, (int) h.fileCrc);
        putInt(b, 27, (int) h.packetCrc);

        b[31] = 0;
        b[32] = 0;
        b[33] = 0;
        b[34] = 0;

        int xor = 0;
        for (int i = 0; i <= 34; i++) xor ^= b[i] & 0xff;
        b[35] = (byte) xor;
        return b;
    }

    public static FrameHeader decode(byte[] b) {
        if (!isValid(b)) throw new IllegalArgumentException("Invalid AudioModem header");
        return new FrameHeader(
                b[3] & 0xff,
                b[4] & 0xff,
                getInt(b, 5),
                getInt(b, 9),
                getInt(b, 13),
                getU16(b, 17),
                getInt(b, 19),
                Integer.toUnsignedLong(getInt(b, 23)),
                Integer.toUnsignedLong(getInt(b, 27))
        );
    }

    public static boolean isValid(byte[] b) {
        if (b == null || b.length < Protocol321.HEADER_BYTES) return false;
        if (b[0] != 'V' || b[1] != '3' || b[2] != 2) return false;
        int xor = 0;
        for (int i = 0; i <= 34; i++) xor ^= b[i] & 0xff;
        return (b[35] & 0xff) == (xor & 0xff);
    }

    private static void putU16(byte[] b, int p, int v) {
        b[p] = (byte) v;
        b[p + 1] = (byte) (v >>> 8);
    }

    private static int getU16(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
    }

    private static void putInt(byte[] b, int p, int v) {
        b[p] = (byte) v;
        b[p + 1] = (byte) (v >>> 8);
        b[p + 2] = (byte) (v >>> 16);
        b[p + 3] = (byte) (v >>> 24);
    }

    private static int getInt(byte[] b, int p) {
        return (b[p] & 0xff)
                | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16)
                | ((b[p + 3] & 0xff) << 24);
    }
}

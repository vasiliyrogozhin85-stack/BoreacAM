package com.boreac.am.modem;

import java.util.Arrays;

public final class FrameEncoder {
    private FrameEncoder() {}

    public static short[] buildFrame(FrameHeader header, byte[] payload) {
        int payloadLength = header.payloadLength;
        if (payloadLength < 0 || payloadLength > Protocol321.PAYLOAD_MAX) {
            throw new IllegalArgumentException("Invalid payload length");
        }
        if (payloadLength > 0 && (payload == null || payload.length < payloadLength)) {
            throw new IllegalArgumentException("Payload is shorter than the header");
        }

        ShortBuilder out = new ShortBuilder(
                Protocol321.frameSamples(payloadLength) + Protocol321.gapSamples());

        for (int i = 0; i < Protocol321.PREAMBLE_SYMBOLS; i++) {
            out.addAll(QamCodec.trainingSymbol(i));
        }
        for (int i = 0; i < Protocol321.SYNC_SYMBOLS; i++) {
            out.addAll(QamCodec.syncSymbol(i));
        }

        int phase = Protocol321.PREAMBLE_SYMBOLS + Protocol321.SYNC_SYMBOLS;
        phase = appendBytes(out, HeaderCodec.encode(header), Protocol321.HEADER_BYTES, phase);
        if (payloadLength > 0) appendBytes(out, payload, payloadLength, phase);

        out.addZeros(Protocol321.gapSamples());
        return out.toArray();
    }

    private static int appendBytes(ShortBuilder out, byte[] bytes, int count, int phase) {
        int pos = 0;
        byte[] seven = new byte[Protocol321.BYTES_PER_SYMBOL];

        while (pos < count) {
            Arrays.fill(seven, (byte) 0);
            int take = Math.min(Protocol321.BYTES_PER_SYMBOL, count - pos);
            System.arraycopy(bytes, pos, seven, 0, take);
            out.addAll(QamCodec.buildSymbol(seven, phase));
            phase++;
            pos += take;
        }
        return phase;
    }
}

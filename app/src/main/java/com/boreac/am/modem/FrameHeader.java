package com.boreac.am.modem;

public final class FrameHeader {
    public final int frameType;
    public final int mode;
    public final int sessionId;
    public final int packetNo;
    public final int totalPackets;
    public final int payloadLength;
    public final int fileSize;
    public final long fileCrc;
    public final long packetCrc;

    public FrameHeader(
            int frameType,
            int mode,
            int sessionId,
            int packetNo,
            int totalPackets,
            int payloadLength,
            int fileSize,
            long fileCrc,
            long packetCrc
    ) {
        this.frameType = frameType;
        this.mode = mode;
        this.sessionId = sessionId;
        this.packetNo = packetNo;
        this.totalPackets = totalPackets;
        this.payloadLength = payloadLength;
        this.fileSize = fileSize;
        this.fileCrc = fileCrc & 0xffffffffL;
        this.packetCrc = packetCrc & 0xffffffffL;
    }
}

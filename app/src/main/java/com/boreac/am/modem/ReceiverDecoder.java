package com.boreac.am.modem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class ReceiverDecoder {
    public interface Listener {
        void onProgress(double progress, String detail);
    }

    public static final class Result {
        public final int fileSize;
        public final long fileCrc;
        public final int packets;

        Result(int fileSize, long fileCrc, int packets) {
            this.fileSize = fileSize;
            this.fileCrc = fileCrc;
            this.packets = packets;
        }
    }

    private ReceiverDecoder() {}

    public static Result decode(short[] samples, File output, Listener listener)
            throws IOException, ProtocolException {
        if (samples == null || samples.length < Protocol321.SYMBOL_SAMPLES * 18) {
            throw new ProtocolException("Запись слишком короткая.");
        }

        double threshold = calculateThreshold(samples);
        int search = 0;
        int expectedPacket = 0;
        int sessionId = 0;
        int fileSize = -1;
        long fileCrc = -1L;
        int totalPackets = -1;
        boolean gotSession = false;
        boolean endSeen = false;

        if (output.exists() && !output.delete()) {
            throw new IOException("Не удалось очистить временный файл.");
        }

        try (FileOutputStream out = new FileOutputStream(output)) {
            while (search < samples.length - Protocol321.SYMBOL_SAMPLES) {
                int approx = findSignal(samples, search, threshold);
                if (approx < 0) break;

                Alignment aligned = alignFrame(samples, approx);
                if (aligned == null) {
                    search = approx + Protocol321.ENERGY_WINDOW;
                    continue;
                }

                FrameHeader h = aligned.header;
                int payloadLength = h.payloadLength;

                if (!gotSession) {
                    sessionId = h.sessionId;
                    fileSize = h.fileSize;
                    fileCrc = h.fileCrc;
                    totalPackets = h.totalPackets;
                    gotSession = true;
                }

                if (h.sessionId == sessionId) {
                    if (h.frameType == Protocol321.FRAME_DATA) {
                        if (payloadLength < 1 || payloadLength > Protocol321.PAYLOAD_MAX) {
                            search = advance(aligned.frameStart, payloadLength, search);
                            continue;
                        }

                        byte[] payload = decodePayload(samples, aligned.frameStart, payloadLength);
                        long packetCrc = Crc32Util.of(payload, payloadLength);
                        if (packetCrc == h.packetCrc) {
                            if (h.packetNo == expectedPacket) {
                                out.write(payload, 0, payloadLength);
                                expectedPacket++;
                                if (listener != null && totalPackets > 0) {
                                    listener.onProgress(
                                            Math.min(1.0, expectedPacket / (double) totalPackets),
                                            "Принято пакетов: " + expectedPacket + " / " + totalPackets
                                    );
                                }
                            } else if (h.packetNo > expectedPacket) {
                                throw new ProtocolException(
                                        "Пропущен пакет " + expectedPacket + ", получен " + h.packetNo + ".");
                            }
                        }
                    } else if (h.frameType == Protocol321.FRAME_END) {
                        if (expectedPacket != h.totalPackets) {
                            throw new ProtocolException(
                                    "Получено " + expectedPacket + " из " + h.totalPackets + " пакетов.");
                        }
                        endSeen = true;
                        break;
                    }
                }

                search = advance(aligned.frameStart, payloadLength, search);
            }
        }

        if (!gotSession) throw new ProtocolException("Кадры AudioModem 3.2.3 не найдены.");
        if (!endSeen) throw new ProtocolException("Конечный кадр END не найден.");
        if (output.length() != fileSize) {
            throw new ProtocolException(
                    "Размер файла не совпадает: ожидалось " + fileSize + ", получено " + output.length() + ".");
        }

        long actualCrc;
        try (FileInputStream in = new FileInputStream(output)) {
            actualCrc = Crc32Util.of(in);
        }
        if (actualCrc != fileCrc) {
            throw new ProtocolException(
                    "CRC файла не совпадает: ожидалось " + hex(fileCrc) + ", получено " + hex(actualCrc) + ".");
        }

        if (listener != null) listener.onProgress(1.0, "Файл восстановлен. CRC OK.");
        return new Result(fileSize, fileCrc, expectedPacket);
    }

    private static int advance(int frameStart, int payloadLength, int previous) {
        int safePayload = Math.max(0, Math.min(Protocol321.PAYLOAD_MAX, payloadLength));
        int next = frameStart + Protocol321.frameSamples(safePayload) + Protocol321.gapSamples() / 3;
        return Math.max(next, previous + Protocol321.SYMBOL_SAMPLES);
    }

    private static double calculateThreshold(short[] samples) {
        int noiseCount = Math.min(samples.length, Protocol321.SAMPLE_RATE / 5);
        if (noiseCount < Protocol321.ENERGY_WINDOW) return Protocol321.MIN_ENERGY_RMS;
        double sum = 0.0;
        int windows = 0;
        for (int p = 0; p + Protocol321.ENERGY_WINDOW <= noiseCount; p += Protocol321.ENERGY_WINDOW) {
            sum += rms(samples, p, Protocol321.ENERGY_WINDOW);
            windows++;
        }
        double noise = windows == 0 ? 0.0 : sum / windows;
        return Math.max(Protocol321.MIN_ENERGY_RMS, noise * 4.0);
    }

    private static int findSignal(short[] samples, int start, double threshold) {
        int max = samples.length - Protocol321.ENERGY_WINDOW;
        for (int p = Math.max(0, start); p <= max; p += Protocol321.ENERGY_STEP) {
            if (rms(samples, p, Protocol321.ENERGY_WINDOW) >= threshold) return p;
        }
        return -1;
    }

    private static double rms(short[] samples, int start, int count) {
        double energy = 0.0;
        for (int i = 0; i < count; i++) {
            double v = samples[start + i];
            energy += v * v;
        }
        return Math.sqrt(energy / count);
    }

    private static Alignment alignFrame(short[] samples, int approx) {
        for (int delta = -Protocol321.ALIGN_RANGE; delta <= Protocol321.ALIGN_RANGE; delta++) {
            int candidate = approx + delta;
            if (candidate < 0) continue;
            byte[] headerBytes = decodeHeaderAt(samples, candidate);
            if (headerBytes != null && HeaderCodec.isValid(headerBytes)) {
                return new Alignment(candidate, HeaderCodec.decode(headerBytes));
            }
        }
        return null;
    }

    private static byte[] decodeHeaderAt(short[] samples, int frameStart) {
        int headerStart = frameStart
                + (Protocol321.PREAMBLE_SYMBOLS + Protocol321.SYNC_SYMBOLS)
                * Protocol321.SYMBOL_SAMPLES;

        int required = headerStart + Protocol321.HEADER_SYMBOLS * Protocol321.SYMBOL_SAMPLES;
        if (frameStart < 0 || required > samples.length) return null;

        byte[] header = new byte[Protocol321.HEADER_BYTES];
        int out = 0;

        byte[] first = QamCodec.decodeSymbol(
                samples,
                headerStart,
                Protocol321.PREAMBLE_SYMBOLS + Protocol321.SYNC_SYMBOLS
        );
        if (first[0] != 'V' || first[1] != '3' || first[2] != 2) return null;

        for (byte b : first) {
            if (out < header.length) header[out++] = b;
        }

        for (int symbol = 1; symbol < Protocol321.HEADER_SYMBOLS; symbol++) {
            byte[] decoded = QamCodec.decodeSymbol(
                    samples,
                    headerStart + symbol * Protocol321.SYMBOL_SAMPLES,
                    Protocol321.PREAMBLE_SYMBOLS + Protocol321.SYNC_SYMBOLS + symbol
            );
            for (byte b : decoded) {
                if (out < header.length) header[out++] = b;
            }
        }
        return header;
    }

    private static byte[] decodePayload(short[] samples, int frameStart, int payloadLength)
            throws ProtocolException {
        int symbols = Protocol321.payloadSymbols(payloadLength);
        int payloadStart = frameStart
                + (Protocol321.PREAMBLE_SYMBOLS + Protocol321.SYNC_SYMBOLS
                + Protocol321.HEADER_SYMBOLS) * Protocol321.SYMBOL_SAMPLES;

        if (payloadStart + symbols * Protocol321.SYMBOL_SAMPLES > samples.length) {
            throw new ProtocolException("Запись оборвана внутри DATA-кадра.");
        }

        byte[] payload = new byte[payloadLength];
        int out = 0;
        for (int symbol = 0; symbol < symbols; symbol++) {
            byte[] decoded = QamCodec.decodeSymbol(
                    samples,
                    payloadStart + symbol * Protocol321.SYMBOL_SAMPLES,
                    Protocol321.PREAMBLE_SYMBOLS + Protocol321.SYNC_SYMBOLS
                            + Protocol321.HEADER_SYMBOLS + symbol
            );
            int take = Math.min(decoded.length, payloadLength - out);
            System.arraycopy(decoded, 0, payload, out, take);
            out += take;
        }
        return payload;
    }

    private static String hex(long value) {
        return String.format("%08X", value & 0xffffffffL);
    }

    private static final class Alignment {
        final int frameStart;
        final FrameHeader header;

        Alignment(int frameStart, FrameHeader header) {
            this.frameStart = frameStart;
            this.header = header;
        }
    }
}

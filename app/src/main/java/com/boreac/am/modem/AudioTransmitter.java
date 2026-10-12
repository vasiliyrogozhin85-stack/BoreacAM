package com.boreac.am.modem;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioTransmitter {
    public interface Listener {
        void onProgress(double progress, String detail);
    }

    private AudioTransmitter() {}

    public static void send(File source, AtomicBoolean cancelled, Listener listener) throws IOException {
        long sizeLong = source.length();
        if (sizeLong < 0 || sizeLong > Integer.MAX_VALUE) {
            throw new IOException("Поддерживаются файлы размером до 2 ГБ.");
        }
        int fileSize = (int) sizeLong;

        long fileCrc;
        try (FileInputStream in = new FileInputStream(source)) {
            fileCrc = Crc32Util.of(in);
        }

        int total = fileSize == 0 ? 0
                : (fileSize + Protocol321.PAYLOAD_MAX - 1) / Protocol321.PAYLOAD_MAX;
        int session = positiveSessionId();

        int minBuffer = AudioTrack.getMinBufferSize(
                Protocol321.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) throw new IOException("AudioTrack не поддерживает 48 кГц mono PCM16.");

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(Protocol321.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(minBuffer * 4, 32 * 1024))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        try {
            track.setVolume(1.0f);
            track.play();
            writeBlocking(track, new short[Protocol321.leadSamples()], cancelled);

            byte[] payload = new byte[Protocol321.PAYLOAD_MAX];
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
                for (int packet = 0; packet < total; packet++) {
                    if (cancelled.get()) throw new CancelledException();

                    int payloadLength = readExactOrEof(in, payload);
                    long packetCrc = Crc32Util.of(payload, payloadLength);
                    FrameHeader header = new FrameHeader(
                            Protocol321.FRAME_DATA,
                            Protocol321.MODE_NORMAL,
                            session,
                            packet,
                            total,
                            payloadLength,
                            fileSize,
                            fileCrc,
                            packetCrc
                    );

                    short[] frame = FrameEncoder.buildFrame(header, payload);
                    for (int repeat = 0; repeat < Protocol321.DATA_REPEATS; repeat++) {
                        writeBlocking(track, frame, cancelled);
                    }

                    if (listener != null) {
                        listener.onProgress(
                                total == 0 ? 1.0 : (packet + 1) / (double) total,
                                "Передано пакетов: " + (packet + 1) + " / " + total
                        );
                    }
                }
            }

            FrameHeader end = new FrameHeader(
                    Protocol321.FRAME_END,
                    Protocol321.MODE_NORMAL,
                    session,
                    total,
                    total,
                    0,
                    fileSize,
                    fileCrc,
                    0
            );
            writeBlocking(track, FrameEncoder.buildFrame(end, new byte[0]), cancelled);

            if (listener != null) listener.onProgress(1.0, "Передача завершена.");
            track.stop();
        } finally {
            try {
                track.flush();
            } catch (Exception ignored) {
            }
            track.release();
        }
    }

    private static void writeBlocking(AudioTrack track, short[] samples, AtomicBoolean cancelled)
            throws IOException {
        int offset = 0;
        while (offset < samples.length) {
            if (cancelled.get()) throw new CancelledException();
            int written = track.write(
                    samples,
                    offset,
                    samples.length - offset,
                    AudioTrack.WRITE_BLOCKING
            );
            if (written < 0) throw new IOException("Ошибка AudioTrack: " + written);
            offset += written;
        }
    }

    private static int readExactOrEof(BufferedInputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read < 0) break;
            if (read > 0) total += read;
        }
        return total;
    }

    private static int positiveSessionId() {
        long value = (System.nanoTime() ^ System.currentTimeMillis()) & 0x7fffffffL;
        return (int) Math.max(1L, value);
    }

    public static final class CancelledException extends IOException {
        CancelledException() {
            super("Передача отменена.");
        }
    }
}

package com.boreac.am.modem;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioReceiver {
    public interface Listener {
        void onLevel(double level);
        void onError(Exception error);
    }

    private final Context context;
    private final File rawFile;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private AudioRecord recorder;

    public AudioReceiver(Context context, File rawFile) {
        this.context = context.getApplicationContext();
        this.rawFile = rawFile;
    }

    public boolean isRunning() {
        return running.get();
    }

    public File getRawFile() {
        return rawFile;
    }

    public void start(Listener listener) throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IOException("Приём уже запущен.");
        }

        if (rawFile.exists() && !rawFile.delete()) {
            running.set(false);
            throw new IOException("Не удалось очистить временную запись.");
        }

        int min = AudioRecord.getMinBufferSize(
                Protocol321.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (min <= 0) {
            running.set(false);
            throw new IOException("Микрофон не поддерживает 48 кГц mono PCM16.");
        }

        int source = chooseSource();
        recorder = new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(Protocol321.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(min * 4, 32 * 1024))
                .build();

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            running.set(false);
            recorder.release();
            recorder = null;
            throw new IOException("AudioRecord не инициализирован.");
        }

        disableEffects(recorder.getAudioSessionId());
        recorder.startRecording();

        thread = new Thread(() -> recordLoop(listener), "BoreacAM-RX");
        thread.start();
    }

    public File stop() throws IOException {
        if (!running.getAndSet(false)) return rawFile;

        AudioRecord local = recorder;
        if (local != null) {
            try {
                local.stop();
            } catch (Exception ignored) {
            }
        }

        Thread t = thread;
        if (t != null) {
            try {
                t.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (local != null) local.release();
        recorder = null;
        thread = null;

        if (!rawFile.exists() || rawFile.length() < 2) {
            throw new IOException("Микрофонная запись пуста.");
        }
        return rawFile;
    }

    private void recordLoop(Listener listener) {
        short[] buffer = new short[4096];

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(rawFile))) {
            while (running.get()) {
                int read = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (read < 0) throw new IOException("Ошибка AudioRecord: " + read);
                if (read == 0) continue;

                double energy = 0.0;
                for (int i = 0; i < read; i++) {
                    short v = buffer[i];
                    out.write(v & 0xff);
                    out.write((v >>> 8) & 0xff);
                    energy += (double) v * v;
                }
                if (listener != null) {
                    listener.onLevel(Math.sqrt(energy / read) / Short.MAX_VALUE);
                }
            }
        } catch (Exception e) {
            running.set(false);
            if (listener != null) listener.onError(e);
        }
    }

    private int chooseSource() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            String supported = manager == null ? null
                    : manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
            if ("true".equalsIgnoreCase(supported)) {
                return MediaRecorder.AudioSource.UNPROCESSED;
            }
        }
        return MediaRecorder.AudioSource.VOICE_RECOGNITION;
    }

    private static void disableEffects(int sessionId) {
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl effect = AutomaticGainControl.create(sessionId);
                if (effect != null) effect.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor effect = NoiseSuppressor.create(sessionId);
                if (effect != null) effect.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler effect = AcousticEchoCanceler.create(sessionId);
                if (effect != null) effect.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
    }
}

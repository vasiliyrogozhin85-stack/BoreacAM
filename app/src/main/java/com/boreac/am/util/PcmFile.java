package com.boreac.am.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class PcmFile {
    private PcmFile() {}

    public static short[] readAll(File file) throws IOException {
        long bytes = file.length();
        if ((bytes & 1L) != 0L) throw new IOException("Некорректный PCM-файл.");
        if (bytes > 128L * 1024L * 1024L) {
            throw new IOException("Запись слишком большая для декодирования (>128 МБ).");
        }

        int samples = (int) (bytes / 2L);
        short[] result = new short[samples];

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            int samplePos = 0;
            int read;
            int carry = -1;

            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                int p = 0;

                if (carry >= 0) {
                    result[samplePos++] = (short) ((carry & 0xff) | ((buffer[0] & 0xff) << 8));
                    p = 1;
                    carry = -1;
                }

                while (p + 1 < read) {
                    result[samplePos++] = (short) ((buffer[p] & 0xff) | ((buffer[p + 1] & 0xff) << 8));
                    p += 2;
                }
                if (p < read) carry = buffer[p] & 0xff;
            }
        }
        return result;
    }
}

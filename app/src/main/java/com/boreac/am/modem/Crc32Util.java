package com.boreac.am.modem;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

public final class Crc32Util {
    private Crc32Util() {}

    public static long of(byte[] data, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, length);
        return crc.getValue();
    }

    public static long of(InputStream input) throws IOException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) crc.update(buffer, 0, read);
        }
        return crc.getValue();
    }
}

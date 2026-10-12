package com.boreac.am.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class IoUtils {
    private IoUtils() {}

    public static File copyUriToCache(Context context, Uri uri, String fileName) throws IOException {
        File dir = new File(context.getCacheDir(), "boreacam");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать временную папку.");
        }

        File out = new File(dir, fileName);
        try (InputStream in = new BufferedInputStream(
                    requireStream(context.getContentResolver(), uri));
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            copy(in, os);
        }
        return out;
    }

    public static void copyFileToUri(Context context, File file, Uri uri) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             OutputStream out = new BufferedOutputStream(
                     requireOutput(context.getContentResolver(), uri))) {
            copy(in, out);
        }
    }

    public static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isBlank()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "file.bin";
    }

    public static String safeFileName(String name) {
        return name.replaceAll("[^A-Za-zА-Яа-я0-9._-]+", "_");
    }

    private static InputStream requireStream(ContentResolver resolver, Uri uri) throws IOException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("Не удалось открыть исходный файл.");
        return in;
    }

    private static OutputStream requireOutput(ContentResolver resolver, Uri uri) throws IOException {
        OutputStream out = resolver.openOutputStream(uri, "w");
        if (out == null) throw new IOException("Не удалось открыть выходной файл.");
        return out;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read > 0) out.write(buffer, 0, read);
        }
    }
}

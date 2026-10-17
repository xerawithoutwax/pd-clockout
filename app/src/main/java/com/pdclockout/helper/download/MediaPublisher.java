package com.pdclockout.helper.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class MediaPublisher {
    private MediaPublisher() {}

    public static Uri publish(Context context, File source, String displayName) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String uniqueName = uniqueDisplayName(resolver, displayName);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime(displayName));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PD칼퇴도우미");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Android Download 저장소 생성 실패");
        try (FileInputStream in = new FileInputStream(source);
             OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("Android Download 저장 스트림 생성 실패");
            byte[] buf = new byte[256 * 1024];
            int read;
            while ((read = in.read(buf)) >= 0) out.write(buf, 0, read);
            out.flush();
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }

    private static String uniqueDisplayName(ContentResolver resolver, String requested) {
        String relative = Environment.DIRECTORY_DOWNLOADS + "/PD칼퇴도우미/";
        String candidate = requested;
        String ext = "";
        String base = requested;
        int dot = requested.lastIndexOf('.');
        if (dot > 0) {
            base = requested.substring(0, dot);
            ext = requested.substring(dot);
        }
        for (int n = 1; n < 10000; n++) {
            if (!exists(resolver, relative, candidate)) return candidate;
            candidate = base + "_" + (n + 1) + ext;
        }
        return requested;
    }

    private static boolean exists(ContentResolver resolver, String relativePath, String name) {
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] args = { relativePath, name };
        try (Cursor c = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String mime(String name) {
        String low = name.toLowerCase(Locale.ROOT);
        if (low.endsWith(".mp3")) return "audio/mpeg";
        if (low.endsWith(".m4a")) return "audio/mp4";
        if (low.endsWith(".webm")) return "video/webm";
        if (low.endsWith(".mov")) return "video/quicktime";
        return "video/mp4";
    }
}

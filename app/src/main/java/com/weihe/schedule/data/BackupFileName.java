package com.weihe.schedule.data;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/** A document ID is opaque; only provider metadata identifies the file's name. */
public final class BackupFileName {
    private static final String FALLBACK = "\u672A\u547D\u540D\u5907\u4EFD\u6587\u4EF6";

    interface Query {
        Cursor open();
    }

    private BackupFileName() {
    }

    /** Call on a worker thread: document providers can be remote or slow. */
    public static String read(ContentResolver resolver, Uri uri) {
        return read(() -> resolver.query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null));
    }

    static String read(Query query) {
        try (Cursor cursor = query.open()) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String name = cursor.getString(column);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (RuntimeException unavailable) {
            // Missing metadata must not prevent reading an otherwise valid backup.
        }
        return FALLBACK;
    }
}

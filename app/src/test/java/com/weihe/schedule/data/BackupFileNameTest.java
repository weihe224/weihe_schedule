package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.database.Cursor;
import java.lang.reflect.Proxy;
import org.junit.Test;

public class BackupFileNameTest {
    private static final String FALLBACK = "\u672A\u547D\u540D\u5907\u4EFD\u6587\u4EF6";
    private boolean closed;

    private Cursor cursor(boolean hasRow, int column, String name, boolean unreadable) {
        return (Cursor) Proxy.newProxyInstance(Cursor.class.getClassLoader(),
                new Class<?>[] {Cursor.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "moveToFirst": return hasRow;
                        case "getColumnIndex": return column;
                        case "getString":
                            if (unreadable) {
                                throw new IllegalStateException("provider disconnected");
                            }
                            return name;
                        case "close": closed = true; return null;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }

    @Test
    public void providerNameIsPreservedAndCursorIsClosed() {
        assertEquals("\u8BFE\u8868 2026.json", BackupFileName.read(
                () -> cursor(true, 0, " \u8BFE\u8868 2026.json ", false)));
        assertTrue(closed);
    }

    @Test
    public void nullCursorUsesReadableFallback() {
        assertEquals(FALLBACK, BackupFileName.read(() -> null));
    }

    @Test
    public void emptyCursorIsClosed() {
        assertEquals(FALLBACK, BackupFileName.read(
                () -> cursor(false, 0, "unused", false)));
        assertTrue(closed);
    }

    @Test
    public void missingNameColumnUsesFallbackAndClosesCursor() {
        assertEquals(FALLBACK, BackupFileName.read(
                () -> cursor(true, -1, "unused", false)));
        assertTrue(closed);
    }

    @Test
    public void blankOrNullNameUsesFallback() {
        assertEquals(FALLBACK, BackupFileName.read(
                () -> cursor(true, 0, "  ", false)));
        assertEquals(FALLBACK, BackupFileName.read(
                () -> cursor(true, 0, null, false)));
    }

    @Test
    public void deniedMetadataDoesNotPreventBackupPreview() {
        assertEquals(FALLBACK, BackupFileName.read(() -> {
            throw new SecurityException("metadata denied");
        }));
    }

    @Test
    public void disconnectionDuringReadStillClosesCursor() {
        assertEquals(FALLBACK, BackupFileName.read(
                () -> cursor(true, 0, "unused", true)));
        assertTrue(closed);
    }
}

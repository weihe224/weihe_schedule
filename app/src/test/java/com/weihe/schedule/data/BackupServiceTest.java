package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import com.weihe.schedule.model.Course;

public class BackupServiceTest {
    private int callbacks;
    private int expectedCallbacks = 1;
    private final BackupService service = new BackupService(Runnable::run, task -> {
        callbacks++;
        task.run();
    });

    @After
    public void everyOperationDeliversItsResult() {
        assertEquals(expectedCallbacks, callbacks);
    }

    private static CourseRepository repository(FakePreferences preferences, String name) {
        CourseRepository.useStoreForNextInstance(preferences);
        CourseRepository repository = new CourseRepository((android.content.Context) null);
        Course course = new Course();
        course.name = name;
        repository.replaceCourses(List.of(course));
        return repository;
    }

    private BackupCodec.Document document(String name) {
        BackupService.Result<BackupCodec.Document> prepared = service.prepare(
                repository(new FakePreferences(), name), "1.1");
        assertTrue(prepared.problem(), prepared.isOk());
        return prepared.value();
    }

    private static byte[] bytes(BackupCodec.Document document) {
        return BackupCodec.write(document).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void preparedSnapshotDoesNotChangeWhileDestinationPickerIsOpen() {
        CourseRepository repository = repository(new FakePreferences(), "Original");
        BackupCodec.Document snapshot = service.prepare(repository, "1.1").value();
        Course replacement = new Course();
        replacement.name = "Changed";
        repository.replaceCourses(List.of(replacement));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.write(() -> output, snapshot, result -> assertTrue(result.isOk()));
        BackupCodec.Document exported = BackupCodec.parse(
                output.toString(StandardCharsets.UTF_8)).document();
        assertEquals("Original", exported.coursesOf(exported.activeSemesterId()).get(0).name);
    }

    @Test
    public void writeWaitsForWorkerAndClosesBeforeDispatchingResult() {
        expectedCallbacks = 0;
        Queue<Runnable> workers = new ArrayDeque<>();
        Queue<Runnable> replies = new ArrayDeque<>();
        BackupService queued = new BackupService(workers::add, replies::add);
        boolean[] closed = {false};
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public void close() { closed[0] = true; }
        };
        queued.write(() -> { opens.incrementAndGet(); return output; }, document("A"),
                result -> { assertTrue(result.isOk()); callbacks.incrementAndGet(); });
        assertEquals(0, opens.get());
        assertEquals(0, callbacks.get());
        workers.remove().run();
        assertTrue(closed[0]);
        assertEquals(1, opens.get());
        assertEquals(0, callbacks.get());
        replies.remove().run();
        assertEquals(1, callbacks.get());
    }

    @Test
    public void unavailableOutputIsFailure() {
        service.write(() -> null, document("A"), result -> {
            assertFalse(result.isOk());
            assertEquals("\u65E0\u6CD5\u5199\u5165\u6240\u9009\u4F4D\u7F6E", result.problem());
        });
    }

    @Test
    public void deniedOutputKeepsTheExistingPermissionMessage() {
        service.write(() -> { throw new SecurityException(); }, document("A"), result -> {
            assertFalse(result.isOk());
            assertEquals("\u6CA1\u6709\u5199\u5165\u8BE5\u4F4D\u7F6E\u7684\u6743\u9650",
                    result.problem());
        });
    }

    @Test
    public void failedCloseMustNotReportSuccessfulExport() {
        service.write(() -> new ByteArrayOutputStream() {
            @Override public void close() throws IOException { throw new IOException("full"); }
        }, document("A"), result -> {
            assertFalse(result.isOk());
            assertTrue(result.problem().contains("full"));
        });
    }

    @Test
    public void failedWriteClosesOutputAndReportsFailure() {
        boolean[] closed = {false};
        service.write(() -> new java.io.OutputStream() {
            @Override public void write(int value) throws IOException {
                throw new IOException("full");
            }
            @Override public void close() { closed[0] = true; }
        }, document("A"), result -> {
            assertFalse(result.isOk());
            assertTrue(closed[0]);
        });
    }

    @Test
    public void previewContainsCheckedDocumentAndProviderName() {
        byte[] bytes = bytes(document("A"));
        boolean[] closed = {false};
        service.load(() -> new ByteArrayInputStream(bytes) {
            @Override public void close() { closed[0] = true; }
        }, () -> "backup.json", result -> {
            assertTrue(result.problem(), result.isOk());
            assertTrue(closed[0]);
            assertEquals("backup.json", result.value().preview().fileName());
            assertEquals(1, result.value().document().courseCount());
        });
    }

    @Test
    public void invalidPreviewDoesNotQueryMetadata() {
        service.load(() -> new ByteArrayInputStream(new byte[] {1}),
                () -> { throw new AssertionError("metadata should not be queried"); },
                result -> assertFalse(result.isOk()));
    }

    @Test
    public void emptyIOExceptionHasTheExistingReadableFallback() {
        service.load(() -> { throw new IOException(); }, () -> "unused", result -> {
            assertFalse(result.isOk());
            assertEquals("\u65E0\u6CD5\u8BFB\u53D6\u6240\u9009\u6587\u4EF6", result.problem());
        });
    }

    @Test
    public void restoreReopensTheFileInsteadOfApplyingTheEarlierPreview() {
        expectedCallbacks = 2;
        AtomicReference<byte[]> contents = new AtomicReference<>(bytes(document("Preview")));
        service.load(() -> new ByteArrayInputStream(contents.get()), () -> "backup.json",
                result -> assertTrue(result.isOk()));
        contents.set(bytes(document("NewFile")));
        CourseRepository target = repository(new FakePreferences(), "Before");
        service.restore(() -> new ByteArrayInputStream(contents.get()), () -> target, result -> {
            assertTrue(result.problem(), result.isOk());
            assertTrue(result.value().isRestored());
        });
        assertEquals("NewFile", target.getCourses().get(0).name);
    }

    @Test
    public void invalidRestoreNeverTouchesTheRepository() {
        service.restore(() -> new ByteArrayInputStream(new byte[] {1}),
                () -> { throw new AssertionError("invalid file must not reach storage"); },
                result -> assertFalse(result.isOk()));
    }

    @Test
    public void readCloseFailureCannotBeReportedAfterChangingTheTimetable() {
        byte[] bytes = bytes(document("A"));
        service.restore(() -> new ByteArrayInputStream(bytes) {
            @Override public void close() throws IOException { throw new IOException("closed"); }
        }, () -> { throw new AssertionError("close must finish before storage is opened"); },
                result -> {
                    assertFalse(result.isOk());
                    assertEquals("closed", result.problem());
                });
    }

    @Test
    public void repositoryRefusalRemainsARestoreReportNotAReadError() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository target = repository(preferences, "Before");
        preferences.failCommitNumber(preferences.commitCount() + 1);
        service.restore(() -> new ByteArrayInputStream(bytes(document("After"))),
                () -> target, result -> {
                    assertTrue(result.isOk());
                    assertNotNull(result.value());
                    assertEquals(RestoreReport.Status.UNCHANGED, result.value().status());
                });
        assertEquals("Before", target.getCourses().get(0).name);
    }

    @Test
    public void captureIncludesInactiveTermsAndPreservesTheActiveSelection() {
        expectedCallbacks = 0;
        CourseRepository repository = repository(new FakePreferences(), "First");
        var second = repository.addSemester(
                repository.getActiveSemester().start().plusYears(1), "Second");
        repository.setActiveSemester(second);
        Course course = new Course();
        course.name = "Second";
        repository.replaceCourses(List.of(course));
        var result = service.prepare(repository, "1.1");
        assertTrue(result.problem(), result.isOk());
        assertEquals(2, result.value().semesterCount());
        assertEquals(2, result.value().courseCount());
        assertEquals(second.id(), result.value().activeSemesterId());
        assertEquals(second.id(), repository.getActiveSemester().id());
    }
}

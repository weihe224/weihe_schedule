package com.weihe.schedule.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/** File operations and dispatch, without dependencies on Android UI classes. */
public final class BackupService {
    @FunctionalInterface
    public interface Source<T> {
        T open() throws IOException;
    }

    public static final class Result<T> {
        private final T value;
        private final String problem;

        private Result(T value, String problem) {
            this.value = value;
            this.problem = problem;
        }

        public boolean isOk() { return problem == null; }
        public T value() { return value; }
        public String problem() { return problem; }
    }

    public static final class Loaded {
        private final BackupCodec.Document document;
        private final BackupPreview preview;

        private Loaded(String name, BackupCodec.Document document) {
            this.document = document;
            this.preview = BackupPreview.of(name, document);
        }

        public BackupCodec.Document document() { return document; }
        public BackupPreview preview() { return preview; }
    }

    private final Executor worker;
    private final Executor replies;

    public BackupService(Executor replies) {
        this(task -> new Thread(task, "BackupIO").start(), replies);
    }

    BackupService(Executor worker, Executor replies) {
        this.worker = worker;
        this.replies = replies;
    }

    /** Capture before opening the picker: cancellation must not open a file. */
    public Result<BackupCodec.Document> prepare(CourseRepository repository,
            String appVersion) {
        BackupCodec.Document document;
        try {
            List<Semester> semesters = repository.getSemesters();
            Map<Long, List<Course>> courses = new LinkedHashMap<>();
            for (Semester semester : semesters) {
                courses.put(semester.id(), repository.coursesOf(semester));
            }
            List<BellSchedule> schedules =
                    BellSchedules.copyOf(repository.getBellSchedules());
            document = BackupCodec.of(semesters, courses, schedules,
                    repository.getActiveSemester().id(),
                    OffsetDateTime.now().toString(), appVersion,
                    repository.getDefaultScheduleId());
        } catch (RuntimeException error) {
            return failed("\u8BFB\u53D6\u5F53\u524D\u8BFE\u8868\u65F6\u51FA\u9519\uFF1A" + error);
        }
        BackupCodec.Result check = BackupCodec.parse(BackupCodec.write(document));
        if (!check.isOk()) {
            return failed("\u751F\u6210\u7684\u5907\u4EFD\u672A\u901A\u8FC7\u81EA\u68C0\uFF1A"
                    + check.problem());
        }
        return succeeded(document);
    }

    public void write(Source<OutputStream> destination, BackupCodec.Document document,
            Consumer<Result<Void>> callback) {
        execute(() -> {
            try (OutputStream output = destination.open()) {
                if (output == null) {
                    return failed("\u65E0\u6CD5\u5199\u5165\u6240\u9009\u4F4D\u7F6E");
                }
                BackupCodec.writeTo(document, output);
            } catch (IOException error) {
                return failed(writeFailureReason(error));
            } catch (SecurityException denied) {
                return failed("\u6CA1\u6709\u5199\u5165\u8BE5\u4F4D\u7F6E\u7684\u6743\u9650");
            }
            return succeeded(null);
        }, callback);
    }

    public void load(Source<InputStream> source, Supplier<String> fileName,
            Consumer<Result<Loaded>> callback) {
        execute(() -> {
            try (InputStream input = source.open()) {
                BackupCodec.Result checked = BackupCodec.read(input, BackupCodec.MAX_BYTES);
                if (!checked.isOk()) {
                    return failed(checked.problem());
                }
                return succeeded(new Loaded(fileName.get(), checked.document()));
            } catch (IOException error) {
                return failed(readFailureReason(error));
            } catch (RuntimeException error) {
                return failed("\u8BFB\u53D6\u5907\u4EFD\u6587\u4EF6\u65F6\u51FA\u9519\uFF1A" + error);
            }
        }, callback);
    }

    /** Reopen on confirmation: the file may have changed since its preview. */
    public void restore(Source<InputStream> source, Supplier<CourseRepository> repository,
            Consumer<Result<RestoreReport>> callback) {
        execute(() -> {
            BackupCodec.Document document;
            try (InputStream input = source.open()) {
                BackupCodec.Result checked = BackupCodec.read(input, BackupCodec.MAX_BYTES);
                if (!checked.isOk()) {
                    return failed(checked.problem());
                }
                document = checked.document();
            } catch (IOException error) {
                return failed(readFailureReason(error));
            } catch (RuntimeException error) {
                return failed(restoreFailureReason(error));
            }
            // Finish the fallible read/close before mutating storage. Otherwise a
            // close failure could be reported as "restore not started" after a commit.
            try {
                return succeeded(repository.get().restoreFrom(document));
            } catch (RuntimeException error) {
                return failed(restoreFailureReason(error));
            }
        }, callback);
    }

    private <T> void execute(Supplier<Result<T>> operation, Consumer<Result<T>> callback) {
        worker.execute(() -> {
            Result<T> result = operation.get();
            replies.execute(() -> callback.accept(result));
        });
    }

    private static <T> Result<T> succeeded(T value) {
        return new Result<>(value, null);
    }

    private static <T> Result<T> failed(String problem) {
        return new Result<>(null, problem);
    }

    private static String readFailureReason(IOException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "\u65E0\u6CD5\u8BFB\u53D6\u6240\u9009\u6587\u4EF6" : message;
    }

    private static String restoreFailureReason(RuntimeException error) {
        return "\u6062\u590D\u65F6\u51FA\u9519\uFF1A" + error;
    }

    /** Shared with the raw damaged-payload export, which uses the same file UI. */
    public static String writeFailureReason(IOException error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "\u5199\u5165\u6587\u4EF6\u5931\u8D25\uFF0C\u8BF7\u6362\u4E00\u4E2A"
                    + "\u4F4D\u7F6E\u518D\u8BD5";
        }
        return "\u5199\u5165\u6587\u4EF6\u5931\u8D25\uFF1A" + message
                + "\n\u82E5\u662F\u5B58\u50A8\u7A7A\u95F4\u4E0D\u8DB3\uFF0C\u8BF7\u6E05"
                + "\u7406\u540E\u91CD\u8BD5\u3002";
    }
}

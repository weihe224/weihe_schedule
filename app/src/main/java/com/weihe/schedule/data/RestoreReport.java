package com.weihe.schedule.data;

/**
 * What a restore attempt actually did, in terms the screen can report honestly.
 *
 * <p>The four outcomes are deliberately not a boolean. "It worked" and "it did
 * not work" are not the only two things that can happen when data is replaced:
 * the write can leave nothing changed, or it can leave the store in a state the
 * app could not put back. The last one is the dangerous case, and a caller that
 * only sees {@code false} would be tempted to tell the user everything is fine.
 * Carrying the outcome as a value makes that harder to get wrong.</p>
 */
public final class RestoreReport {

    public enum Status {
        /** The backup was written and read back as expected. */
        RESTORED,
        /** The write did not read back as expected, and the old data was put back and verified. */
        ROLLED_BACK,
        /** Nothing was written: the file was refused, or the commit did not go through. */
        UNCHANGED,
        /**
         * The store does not hold the backup, and the old data could not be put
         * back either. The rollback material is still on disk; the user has to be
         * told not to keep editing until this is resolved.
         */
        NEEDS_ATTENTION
    }

    private final Status status;
    private final String message;
    private final String detail;

    private RestoreReport(Status status, String message, String detail) {
        this.status = status;
        this.message = message;
        this.detail = detail;
    }

    static RestoreReport restored(String message) {
        return new RestoreReport(Status.RESTORED, message, "");
    }

    static RestoreReport rolledBack(String message, String detail) {
        return new RestoreReport(Status.ROLLED_BACK, message, detail);
    }

    static RestoreReport unchanged(String message) {
        return new RestoreReport(Status.UNCHANGED, message, "");
    }

    static RestoreReport needsAttention(String message, String detail) {
        return new RestoreReport(Status.NEEDS_ATTENTION, message, detail);
    }

    public Status status() {
        return status;
    }

    /** A sentence fit to show the user. */
    public String message() {
        return message;
    }

    /** Why it went wrong, when there is more to say than {@link #message()}. */
    public String detail() {
        return detail;
    }

    public boolean isRestored() {
        return status == Status.RESTORED;
    }

    /** True when the app's own data may no longer match either state. */
    public boolean needsAttention() {
        return status == Status.NEEDS_ATTENTION;
    }

    @Override
    public String toString() {
        return status + ": " + message + (detail.isEmpty() ? "" : " (" + detail + ")");
    }
}

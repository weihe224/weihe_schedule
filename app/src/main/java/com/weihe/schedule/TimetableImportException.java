package com.weihe.schedule;

/** Raised when an imported timetable does not contain a usable schedule. */
public class TimetableImportException extends Exception {
    public TimetableImportException(String message) {
        super(message);
    }

    public TimetableImportException(String message, Throwable cause) {
        super(message, cause);
    }
}

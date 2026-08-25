package com.deployforge.log;

/** Severity of a deployment log line. */
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    public boolean atLeast(LogLevel minimum) {
        return ordinal() >= minimum.ordinal();
    }
}

package me.ethanchen.testclient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.badlogic.gdx.ApplicationLogger;

/** ApplicationLogger that prints {@code [HH:mm:ss.SSS] [tag] message} to the console. */
final class TimestampedLogger implements ApplicationLogger {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    static String now() {
        return LocalTime.now().format(TIME);
    }

    private static String prefix() {
        return "[" + now() + "] ";
    }

    @Override
    public synchronized void log(String tag, String message) {
        System.out.println(prefix() + "[" + tag + "] " + message);
    }

    @Override
    public synchronized void log(String tag, String message, Throwable exception) {
        System.out.println(prefix() + "[" + tag + "] " + message);
        exception.printStackTrace(System.out);
    }

    @Override
    public synchronized void error(String tag, String message) {
        System.err.println(prefix() + "[" + tag + "] " + message);
    }

    @Override
    public synchronized void error(String tag, String message, Throwable exception) {
        System.err.println(prefix() + "[" + tag + "] " + message);
        exception.printStackTrace(System.err);
    }

    @Override
    public synchronized void debug(String tag, String message) {
        System.out.println(prefix() + "[" + tag + "] " + message);
    }

    @Override
    public synchronized void debug(String tag, String message, Throwable exception) {
        System.out.println(prefix() + "[" + tag + "] " + message);
        exception.printStackTrace(System.out);
    }
}

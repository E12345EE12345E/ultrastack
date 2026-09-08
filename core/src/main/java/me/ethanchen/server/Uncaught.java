package me.ethanchen.server;

/**
 * Logs unexpected failures on server threads. {@link VirtualMachineError} (OOM, stack overflow)
 * is rethrown so the process still dies; {@link LinkageError} and other {@link Error}s are not,
 * so a missing class in one handler cannot kill {@code server-core-loop} or a room shard.
 */
final class Uncaught {
    private Uncaught() {}

    static void log(String context, Throwable t) {
        System.err.println(context + t);
        t.printStackTrace(System.err);
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
    }
}

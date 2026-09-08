package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UncaughtTest {

    @Test
    void logsLinkageErrorWithoutRethrowing() {
        Uncaught.log("test: ", new NoClassDefFoundError("com.badlogic.gdx.utils.IntArray"));
    }

    @Test
    void rethrowsVirtualMachineError() {
        OutOfMemoryError oom = new OutOfMemoryError("test");
        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class, () -> Uncaught.log("test: ", oom));
        assertTrue(thrown == oom);
    }
}

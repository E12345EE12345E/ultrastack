package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoobGravityControllerTest {

    @Test
    void activate_disablesGravityAndDoublesMeterFill() {
        NoobGravityController c = new NoobGravityController();
        c.activate();

        assertEquals(0f, c.gravitySpeedFactor(), 0.0001f);
        assertEquals(2f, c.passiveMeterFillMultiplier(), 0.0001f);
        assertTrue(c.isActive());
    }

    @Test
    void secondActivateDuringDisable_stacksMeterOnly() {
        NoobGravityController c = new NoobGravityController();
        c.activate();
        c.tick(3_000);
        c.activate();

        assertEquals(3_000L, c.elapsedMs());
        assertEquals(2, c.stacks());
        assertEquals(0f, c.gravitySpeedFactor(), 0.0001f);
        assertEquals(3f, c.passiveMeterFillMultiplier(), 0.0001f);
    }

    @Test
    void activateDuringRamp_resetsDisableWindowAndStacksMeter() {
        NoobGravityController c = new NoobGravityController();
        c.activate();
        c.tick(12_000); // into ramp (2s into the 5s ramp)
        assertEquals(0.4f, c.gravitySpeedFactor(), 0.0001f);
        assertEquals(1f, c.passiveMeterFillMultiplier(), 0.0001f);

        c.activate();
        assertEquals(0L, c.elapsedMs());
        assertEquals(2, c.stacks());
        assertEquals(0f, c.gravitySpeedFactor(), 0.0001f);
        assertEquals(3f, c.passiveMeterFillMultiplier(), 0.0001f);
    }

    @Test
    void ramp_lerpsFallSpeedLinearly() {
        NoobGravityController c = new NoobGravityController();
        c.activate();
        c.tick(10_000);
        assertEquals(0f, c.gravitySpeedFactor(), 0.0001f);

        c.tick(2_500);
        assertEquals(0.5f, c.gravitySpeedFactor(), 0.0001f);

        c.tick(2_500);
        assertFalse(c.isActive());
        assertEquals(1f, c.gravitySpeedFactor(), 0.0001f);
        assertEquals(1f, c.passiveMeterFillMultiplier(), 0.0001f);
    }

    @Test
    void threeActivations_quadrupleMeterFill() {
        NoobGravityController c = new NoobGravityController();
        c.activate();
        c.activate();
        c.activate();
        assertEquals(4f, c.passiveMeterFillMultiplier(), 0.0001f);
    }
}

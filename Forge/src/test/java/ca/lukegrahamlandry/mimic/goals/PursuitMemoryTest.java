package ca.lukegrahamlandry.mimic.goals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PursuitMemoryTest {
    @Test void freshMemoryIsActive() {
        assertFalse(new PursuitMemory().expired(160));
    }
    @Test void forgetsAtExactVisibilityDeadline() {
        PursuitMemory memory = new PursuitMemory();
        for (int i = 0; i < 159; i++) memory.tick(false, true);
        assertFalse(memory.expired(160));
        memory.tick(false, true);
        assertTrue(memory.expired(160));
    }
    @Test void regainingSightResetsSearchTime() {
        PursuitMemory memory = new PursuitMemory();
        for (int i = 0; i < 159; i++) memory.tick(false, true);
        memory.tick(true, true);
        memory.tick(false, true);
        assertFalse(memory.expired(160));
    }
    @Test void abandonsUnreachableVisibleTarget() {
        PursuitMemory memory = new PursuitMemory();
        for (int i = 0; i < 199; i++) memory.tick(true, false);
        assertFalse(memory.expired(160));
        memory.tick(true, false);
        assertTrue(memory.expired(160));
    }
    @Test void progressResetsStuckDeadline() {
        PursuitMemory memory = new PursuitMemory();
        for (int i = 0; i < 199; i++) memory.tick(true, false);
        memory.tick(true, true);
        memory.tick(true, false);
        assertFalse(memory.expired(160));
    }
    @Test void newHuntClearsBothDeadlines() {
        PursuitMemory memory = new PursuitMemory();
        for (int i = 0; i < 220; i++) memory.tick(false, false);
        assertTrue(memory.expired(160));
        memory.reset();
        assertFalse(memory.expired(160));
    }
}

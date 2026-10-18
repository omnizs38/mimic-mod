package ca.lukegrahamlandry.mimic.horror;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HorrorRulesTest {
    @Test void calendarCountsCompletedDays() {
        assertEquals(0, HorrorRules.calendarDay(23999));
        assertEquals(1, HorrorRules.calendarDay(24000));
        assertEquals(0, HorrorRules.calendarDay(-24000));
    }
    @Test void escalationIsBounded() {
        assertEquals(20, HorrorRules.stage(Long.MAX_VALUE, 20));
        assertEquals(0, HorrorRules.stage(-1, 20));
        assertEquals(0, HorrorRules.stage(20, 0));
    }
    @Test void defaultDayTwentyOneDoublesHealthNotDamage() {
        assertEquals(2.0, HorrorRules.multiplier(20, 0.05), 0.0001);
        assertEquals(1.6, HorrorRules.multiplier(20, 0.03), 0.0001);
        assertEquals(1.3, HorrorRules.multiplier(20, 0.015), 0.0001);
    }
    @Test void firstDayHasNoBonus() { assertEquals(1.0, HorrorRules.multiplier(0, 0.05)); }
    @Test void swappedDelayBoundsAreSafe() {
        assertEquals(2400, HorrorRules.encounterDelay(6000, 2400, 0, 0));
        assertEquals(6000, HorrorRules.encounterDelay(6000, 2400, 0, 1));
    }
    @Test void escalationAcceleratesEncounters() {
        assertTrue(HorrorRules.encounterDelay(2400, 6000, 20, 0.5) < HorrorRules.encounterDelay(2400, 6000, 0, 0.5));
    }
    @Test void noConfigurationCanCausePerTickEncounterSpam() {
        assertEquals(200, HorrorRules.encounterDelay(1, 2, 100, -1));
        assertEquals(6000, HorrorRules.encounterDelay(2400, 6000, 0, 9));
    }
    @Test void biteConeRejectsPlayersBehindTheMimic() {
        assertTrue(HorrorRules.inBiteCone(1, 0, 1, 0));
        assertTrue(HorrorRules.inBiteCone(1, 1, 1, 0));
        assertFalse(HorrorRules.inBiteCone(-1, 0, 1, 0));
        assertFalse(HorrorRules.inBiteCone(0, 1, 1, 0));
    }

}

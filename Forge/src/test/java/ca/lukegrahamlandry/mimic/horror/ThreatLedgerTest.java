package ca.lukegrahamlandry.mimic.horror;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ThreatLedgerTest {
    @Test void rotatesAcrossMultiplePlayers() {
        ThreatLedger ledger = new ThreatLedger();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        for (UUID id : List.of(a, b, c)) ledger.observe(id, 0, 160, 1, 4);
        Set<UUID> players = Set.of(a, b, c);
        assertEquals(a, ledger.choose(1, players));
        assertEquals(b, ledger.choose(2, players));
        assertEquals(c, ledger.choose(3, players));
        assertEquals(a, ledger.choose(4, players));
    }
    @Test void neverFocusesAnIneligiblePlayer() {
        ThreatLedger ledger = new ThreatLedger(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ledger.observe(a, 0, 160, 10, 4); ledger.observe(b, 0, 160, 1, 4);
        assertEquals(b, ledger.choose(1, Set.of(b)));
        assertNull(ledger.choose(1, Set.of()));
    }
    @Test void expiresAtDeadline() {
        ThreatLedger ledger = new ThreatLedger(); UUID id = UUID.randomUUID();
        ledger.observe(id, 0, 160, 1, 4);
        assertEquals(1, ledger.size(159)); assertEquals(0, ledger.size(160));
    }
    @Test void refreshExtendsAwareness() {
        ThreatLedger ledger = new ThreatLedger(); UUID id = UUID.randomUUID();
        ledger.observe(id, 0, 160, 1, 4); ledger.observe(id, 100, 160, 1, 4);
        assertEquals(1, ledger.size(200));
    }
    @Test void ledgerCannotGrowWithPlayerCount() {
        ThreatLedger ledger = new ThreatLedger();
        for (int i = 0; i < 1000; i++) ledger.observe(UUID.randomUUID(), i, 10000, 1, 4);
        assertEquals(4, ledger.size(1000));
    }
    @Test void provocationBreaksOnlyUnfocusedTies() {
        ThreatLedger ledger = new ThreatLedger(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ledger.observe(a, 0, 160, 1, 4); ledger.observe(b, 0, 160, 10, 4);
        assertEquals(b, ledger.choose(1, Set.of(a,b)));
        assertEquals(a, ledger.choose(2, Set.of(a,b)));
    }
    @Test void lockingOrTamingCanClearEveryThreat() {
        ThreatLedger ledger = new ThreatLedger(); ledger.observe(UUID.randomUUID(), 0, 160, 1, 4);
        ledger.clear(); assertEquals(0, ledger.size(0));
    }
}

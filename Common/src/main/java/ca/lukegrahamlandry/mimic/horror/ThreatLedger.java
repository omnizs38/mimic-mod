package ca.lukegrahamlandry.mimic.horror;

import java.util.*;

/** Bounded, entity-local awareness. IDs expire instead of retaining disconnected player objects. */
public final class ThreatLedger {
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private long focusOrder;
    private static final class Entry {
        long expiresAt, focused;
        double threat;
    }
    public void observe(UUID id, long now, int ttl, double threat, int limit) {
        prune(now);
        if (!entries.containsKey(id) && entries.size() >= Math.max(1, limit)) {
            UUID oldest = entries.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().expiresAt)).get().getKey();
            entries.remove(oldest);
        }
        Entry e = entries.computeIfAbsent(id, ignored -> new Entry());
        e.expiresAt = now + Math.max(1, ttl);
        e.threat = Math.max(e.threat, threat);
    }
    public void focused(UUID id) {
        Entry e = entries.get(id);
        if (e != null) e.focused = ++focusOrder;
    }
    public UUID choose(long now, Set<UUID> eligible) {
        prune(now);
        UUID selected = entries.entrySet().stream().filter(e -> eligible.contains(e.getKey()))
                .min(Comparator.<Map.Entry<UUID, Entry>>comparingLong(e -> e.getValue().focused)
                        .thenComparingDouble(e -> -e.getValue().threat))
                .map(Map.Entry::getKey).orElse(null);
        if (selected != null) focused(selected);
        return selected;
    }
    public void prune(long now) { entries.entrySet().removeIf(e -> e.getValue().expiresAt <= now); }
    public int size(long now) { prune(now); return entries.size(); }
    public void clear() { entries.clear(); focusOrder = 0; }
}

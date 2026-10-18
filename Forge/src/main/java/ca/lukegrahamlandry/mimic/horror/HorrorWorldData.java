package ca.lukegrahamlandry.mimic.horror;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One escalation clock for every dimension, saved with the overworld. */
public class HorrorWorldData extends SavedData {
    private long highestDay;
    private final Map<UUID, Long> encounters = new LinkedHashMap<>();
    public static HorrorWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HorrorWorldData::load, HorrorWorldData::new, "mimic_horror");
    }
    public static HorrorWorldData load(CompoundTag tag) {
        HorrorWorldData data = new HorrorWorldData();
        data.highestDay = Math.max(0L, tag.getLong("HighestDay"));
        CompoundTag timers = tag.getCompound("Encounters");
        for (String key : timers.getAllKeys()) {
            if (data.encounters.size() >= 4096) break;
            try { data.encounters.put(UUID.fromString(key), Math.max(0L, timers.getLong(key))); }
            catch (IllegalArgumentException ignored) { /* Ignore corrupt IDs, not the rest of the save. */ }
        }
        return data;
    }
    public long advance(long dayTime) {
        long day = HorrorRules.calendarDay(dayTime);
        if (day > highestDay) { highestDay = day; setDirty(); }
        return highestDay;
    }
    public long nextEncounter(UUID player) { return encounters.getOrDefault(player, 0L); }
    public void schedule(UUID player, long tick) {
        // Bound saved account history on long-running public servers.
        if (!encounters.containsKey(player) && encounters.size() >= 4096) encounters.remove(encounters.keySet().iterator().next());
        encounters.put(player, tick);
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putLong("HighestDay", highestDay);
        CompoundTag timers = new CompoundTag();
        encounters.forEach((id, tick) -> timers.putLong(id.toString(), tick));
        tag.put("Encounters", timers);
        return tag;
    }
}

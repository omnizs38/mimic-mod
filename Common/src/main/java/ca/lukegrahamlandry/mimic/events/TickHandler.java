package ca.lukegrahamlandry.mimic.events;

import ca.lukegrahamlandry.mimic.Constants;
import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TickHandler {
    public static final List<MimicSpawnData> spawns = new ArrayList<>();

    public static boolean schedule(ServerLevel level, BlockPos pos) {
        if (spawns.stream().anyMatch(s -> s.world == level && s.pos.equals(pos))) return false;
        spawns.add(new MimicSpawnData(level, pos.immutable()));
        return true;
    }

    public static void onServerTick() {
        Iterator<MimicSpawnData> iterator = spawns.iterator();
        while (iterator.hasNext()) {
            MimicSpawnData spawn = iterator.next();
            if (spawn.time > 0) spawn.time--;
            // Do not force-load a chunk just to transform a chest.
            if (spawn.time == 0 && spawn.world.hasChunkAt(spawn.pos)) {
                iterator.remove();
                spawnMimic(spawn);
            }
        }
    }

    private static void spawnMimic(MimicSpawnData spawn) {
        if (!(spawn.world.getBlockState(spawn.pos).getBlock() instanceof ChestBlock)
                || !(spawn.world.getBlockEntity(spawn.pos) instanceof ChestBlockEntity)
                || ChestBlockEntity.getOpenCount(spawn.world, spawn.pos) > 0) return;
        MimicEntity mimic = (MimicEntity) Registry.ENTITY_TYPE.get(Constants.MIMIC_ENTITY_ID).create(spawn.world);
        if (mimic == null) return;
        mimic.snapToBlock(spawn.pos.above(), null);
        // Only remove the chest after successful entity insertion (events may veto spawning).
        if (spawn.world.addFreshEntity(mimic)) mimic.consumeChest(spawn.pos);
    }

    public static class MimicSpawnData {
        public final BlockPos pos;
        public int time = 40;
        public final ServerLevel world;

        private MimicSpawnData(ServerLevel world, BlockPos pos) {
            this.pos = pos;
            this.world = world;
        }
    }
}

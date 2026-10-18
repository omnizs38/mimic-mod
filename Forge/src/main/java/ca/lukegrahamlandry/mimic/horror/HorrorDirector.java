package ca.lukegrahamlandry.mimic.horror;

import ca.lukegrahamlandry.mimic.MimicForgeConfig;
import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import ca.lukegrahamlandry.mimic.init.EntityInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.Event;
import java.util.*;

/** Server-thread only. No block replacement, client clocks, forced chunks, or packet broadcasts. */
public final class HorrorDirector {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private static final class Session {
        final int entityId;
        final ServerLevel level;
        final long readyAt;
        Session(ServerPlayer player, long readyAt) {
            entityId = player.getId(); level = player.getLevel(); this.readyAt = readyAt;
        }
    }
    public static int stage(ServerLevel level) {
        if (!MimicForgeConfig.horrorEnabled.get()) return 0;
        MinecraftServer server = level.getServer();
        return HorrorRules.stage(HorrorWorldData.get(server).advance(server.overworld().getDayTime()),
                MimicForgeConfig.escalationDays.get());
    }
    public void clear() { sessions.clear(); }
    public static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isSleeping();
    }
    public void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        HorrorWorldData data = HorrorWorldData.get(server);
        int stage = HorrorRules.stage(data.advance(server.overworld().getDayTime()), MimicForgeConfig.escalationDays.get());
        if (!MimicForgeConfig.horrorEnabled.get()) { clear(); return; }
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (!players.isEmpty()) Collections.rotate(players, (int) ((now / 20) % players.size()));
        Set<UUID> active = new HashSet<>();
        Map<ServerLevel, List<MimicEntity>> wild = null;
        int budget = 2;
        for (ServerPlayer player : players) {
            if (!eligible(player)) { sessions.remove(player.getUUID()); continue; }
            active.add(player.getUUID());
            ServerLevel level = player.getLevel();
            if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) continue;
            Session session = sessions.get(player.getUUID());
            if (session == null || session.entityId != player.getId() || session.level != level) {
                long ready = Math.max(data.nextEncounter(player.getUUID()), now + MimicForgeConfig.loginGraceTicks.get() + level.random.nextInt(1201));
                session = new Session(player, ready);
                sessions.put(player.getUUID(), session);
                data.schedule(player.getUUID(), ready);
            }
            if (budget == 0 || now < session.readyAt || now < data.nextEncounter(player.getUUID())) continue;
            budget--;
            data.schedule(player.getUUID(), now + HorrorRules.encounterDelay(MimicForgeConfig.encounterMinTicks.get(),
                    MimicForgeConfig.encounterMaxTicks.get(), stage, level.random.nextDouble()));
            boolean spawned = false;
            if (MimicForgeConfig.surpriseSpawns.get() && level.random.nextInt(100) < Math.min(70, 30 + stage * 2)) {
                if (wild == null) wild = scan(server);
                List<MimicEntity> inLevel = wild.computeIfAbsent(level, ignored -> new ArrayList<>());
                int total = wild.values().stream().mapToInt(List::size).sum();
                long nearby = inLevel.stream().filter(m -> m.distanceToSqr(player) < 48 * 48).count();
                if (total < MimicForgeConfig.maxWildTotal.get() && inLevel.size() < MimicForgeConfig.maxWildPerLevel.get()
                        && nearby < MimicForgeConfig.maxNearbyWild.get()) {
                    MimicEntity mimic = spawnBehindAwareness(level, player);
                    if (mimic != null) { inLevel.add(mimic); spawned = true; }
                }
            }
            // False alarms are intentional: a cue must not reliably prove a mimic has spawned.
            if (MimicForgeConfig.paranoiaSounds.get() && (!spawned || level.random.nextBoolean())) cue(player, stage);
        }
        sessions.keySet().retainAll(active);
    }
    private static Map<ServerLevel, List<MimicEntity>> scan(MinecraftServer server) {
        Map<ServerLevel, List<MimicEntity>> result = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            List<MimicEntity> list = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof MimicEntity mimic && mimic.isAlive() && !mimic.isTamed()) list.add(mimic);
            }
            result.put(level, list);
        }
        return result;
    }
    private static MimicEntity spawnBehindAwareness(ServerLevel level, ServerPlayer player) {
        MimicEntity mimic = EntityInit.MIMIC.get().create(level);
        if (mimic == null) return null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = 12 + level.random.nextInt(21);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
            int startY = player.blockPosition().getY() + level.random.nextInt(9) - 4;
            for (int offset = 0; offset <= 24; offset++) {
                int y = startY + (offset % 2 == 0 ? offset / 2 : -(offset + 1) / 2);
                BlockPos pos = new BlockPos(x, y, z);
                if (!safeSite(level, pos, mimic)) continue;
                Vec3 eyes = mimic.getEyePosition();
                if (observed(level, eyes)) continue;
                if (ForgeEventFactory.canEntitySpawn(mimic, level, mimic.getX(), mimic.getY(), mimic.getZ(), null,
                        MobSpawnType.EVENT) == Event.Result.DENY) continue;
                if (!ForgeEventFactory.doSpecialSpawn(mimic, level, (float) mimic.getX(), (float) mimic.getY(),
                        (float) mimic.getZ(), null, MobSpawnType.EVENT)) {
                    mimic.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                }
                mimic.setStealth(true);
                mimic.markHorrorSpawn(level.getGameTime() + 12000);
                if (level.addFreshEntity(mimic)) return mimic;
                return null; // An entity-join veto must not be retried repeatedly in the same event.
            }
        }
        return null;
    }
    public static boolean safeSite(ServerLevel level, BlockPos pos, MimicEntity mimic) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 2 >= level.getMaxBuildHeight()
                || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        BlockPos floor = pos.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || level.getBlockState(floor).is(Blocks.MAGMA_BLOCK) || level.getBlockState(floor).is(Blocks.CACTUS)
                || level.getBlockState(floor).is(Blocks.CAMPFIRE) || level.getBlockState(floor).is(Blocks.SOUL_CAMPFIRE)) return false;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || !level.getFluidState(pos).isEmpty()) return false;
        mimic.snapToBlock(pos, Direction.NORTH);
        return level.getWorldBorder().isWithinBounds(mimic.getBoundingBox())
                && level.noCollision(mimic, mimic.getBoundingBox());
    }
    private static boolean observed(ServerLevel level, Vec3 position) {
        for (ServerPlayer observer : level.players()) {
            if (!observer.isAlive() || observer.isSpectator()) continue;
            Vec3 delta = position.subtract(observer.getEyePosition());
            if (delta.lengthSqr() < 64) return true;
            if (delta.lengthSqr() > 64 * 64 || observer.getLookAngle().dot(delta.normalize()) < 0.5) continue;
            if (level.clip(new ClipContext(observer.getEyePosition(), position, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, observer)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
    static void cue(ServerPlayer player, int stage) {
        ServerLevel level = player.getLevel();
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize();
        if (forward.lengthSqr() < 0.1) forward = new Vec3(0, 0, 1);
        Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(level.random.nextDouble() * 5 - 2.5);
        Vec3 origin = player.position().subtract(forward.scale(3 + level.random.nextDouble() * 4)).add(side);
        SoundEvent[] sounds = {SoundEvents.WOOD_STEP, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE,
                SoundEvents.AMBIENT_CAVE, SoundEvents.ENDERMAN_STARE};
        int choices = stage >= 5 ? sounds.length : 3;
        SoundEvent sound = sounds[level.random.nextInt(choices)];
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE, origin.x, origin.y + 0.5,
                origin.z, 0.35F, 0.75F + level.random.nextFloat() * 0.3F, level.random.nextLong()));
    }
}

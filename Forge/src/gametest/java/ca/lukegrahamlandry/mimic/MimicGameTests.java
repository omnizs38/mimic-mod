package ca.lukegrahamlandry.mimic;

import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import com.mojang.authlib.GameProfile;
import ca.lukegrahamlandry.mimic.goals.MimicAttackGoal;
import ca.lukegrahamlandry.mimic.goals.MimicStalkGoal;
import ca.lukegrahamlandry.mimic.horror.HorrorWorldData;
import ca.lukegrahamlandry.mimic.horror.HorrorDirector;
import ca.lukegrahamlandry.mimic.init.EntityInit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("mimic")
@PrefixGameTestTemplate(false)
public class MimicGameTests {
    private static MimicEntity spawn(GameTestHelper h) {
        MimicEntity mimic = h.spawn(EntityInit.MIMIC.get(), 2, 2, 2);
        mimic.setNoAi(true);
        return mimic;
    }
    private static Player survivalPlayer(GameTestHelper h) {
        return new Player(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), 0,
                new GameProfile(UUID.randomUUID(), "mimic-test"), null) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void corruptSaveDoesNotCrash(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        CompoundTag save = new CompoundTag();
        mimic.addAdditionalSaveData(save);
        save.putBoolean("tame", true);
        save.putString("owner", "not-a-uuid");
        CompoundTag items = new CompoundTag();
        for (int i = 0; i < 40; i++) items.put(String.valueOf(i), new ItemStack(Items.DIAMOND).save(new CompoundTag()));
        save.put("mimichelditems", items);
        mimic.readAdditionalSaveData(save);
        check(mimic.getContainerSize() == 27, "Inventory must stay at 27 slots");
        check(mimic.getItem(26).is(Items.DIAMOND), "Valid slots must survive corrupt oversized NBT");
        check(mimic.getOwner() == null, "Corrupt owner must fail closed");
        mimic.addAdditionalSaveData(new CompoundTag());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sparseInventoryPreservesLaterSlots(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        CompoundTag save = new CompoundTag();
        mimic.addAdditionalSaveData(save);
        CompoundTag items = new CompoundTag();
        items.put("26", new ItemStack(Items.EMERALD, 9).save(new CompoundTag()));
        save.put("mimichelditems", items);
        mimic.readAdditionalSaveData(save);
        check(mimic.getItem(0).isEmpty() && mimic.getItem(26).getCount() == 9,
                "Missing earlier tags must not discard valid later inventory slots");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyOwnerMigrates(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        UUID owner = UUID.randomUUID();
        CompoundTag save = new CompoundTag();
        mimic.addAdditionalSaveData(save);
        save.putBoolean("tame", true);
        save.putString("owner", owner.toString());
        mimic.readAdditionalSaveData(save);
        CompoundTag migrated = new CompoundTag();
        mimic.addAdditionalSaveData(migrated);
        check(migrated.hasUUID("Owner") && owner.equals(migrated.getUUID("Owner")), "Legacy UUID must migrate losslessly");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lockingClearsCombat(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        Player player = survivalPlayer(h);
        mimic.provoke(player);
        check(mimic.getTarget() == player, "Provocation must target the actual attacker");
        mimic.setLocked(true);
        check(mimic.getTarget() == null && !mimic.isAngry(), "Locking must cancel combat");
        mimic.provoke(player);
        check(mimic.getTarget() == null, "Locked mimics cannot reacquire targets");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryRejectsRemoteAccess(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        Player player = survivalPlayer(h);
        player.moveTo(mimic.getX(), mimic.getY(), mimic.getZ());
        check(mimic.stillValid(player), "Nearby player must be able to use a wild mimic");
        player.moveTo(mimic.getX() + 20, mimic.getY(), mimic.getZ());
        check(!mimic.stillValid(player), "Remote menus must become invalid");
        mimic.setTamed(true);
        check(!mimic.stillValid(player), "Unknown owners must not grant inventory access");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chestContentsMoveExactlyOnce(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        mimic.consumeChest(h.absolutePos(pos));
        mimic.consumeChest(h.absolutePos(pos));
        h.assertBlockPresent(Blocks.AIR, pos);
        check(mimic.getItem(0).is(Items.DIAMOND) && mimic.getItem(0).getCount() == 17,
                "Consumed chest contents must not duplicate or disappear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void attackCannotStartThroughWall(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        Player player = survivalPlayer(h);
        player.moveTo(mimic.getX() + 1.8, mimic.getY(), mimic.getZ());
        mimic.provoke(player);
        check(mimic.canHunt(player), "Survival fixture must be a valid target");
        check(mimic.isAngry(), "Provocation must set combat state immediately");
        check(mimic.distanceToSqr(player) <= 4.0, "Fixture target must be in melee range");
        mimic.getSensing().tick();
        net.minecraft.world.phys.BlockHitResult hit = mimic.level.clip(new net.minecraft.world.level.ClipContext(
                mimic.getEyePosition(), player.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mimic));
        check(mimic.getSensing().hasLineOfSight(player), "Fixture sight blocked: " + mimic.getEyePosition() + " -> "
                + player.getEyePosition() + "; hit " + hit.getBlockPos() + " " + mimic.level.getBlockState(hit.getBlockPos()));
        check(new MimicAttackGoal(mimic).canUse(), "An exposed nearby survival player must be attackable");
        h.setBlock(3, 2, 2, Blocks.STONE);
        h.setBlock(3, 3, 2, Blocks.STONE);
        h.runAfterDelay(2, () -> {
            mimic.getSensing().tick(); // NoAI disables the usual per-tick sensing-cache reset.
            check(!new MimicAttackGoal(mimic).canUse(), "Bite must require line of sight");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void escalationSurvivesSaveAndClockRollback(GameTestHelper h) {
        HorrorWorldData data = new HorrorWorldData();
        check(data.advance(48000) == 2, "Two completed days must be recorded");
        HorrorWorldData restored = HorrorWorldData.load(data.save(new CompoundTag()));
        check(restored.advance(0) == 2, "Time rollback or restart must not reduce escalation");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void cooldownsSurviveSave(GameTestHelper h) {
        HorrorWorldData data = new HorrorWorldData(); UUID id = UUID.randomUUID();
        data.schedule(id, 100000);
        HorrorWorldData restored = HorrorWorldData.load(data.save(new CompoundTag()));
        check(restored.nextEncounter(id) == 100000, "Relogging must not reset a later saved cooldown");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void multipleAttackersAreRememberedWithoutRedirectingBite(GameTestHelper h) {
        MimicEntity mimic = spawn(h); Player first = survivalPlayer(h), second = survivalPlayer(h);
        mimic.provoke(first); mimic.startAttackAnim(); mimic.provoke(second);
        check(mimic.trackedPlayers() == 2, "A mimic must remember more than one attacker");
        check(mimic.getTarget() == first, "An already telegraphed bite must not redirect to another player");
        mimic.setLocked(true);
        check(mimic.trackedPlayers() == 0 && mimic.getTarget() == null, "Locking must clear every threat");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void safeSpawnRejectsWallsAndUnsupportedAir(GameTestHelper h) {
        MimicEntity mimic = spawn(h); BlockPos relative = new BlockPos(4, 2, 4);
        BlockPos pos = h.absolutePos(relative);
        check(HorrorDirector.safeSite(h.getLevel(), pos, mimic), "Supported empty air must be a valid site");
        h.setBlock(relative, Blocks.STONE);
        check(!HorrorDirector.safeSite(h.getLevel(), pos, mimic), "Never spawn inside a solid block");
        h.setBlock(relative, Blocks.AIR); h.setBlock(relative.below(), Blocks.AIR);
        check(!HorrorDirector.safeSite(h.getLevel(), pos, mimic), "Never spawn over unsupported air");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void spawnSiteDoesNotDependOnDaylight(GameTestHelper h) {
        MimicEntity mimic = spawn(h); BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        long previous = h.getLevel().getDayTime();
        try {
            h.getLevel().setDayTime(6000);
            check(HorrorDirector.safeSite(h.getLevel(), pos, mimic), "Daylight must not prevent a horror spawn");
            h.getLevel().setDayTime(18000);
            check(HorrorDirector.safeSite(h.getLevel(), pos, mimic), "Nighttime must remain eligible");
        } finally { h.getLevel().setDayTime(previous); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void watchingDependsOnGazeNotJustDistance(GameTestHelper h) {
        MimicEntity mimic = spawn(h); Player player = survivalPlayer(h);
        player.moveTo(mimic.getX() + 3, mimic.getY(), mimic.getZ());
        player.setYRot(90); player.setXRot(0);
        check(MimicStalkGoal.isWatching(player, mimic), "Looking toward the mimic must count as watching");
        player.setYRot(-90);
        check(!MimicStalkGoal.isWatching(player, mimic), "Looking away must allow stalking");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void applyingEscalationDoesNotHealDamage(GameTestHelper h) {
        MimicEntity mimic = spawn(h); mimic.setHealth(mimic.getMaxHealth() / 2);
        mimic.applyCombatConfig();
        check(Math.abs(mimic.getHealth() / mimic.getMaxHealth() - 0.5) < 0.001,
                "Stage or configuration changes must preserve the health percentage");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void tamingDisablesDirectorExpiry(GameTestHelper h) {
        MimicEntity mimic = spawn(h); mimic.markHorrorSpawn(100000); mimic.setTamed(true);
        CompoundTag save = new CompoundTag(); mimic.addAdditionalSaveData(save);
        check(!save.contains("HorrorExpiresAt"), "A tamed horror spawn must never expire as an encounter actor");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void differentMimicsDoNotShareTargets(GameTestHelper h) {
        MimicEntity a = spawn(h), b = h.spawn(EntityInit.MIMIC.get(), 6, 2, 6);
        b.setNoAi(true); Player first = survivalPlayer(h), second = survivalPlayer(h);
        a.provoke(first); b.provoke(second);
        check(a.getTarget() == first && b.getTarget() == second, "Target state must be per-entity, not shared across the server");
        check(a.trackedPlayers() == 1 && b.trackedPlayers() == 1, "Threat ledgers must remain isolated");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void spawnValidationNeverLoadsRemoteChunks(GameTestHelper h) {
        MimicEntity mimic = spawn(h);
        BlockPos remote = h.absolutePos(new BlockPos(100000, 2, 100000));
        check(!h.getLevel().hasChunkAt(remote), "Fixture remote chunk must start unloaded");
        check(!HorrorDirector.safeSite(h.getLevel(), remote, mimic), "Unloaded chunks must be rejected");
        check(!h.getLevel().hasChunkAt(remote), "Checking a spawn site must not force a chunk load");
        h.succeed();
    }

}

package ca.lukegrahamlandry.mimic.horror;

import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import ca.lukegrahamlandry.mimic.init.EntityInit;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("mimic")
@PrefixGameTestTemplate(false)
public class MultiplayerGameTests {
    private static class RecordingConnection extends Connection {
        final List<Packet<?>> sent = new ArrayList<>();
        RecordingConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet) { sent.add(packet); }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { sent.add(packet); }
    }
    private static ServerPlayer player(GameTestHelper h, RecordingConnection connection) {
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new GameProfile(UUID.randomUUID(), "mimic-test"), null);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 3));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        connection.sent.clear();
        return player;
    }
    private static MimicEntity mimic(GameTestHelper h) {
        MimicEntity mimic = h.spawn(EntityInit.MIMIC.get(), 2, 2, 2);
        mimic.setNoAi(true); mimic.setStealth(true);
        return mimic;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @GameTest(template = "empty")
    public static void fearPacketOnlyReachesIntendedPlayer(GameTestHelper h) {
        RecordingConnection first = new RecordingConnection(), second = new RecordingConnection();
        ServerPlayer a = player(h, first); player(h, second);
        HorrorDirector.cue(a, 10);
        check(first.sent.size() == 1 && first.sent.get(0) instanceof ClientboundSoundPacket,
                "The intended player must receive one sound packet");
        check(second.sent.isEmpty(), "A private scare must not leak to another player's connection");
        ClientboundSoundPacket packet = (ClientboundSoundPacket) first.sent.get(0);
        check(packet.getSource() == SoundSource.HOSTILE && packet.getVolume() <= 0.4F,
                "Sound volume must remain bounded and respect the Hostile Creatures slider");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void secondViewerCannotStealTrapOwnership(GameTestHelper h) {
        MimicEntity mimic = mimic(h);
        ServerPlayer a = player(h, new RecordingConnection()), b = player(h, new RecordingConnection());
        a.containerMenu = mimic.createMenu(1, a.getInventory(), a);
        check(a.containerMenu != null, "The first nearby viewer must open the mimic");
        check(mimic.createMenu(2, b.getInventory(), b) == null, "Another viewer must not overwrite a pending trap victim");
        h.runAfterDelay(17, () -> {
            check(mimic.getTarget() == a, "The trap must provoke its original viewer, not another player");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void trapNeverClosesUnrelatedMenu(GameTestHelper h) {
        MimicEntity mimic = mimic(h); RecordingConnection connection = new RecordingConnection();
        ServerPlayer a = player(h, connection);
        AbstractContainerMenu menu = mimic.createMenu(1, a.getInventory(), a);
        check(menu != null, "The trap fixture must start with a valid menu");
        a.containerMenu = a.inventoryMenu;
        connection.sent.clear();
        h.runAfterDelay(17, () -> {
            check(mimic.getTarget() == a, "Leaving the menu must not erase the provocation");
            check(connection.sent.stream().noneMatch(p -> p instanceof ClientboundContainerClosePacket),
                    "A delayed trap must not close the player's unrelated current menu");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void ownedStorageRemainsPrivateBetweenPlayers(GameTestHelper h) {
        MimicEntity mimic = mimic(h);
        ServerPlayer owner = player(h, new RecordingConnection()), stranger = player(h, new RecordingConnection());
        CompoundTag tag = new CompoundTag(); mimic.addAdditionalSaveData(tag);
        tag.putBoolean("tame", true); tag.putUUID("Owner", owner.getUUID());
        mimic.readAdditionalSaveData(tag);
        check(mimic.stillValid(owner) && !mimic.stillValid(stranger), "Only the recorded owner may use tamed storage");
        check(mimic.createMenu(1, stranger.getInventory(), stranger) == null, "A second connection must not bypass ownership");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void biteHitsSeveralPlayersButNotPlayersBehindOrCreative(GameTestHelper h) {
        MimicEntity mimic = mimic(h); mimic.setStealth(false);
        ServerPlayer a = player(h, new RecordingConnection()), b = player(h, new RecordingConnection());
        ServerPlayer behind = player(h, new RecordingConnection()), creative = player(h, new RecordingConnection());
        a.moveTo(mimic.getX() + 1.5, mimic.getY(), mimic.getZ());
        b.moveTo(mimic.getX() + 1.2, mimic.getY(), mimic.getZ() + 0.6);
        behind.moveTo(mimic.getX() - 1.5, mimic.getY(), mimic.getZ());
        creative.moveTo(mimic.getX() + 1.2, mimic.getY(), mimic.getZ() - 0.6);
        creative.setGameMode(GameType.CREATIVE);
        List<ServerPlayer> players = List.of(a, b, behind, creative);
        for (ServerPlayer player : players) h.getLevel().addNewPlayer(player);
        // Vanilla grants newly constructed ServerPlayers 60 ticks of spawn protection.
        h.runAfterDelay(65, () -> {
          try {
            float aHealth = a.getHealth(), bHealth = b.getHealth();
            float behindHealth = behind.getHealth(), creativeHealth = creative.getHealth();
            mimic.performBite(a);
            check(a.getHealth() < aHealth && b.getHealth() < bHealth, "One bite must pressure several nearby survival players: primary=" + a.getHealth() + ", secondary=" + b.getHealth());
            check(behind.getHealth() == behindHealth, "Players behind the bite cone must remain unharmed");
            check(creative.getHealth() == creativeHealth, "Creative players must not take cleave damage");
            h.succeed();
          } finally { for (ServerPlayer player : players) player.discard(); }
        });
    }

}

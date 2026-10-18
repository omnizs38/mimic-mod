package ca.lukegrahamlandry.mimic;

import ca.lukegrahamlandry.mimic.events.TickHandler;
import ca.lukegrahamlandry.mimic.horror.HorrorDirector;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeTickHandler {
    private static final HorrorDirector HORROR = new HorrorDirector();
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MimicEntity mimic) {
            mimic.applyCombatConfig();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TickHandler.spawns.clear();
        HORROR.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) TickHandler.onServerTick();
        else HORROR.tick(event.getServer());
    }
}

package ca.lukegrahamlandry.mimic.goals;

import ca.lukegrahamlandry.mimic.MimicForgeConfig;
import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.EnumSet;

/** Creeps while unobserved. Any eligible observer can freeze it, not just its current prey. */
public class MimicStalkGoal extends Goal {
    private final MimicEntity mimic;
    private Player prey;
    private int repath;
    public MimicStalkGoal(MimicEntity mimic) {
        this.mimic = mimic;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    public static boolean isWatching(Player player, MimicEntity mimic) {
        Vec3 delta = mimic.getEyePosition().subtract(player.getEyePosition());
        return delta.lengthSqr() <= 48 * 48 && player.getLookAngle().dot(delta.normalize()) > 0.5
                && player.hasLineOfSight(mimic);
    }
    private boolean watched() {
        return mimic.level.players().stream().filter(mimic::canHunt).anyMatch(player -> isWatching(player, mimic));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canUse() {
        if (!MimicForgeConfig.horrorEnabled.get() || !mimic.canHunt() || !mimic.isStealth()
                || mimic.getTarget() != null || mimic.isOpen() || watched()) return false;
        prey = mimic.level.players().stream().filter(mimic::canHunt)
                .filter(player -> mimic.distanceToSqr(player) <= 28 * 28 && mimic.getSensing().hasLineOfSight(player))
                .min(Comparator.comparingDouble(mimic::distanceToSqr)).orElse(null);
        return prey != null;
    }
    @Override public boolean canContinueToUse() {
        return MimicForgeConfig.horrorEnabled.get() && mimic.canHunt(prey) && !mimic.isOpen()
                && mimic.getTarget() == null && mimic.distanceToSqr(prey) > 9
                && mimic.distanceToSqr(prey) <= 28 * 28 && mimic.getSensing().hasLineOfSight(prey) && !watched();
    }
    @Override public void start() { repath = 0; mimic.setStealth(false); }
    @Override public void tick() {
        mimic.getLookControl().setLookAt(prey, 20, 20);
        if (--repath <= 0) {
            mimic.getNavigation().moveTo(prey, 0.45);
            repath = 10;
        }
    }
    @Override public void stop() {
        mimic.getNavigation().stop();
        if (mimic.canHunt() && mimic.getTarget() == null && !mimic.isOpen()) mimic.setStealth(true);
        prey = null;
    }
}

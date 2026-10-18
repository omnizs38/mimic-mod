package ca.lukegrahamlandry.mimic.goals;

import ca.lukegrahamlandry.mimic.MimicForgeConfig;
import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class MimicChaseGoal extends Goal {
    private final MimicEntity mimic;
    private final PursuitMemory memory = new PursuitMemory();
    private Vec3 lastSeen;
    private Vec3 previousPosition;
    private LivingEntity trackedTarget;
    private int repathTicks;

    public MimicChaseGoal(MimicEntity mimic) {
        this.mimic = mimic;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canUse() {
        return mimic.isAngry() && mimic.canHunt(mimic.getTarget());
    }
    @Override public boolean canContinueToUse() { return canUse(); }

    @Override public void start() {
        memory.reset();
        repathTicks = 0;
        trackedTarget = mimic.getTarget();
        lastSeen = null;
        previousPosition = mimic.position();
    }

    @Override public void tick() {
        LivingEntity target = mimic.getTarget();
        if (!mimic.canHunt(target)) return;
        if (trackedTarget != target) start();
        double range = mimic.pursuitRange();
        boolean visible = mimic.getSensing().hasLineOfSight(target);
        double distance = mimic.distanceToSqr(target);
        memory.tick(visible, distance <= 4.0 || mimic.position().distanceToSqr(previousPosition) > 0.0001);
        previousPosition = mimic.position();
        if (distance > range * range || memory.expired(mimic.sightMemoryTicks())) {
            mimic.finishHunt();
            return;
        }
        if (visible) lastSeen = target.position();
        if (lastSeen == null) return;
        mimic.getLookControl().setLookAt(lastSeen.x, lastSeen.y + target.getEyeHeight(), lastSeen.z);
        if (mimic.getAttackTick() > 0 || (visible && distance <= 2.25)) {
            mimic.getNavigation().stop();
            return;
        }
        // Repath at most twice a second; failed paths back off for a full second.
        if (--repathTicks <= 0) {
            boolean found = mimic.getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z,
                    MimicForgeConfig.chaseSpeed.get());
            repathTicks = found ? 10 : 20;
        }
    }

    @Override public void stop() {
        mimic.getNavigation().stop();
        trackedTarget = null;
        lastSeen = null;
    }
}

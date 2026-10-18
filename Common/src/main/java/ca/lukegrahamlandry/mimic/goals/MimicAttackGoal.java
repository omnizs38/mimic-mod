package ca.lukegrahamlandry.mimic.goals;

import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class MimicAttackGoal extends Goal {
    private final MimicEntity mimic;
    private LivingEntity victim;
    private boolean struck;

    public MimicAttackGoal(MimicEntity mimic) { this.mimic = mimic; }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    private boolean inRange(LivingEntity target) {
        return target != null && mimic.distanceToSqr(target) <= 4.0
                && mimic.getSensing().hasLineOfSight(target);
    }

    @Override public boolean canUse() {
        return mimic.isAngry() && mimic.canHunt(mimic.getTarget()) && inRange(mimic.getTarget());
    }
    @Override public boolean canContinueToUse() {
        return mimic.isAngry() && mimic.canHunt(victim) && mimic.getTarget() == victim
                && mimic.getAttackTick() > 0;
    }
    @Override public void start() {
        victim = mimic.getTarget();
        struck = false;
        mimic.getNavigation().stop();
        mimic.startAttackAnim();
    }
    @Override public void tick() {
        if (!struck && mimic.getAttackTick() <= 4) {
            struck = true;
            if (mimic.canHunt(victim) && inRange(victim)) mimic.performBite(victim);
        }
    }
    @Override public void stop() { victim = null; }
}

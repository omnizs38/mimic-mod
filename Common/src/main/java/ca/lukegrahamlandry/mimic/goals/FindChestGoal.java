package ca.lukegrahamlandry.mimic.goals;

import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public class FindChestGoal extends MoveToBlockGoal {
    private final MimicEntity mimic;

    public FindChestGoal(MimicEntity mimic, double speed) {
        super(mimic, speed, 8, 2);
        this.mimic = mimic;
    }
    private boolean canSearch() {
        return mimic.canEatChests() && !mimic.isAngry() && !mimic.isStealth() && !mimic.isOpen();
    }
    @Override public boolean canUse() { return canSearch() && super.canUse(); }
    @Override public boolean canContinueToUse() {
        return canSearch() && super.canContinueToUse() && !isReachedTarget();
    }
    @Override public void stop() {
        super.stop();
        mimic.getNavigation().stop();
    }
    @Override protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.isEmptyBlock(pos.above())
                && level.getBlockState(pos).getBlock() instanceof ChestBlock
                && level.getBlockEntity(pos) instanceof ChestBlockEntity
                && ChestBlockEntity.getOpenCount(level, pos) == 0;
    }
}

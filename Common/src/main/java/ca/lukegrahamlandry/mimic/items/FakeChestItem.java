package ca.lukegrahamlandry.mimic.items;

import ca.lukegrahamlandry.mimic.events.TickHandler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class FakeChestItem extends Item {
    public FakeChestItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());

        if (state.getBlock() instanceof ChestBlock && context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
            if (!TickHandler.schedule((ServerLevel) context.getLevel(), context.getClickedPos())) return InteractionResult.FAIL;
            if (!context.getPlayer().isCreative()) context.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }

        return super.useOn(context);
    }
}

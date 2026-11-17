package dev.freetrade.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Common block behaviour: owner on placement, owner-only GUI and breaking, contents dropped on removal. */
public abstract class MarketBlock extends BaseEntityBlock {
    protected MarketBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player p && level.getBlockEntity(pos) instanceof MarketStorageBlockEntity be) {
            be.setOwner(p);
        }
    }

    protected abstract MenuProvider menu(MarketStorageBlockEntity be);

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof MarketStorageBlockEntity be)) return InteractionResult.PASS;
        if (!be.canAccess(player)) {
            player.sendOverlayMessage(Component.translatable("freetrade.block.not_owner", be.ownerName()));
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) sp.openMenu(menu(be), pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MarketStorageBlockEntity be && !be.canAccess(player)) return 0f;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : MarketStorageBlockEntity::serverTick;
    }
}

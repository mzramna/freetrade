package dev.freetrade.block;

import com.mojang.serialization.MapCodec;

import dev.freetrade.menu.AutoBuyerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AutoBuyerBlock extends MarketBlock {
    public static final MapCodec<AutoBuyerBlock> CODEC = simpleCodec(AutoBuyerBlock::new);

    public AutoBuyerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoBuyerBlockEntity(pos, state);
    }

    @Override
    protected MenuProvider menu(MarketStorageBlockEntity be) {
        return new SimpleMenuProvider((id, inv, p) -> new AutoBuyerMenu(id, inv, (AutoBuyerBlockEntity) be),
                Component.translatable("block.freetrade.auto_buyer"));
    }
}

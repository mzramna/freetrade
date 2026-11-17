package dev.freetrade.block;

import com.mojang.serialization.MapCodec;

import dev.freetrade.menu.SellPointMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SellPointBlock extends MarketBlock {
    public static final MapCodec<SellPointBlock> CODEC = simpleCodec(SellPointBlock::new);

    public SellPointBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SellPointBlockEntity(pos, state);
    }

    @Override
    protected MenuProvider menu(MarketStorageBlockEntity be) {
        return new SimpleMenuProvider((id, inv, p) -> new SellPointMenu(id, inv, (SellPointBlockEntity) be),
                Component.translatable("block.freetrade.sell_point"));
    }
}

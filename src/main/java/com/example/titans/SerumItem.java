package com.example.titans;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
public class SerumItem extends Item {
    private final TitanType type;
    public SerumItem(Properties p, TitanType t) { super(p); type = t; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) TitanManager.toggle(sp, type);
        return InteractionResult.SUCCESS;
    }
}

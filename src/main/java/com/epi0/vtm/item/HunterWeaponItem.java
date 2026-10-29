package com.epi0.vtm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A hunter's ranged weapon, usable by anyone: no ammunition, but it wears out. */
public class HunterWeaponItem extends Item {
    private final HunterArms.Profile profile;

    public HunterWeaponItem(HunterArms.Profile profile, Properties properties) {
        super(properties);
        this.profile = profile;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel server) {
            HunterArms.fire(server, player, player.getLookAngle(), profile);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        player.getCooldowns().addCooldown(this, profile.cooldown);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.vtm.hunter_weapon.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

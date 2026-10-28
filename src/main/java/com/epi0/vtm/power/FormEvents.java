package com.epi0.vtm.power;

import com.epi0.vtm.VtmMod;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Mist form: a drifting cloud cannot touch the world, and the world (mostly) cannot touch it. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class FormEvents {

    private static boolean isMist(Player player) {
        return Forms.isIn(player, Form.MIST);
    }

    private static boolean harmsMist(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && isMist(player) && !harmsMist(event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isMist(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfMist(event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfMist(event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfMist(event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfMist(event);
    }

    private static void cancelIfMist(PlayerInteractEvent event) {
        if (isMist(event.getEntity()) && event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }

    private FormEvents() {}
}

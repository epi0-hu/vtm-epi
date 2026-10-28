package com.epi0.vtm.event;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.power.PassiveEffects;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.SenseLink;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.vampire.Banes;
import com.epi0.vtm.vampire.Feeding;
import com.epi0.vtm.vampire.Frenzy;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import com.epi0.vtm.vampire.VampireRules;
import com.epi0.vtm.vampire.VampireTicker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Common (both-side) game-bus events. Anything that mutates state is guarded to the logical server. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class ServerEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VampireManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Feeding.forget(event.getEntity().getUUID());
        PowerManager.forget(event.getEntity().getUUID());
        Frenzy.forget(event.getEntity().getUUID());
        Banes.forget(event.getEntity().getUUID());
        SenseLink.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SenseLink.end(player);
            VampireManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VampireData data = VampireData.of(player);
        if (data.isVampire() && data.blood() < VampireRules.RESPAWN_BLOOD) {
            data = data.withBlood(VampireRules.RESPAWN_BLOOD);
            player.setData(ModAttachments.VAMPIRE, data);
        }
        PassiveEffects.apply(player, data);
        VampireManager.sync(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VampireTicker.tick(player);
        }
    }

    /** Sneak + empty main hand on a creature = bite. Cancelled on both sides so e.g. villager trading doesn't open. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty()
                || !(event.getTarget() instanceof LivingEntity target)
                || !VampireData.of(player).isVampire()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer serverPlayer) {
            Feeding.bite(serverPlayer, target);
        }
    }

    private ServerEvents() {}
}

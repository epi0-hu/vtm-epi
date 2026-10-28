package com.epi0.vtm.power;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.network.SenseLinkPayload;
import com.epi0.vtm.network.VtmNetwork;
import com.epi0.vtm.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auspex 5, Share the Senses: the caster's view is carried into another creature while their own body
 * stands helpless. The link ends if the caster is hurt, the host dies or strays too far, or time runs out.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class SenseLink {
    public static final double RANGE = 48.0;
    private static final double MAX_DISTANCE = 64.0;

    private record Link(UUID host, long until) {}

    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

    public static boolean isLinked(ServerPlayer player) {
        return LINKS.containsKey(player.getUUID());
    }

    /** Power action: link to the creature under the crosshair, or end the current link for free. */
    public static boolean toggle(PowerContext ctx) {
        ServerPlayer player = ctx.player();
        if (isLinked(player)) {
            end(player);
            ctx.waive();
            return true;
        }
        LivingEntity host = ctx.target(RANGE);
        if (host == null) {
            return false;
        }
        int duration = ctx.ticks(20);
        LINKS.put(player.getUUID(), new Link(host.getUUID(), player.level().getGameTime() + duration));
        player.addEffect(new MobEffectInstance(ModEffects.PROJECTION, duration + 20, 0, false, false, true));
        VtmNetwork.send(player, new SenseLinkPayload(host.getId()));
        player.displayClientMessage(Component.translatable("message.vtm.sense_linked", host.getDisplayName()), true);
        return true;
    }

    public static void end(ServerPlayer player) {
        if (LINKS.remove(player.getUUID()) != null) {
            player.removeEffect(ModEffects.PROJECTION);
            VtmNetwork.send(player, new SenseLinkPayload(-1));
        }
    }

    public static void tick(ServerPlayer player) {
        Link link = LINKS.get(player.getUUID());
        if (link == null) {
            return;
        }
        Entity host = player.serverLevel().getEntity(link.host);
        if (host == null || !host.isAlive() || host.distanceTo(player) > MAX_DISTANCE
                || player.level().getGameTime() >= link.until) {
            end(player);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player);
        }
    }

    public static void forget(UUID player) {
        LINKS.remove(player);
    }

    private SenseLink() {}
}

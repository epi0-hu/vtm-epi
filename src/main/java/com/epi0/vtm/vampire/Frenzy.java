package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.NpcWitness;
import com.epi0.vtm.power.SenseLink;
import com.epi0.vtm.registry.Bane;
import com.epi0.vtm.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Beast taking over. Triggered by hunger near a victim, or by clan banes (rage, tasting vampire vitae).
 * While frenzied a vampire is stronger and faster but cannot use powers and feeds on whatever is near.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class Frenzy {
    private static final int DEFAULT_SECONDS = 10;
    private static final int COOLDOWN_TICKS = 60 * 20;
    private static final int MAX_BESTIAL_FEATURES = 5;

    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final Set<UUID> QUELLED = ConcurrentHashMap.newKeySet();

    public static boolean isFrenzied(LivingEntity entity) {
        return entity.hasEffect(ModEffects.FRENZY);
    }

    public static boolean start(ServerPlayer player) {
        return start(player, DEFAULT_SECONDS);
    }

    public static boolean start(ServerPlayer player, int seconds) {
        long now = player.level().getGameTime();
        if (isFrenzied(player) || COOLDOWN.getOrDefault(player.getUUID(), 0L) > now || player.isCreative() || player.isSpectator()) {
            return false;
        }
        SenseLink.end(player);
        player.addEffect(new MobEffectInstance(ModEffects.FRENZY, seconds * 20, 0, false, true, true));
        player.displayClientMessage(Component.translatable("message.vtm.frenzy"), true);
        NpcWitness.broadcast(player, NpcMemory.Event.WITNESSED_FRENZY, 16);
        return true;
    }

    /** Ends frenzy without it counting as a lapse (the Beast was calmed, not spent). */
    public static void quell(ServerPlayer player) {
        if (isFrenzied(player)) {
            QUELLED.add(player.getUUID());
            player.removeEffect(ModEffects.FRENZY);
        }
    }

    /** Once-a-second upkeep: frenzy feeding and hunger-triggered frenzy. */
    public static void tick(ServerPlayer player, VampireData data, Bane bane) {
        if (isFrenzied(player)) {
            LivingEntity prey = nearestPrey(player, 3.5);
            if (prey != null) {
                Feeding.bite(player, prey, true);
            }
            if (VampireData.of(player).hunger() <= 1) {
                player.removeEffect(ModEffects.FRENZY);
            }
            return;
        }
        if (data.hunger() >= bane.frenzyHunger()
                && nearestPrey(player, 4.0) != null
                && player.getRandom().nextFloat() < 0.25F) {
            start(player);
        }
    }

    private static LivingEntity nearestPrey(ServerPlayer player, double range) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        e -> e != player && e.isAlive() && !e.isSpectator()
                                && VampireRules.bloodValue(e) > 0F
                                && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().is(ModEffects.FRENZY.getId()) && event.getEntity() instanceof ServerPlayer player) {
            onEnded(player);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().is(ModEffects.FRENZY.getId())
                && event.getEntity() instanceof ServerPlayer player) {
            onEnded(player);
        }
    }

    private static void onEnded(ServerPlayer player) {
        COOLDOWN.put(player.getUUID(), player.level().getGameTime() + COOLDOWN_TICKS);
        boolean quelled = QUELLED.remove(player.getUUID());
        VampireData data = VampireData.of(player);
        if (!quelled && data.isVampire() && Banes.of(player).bestialFeatures()) {
            int features = Math.min(MAX_BESTIAL_FEATURES, data.bestialFeatures() + 1);
            if (features != data.bestialFeatures()) {
                VampireManager.set(player, data.withBestialFeatures(features));
                player.displayClientMessage(Component.translatable("message.vtm.bestial_feature"), true);
            }
        }
    }

    /** Brujah-style rage: being hurt badly in a fight pushes the Beast over the edge. */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof LivingEntity)
                || !VampireData.of(player).isVampire()) {
            return;
        }
        if (Banes.of(player).rageFrenzy() && player.getHealth() <= player.getMaxHealth() * 0.4F) {
            start(player);
        }
    }

    public static void forget(UUID player) {
        COOLDOWN.remove(player);
        QUELLED.remove(player);
    }

    private Frenzy() {}
}

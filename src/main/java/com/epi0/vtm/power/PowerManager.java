package com.epi0.vtm.power;

import com.epi0.vtm.vampire.Frenzy;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side validation and execution of power activations. The client only ever sends a power id. */
public final class PowerManager {
    private static final Map<UUID, Map<ResourceLocation, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    /** @return true if the power ran and was paid for. */
    public static boolean activate(ServerPlayer player, ResourceLocation powerId) {
        VampireData data = VampireData.of(player);
        Power power = Powers.get(powerId).orElse(null);
        if (!data.isVampire() || power == null || power.kind() != PowerKind.ACTIVE || player.isSpectator()) {
            return false;
        }
        if (Frenzy.isFrenzied(player) && !powerId.equals(Powers.QUELL_THE_BEAST)) {
            fail(player, "message.vtm.frenzied");
            return false;
        }
        if (data.disciplineLevel(power.discipline()) < power.level()) {
            fail(player, "message.vtm.power_locked");
            return false;
        }
        long now = player.level().getGameTime();
        Map<ResourceLocation, Long> mine = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        boolean releasing = power.isEngagedFor(player);
        if (!releasing) {
            long ready = mine.getOrDefault(powerId, 0L);
            if (now < ready) {
                player.displayClientMessage(Component.translatable("message.vtm.power_cooldown",
                        power.name(), (ready - now + 19) / 20), true);
                return false;
            }
            if (data.blood() < power.bloodCost()) {
                fail(player, "message.vtm.power_no_blood");
                return false;
            }
        }

        PowerContext ctx = new PowerContext(player, data);
        if (!power.action().run(ctx)) {
            fail(player, "message.vtm.power_fizzled");
            return false;
        }

        // The power may have changed the caster's data (e.g. stolen blood), so re-read before charging.
        VampireData fresh = VampireData.of(player);
        float cost = ctx.waived() ? 0F : power.bloodCost();
        float blood = Mth.clamp(fresh.blood() - cost + ctx.bloodGain(), 0F, fresh.maxBlood());
        int stains = Mth.clamp(fresh.stains() + ctx.stainChange(), 0, VampireRules.MAX_STAINS);
        int features = Math.max(0, fresh.bestialFeatures() + ctx.featureChange());
        VampireManager.set(player, fresh.withBlood(blood).withStains(stains).withBestialFeatures(features));
        // Toggles start their cooldown when released, so they can always be switched off.
        if (power.isToggle() ? releasing : !ctx.waived()) {
            mine.put(powerId, now + power.cooldownTicks());
        }
        player.displayClientMessage(Component.translatable("message.vtm.power_used", power.name()), true);
        return true;
    }

    public static void forget(UUID player) {
        COOLDOWNS.remove(player);
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    private PowerManager() {}
}

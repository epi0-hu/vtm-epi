package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sneak + empty-hand use on a creature to bite it. */
public final class Feeding {
    private static final Map<UUID, Long> LAST_BITE = new ConcurrentHashMap<>();

    public static void bite(ServerPlayer player, LivingEntity target) {
        long now = player.level().getGameTime();
        Long last = LAST_BITE.get(player.getUUID());
        if (last != null && now - last < VtmConfig.FEEDING_COOLDOWN_TICKS.get()) {
            return;
        }
        LAST_BITE.put(player.getUUID(), now);

        VampireData data = VampireData.of(player);
        if (data.blood() >= data.maxBlood()) {
            player.displayClientMessage(Component.translatable("message.vtm.sated"), true);
            return;
        }
        float value = VampireRules.bloodValue(target);
        if (value <= 0F) {
            player.displayClientMessage(Component.translatable("message.vtm.no_blood"), true);
            return;
        }

        target.hurt(player.damageSources().playerAttack(player), 2F);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 0.7F);

        VampireData next = data.withBlood(Math.min(data.maxBlood(), data.blood() + value));
        if (target.isDeadOrDying() && VampireRules.isInnocent(target)) {
            next = next.withStains(Math.min(VampireRules.MAX_STAINS, next.stains() + 1));
            player.displayClientMessage(Component.translatable("message.vtm.stain"), true);
        }
        VampireManager.set(player, next);
    }

    public static void forget(UUID player) {
        LAST_BITE.remove(player);
    }

    private Feeding() {}
}

package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmConfig;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.NpcWitness;
import com.epi0.vtm.registry.Bane;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sneak + empty-hand use on a creature to bite it. Clan banes change how feeding works. */
public final class Feeding {
    private static final Map<UUID, Long> LAST_BITE = new ConcurrentHashMap<>();

    public static boolean bite(ServerPlayer player, LivingEntity target) {
        return bite(player, target, false);
    }

    /** @param frenzied the Beast is biting: no cooldown pity, and bane dietary rules are ignored */
    public static boolean bite(ServerPlayer player, LivingEntity target, boolean frenzied) {
        long now = player.level().getGameTime();
        Long last = LAST_BITE.get(player.getUUID());
        if (last != null && now - last < VtmConfig.FEEDING_COOLDOWN_TICKS.get()) {
            return false;
        }

        VampireData data = VampireData.of(player);
        Bane bane = Banes.of(player);
        if (!frenzied && bane.preyTag().isPresent()
                && !target.getType().is(TagKey.create(Registries.ENTITY_TYPE, bane.preyTag().get()))) {
            player.displayClientMessage(Component.translatable("message.vtm.bane_prey"), true);
            return false;
        }
        if (data.blood() >= data.maxBlood()) {
            player.displayClientMessage(Component.translatable("message.vtm.sated"), true);
            return false;
        }
        float value = VampireRules.bloodValue(target);
        ServerPlayer victimVampire = target instanceof ServerPlayer other && VampireData.of(other).isVampire() ? other : null;
        if (victimVampire != null) {
            value = Math.min(value, VampireData.of(victimVampire).blood());
        }
        if (value <= 0F) {
            player.displayClientMessage(Component.translatable("message.vtm.no_blood"), true);
            return false;
        }
        LAST_BITE.put(player.getUUID(), now);

        target.hurt(player.damageSources().playerAttack(player), bane.biteDamage());
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 0.7F);
        if (victimVampire != null) {
            VampireData victim = VampireData.of(victimVampire);
            VampireManager.set(victimVampire, victim.withBlood(victim.blood() - value));
        }
        if (bane.biteSelfDamage() > 0F) {
            player.hurt(player.damageSources().magic(), bane.biteSelfDamage());
        }

        float gained = value * bane.bloodGainMultiplier();
        VampireData next = VampireData.of(player);
        next = next.withBlood(Math.min(next.maxBlood(), next.blood() + gained));
        if (target.isDeadOrDying() && VampireRules.isInnocent(target)) {
            next = next.withStains(Math.min(VampireRules.MAX_STAINS, next.stains() + 1));
            player.displayClientMessage(Component.translatable("message.vtm.stain"), true);
        }
        VampireManager.set(player, next);
        NpcWitness.broadcast(player, NpcMemory.Event.WITNESSED_FEEDING, 16);

        if (victimVampire != null && bane.vampireBloodFrenzy()) {
            Frenzy.start(player, 12);
        }
        return true;
    }

    public static void forget(UUID player) {
        LAST_BITE.remove(player);
    }

    private Feeding() {}
}

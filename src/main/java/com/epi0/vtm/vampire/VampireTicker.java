package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmConfig;
import com.epi0.vtm.power.Forms;
import com.epi0.vtm.power.SenseLink;
import com.epi0.vtm.registry.Bane;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;

/** Once-a-second upkeep for vampires: blood decay, sunlight, blood healing, passive experience. */
public final class VampireTicker {
    private static final int SECOND = 20;
    private static final float HEAL_COST = 0.25F;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % SECOND != 0) {
            return;
        }
        VampireData data = VampireData.of(player);
        if (!data.isVampire() || player.isSpectator()) {
            return;
        }
        VampireData next = data;

        // Vampires do not eat: pin the food bar below the natural-regeneration threshold but above sprint-lock.
        player.getFoodData().setFoodLevel(17);
        player.getFoodData().setSaturation(0F);

        if (!player.isCreative()) {
            next = decay(player, next);
            next = heal(player, next);
            applyHungerPenalties(player, next);
            applySunlight(player, next);
            Bane bane = Banes.of(player);
            next = Banes.tick(player, next, bane);
            next = Forms.tick(player, next);
            Frenzy.tick(player, next, bane);
            SenseLink.tick(player);
        }
        next = passiveExperience(player, next);

        if (!next.equals(data)) {
            VampireManager.set(player, next);
        }
    }

    private static VampireData decay(ServerPlayer player, VampireData data) {
        long interval = (long) VtmConfig.BLOOD_DECAY_SECONDS.get() * SECOND;
        if (player.tickCount % interval != 0) {
            return data;
        }
        return data.withBlood(Math.max(0F, data.blood() - VtmConfig.BLOOD_DECAY_AMOUNT.get().floatValue()));
    }

    private static VampireData heal(ServerPlayer player, VampireData data) {
        if (!VtmConfig.BLOOD_HEALING.get()
                || player.tickCount % (SECOND * 3) != 0
                || data.blood() < HEAL_COST
                || !player.isAlive()
                || player.getHealth() >= player.getMaxHealth()) {
            return data;
        }
        player.heal(1F);
        return data.withBlood(data.blood() - HEAL_COST);
    }

    private static void applyHungerPenalties(ServerPlayer player, VampireData data) {
        if (data.hunger() >= VampireRules.MAX_HUNGER) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SECOND * 3, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SECOND * 3, 0, true, false));
        }
    }

    private static void applySunlight(ServerPlayer player, VampireData data) {
        if (!VtmConfig.SUNLIGHT_ENABLED.get() || player.hasEffect(MobEffects.FIRE_RESISTANCE) || !isSunExposed(player)) {
            return;
        }
        float multiplier = data.clan()
                .flatMap(id -> VtmRegistries.clan(player.serverLevel().registryAccess(), id))
                .map(Clan::sunDamageMultiplier)
                .orElse(1F);
        player.igniteForSeconds(Math.max(1, Math.round(4F * multiplier)));
        if (multiplier > 1F) {
            player.hurt(player.damageSources().inFire(), multiplier - 1F);
        }
    }

    /** Same rules as vanilla undead: daylight, open sky, no helmet, not in rain or water. */
    public static boolean isSunExposed(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return level.isDay()
                && !level.isRaining()
                && level.canSeeSky(head)
                && !player.isInWaterOrRain()
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
    }

    private static VampireData passiveExperience(ServerPlayer player, VampireData data) {
        int minutes = VtmConfig.EXPERIENCE_INTERVAL_MINUTES.get();
        if (minutes <= 0 || player.tickCount % ((long) minutes * 60 * SECOND) != 0) {
            return data;
        }
        return data.withExperience(data.experience() + 1);
    }

    private VampireTicker() {}
}

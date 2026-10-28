package com.epi0.vtm.power;

import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Entering, leaving and upkeep of shapechange forms. Rendering lives on the client; rules live in {@code FormEvents}. */
public final class Forms {
    private static final int UPKEEP_SECONDS = 10;
    private static final float UPKEEP_BLOOD = 0.5F;

    public static Optional<Form> of(LivingEntity entity) {
        for (Form form : Form.values()) {
            if (entity.hasEffect(form.effect())) {
                return Optional.of(form);
            }
        }
        return Optional.empty();
    }

    public static boolean isIn(LivingEntity entity, Form form) {
        return entity.hasEffect(form.effect());
    }

    public static void clear(LivingEntity entity) {
        for (Form form : Form.values()) {
            entity.removeEffect(form.effect());
        }
    }

    /** Power action: enter {@code form}, or revert for free if already in it. */
    public static boolean toggle(PowerContext ctx, Form form) {
        ServerPlayer player = ctx.player();
        if (isIn(player, form)) {
            clear(player);
            ctx.waive();
            burst(player);
            return true;
        }
        clear(player);
        SenseLink.end(player);
        player.addEffect(new MobEffectInstance(form.effect(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        burst(player);
        player.level().playSound(null, player.blockPosition(),
                form == Form.WOLF ? SoundEvents.WOLF_GROWL : SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1F, 1F);
        return true;
    }

    private static void burst(ServerPlayer player) {
        player.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + player.getBbHeight() / 2,
                player.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
    }

    /** Holding a form costs blood; run dry and you fall back to your own shape. */
    public static VampireData tick(ServerPlayer player, VampireData data) {
        if (of(player).isEmpty() || player.tickCount % (UPKEEP_SECONDS * 20) != 0) {
            return data;
        }
        if (data.blood() < UPKEEP_BLOOD) {
            clear(player);
            player.displayClientMessage(Component.translatable("message.vtm.form_lost"), true);
            return data;
        }
        return data.withBlood(data.blood() - UPKEEP_BLOOD);
    }

    private Forms() {}
}

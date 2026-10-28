package com.epi0.vtm.power;

import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Everything a power needs to act: the caster, helpers for effects/targeting, and blood/stain bookkeeping. */
public final class PowerContext {
    private final ServerPlayer player;
    private final VampireData data;
    private float bloodGain;
    private int stainChange;

    PowerContext(ServerPlayer player, VampireData data) {
        this.player = player;
        this.data = data;
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return player.serverLevel();
    }

    public VampireData data() {
        return data;
    }

    float bloodGain() {
        return bloodGain;
    }

    int stainChange() {
        return stainChange;
    }

    public void gainBlood(float amount) {
        bloodGain += amount;
    }

    public void changeStains(int delta) {
        stainChange += delta;
    }

    /** Seconds -> ticks, stretched by 20% per point of Blood Potency above 1. */
    public int ticks(double seconds) {
        double scale = 1.0 + 0.2 * Math.max(0, data.bloodPotency() - 1);
        return (int) Math.round(seconds * 20.0 * scale);
    }

    public void self(Holder<MobEffect> effect, double seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, ticks(seconds), amplifier, false, true));
    }

    public void on(LivingEntity target, Holder<MobEffect> effect, double seconds, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks(seconds), amplifier, false, true), player);
    }

    public void say(Component message) {
        player.sendSystemMessage(message);
    }

    public void particles(ParticleOptions type, LivingEntity at, int count) {
        level().sendParticles(type, at.getX(), at.getY() + at.getBbHeight() / 2, at.getZ(), count, 0.3, 0.4, 0.3, 0.02);
    }

    public static boolean isBoss(LivingEntity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Living entities (excluding the caster, bosses and spectators) within {@code radius}. */
    public List<LivingEntity> nearby(double radius, Predicate<LivingEntity> filter) {
        AABB box = player.getBoundingBox().inflate(radius);
        return level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator() && !isBoss(e)
                        && e.distanceToSqr(player) <= radius * radius && filter.test(e));
    }

    public List<Mob> nearbyMobs(double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return level().getEntitiesOfClass(Mob.class, box,
                e -> e.isAlive() && !isBoss(e) && e.distanceToSqr(player) <= radius * radius);
    }

    /** The first living entity under the crosshair within {@code range}, or null. */
    public LivingEntity target(double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        double bestSq = range * range;
        var blockHit = level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            bestSq = Math.min(bestSq, eye.distanceToSqr(blockHit.getLocation()));
        }
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        LivingEntity best = null;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, sweep,
                x -> x != player && x.isAlive() && !x.isSpectator())) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius() + 0.3);
            Optional<Vec3> hit = box.clip(eye, end);
            double distSq = box.contains(eye) ? 0 : hit.map(eye::distanceToSqr).orElse(Double.MAX_VALUE);
            if (distSq < bestSq) {
                best = e;
                bestSq = distSq;
            }
        }
        return best;
    }

    public Mob targetMob(double range) {
        return target(range) instanceof Mob mob && !isBoss(mob) ? mob : null;
    }
}

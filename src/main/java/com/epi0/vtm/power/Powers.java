package com.epi0.vtm.power;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.util.Scheduler;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every discipline power in the mod: five per discipline, one per level.
 * Costs and cooldowns live here so balance changes are one-file edits.
 */
public final class Powers {
    private static final Map<ResourceLocation, Power> BY_ID = new LinkedHashMap<>();

    // ------------------------------------------------------------ Animalism
    static {
        active("sense_the_beast", "animalism", 1, 1, 20, ctx -> {
            List<LivingEntity> found = ctx.nearby(24, e -> true);
            found.forEach(e -> ctx.on(e, MobEffects.GLOWING, 10, 0));
            return !found.isEmpty();
        });
        active("feral_whispers", "animalism", 2, 1, 30, ctx -> {
            boolean any = false;
            for (LivingEntity e : ctx.nearby(10, x -> x instanceof TamableAnimal t && !t.isTame())) {
                ((TamableAnimal) e).tame(ctx.player());
                ctx.particles(ParticleTypes.HEART, e, 6);
                any = true;
            }
            return any;
        });
        active("quell_the_beast", "animalism", 3, 2, 60, ctx -> {
            ServerPlayer p = ctx.player();
            List<MobEffectInstance> harmful = p.getActiveEffects().stream()
                    .filter(i -> i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).toList();
            if (harmful.isEmpty() && ctx.data().stains() == 0) {
                return false;
            }
            harmful.forEach(i -> p.removeEffect(i.getEffect()));
            if (ctx.data().stains() > 0) {
                ctx.changeStains(-1);
            }
            return true;
        });
        active("summon_pack", "animalism", 4, 3, 180, ctx -> {
            ServerPlayer p = ctx.player();
            for (int i = 0; i < 3; i++) {
                Wolf wolf = EntityType.WOLF.create(ctx.level());
                if (wolf == null) {
                    return i > 0;
                }
                wolf.moveTo(p.getX() + (p.getRandom().nextDouble() - 0.5) * 3, p.getY(),
                        p.getZ() + (p.getRandom().nextDouble() - 0.5) * 3, p.getYRot(), 0F);
                wolf.finalizeSpawn(ctx.level(), ctx.level().getCurrentDifficultyAt(wolf.blockPosition()),
                        MobSpawnType.MOB_SUMMONED, null);
                wolf.tame(p);
                ctx.level().addFreshEntity(wolf);
                Scheduler.later(ctx.level().getServer(), ctx.ticks(60), () -> {
                    if (wolf.isAlive()) {
                        wolf.discard();
                    }
                });
            }
            return true;
        });
        active("drawing_out_the_beast", "animalism", 5, 3, 120, ctx -> {
            List<Mob> mobs = ctx.nearbyMobs(8).stream().filter(m -> m instanceof Enemy).toList();
            for (Mob m : mobs) {
                MobControl.calm(m, ctx.ticks(10));
                ctx.on(m, MobEffects.MOVEMENT_SLOWDOWN, 8, 1);
                ctx.on(m, MobEffects.WEAKNESS, 15, 1);
                ctx.particles(ParticleTypes.SMOKE, m, 8);
            }
            return !mobs.isEmpty();
        });
    }

    // ------------------------------------------------------------ Auspex
    static {
        active("heightened_senses", "auspex", 1, 1, 10, ctx -> {
            ctx.self(MobEffects.NIGHT_VISION, 60, 0);
            return true;
        });
        active("sense_the_unseen", "auspex", 2, 1, 30, ctx -> {
            List<LivingEntity> found = ctx.nearby(32, e -> true);
            for (LivingEntity e : found) {
                e.removeEffect(MobEffects.INVISIBILITY);
                ctx.on(e, MobEffects.GLOWING, 8, 0);
            }
            return !found.isEmpty();
        });
        active("premonition", "auspex", 3, 2, 60, ctx -> {
            ctx.self(MobEffects.ABSORPTION, 60, 1);
            ctx.self(MobEffects.LUCK, 60, 0);
            return true;
        });
        active("scry_the_soul", "auspex", 4, 1, 10, ctx -> {
            LivingEntity target = ctx.target(32);
            if (target == null) {
                return false;
            }
            if (target instanceof ServerPlayer other) {
                VampireData d = VampireData.of(other);
                Component clan = d.clan().map(id -> com.epi0.vtm.registry.VtmRegistries.name("clan", id))
                        .orElse(Component.translatable("gui.vtm.none"));
                ctx.say(Component.translatable("power.vtm.scry_the_soul.player", other.getDisplayName(),
                        Component.translatable("species.vtm." + d.species().getSerializedName()), clan,
                        d.isVampire() ? String.valueOf(d.generation()) : "-", d.humanity()));
            } else {
                ctx.say(Component.translatable("power.vtm.scry_the_soul.mob", target.getDisplayName(),
                        String.format("%.1f", target.getHealth()), String.format("%.1f", target.getMaxHealth())));
            }
            return true;
        });
        active("share_the_senses", "auspex", 5, 3, 120, ctx -> {
            ctx.self(MobEffects.NIGHT_VISION, 30, 0);
            ctx.nearby(48, e -> true).forEach(e -> ctx.on(e, MobEffects.GLOWING, 20, 0));
            return true;
        });
    }

    // ------------------------------------------------------------ Blood Sorcery
    static {
        active("blood_bolt", "blood_sorcery", 1, 1, 3, ctx -> {
            LivingEntity target = ctx.target(24);
            if (target == null) {
                return false;
            }
            target.hurt(ctx.player().damageSources().indirectMagic(ctx.player(), ctx.player()), 5F);
            ctx.particles(ParticleTypes.DAMAGE_INDICATOR, target, 6);
            return true;
        });
        active("extinguish_vitae", "blood_sorcery", 2, 1, 20, ctx -> {
            LivingEntity target = ctx.target(16);
            if (target == null) {
                return false;
            }
            boolean cursed = target.getType().is(EntityTypeTags.UNDEAD)
                    || (target instanceof ServerPlayer sp && VampireData.of(sp).isVampire());
            target.hurt(ctx.player().damageSources().indirectMagic(ctx.player(), ctx.player()), cursed ? 12F : 6F);
            ctx.particles(ParticleTypes.CRIMSON_SPORE, target, 12);
            return true;
        });
        active("blood_of_potency", "blood_sorcery", 3, 2, 120, ctx -> {
            ctx.self(MobEffects.DAMAGE_BOOST, 60, 1);
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 60, 0);
            return true;
        });
        active("scorpions_touch", "blood_sorcery", 4, 2, 30, ctx -> {
            LivingEntity target = ctx.target(6);
            if (target == null) {
                return false;
            }
            ctx.on(target, MobEffects.WITHER, 8, 1);
            ctx.on(target, MobEffects.WEAKNESS, 12, 0);
            return true;
        });
        active("theft_of_vitae", "blood_sorcery", 5, 1, 30, ctx -> {
            LivingEntity target = ctx.target(24);
            if (target == null) {
                return false;
            }
            float taken = 3F;
            if (target instanceof ServerPlayer other && VampireData.of(other).isVampire()) {
                VampireData od = VampireData.of(other);
                taken = Math.min(taken, od.blood());
                com.epi0.vtm.vampire.VampireManager.set(other, od.withBlood(od.blood() - taken));
            } else {
                target.hurt(ctx.player().damageSources().indirectMagic(ctx.player(), ctx.player()), 4F);
            }
            ctx.gainBlood(taken);
            ctx.particles(ParticleTypes.CRIMSON_SPORE, target, 12);
            return true;
        });
    }

    // ------------------------------------------------------------ Celerity
    static {
        passive("cats_grace", "celerity", 1);
        active("rapid_reflexes", "celerity", 2, 1, 30, ctx -> {
            ctx.self(MobEffects.DIG_SPEED, 30, 1);
            ctx.self(MobEffects.JUMP, 30, 0);
            return true;
        });
        active("fleetness", "celerity", 3, 2, 45, ctx -> {
            ctx.self(MobEffects.MOVEMENT_SPEED, 20, 2);
            return true;
        });
        active("blink", "celerity", 4, 2, 8, ctx -> {
            ServerPlayer p = ctx.player();
            Vec3 eye = p.getEyePosition();
            Vec3 dir = p.getLookAngle();
            double distance = 10;
            var hit = ctx.level().clip(new ClipContext(eye, eye.add(dir.scale(distance)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            double travel = hit.getType() == HitResult.Type.MISS
                    ? distance : Math.max(0, eye.distanceTo(hit.getLocation()) - 0.6);
            for (double d = travel; d >= 1.0; d -= 0.5) {
                Vec3 move = dir.scale(d);
                if (ctx.level().noCollision(p, p.getBoundingBox().move(move))) {
                    ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
                    p.teleportTo(p.getX() + move.x, p.getY() + move.y, p.getZ() + move.z);
                    p.fallDistance = 0F;
                    return true;
                }
            }
            return false;
        });
        active("traversal", "celerity", 5, 3, 60, ctx -> {
            ctx.self(MobEffects.MOVEMENT_SPEED, 10, 3);
            ctx.self(MobEffects.JUMP, 10, 2);
            ctx.self(MobEffects.SLOW_FALLING, 10, 0);
            return true;
        });
    }

    // ------------------------------------------------------------ Dominate
    static {
        active("cloud_memory", "dominate", 1, 1, 10, ctx -> {
            Mob mob = ctx.targetMob(16);
            if (mob == null) {
                return false;
            }
            MobControl.calm(mob, ctx.ticks(20));
            ctx.particles(ParticleTypes.ENCHANT, mob, 10);
            return true;
        });
        active("compel", "dominate", 2, 1, 20, ctx -> {
            Mob mob = ctx.targetMob(16);
            if (mob == null) {
                return false;
            }
            MobControl.calm(mob, ctx.ticks(15));
            ctx.on(mob, MobEffects.MOVEMENT_SLOWDOWN, 10, 6);
            ctx.particles(ParticleTypes.ENCHANT, mob, 10);
            return true;
        });
        active("mesmerize", "dominate", 3, 2, 30, ctx -> {
            Mob mob = ctx.targetMob(16);
            if (mob == null) {
                return false;
            }
            Mob other = ctx.level().getNearestEntity(Mob.class,
                    net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(16)
                            .selector(e -> e != mob && !PowerContext.isBoss(e)),
                    mob, mob.getX(), mob.getY(), mob.getZ(), mob.getBoundingBox().inflate(16));
            MobControl.calm(mob, ctx.ticks(20));
            if (other == null) {
                return true;
            }
            mob.setTarget(other);
            ctx.particles(ParticleTypes.ENCHANT, mob, 10);
            return true;
        });
        active("mass_compel", "dominate", 4, 3, 90, ctx -> {
            List<Mob> mobs = ctx.nearbyMobs(10);
            mobs.forEach(m -> {
                MobControl.calm(m, ctx.ticks(20));
                ctx.on(m, MobEffects.MOVEMENT_SLOWDOWN, 10, 3);
            });
            return !mobs.isEmpty();
        });
        active("absolute_command", "dominate", 5, 3, 120, ctx -> {
            LivingEntity target = ctx.target(16);
            if (target instanceof Villager) {
                ctx.self(MobEffects.HERO_OF_THE_VILLAGE, 600, 4);
                ctx.particles(ParticleTypes.HAPPY_VILLAGER, target, 10);
                return true;
            }
            if (target instanceof Mob mob && !PowerContext.isBoss(mob)) {
                MobControl.calm(mob, ctx.ticks(60));
                ctx.on(mob, MobEffects.DAMAGE_BOOST, 60, 1);
                ctx.particles(ParticleTypes.ENCHANT, mob, 15);
                return true;
            }
            return false;
        });
    }

    // ------------------------------------------------------------ Fortitude
    static {
        passive("resilience", "fortitude", 1);
        active("toughness", "fortitude", 2, 1, 30, ctx -> {
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 40, 0);
            return true;
        });
        active("defy_bane", "fortitude", 3, 2, 60, ctx -> {
            ctx.player().clearFire();
            ctx.self(MobEffects.FIRE_RESISTANCE, 90, 0);
            return true;
        });
        active("fortify_the_inner_facade", "fortitude", 4, 2, 60, ctx -> {
            ctx.self(MobEffects.ABSORPTION, 30, 2);
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 20, 1);
            return true;
        });
        active("draught_of_endurance", "fortitude", 5, 3, 120, ctx -> {
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 15, 3);
            ctx.self(MobEffects.REGENERATION, 15, 1);
            return true;
        });
    }

    // ------------------------------------------------------------ Obfuscate
    static {
        active("cloak_of_shadows", "obfuscate", 1, 1, 15, ctx -> {
            ctx.self(MobEffects.INVISIBILITY, 20, 0);
            return true;
        });
        active("unseen_passage", "obfuscate", 2, 1, 30, ctx -> {
            ctx.self(MobEffects.INVISIBILITY, 45, 0);
            MobControl.dropAggro(ctx.player(), 16, ctx.ticks(15));
            return true;
        });
        active("ghost_walk", "obfuscate", 3, 2, 45, ctx -> {
            MobControl.hide(ctx.player(), ctx.ticks(30));
            MobControl.dropAggro(ctx.player(), 24, ctx.ticks(30));
            ctx.self(MobEffects.MOVEMENT_SPEED, 30, 0);
            return true;
        });
        active("mask_of_a_thousand_faces", "obfuscate", 4, 2, 60, ctx -> {
            MobControl.hide(ctx.player(), ctx.ticks(60));
            MobControl.dropAggro(ctx.player(), 24, ctx.ticks(60));
            ctx.self(MobEffects.INVISIBILITY, 60, 0);
            return true;
        });
        active("vanish", "obfuscate", 5, 3, 120, ctx -> {
            MobControl.hide(ctx.player(), ctx.ticks(120));
            MobControl.dropAggro(ctx.player(), 32, ctx.ticks(120));
            ctx.player().removeEffect(MobEffects.GLOWING);
            ctx.self(MobEffects.INVISIBILITY, 120, 0);
            ctx.self(MobEffects.MOVEMENT_SPEED, 20, 1);
            return true;
        });
    }

    // ------------------------------------------------------------ Oblivion
    static {
        active("oblivions_sight", "oblivion", 1, 1, 20, ctx -> {
            ctx.self(MobEffects.NIGHT_VISION, 60, 0);
            ctx.nearby(32, e -> e.getType().is(EntityTypeTags.UNDEAD)
                    || (e instanceof ServerPlayer sp && VampireData.of(sp).isVampire()))
                    .forEach(e -> ctx.on(e, MobEffects.GLOWING, 15, 0));
            return true;
        });
        active("shadow_cast", "oblivion", 2, 1, 30, ctx -> {
            List<Mob> mobs = ctx.nearbyMobs(8);
            mobs.forEach(m -> {
                MobControl.calm(m, ctx.ticks(8));
                ctx.on(m, MobEffects.BLINDNESS, 8, 0);
                ctx.on(m, MobEffects.MOVEMENT_SLOWDOWN, 8, 0);
            });
            return !mobs.isEmpty();
        });
        active("arms_of_the_abyss", "oblivion", 3, 2, 45, ctx -> {
            ServerPlayer p = ctx.player();
            List<LivingEntity> found = ctx.nearby(10, e -> true);
            for (LivingEntity e : found) {
                Vec3 pull = p.position().subtract(e.position()).normalize().scale(1.2);
                e.setDeltaMovement(pull.x, 0.3, pull.z);
                e.hurtMarked = true;
                ctx.on(e, MobEffects.MOVEMENT_SLOWDOWN, 4, 2);
            }
            return !found.isEmpty();
        });
        active("withering", "oblivion", 4, 2, 30, ctx -> {
            LivingEntity target = ctx.target(16);
            if (target == null) {
                return false;
            }
            ctx.on(target, MobEffects.WITHER, 10, 1);
            ctx.particles(ParticleTypes.SOUL, target, 10);
            return true;
        });
        active("necrotic_plague", "oblivion", 5, 3, 120, ctx -> {
            List<LivingEntity> found = ctx.nearby(6, e -> true);
            for (LivingEntity e : found) {
                ctx.on(e, MobEffects.WITHER, 10, 1);
                ctx.on(e, MobEffects.WEAKNESS, 10, 0);
                ctx.on(e, MobEffects.MOVEMENT_SLOWDOWN, 10, 0);
                ctx.particles(ParticleTypes.SOUL, e, 8);
            }
            return !found.isEmpty();
        });
    }

    // ------------------------------------------------------------ Potence
    static {
        passive("lethal_body", "potence", 1);
        active("soaring_leap", "potence", 2, 1, 10, ctx -> {
            ServerPlayer p = ctx.player();
            Vec3 look = p.getLookAngle();
            p.setDeltaMovement(look.x * 1.4, 0.9, look.z * 1.4);
            p.hurtMarked = true;
            p.fallDistance = 0F;
            ctx.self(MobEffects.SLOW_FALLING, 4, 0);
            return true;
        });
        active("prowess", "potence", 3, 2, 60, ctx -> {
            ctx.self(MobEffects.DAMAGE_BOOST, 30, 1);
            return true;
        });
        active("brutal_strike", "potence", 4, 2, 20, ctx -> {
            ServerPlayer p = ctx.player();
            Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
            List<LivingEntity> hit = ctx.nearby(5, e -> {
                Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                return to.dot(look) > 0.3;
            });
            for (LivingEntity e : hit) {
                e.hurt(p.damageSources().playerAttack(p), 6F);
                e.knockback(1.5, p.getX() - e.getX(), p.getZ() - e.getZ());
            }
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1F, 0.6F);
            return !hit.isEmpty();
        });
        active("earthshock", "potence", 5, 3, 60, ctx -> {
            ServerPlayer p = ctx.player();
            List<LivingEntity> hit = ctx.nearby(7, e -> true);
            for (LivingEntity e : hit) {
                e.hurt(p.damageSources().playerAttack(p), 8F);
                e.knockback(1.8, p.getX() - e.getX(), p.getZ() - e.getZ());
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
                ctx.on(e, MobEffects.MOVEMENT_SLOWDOWN, 3, 2);
            }
            ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 6, 2.5, 0.1, 2.5, 0.0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1F, 0.8F);
            return true;
        });
    }

    // ------------------------------------------------------------ Presence
    static {
        active("awe", "presence", 1, 1, 30, ctx -> {
            ctx.self(MobEffects.HERO_OF_THE_VILLAGE, 180, 0);
            return true;
        });
        active("daunt", "presence", 2, 1, 30, ctx -> {
            List<Mob> mobs = ctx.nearbyMobs(8);
            mobs.forEach(m -> {
                MobControl.calm(m, ctx.ticks(10));
                ctx.on(m, MobEffects.WEAKNESS, 15, 0);
                ctx.on(m, MobEffects.MOVEMENT_SLOWDOWN, 10, 0);
            });
            return !mobs.isEmpty();
        });
        active("entrancement", "presence", 3, 2, 45, ctx -> {
            boolean any = false;
            for (LivingEntity e : ctx.nearby(12, x -> x instanceof Animal)) {
                if (e instanceof TamableAnimal t && !t.isTame()) {
                    t.tame(ctx.player());
                } else if (e instanceof Animal animal) {
                    animal.setInLove(ctx.player());
                }
                ctx.particles(ParticleTypes.HEART, e, 6);
                any = true;
            }
            return any;
        });
        active("dread_gaze", "presence", 4, 2, 45, ctx -> {
            LivingEntity target = ctx.target(24);
            if (target == null) {
                return false;
            }
            ctx.on(target, MobEffects.WEAKNESS, 15, 1);
            ctx.on(target, MobEffects.MOVEMENT_SLOWDOWN, 15, 1);
            ctx.on(target, MobEffects.BLINDNESS, 8, 0);
            if (target instanceof Mob mob) {
                MobControl.calm(mob, ctx.ticks(15));
            } else {
                ctx.on(target, MobEffects.CONFUSION, 10, 0);
            }
            return true;
        });
        active("majesty", "presence", 5, 3, 120, ctx -> {
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 15, 2);
            ctx.nearbyMobs(16).forEach(m -> MobControl.calm(m, ctx.ticks(15)));
            return true;
        });
    }

    // ------------------------------------------------------------ Protean
    static {
        active("eyes_of_the_beast", "protean", 1, 1, 20, ctx -> {
            ctx.self(MobEffects.NIGHT_VISION, 300, 0);
            return true;
        });
        active("weight_of_the_feather", "protean", 2, 1, 30, ctx -> {
            ctx.self(MobEffects.SLOW_FALLING, 60, 0);
            ctx.self(MobEffects.JUMP, 60, 1);
            return true;
        });
        active("feral_weapons", "protean", 3, 2, 60, ctx -> {
            ctx.self(MobEffects.DAMAGE_BOOST, 45, 0);
            ctx.self(MobEffects.DIG_SPEED, 45, 1);
            return true;
        });
        active("earth_meld", "protean", 4, 2, 120, ctx -> {
            ctx.player().clearFire();
            ctx.self(MobEffects.FIRE_RESISTANCE, 120, 0);
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 10, 4);
            ctx.self(MobEffects.REGENERATION, 10, 2);
            ctx.self(MobEffects.MOVEMENT_SLOWDOWN, 10, 6);
            MobControl.hide(ctx.player(), ctx.ticks(10));
            MobControl.dropAggro(ctx.player(), 24, ctx.ticks(10));
            return true;
        });
        active("shapechange", "protean", 5, 3, 120, ctx -> {
            ctx.self(MobEffects.MOVEMENT_SPEED, 60, 1);
            ctx.self(MobEffects.JUMP, 60, 1);
            ctx.self(MobEffects.DAMAGE_BOOST, 60, 1);
            ctx.self(MobEffects.NIGHT_VISION, 60, 0);
            return true;
        });
    }

    // ------------------------------------------------------------ Thin-Blood Alchemy
    static {
        active("far_reach", "thin_blood_alchemy", 1, 1, 15, ctx -> {
            ServerPlayer p = ctx.player();
            List<ItemEntity> items = ctx.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12));
            items.forEach(item -> item.setPos(p.getX(), p.getY() + 0.5, p.getZ()));
            return !items.isEmpty();
        });
        active("catalyze", "thin_blood_alchemy", 2, 1, 45, ctx -> {
            ctx.self(MobEffects.DIG_SPEED, 40, 2);
            ctx.self(MobEffects.DAMAGE_BOOST, 40, 0);
            return true;
        });
        active("wake_with_evenings_fire", "thin_blood_alchemy", 3, 2, 90, ctx -> {
            ctx.player().clearFire();
            ctx.self(MobEffects.FIRE_RESISTANCE, 90, 0);
            return true;
        });
        active("defractionate", "thin_blood_alchemy", 4, 2, 60, ctx -> {
            ServerPlayer p = ctx.player();
            new ArrayList<>(p.getActiveEffects()).stream()
                    .filter(i -> i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    .forEach(i -> p.removeEffect(i.getEffect()));
            ctx.self(MobEffects.REGENERATION, 10, 2);
            return true;
        });
        active("prismatic", "thin_blood_alchemy", 5, 3, 120, ctx -> {
            ctx.self(MobEffects.DAMAGE_RESISTANCE, 20, 1);
            ctx.self(MobEffects.MOVEMENT_SPEED, 20, 1);
            ctx.self(MobEffects.DAMAGE_BOOST, 20, 1);
            return true;
        });
    }

    private static void active(String name, String discipline, int level, int cost, int cooldownSeconds, PowerAction action) {
        register(name, discipline, level, PowerKind.ACTIVE, cost, cooldownSeconds * 20, action);
    }

    private static void passive(String name, String discipline, int level) {
        register(name, discipline, level, PowerKind.PASSIVE, 0, 0, ctx -> false);
    }

    private static void register(String name, String discipline, int level, PowerKind kind, int cost, int cooldownTicks,
                                 PowerAction action) {
        ResourceLocation id = VtmMod.id(name);
        if (BY_ID.put(id, new Power(id, VtmMod.id(discipline), level, kind, cost, cooldownTicks, action)) != null) {
            throw new IllegalStateException("Duplicate power " + id);
        }
    }

    public static Optional<Power> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static List<Power> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static List<Power> forDiscipline(ResourceLocation discipline) {
        return BY_ID.values().stream().filter(p -> p.discipline().equals(discipline)).toList();
    }

    /** The power unlocked by reaching {@code level} in {@code discipline}, if any. */
    public static Optional<Power> at(ResourceLocation discipline, int level) {
        return BY_ID.values().stream().filter(p -> p.discipline().equals(discipline) && p.level() == level).findFirst();
    }

    /** All powers the player has unlocked, ordered by discipline then level. */
    public static List<Power> unlocked(VampireData data) {
        return BY_ID.values().stream()
                .filter(p -> data.disciplineLevel(p.discipline()) >= p.level())
                .toList();
    }

    private Powers() {}
}

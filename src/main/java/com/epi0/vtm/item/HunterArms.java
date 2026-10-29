package com.epi0.vtm.item;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What hunters shoot with. Two kinds of weapon, each a profile of how hard, how fast, how true and how often it fires:
 * the stake crossbow every hunter carries, and the rifle an inquisitor does. Any item in the
 * {@code vtm:hunter_firearms} tag counts as a rifle, so a modpack can hand hunters the guns of another mod; the shot
 * itself is always ours, so damage, scars and resistances behave the same.
 */
public final class HunterArms {
    public static final TagKey<Item> FIREARMS = TagKey.create(Registries.ITEM, VtmMod.id("hunter_firearms"));

    /** How a weapon fires. {@code damage} is the arrow's base damage (it is multiplied by the shot's speed). */
    public enum Profile {
        CROSSBOW(1.4, 3.0F, 3.0F, 40, 26, SoundEvents.CROSSBOW_SHOOT, false),
        RIFLE(1.7, 3.8F, 1.0F, 65, 36, SoundEvents.FIREWORK_ROCKET_BLAST, true);

        public final double damage;
        public final float speed;
        public final float inaccuracy;
        /** Ticks between shots. */
        public final int cooldown;
        /** Blocks within which an NPC will open fire. */
        public final int range;
        public final SoundEvent sound;
        /** Whether the shot makes smoke at the muzzle (and a good deal of noise). */
        public final boolean loud;

        Profile(double damage, float speed, float inaccuracy, int cooldown, int range, SoundEvent sound, boolean loud) {
            this.damage = damage;
            this.speed = speed;
            this.inaccuracy = inaccuracy;
            this.cooldown = cooldown;
            this.range = range;
            this.sound = sound;
            this.loud = loud;
        }
    }

    /** How this item fires, or null if it is not a hunter's weapon. */
    @Nullable
    public static Profile profile(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(ModItems.STAKE_CROSSBOW.get())) {
            return Profile.CROSSBOW;
        }
        return stack.is(FIREARMS) ? Profile.RIFLE : null;
    }

    /** A firearm for an inquisitor: one of the tagged items, ours unless a pack has added others. */
    public static ItemStack firearm(RandomSource random) {
        List<Item> pool = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(FIREARMS).ifPresent(tag -> tag.forEach(holder -> pool.add(holder.value())));
        pool.remove(Items.AIR);
        return new ItemStack(pool.isEmpty() ? ModItems.HUNTER_RIFLE.get() : pool.get(random.nextInt(pool.size())));
    }

    /** Looses a shot from {@code shooter} along {@code direction}. */
    public static void fire(ServerLevel level, LivingEntity shooter, Vec3 direction, Profile profile) {
        Arrow shot = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
        shot.pickup = AbstractArrow.Pickup.DISALLOWED;
        shot.setBaseDamage(profile.damage);
        shot.setNoGravity(profile.loud);
        Vec3 muzzle = shooter.getEyePosition().add(direction.normalize().scale(0.8));
        shot.setPos(muzzle.x, muzzle.y - 0.1, muzzle.z);
        shot.shoot(direction.x, direction.y, direction.z, profile.speed, profile.inaccuracy);
        level.addFreshEntity(shot);
        level.playSound(null, shooter.blockPosition(), profile.sound, SoundSource.HOSTILE, profile.loud ? 2.5F : 1F, 0.9F + level.random.nextFloat() * 0.2F);
        if (profile.loud) {
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private HunterArms() {}
}

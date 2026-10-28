package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Effects are used because the game already syncs them to every nearby client and persists them,
 * which is exactly what shapeshifting and frenzy need.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, VtmMod.MOD_ID);

    /** The Beast has taken the wheel: stronger and faster, but no powers and it attacks what is near. */
    public static final DeferredHolder<MobEffect, VtmEffect> FRENZY = EFFECTS.register("frenzy",
            () -> new VtmEffect(MobEffectCategory.HARMFUL, 0x8B0000)
                    .with(Attributes.ATTACK_DAMAGE, VtmMod.id("frenzy_damage"), 3.0, Operation.ADD_VALUE)
                    .with(Attributes.MOVEMENT_SPEED, VtmMod.id("frenzy_speed"), 0.2, Operation.ADD_MULTIPLIED_TOTAL));

    /** Body left behind while the senses are elsewhere. */
    public static final DeferredHolder<MobEffect, VtmEffect> PROJECTION = EFFECTS.register("projection",
            () -> new VtmEffect(MobEffectCategory.NEUTRAL, 0x6FA8DC)
                    .with(Attributes.MOVEMENT_SPEED, VtmMod.id("projection_speed"), -1.0, Operation.ADD_MULTIPLIED_TOTAL)
                    .with(Attributes.JUMP_STRENGTH, VtmMod.id("projection_jump"), -1.0, Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, VtmEffect> WOLF_FORM = EFFECTS.register("wolf_form",
            () -> new VtmEffect(MobEffectCategory.BENEFICIAL, 0x7D6608)
                    .with(Attributes.SCALE, VtmMod.id("wolf_scale"), -0.5, Operation.ADD_MULTIPLIED_TOTAL)
                    .with(Attributes.MOVEMENT_SPEED, VtmMod.id("wolf_speed"), 0.35, Operation.ADD_MULTIPLIED_TOTAL)
                    .with(Attributes.JUMP_STRENGTH, VtmMod.id("wolf_jump"), 0.15, Operation.ADD_VALUE)
                    .with(Attributes.STEP_HEIGHT, VtmMod.id("wolf_step"), 0.4, Operation.ADD_VALUE)
                    .with(Attributes.ATTACK_DAMAGE, VtmMod.id("wolf_damage"), 2.0, Operation.ADD_VALUE)
                    .with(Attributes.SAFE_FALL_DISTANCE, VtmMod.id("wolf_fall"), 3.0, Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, VtmEffect> BAT_FORM = EFFECTS.register("bat_form",
            () -> new VtmEffect(MobEffectCategory.BENEFICIAL, 0x3C4A5C)
                    .with(Attributes.SCALE, VtmMod.id("bat_scale"), -0.5, Operation.ADD_MULTIPLIED_TOTAL)
                    .with(NeoForgeMod.CREATIVE_FLIGHT, VtmMod.id("bat_flight"), 1.0, Operation.ADD_VALUE)
                    .with(Attributes.SAFE_FALL_DISTANCE, VtmMod.id("bat_fall"), 100.0, Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, VtmEffect> MIST_FORM = EFFECTS.register("mist_form",
            () -> new VtmEffect(MobEffectCategory.BENEFICIAL, 0xBBC7D2)
                    .with(Attributes.SCALE, VtmMod.id("mist_scale"), -0.5, Operation.ADD_MULTIPLIED_TOTAL)
                    .with(NeoForgeMod.CREATIVE_FLIGHT, VtmMod.id("mist_flight"), 1.0, Operation.ADD_VALUE)
                    .with(Attributes.SAFE_FALL_DISTANCE, VtmMod.id("mist_fall"), 100.0, Operation.ADD_VALUE));

    public static Holder<MobEffect> holder(DeferredHolder<MobEffect, ? extends MobEffect> effect) {
        return effect;
    }

    private ModEffects() {}
}

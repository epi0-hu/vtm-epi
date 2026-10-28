package com.epi0.vtm.power;

import com.epi0.vtm.registry.ModEffects;
import com.epi0.vtm.registry.VtmEffect;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;

/** A Protean shapechange. Each is an infinite effect, so it syncs to nearby clients and survives relogging. */
public enum Form {
    WOLF(ModEffects.WOLF_FORM),
    BAT(ModEffects.BAT_FORM),
    MIST(ModEffects.MIST_FORM);

    private final DeferredHolder<MobEffect, VtmEffect> effect;

    Form(DeferredHolder<MobEffect, VtmEffect> effect) {
        this.effect = effect;
    }

    public DeferredHolder<MobEffect, VtmEffect> effect() {
        return effect;
    }
}

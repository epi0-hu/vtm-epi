package com.epi0.vtm.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** A mob effect whose whole behaviour is a set of attribute modifiers plus event hooks elsewhere. */
public class VtmEffect extends MobEffect {
    public VtmEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public VtmEffect with(Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        addAttributeModifier(attribute, id, amount, op);
        return this;
    }
}

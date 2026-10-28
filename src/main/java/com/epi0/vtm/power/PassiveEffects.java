package com.epi0.vtm.power;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Always-on bonuses from Celerity, Potence and Fortitude, applied as permanent attribute modifiers
 * that scale with the discipline level. Re-applied whenever vampire data changes.
 */
public final class PassiveEffects {
    private record Bonus(Holder<Attribute> attribute, ResourceLocation id, double perLevel, AttributeModifier.Operation op) {}

    private static final ResourceLocation CELERITY = VtmMod.id("celerity");
    private static final ResourceLocation POTENCE = VtmMod.id("potence");
    private static final ResourceLocation FORTITUDE = VtmMod.id("fortitude");

    private static final Bonus[] CELERITY_BONUSES = {
            new Bonus(Attributes.MOVEMENT_SPEED, VtmMod.id("celerity_speed"), 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
    };
    private static final Bonus[] POTENCE_BONUSES = {
            new Bonus(Attributes.ATTACK_DAMAGE, VtmMod.id("potence_damage"), 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.ATTACK_KNOCKBACK, VtmMod.id("potence_knockback"), 0.2, AttributeModifier.Operation.ADD_VALUE),
    };
    private static final Bonus[] FORTITUDE_BONUSES = {
            new Bonus(Attributes.ARMOR, VtmMod.id("fortitude_armor"), 1.5, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.ARMOR_TOUGHNESS, VtmMod.id("fortitude_toughness"), 0.5, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.KNOCKBACK_RESISTANCE, VtmMod.id("fortitude_knockback"), 0.1, AttributeModifier.Operation.ADD_VALUE),
    };

    public static void apply(ServerPlayer player, VampireData data) {
        boolean vampire = data.isVampire();
        applyLevels(player, vampire ? data.disciplineLevel(CELERITY) : 0,
                vampire ? data.disciplineLevel(POTENCE) : 0, vampire ? data.disciplineLevel(FORTITUDE) : 0);
    }

    /** For any creature, e.g. NPC vampires: the same bonuses, straight from discipline levels. */
    public static void applyLevels(LivingEntity entity, int celerity, int potence, int fortitude) {
        apply(entity, CELERITY_BONUSES, celerity);
        apply(entity, POTENCE_BONUSES, potence);
        apply(entity, FORTITUDE_BONUSES, fortitude);
    }

    private static void apply(LivingEntity player, Bonus[] bonuses, int level) {
        for (Bonus bonus : bonuses) {
            AttributeInstance instance = player.getAttribute(bonus.attribute);
            if (instance == null) {
                continue;
            }
            if (level <= 0) {
                instance.removeModifier(bonus.id);
            } else {
                instance.addOrReplacePermanentModifier(new AttributeModifier(bonus.id, bonus.perLevel * level, bonus.op));
            }
        }
    }

    private PassiveEffects() {}
}

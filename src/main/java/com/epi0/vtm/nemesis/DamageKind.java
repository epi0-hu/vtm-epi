package com.epi0.vtm.nemesis;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** How a vampire was hurt; what it learns to resist and what scars it keeps. */
public enum DamageKind {
    MELEE(Scar.MAULED), PROJECTILE(Scar.PIERCED), FIRE(Scar.BURNED), SUN(Scar.SUNBLASTED), EXPLOSION(Scar.SHATTERED);

    private final Scar scar;

    DamageKind(Scar scar) {
        this.scar = scar;
    }

    public Scar scar() {
        return scar;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DamageKind of(DamageSource source, Level level) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return source.getEntity() == null && level.isDay() ? SUN : FIRE;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return PROJECTILE;
        }
        return MELEE;
    }
}

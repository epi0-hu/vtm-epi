package com.epi0.vtm.vampire;

import net.minecraft.util.StringRepresentable;

public enum Species implements StringRepresentable {
    MORTAL("mortal"),
    VAMPIRE("vampire");

    public static final com.mojang.serialization.Codec<Species> CODEC = StringRepresentable.fromEnum(Species::values);

    private final String name;

    Species(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

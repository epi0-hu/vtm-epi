package com.epi0.vtm;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side tunables. Values are only readable once a world/server has loaded the config. */
public final class VtmConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BLOOD_DECAY_SECONDS = BUILDER
            .comment("Seconds between each point of passive blood loss for vampires.")
            .defineInRange("bloodDecaySeconds", 90, 5, 86400);

    public static final ModConfigSpec.DoubleValue BLOOD_DECAY_AMOUNT = BUILDER
            .comment("Blood lost every time the decay interval elapses.")
            .defineInRange("bloodDecayAmount", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.BooleanValue SUNLIGHT_ENABLED = BUILDER
            .comment("Whether vampires burn in direct sunlight.")
            .define("sunlightEnabled", true);

    public static final ModConfigSpec.BooleanValue BLOOD_HEALING = BUILDER
            .comment("Whether vampires heal by spending blood instead of regenerating from food.")
            .define("bloodHealing", true);

    public static final ModConfigSpec.IntValue STARTING_EXPERIENCE = BUILDER
            .comment("Experience a freshly embraced vampire starts with.")
            .defineInRange("startingExperience", 15, 0, 1000);

    public static final ModConfigSpec.IntValue EXPERIENCE_INTERVAL_MINUTES = BUILDER
            .comment("Minutes of play as a vampire per point of experience. 0 disables passive experience.")
            .defineInRange("experienceIntervalMinutes", 20, 0, 1440);

    public static final ModConfigSpec.IntValue FEEDING_COOLDOWN_TICKS = BUILDER
            .comment("Ticks between bites.")
            .defineInRange("feedingCooldownTicks", 30, 0, 1200);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private VtmConfig() {}
}

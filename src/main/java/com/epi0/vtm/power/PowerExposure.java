package com.epi0.vtm.power;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.Hunters;
import com.epi0.vtm.npc.CivilianNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How loud a power is. Using one where mortals can see is how the hunters find out what you are: the more
 * spectacular the power, the more they learn. Some powers do the opposite: they make mortals forget, or look away.
 */
public final class PowerExposure {
    /** Witnesses nearer than this (and who can see the caster) count. */
    public static final double WITNESS_RANGE = 24.0;

    private static final Map<ResourceLocation, Integer> NOISE = new HashMap<>();

    private static void noise(String name, int amount) {
        NOISE.put(VtmMod.id(name), amount);
    }

    static {
        // Animalism: beasts come when called.
        noise("feral_whispers", 3);
        noise("summon_pack", 8);
        noise("drawing_out_the_beast", 8);
        // Blood sorcery is spectacular.
        noise("blood_bolt", 12);
        noise("extinguish_vitae", 8);
        noise("blood_of_potency", 4);
        noise("scorpions_touch", 10);
        noise("theft_of_vitae", 10);
        // Celerity: blurs, and the odd vanishing.
        noise("rapid_reflexes", 3);
        noise("fleetness", 6);
        noise("blink", 8);
        noise("traversal", 8);
        // Dominate: most of it is done with a look, but it is seen; clouding memory is how it is undone.
        noise("cloud_memory", -12);
        noise("compel", 4);
        noise("mesmerize", 5);
        noise("mass_compel", 8);
        noise("absolute_command", 10);
        // Fortitude: bodies that should not survive.
        noise("toughness", 2);
        noise("defy_bane", 2);
        noise("draught_of_endurance", 4);
        // Obfuscate hides.
        noise("cloak_of_shadows", -3);
        noise("mask_of_a_thousand_faces", -8);
        noise("vanish", -10);
        // Oblivion: things that should not happen in a street.
        noise("oblivions_sight", 3);
        noise("shadow_cast", 8);
        noise("arms_of_the_abyss", 12);
        noise("withering", 10);
        noise("necrotic_plague", 12);
        // Potence: strength that should not be.
        noise("soaring_leap", 5);
        noise("prowess", 3);
        noise("brutal_strike", 10);
        noise("earthshock", 12);
        // Presence: the room turns to look.
        noise("awe", 3);
        noise("daunt", 6);
        noise("entrancement", 4);
        noise("dread_gaze", 8);
        noise("majesty", 6);
        // Protean: changes that cannot be explained.
        noise("eyes_of_the_beast", 5);
        noise("feral_weapons", 8);
        noise("earth_meld", 6);
        noise("shapechange", 15);
        noise("bat_form", 15);
        noise("mist_form", 12);
        // Thin-blood alchemy: flashes and smoke.
        noise("catalyze", 3);
        noise("wake_with_evenings_fire", 8);
        noise("defractionate", 3);
        noise("prismatic", 5);
    }

    public static java.util.Set<ResourceLocation> noisyPowers() {
        return java.util.Set.copyOf(NOISE.keySet());
    }

    public static int noiseOf(ResourceLocation power) {
        return NOISE.getOrDefault(power, 0);
    }

    /** The mortals who can see {@code player} right now. */
    public static List<CivilianNpc> witnesses(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(CivilianNpc.class, player.getBoundingBox().inflate(WITNESS_RANGE),
                c -> c.isAlive() && !c.isResting() && c.hasLineOfSight(player));
    }

    /** A power was just used. Returns the change in exposure (positive: they learned something). */
    public static int cast(ServerPlayer player, ResourceLocation power) {
        int noise = noiseOf(power);
        if (noise == 0) {
            return 0;
        }
        City city = CityLife.at(player);
        if (city == null) {
            return 0;
        }
        if (noise < 0) {
            int before = city.exposureOf(player.getUUID());
            city.addExposure(player.getUUID(), noise);
            return city.exposureOf(player.getUUID()) - before;
        }
        int seen = witnesses(player).size();
        if (seen == 0) {
            return 0;
        }
        int amount = noise * (seen >= 3 ? 2 : 1);
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            amount /= 2; // seen, but not clearly
        }
        int before = city.exposureOf(player.getUUID());
        Hunters.expose(player, city, amount);
        int learned = city.exposureOf(player.getUUID()) - before;
        if (learned >= 8) {
            player.displayClientMessage(Component.translatable("message.vtm.power_seen"), true);
        }
        return learned;
    }

    private PowerExposure() {}
}

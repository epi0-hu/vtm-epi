package com.epi0.vtm.dialogue;

import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Something a conversation does to the world. Every field present is applied, in the order declared here.
 *
 * @param rapport    change to the NPC's rapport with the player
 * @param flag       flag to set on the pair
 * @param clearFlag  flag to clear
 * @param blood      blood the player drinks from the NPC, willingly given (vampires only; mortals get nothing)
 * @param experience experience awarded to a vampire player
 * @param humanity   change to the player's humanity
 * @param standing   change to the player's standing with the NPC's faction
 * @param join       the player joins the NPC's faction
 * @param leave      the player renounces their faction
 * @param promote    the player advances a rank (only if they have the standing)
 * @param heat       change to the player's heat (trouble) in the NPC's city
 * @param flagToday  a flag that lasts until dawn (clears the previous days' ones)
 * @param ghoul      the NPC, a mortal, drinks the player's blood and becomes their retainer
 * @param exposure   change to how much the city's mortals know of what the player is
 * @param alarm      the NPC glimpses something frightening in the player and will remember it as a threat
 */
public record Effect(Optional<Integer> rapport, Optional<String> flag, Optional<String> clearFlag, Optional<Float> blood,
                     Optional<Integer> experience, Optional<Integer> humanity, boolean alarm, Optional<Integer> standing,
                     boolean join, boolean leave, boolean promote, Optional<NemesisEffect> nemesis,
                     Optional<Integer> heat, Optional<String> flagToday, boolean ghoul, Optional<Integer> exposure) {
    public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("rapport").forGetter(Effect::rapport),
            Codec.STRING.optionalFieldOf("flag").forGetter(Effect::flag),
            Codec.STRING.optionalFieldOf("clear_flag").forGetter(Effect::clearFlag),
            Codec.FLOAT.optionalFieldOf("blood").forGetter(Effect::blood),
            Codec.INT.optionalFieldOf("experience").forGetter(Effect::experience),
            Codec.INT.optionalFieldOf("humanity").forGetter(Effect::humanity),
            Codec.BOOL.optionalFieldOf("alarm", false).forGetter(Effect::alarm),
            Codec.INT.optionalFieldOf("standing").forGetter(Effect::standing),
            Codec.BOOL.optionalFieldOf("join", false).forGetter(Effect::join),
            Codec.BOOL.optionalFieldOf("leave", false).forGetter(Effect::leave),
            Codec.BOOL.optionalFieldOf("promote", false).forGetter(Effect::promote),
            NemesisEffect.CODEC.optionalFieldOf("nemesis").forGetter(Effect::nemesis),
            Codec.INT.optionalFieldOf("heat").forGetter(Effect::heat),
            Codec.STRING.optionalFieldOf("flag_today").forGetter(Effect::flagToday),
            Codec.BOOL.optionalFieldOf("ghoul", false).forGetter(Effect::ghoul),
            Codec.INT.optionalFieldOf("exposure").forGetter(Effect::exposure)
    ).apply(i, Effect::new));

    public void apply(ServerPlayer player, VtmNpc npc) {
        long now = npc.level().getGameTime();
        var uuid = player.getUUID();
        rapport.ifPresent(delta -> npc.memory().adjustRapport(uuid,
                npc instanceof com.epi0.vtm.npc.CivilianNpc civilian ? civilian.persona().scaleRapport(delta) : delta, now));
        flagToday.ifPresent(f -> {
            npc.memory().clearFlagsWithPrefix(uuid, f + ":", now);
            npc.memory().setFlag(uuid, Condition.todayFlag(npc, f), true, now);
        });
        if (ghoul && npc instanceof com.epi0.vtm.npc.CivilianNpc civilian) {
            civilian.makeGhoul(player);
        }
        flag.ifPresent(f -> npc.memory().setFlag(uuid, f, true, now));
        clearFlag.ifPresent(f -> npc.memory().setFlag(uuid, f, false, now));
        if (alarm) {
            npc.witness(player, NpcMemory.Event.WITNESSED_FRENZY);
        }
        if (npc instanceof com.epi0.vtm.npc.CivilianNpc civilian && (ghoul || flag.filter("vessel"::equals).isPresent() || flagToday.filter("drank"::equals).isPresent())) {
            com.epi0.vtm.city.Herd.touch(player, civilian);
        }

        nemesis.ifPresent(n -> n.apply(player, npc));
        exposure.ifPresent(delta -> {
            com.epi0.vtm.city.City city = DialogueCity.of(npc);
            if (city != null) {
                city.addExposure(player.getUUID(), delta);
                com.epi0.vtm.city.CityData.get(player.getServer()).setDirty();
            }
        });
        heat.ifPresent(delta -> {
            com.epi0.vtm.city.City city = DialogueCity.of(npc);
            if (city != null) {
                if (delta > 0) {
                    com.epi0.vtm.city.CityLife.crime(player, city, delta);
                } else {
                    city.addHeat(player.getUUID(), delta);
                    com.epi0.vtm.city.CityData.get(player.getServer()).setDirty();
                }
            }
        });
        VampireData data = VampireData.of(player);
        if (!data.isVampire()) {
            return;
        }
        standing.ifPresent(delta -> SectManager.adjustStanding(player, npc.factionId(), delta));
        if (join) {
            SectManager.join(player, npc.factionId());
        }
        if (leave) {
            SectManager.leave(player);
        }
        if (promote) {
            SectManager.promote(player);
        }
        data = VampireData.of(player);
        if (blood.isPresent()) {
            data = data.withBlood(Math.max(0F, Math.min(data.maxBlood(), data.blood() + blood.get())));
        }
        if (experience.isPresent()) {
            data = data.withExperience(data.experience() + experience.get());
        }
        if (humanity.isPresent()) {
            data = data.withHumanity(Math.max(0, Math.min(10, data.humanity() + humanity.get())));
        }
        VampireManager.set(player, data);
    }
}

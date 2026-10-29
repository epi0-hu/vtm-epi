package com.epi0.vtm.dialogue;

import com.epi0.vtm.nemesis.Feud;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * A set of requirements on the conversation, all of which must hold; an absent field is not checked. Used to pick a
 * dialogue tree, to choose between alternative lines, and to show or enable an option.
 * <p>
 * Fields about the NPC, the player, their sects and their shared memory are described on {@link CoreCondition}; those
 * about a named vampire (a "nemesis") are in {@code nemesis}.
 */
public record Condition(CoreCondition core, Optional<NemesisReq> nemesis, Optional<PlaceReq> place, Optional<PersonReq> person) {

    /**
     * Requirements on the NPC as a named vampire and on its history with this player.
     *
     * @param present    the NPC is (or is not) a named vampire
     * @param yielding   the NPC kneels, beaten, awaiting the player's verdict
     * @param bound      the NPC is bound to this player
     * @param minLevel   level of the named vampire
     * @param role       seat in its court: leader, lieutenant, enforcer or agent
     * @param minEnmity  bounds on how much it hates this player
     * @param maxEnmity  see {@code minEnmity}
     * @param minRespect how much it respects this player
     * @param minDefeats times the player has beaten it
     * @param minLosses  times it has killed the player
     * @param minSpared  times the player has shown it mercy
     * @param scarred    whether it carries scars
     */
    public record NemesisReq(Optional<Boolean> present, Optional<Boolean> yielding, Optional<Boolean> bound,
                             Optional<Integer> minLevel, Optional<String> role, Optional<Integer> minEnmity,
                             Optional<Integer> maxEnmity, Optional<Integer> minRespect, Optional<Integer> minDefeats,
                             Optional<Integer> minLosses, Optional<Integer> minSpared, Optional<Boolean> scarred) {
        public static final Codec<NemesisReq> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("present").forGetter(NemesisReq::present),
                Codec.BOOL.optionalFieldOf("yielding").forGetter(NemesisReq::yielding),
                Codec.BOOL.optionalFieldOf("bound").forGetter(NemesisReq::bound),
                Codec.INT.optionalFieldOf("min_level").forGetter(NemesisReq::minLevel),
                Codec.STRING.optionalFieldOf("role").forGetter(NemesisReq::role),
                Codec.INT.optionalFieldOf("min_enmity").forGetter(NemesisReq::minEnmity),
                Codec.INT.optionalFieldOf("max_enmity").forGetter(NemesisReq::maxEnmity),
                Codec.INT.optionalFieldOf("min_respect").forGetter(NemesisReq::minRespect),
                Codec.INT.optionalFieldOf("min_defeats").forGetter(NemesisReq::minDefeats),
                Codec.INT.optionalFieldOf("min_losses").forGetter(NemesisReq::minLosses),
                Codec.INT.optionalFieldOf("min_spared").forGetter(NemesisReq::minSpared),
                Codec.BOOL.optionalFieldOf("scarred").forGetter(NemesisReq::scarred)
        ).apply(i, NemesisReq::new));

        boolean test(ServerPlayer player, VtmNpc npc) {
            NemesisRecord r = npc instanceof VampireNpc v ? NemesisManager.recordOf(v) : null;
            if (present.isPresent() && present.get() != (r != null)) {
                return false;
            }
            if (yielding.isPresent() && yielding.get() != (npc instanceof VampireNpc v && v.isYielding())) {
                return false;
            }
            if (bound.isPresent() && bound.get() != (npc instanceof VampireNpc v && player.getUUID().equals(v.boundOwner()))) {
                return false;
            }
            if (r == null) {
                return minLevel.isEmpty() && role.isEmpty() && minEnmity.isEmpty() && maxEnmity.isEmpty()
                        && minRespect.isEmpty() && minDefeats.isEmpty() && minLosses.isEmpty() && minSpared.isEmpty()
                        && scarred.isEmpty();
            }
            Feud feud = r.feudOrNull(player.getUUID());
            int enmity = feud == null ? 0 : feud.enmity;
            return minLevel.map(v -> r.level >= v).orElse(true)
                    && role.map(v -> r.role.id().equals(v)).orElse(true)
                    && minEnmity.map(v -> enmity >= v).orElse(true)
                    && maxEnmity.map(v -> enmity <= v).orElse(true)
                    && minRespect.map(v -> feud != null && feud.respect >= v).orElse(true)
                    && minDefeats.map(v -> feud != null && feud.defeats >= v).orElse(true)
                    && minLosses.map(v -> feud != null && feud.losses >= v).orElse(true)
                    && minSpared.map(v -> feud != null && feud.spared >= v).orElse(true)
                    && scarred.map(v -> v == !r.scars.isEmpty()).orElse(true);
        }
    }

    /**
     * Requirements on where the conversation happens and what the NPC does there.
     *
     * @param job      the NPC's trade: resident, shopkeeper, bartender or watchman
     * @param inCity   whether the NPC is in a city
     * @param minHeat  how much trouble the player has caused in the city (bounds)
     * @param maxHeat  see {@code minHeat}
     * @param owner    the faction that rules the city the NPC is in
     */
    public record PlaceReq(Optional<String> job, Optional<Boolean> inCity, Optional<Integer> minHeat, Optional<Integer> maxHeat,
                           Optional<net.minecraft.resources.ResourceLocation> owner) {
        public static final Codec<PlaceReq> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("job").forGetter(PlaceReq::job),
                Codec.BOOL.optionalFieldOf("in_city").forGetter(PlaceReq::inCity),
                Codec.INT.optionalFieldOf("min_heat").forGetter(PlaceReq::minHeat),
                Codec.INT.optionalFieldOf("max_heat").forGetter(PlaceReq::maxHeat),
                net.minecraft.resources.ResourceLocation.CODEC.optionalFieldOf("owner").forGetter(PlaceReq::owner)
        ).apply(i, PlaceReq::new));

        boolean test(ServerPlayer player, VtmNpc npc) {
            com.epi0.vtm.city.City city = DialogueCity.of(npc);
            if (inCity.isPresent() && inCity.get() != (city != null)) {
                return false;
            }
            if (job.isPresent() && !(npc instanceof com.epi0.vtm.npc.CivilianNpc civilian && civilian.job().id().equals(job.get()))) {
                return false;
            }
            int heat = city == null ? 0 : city.heatOf(player.getUUID());
            return minHeat.map(v -> heat >= v).orElse(true) && maxHeat.map(v -> heat <= v).orElse(true)
                    && owner.map(v -> city != null && city.owner.equals(v)).orElse(true);
        }
    }

    /**
     * Requirements on who the NPC is as a person.
     *
     * @param temperament  cheerful, nervous, brave, suspicious, lonely, devout or curious
     * @param age          young, adult or elder
     * @param family       whether they live with other people
     * @param noFlagToday  a flag the pair has not set yet today (see {@code flag_today} in effects)
     */
    public record PersonReq(Optional<String> temperament, Optional<String> age, Optional<Boolean> family,
                            Optional<String> noFlagToday) {
        public static final Codec<PersonReq> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("temperament").forGetter(PersonReq::temperament),
                Codec.STRING.optionalFieldOf("age").forGetter(PersonReq::age),
                Codec.BOOL.optionalFieldOf("family").forGetter(PersonReq::family),
                Codec.STRING.optionalFieldOf("no_flag_today").forGetter(PersonReq::noFlagToday)
        ).apply(i, PersonReq::new));

        boolean test(ServerPlayer player, VtmNpc npc) {
            if (noFlagToday.isPresent() && npc.memory().hasFlag(player.getUUID(), todayFlag(npc, noFlagToday.get()))) {
                return false;
            }
            if (!(npc instanceof com.epi0.vtm.npc.CivilianNpc civilian)) {
                return temperament.isEmpty() && age.isEmpty() && family.isEmpty();
            }
            return temperament.map(v -> civilian.persona().temperament().id().equals(v)).orElse(true)
                    && age.map(v -> civilian.persona().age().id().equals(v)).orElse(true)
                    && family.map(v -> v == !com.epi0.vtm.npc.Household.members(civilian).isEmpty()).orElse(true);
        }
    }

    /** The name a once-a-day flag takes today. */
    public static String todayFlag(VtmNpc npc, String flag) {
        return flag + ":" + npc.level().getDayTime() / 24000L;
    }

    public static final MapCodec<Condition> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            CoreCondition.MAP.forGetter(Condition::core),
            NemesisReq.CODEC.optionalFieldOf("nemesis").forGetter(Condition::nemesis),
            PlaceReq.CODEC.optionalFieldOf("place").forGetter(Condition::place),
            PersonReq.CODEC.optionalFieldOf("person").forGetter(Condition::person)
    ).apply(i, Condition::new));

    public static final Codec<Condition> CODEC = MAP.codec();

    public static final Condition ANY = new Condition(CoreCondition.NONE, Optional.empty(), Optional.empty(), Optional.empty());

    public static final MapCodec<Condition> WHEN = CODEC.optionalFieldOf("when", ANY);

    public boolean test(ServerPlayer player, VtmNpc npc) {
        return core.test(player, npc) && nemesis.map(n -> n.test(player, npc)).orElse(true)
                && place.map(p -> p.test(player, npc)).orElse(true) && person.map(p -> p.test(player, npc)).orElse(true);
    }
}

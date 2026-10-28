package com.epi0.vtm.dialogue;

import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * A set of requirements on the conversation, all of which must hold; an absent field is not checked.
 * Used to pick a dialogue tree, to choose between alternative lines, and to show or enable an option.
 *
 * @param npc         {@code civilian} or {@code vampire}
 * @param clan        the NPC's clan
 * @param faction     the NPC's faction
 * @param attitude    how the NPC currently regards the player
 * @param night       whether it must be night (true) or day (false)
 * @param playerVampire whether the player must be a vampire (true) or a mortal (false)
 * @param playerClan  the player's clan
 * @param minHumanity the player's humanity
 * @param discipline  the player's level in a discipline
 * @param minSeen     times the NPC has noticed the player
 * @param minRapport  lower bound on rapport
 * @param maxRapport  upper bound on rapport
 * @param flag        a flag that must be set between the two
 * @param noFlag      a flag that must not be set
 * @param threat      whether the NPC considers the player a threat (has been harmed or has witnessed feeding/frenzy)
 */
public record CoreCondition(Optional<String> npc, Optional<ResourceLocation> clan, Optional<ResourceLocation> faction,
                        Optional<Attitude> attitude, Optional<Boolean> night, Optional<Boolean> playerVampire,
                        Optional<ResourceLocation> playerClan, Optional<Integer> minHumanity,
                        Optional<Requirement> discipline, Optional<Integer> minSeen, Optional<Integer> minRapport,
                        Optional<Integer> maxRapport, Optional<String> flag, Optional<String> noFlag,
                        Optional<Boolean> threat, Optional<SectReq> sect) {

    public record Requirement(ResourceLocation id, int level) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Requirement::id),
                Codec.INT.optionalFieldOf("level", 1).forGetter(Requirement::level)
        ).apply(i, Requirement::new));
    }

    /**
     * Membership requirements, all measured against the NPC's own faction.
     *
     * @param member     the player belongs to the NPC's faction
     * @param unaligned  the player belongs to no faction at all
     * @param minRank    the player is a member of at least this rank
     * @param outranks   the NPC holds a higher rank than the player (who must be a member)
     * @param canPromote the player has the standing for the next rank
     * @param minStanding standing with the NPC's faction
     * @param maxStanding standing with the NPC's faction
     */
    public record SectReq(Optional<Boolean> member, Optional<Boolean> unaligned, Optional<Integer> minRank,
                          Optional<Boolean> outranks, Optional<Boolean> canPromote, Optional<Integer> minStanding,
                          Optional<Integer> maxStanding, Optional<Boolean> ruler) {
        public static final Codec<SectReq> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("member").forGetter(SectReq::member),
                Codec.BOOL.optionalFieldOf("unaligned").forGetter(SectReq::unaligned),
                Codec.INT.optionalFieldOf("min_rank").forGetter(SectReq::minRank),
                Codec.BOOL.optionalFieldOf("outranks").forGetter(SectReq::outranks),
                Codec.BOOL.optionalFieldOf("can_promote").forGetter(SectReq::canPromote),
                Codec.INT.optionalFieldOf("min_standing").forGetter(SectReq::minStanding),
                Codec.INT.optionalFieldOf("max_standing").forGetter(SectReq::maxStanding),
                Codec.BOOL.optionalFieldOf("ruler").forGetter(SectReq::ruler)
        ).apply(i, SectReq::new));

        private static boolean isRuler(ServerPlayer player, VtmNpc npc) {
            var reign = com.epi0.vtm.court.CourtData.get(player.getServer()).reignOf(player.getUUID());
            return reign != null && reign.faction.equals(npc.factionId());
        }

        boolean test(ServerPlayer player, VtmNpc npc) {
            Membership m = Membership.of(player);
            boolean isMember = m.isMemberOf(npc.factionId());
            return member.map(v -> v == isMember).orElse(true)
                    && unaligned.map(v -> v == m.faction().isEmpty()).orElse(true)
                    && minRank.map(v -> isMember && m.rank() >= v).orElse(true)
                    && outranks.map(v -> v == (isMember && npc.rank() > m.rank())).orElse(true)
                    && canPromote.map(v -> v == (isMember && SectManager.canPromote(m, npc.level().registryAccess()))).orElse(true)
                    && minStanding.map(v -> m.standingWith(npc.factionId()) >= v).orElse(true)
                    && maxStanding.map(v -> m.standingWith(npc.factionId()) <= v).orElse(true)
                    && ruler.map(v -> v == isRuler(player, npc)).orElse(true);
        }
    }

    private static final Codec<Attitude> ATTITUDE = Codec.STRING.xmap(
            s -> Attitude.valueOf(s.toUpperCase(java.util.Locale.ROOT)), a -> a.name().toLowerCase(java.util.Locale.ROOT));

    public static final com.mojang.serialization.MapCodec<CoreCondition> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("npc").forGetter(CoreCondition::npc),
            ResourceLocation.CODEC.optionalFieldOf("clan").forGetter(CoreCondition::clan),
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(CoreCondition::faction),
            ATTITUDE.optionalFieldOf("attitude").forGetter(CoreCondition::attitude),
            Codec.BOOL.optionalFieldOf("night").forGetter(CoreCondition::night),
            Codec.BOOL.optionalFieldOf("player_vampire").forGetter(CoreCondition::playerVampire),
            ResourceLocation.CODEC.optionalFieldOf("player_clan").forGetter(CoreCondition::playerClan),
            Codec.INT.optionalFieldOf("min_humanity").forGetter(CoreCondition::minHumanity),
            Requirement.CODEC.optionalFieldOf("discipline").forGetter(CoreCondition::discipline),
            Codec.INT.optionalFieldOf("min_seen").forGetter(CoreCondition::minSeen),
            Codec.INT.optionalFieldOf("min_rapport").forGetter(CoreCondition::minRapport),
            Codec.INT.optionalFieldOf("max_rapport").forGetter(CoreCondition::maxRapport),
            Codec.STRING.optionalFieldOf("flag").forGetter(CoreCondition::flag),
            Codec.STRING.optionalFieldOf("no_flag").forGetter(CoreCondition::noFlag),
            Codec.BOOL.optionalFieldOf("threat").forGetter(CoreCondition::threat),
            SectReq.CODEC.optionalFieldOf("sect").forGetter(CoreCondition::sect)
    ).apply(i, CoreCondition::new));

    public static final CoreCondition NONE = new CoreCondition(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    boolean test(ServerPlayer player, VtmNpc npc) {
        VampireData data = VampireData.of(player);
        NpcMemory memory = npc.memory();
        var uuid = player.getUUID();

        if (this.npc.isPresent() && !this.npc.get().equals(npc instanceof CivilianNpc ? "civilian" : "vampire")) {
            return false;
        }
        if (clan.isPresent() && !(npc instanceof VampireNpc v && clan.get().equals(v.clanId()))) {
            return false;
        }
        if (faction.isPresent() && !faction.get().equals(npc.factionId())) {
            return false;
        }
        if (attitude.isPresent() && attitude.get() != npc.attitudeTo(player)) {
            return false;
        }
        if (night.isPresent() && night.get() != VtmNpc.isNight(npc.level())) {
            return false;
        }
        if (playerVampire.isPresent() && playerVampire.get() != data.isVampire()) {
            return false;
        }
        if (playerClan.isPresent() && !data.clan().equals(playerClan)) {
            return false;
        }
        if (minHumanity.isPresent() && data.humanity() < minHumanity.get()) {
            return false;
        }
        if (discipline.isPresent() && data.disciplineLevel(discipline.get().id()) < discipline.get().level()) {
            return false;
        }
        if (minSeen.isPresent() && memory.about(uuid).map(NpcMemory.Encounter::seen).orElse(0) < minSeen.get()) {
            return false;
        }
        if (minRapport.isPresent() && memory.rapport(uuid) < minRapport.get()) {
            return false;
        }
        if (maxRapport.isPresent() && memory.rapport(uuid) > maxRapport.get()) {
            return false;
        }
        if (flag.isPresent() && !memory.hasFlag(uuid, flag.get())) {
            return false;
        }
        if (noFlag.isPresent() && memory.hasFlag(uuid, noFlag.get())) {
            return false;
        }
        if (threat.isPresent() && threat.get() != memory.isThreat(uuid)) {
            return false;
        }
        return sect.isEmpty() || sect.get().test(player, npc);
    }
}

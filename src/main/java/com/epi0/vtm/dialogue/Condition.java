package com.epi0.vtm.dialogue;

import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.NpcMemory;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
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
public record Condition(Optional<String> npc, Optional<ResourceLocation> clan, Optional<ResourceLocation> faction,
                        Optional<Attitude> attitude, Optional<Boolean> night, Optional<Boolean> playerVampire,
                        Optional<ResourceLocation> playerClan, Optional<Integer> minHumanity,
                        Optional<Requirement> discipline, Optional<Integer> minSeen, Optional<Integer> minRapport,
                        Optional<Integer> maxRapport, Optional<String> flag, Optional<String> noFlag,
                        Optional<Boolean> threat) {

    public record Requirement(ResourceLocation id, int level) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Requirement::id),
                Codec.INT.optionalFieldOf("level", 1).forGetter(Requirement::level)
        ).apply(i, Requirement::new));
    }

    private static final Codec<Attitude> ATTITUDE = Codec.STRING.xmap(
            s -> Attitude.valueOf(s.toUpperCase(java.util.Locale.ROOT)), a -> a.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<Condition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("npc").forGetter(Condition::npc),
            ResourceLocation.CODEC.optionalFieldOf("clan").forGetter(Condition::clan),
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Condition::faction),
            ATTITUDE.optionalFieldOf("attitude").forGetter(Condition::attitude),
            Codec.BOOL.optionalFieldOf("night").forGetter(Condition::night),
            Codec.BOOL.optionalFieldOf("player_vampire").forGetter(Condition::playerVampire),
            ResourceLocation.CODEC.optionalFieldOf("player_clan").forGetter(Condition::playerClan),
            Codec.INT.optionalFieldOf("min_humanity").forGetter(Condition::minHumanity),
            Requirement.CODEC.optionalFieldOf("discipline").forGetter(Condition::discipline),
            Codec.INT.optionalFieldOf("min_seen").forGetter(Condition::minSeen),
            Codec.INT.optionalFieldOf("min_rapport").forGetter(Condition::minRapport),
            Codec.INT.optionalFieldOf("max_rapport").forGetter(Condition::maxRapport),
            Codec.STRING.optionalFieldOf("flag").forGetter(Condition::flag),
            Codec.STRING.optionalFieldOf("no_flag").forGetter(Condition::noFlag),
            Codec.BOOL.optionalFieldOf("threat").forGetter(Condition::threat)
    ).apply(i, Condition::new));

    public static final Condition ANY = new Condition(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final com.mojang.serialization.MapCodec<Condition> WHEN = CODEC.optionalFieldOf("when", ANY);

    public boolean test(ServerPlayer player, VtmNpc npc) {
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
        return threat.isEmpty() || threat.get() == memory.isThreat(uuid);
    }
}

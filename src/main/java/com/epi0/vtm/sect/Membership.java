package com.epi0.vtm.sect;

import com.epi0.vtm.registry.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A player's place in vampire society: the sect they belong to (if any), their rank in it, and their standing with
 * every faction, which is remembered even when they are not a member. Immutable; changes go through {@link SectManager}.
 */
public record Membership(Optional<ResourceLocation> faction, int rank, Map<ResourceLocation, Integer> standing) {
    public static final int MAX_STANDING = 100;
    public static final Membership DEFAULT = new Membership(Optional.empty(), 0, Map.of());

    public static final Codec<Membership> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Membership::faction),
            Codec.INT.optionalFieldOf("rank", 0).forGetter(Membership::rank),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("standing", Map.of()).forGetter(Membership::standing)
    ).apply(i, Membership::new));

    public static Membership of(Player player) {
        return player.getData(ModAttachments.MEMBERSHIP);
    }

    public boolean isMemberOf(ResourceLocation id) {
        return faction.filter(id::equals).isPresent();
    }

    public int standingWith(ResourceLocation id) {
        return standing.getOrDefault(id, 0);
    }

    public Membership withFaction(Optional<ResourceLocation> v, int rank) {
        return new Membership(v, rank, standing);
    }

    public Membership withRank(int v) {
        return new Membership(faction, v, standing);
    }

    public Membership withStanding(ResourceLocation id, int value) {
        Map<ResourceLocation, Integer> next = new HashMap<>(standing);
        next.put(id, Math.max(-MAX_STANDING, Math.min(MAX_STANDING, value)));
        return new Membership(faction, rank, next);
    }
}

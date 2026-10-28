package com.epi0.vtm.city;

import com.epi0.vtm.registry.ModAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** The cities a player has been to or heard of. The Domains screen shows nothing else. */
public record CityKnowledge(Set<UUID> cities) {
    public static final CityKnowledge EMPTY = new CityKnowledge(Set.of());
    public static final Codec<CityKnowledge> CODEC = UUIDUtil.CODEC.listOf()
            .xmap(list -> new CityKnowledge(new LinkedHashSet<>(list)), k -> java.util.List.copyOf(k.cities()));

    public static CityKnowledge of(Player player) {
        return player.getData(ModAttachments.CITY_KNOWLEDGE);
    }

    public boolean knows(UUID city) {
        return cities.contains(city);
    }

    public CityKnowledge with(UUID city) {
        Set<UUID> next = new LinkedHashSet<>(cities);
        next.add(city);
        return new CityKnowledge(next);
    }
}

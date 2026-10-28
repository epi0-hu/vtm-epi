package com.epi0.vtm.nemesis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One line of the city's history. {@code key} names the lang entry {@code chronicle.vtm.<key>}, which receives
 * {@code args} (usually names) as its arguments.
 *
 * @param involved   the named vampires it concerns, so a player who knows any of them sees it
 * @param publicNews anyone hears of it (a leader falls, a vampire is destroyed)
 */
public record ChronicleEntry(long time, String key, List<String> args, Optional<ResourceLocation> faction,
                             List<UUID> involved, boolean publicNews) {
    public static final Codec<ChronicleEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("time").forGetter(ChronicleEntry::time),
            Codec.STRING.fieldOf("key").forGetter(ChronicleEntry::key),
            Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(ChronicleEntry::args),
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(ChronicleEntry::faction),
            UUIDUtil.CODEC.listOf().optionalFieldOf("involved", List.of()).forGetter(ChronicleEntry::involved),
            Codec.BOOL.optionalFieldOf("public", false).forGetter(ChronicleEntry::publicNews)
    ).apply(i, ChronicleEntry::new));
}

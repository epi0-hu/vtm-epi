package com.epi0.vtm.nemesis;

import com.epi0.vtm.registry.ModAttachments;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** How players come to know the named vampires: by meeting them, watching them fight, and being told. */
public final class Intel {

    public static boolean knows(ServerPlayer player, NemesisRecord record) {
        return NemesisKnowledge.of(player).about(record.id).isPresent();
    }

    /** The player has laid eyes on the vampire. */
    public static void meet(ServerPlayer player, NemesisRecord record) {
        NemesisKnowledge before = NemesisKnowledge.of(player);
        boolean firstTime = before.about(record.id).map(k -> k.tier() < 1).orElse(true);
        player.setData(ModAttachments.NEMESIS_KNOWLEDGE, before.update(record.id, k -> placed(k.withTier(1), record)));
        if (firstTime) {
            player.displayClientMessage(Component.translatable("message.vtm.nemesis.met", record.name), false);
        }
    }

    /** The player saw a trait at work. */
    public static void witness(ServerPlayer player, NemesisRecord record, Trait trait) {
        NemesisKnowledge before = NemesisKnowledge.of(player);
        if (before.about(record.id).map(k -> k.knows(trait)).orElse(false)) {
            return;
        }
        player.setData(ModAttachments.NEMESIS_KNOWLEDGE, before.update(record.id, k -> k.withTier(1).withTrait(trait)));
        player.displayClientMessage(Component.translatable("message.vtm.nemesis.learned_trait", record.name,
                Component.translatable(trait.nameKey())), false);
    }

    /** The player knows this vampire as well as one can: a thrall has no secrets from their master. */
    public static void knowFully(ServerPlayer player, NemesisRecord record) {
        player.setData(ModAttachments.NEMESIS_KNOWLEDGE, NemesisKnowledge.of(player).update(record.id, k -> {
            NemesisKnowledge.Known full = placed(k.withTier(2), record);
            for (Trait trait : record.traits) {
                full = full.withTrait(trait);
            }
            return full;
        }));
    }

    /** Where the vampire was last seen, as far as this player has been told. */
    public static void locate(ServerPlayer player, NemesisRecord record) {
        player.setData(ModAttachments.NEMESIS_KNOWLEDGE,
                NemesisKnowledge.of(player).update(record.id, k -> placed(k, record)));
    }

    private static NemesisKnowledge.Known placed(NemesisKnowledge.Known known, NemesisRecord record) {
        return record.lastSeen != null && record.seenDimension != null
                ? known.withPlace(GlobalPos.of(record.seenDimension, record.lastSeen)) : known;
    }

    /**
     * Teaches the player one more thing about {@code record}: first that they exist and where they were, then their
     * traits one at a time, then their kin and rivals. Returns what was learned, if anything was left to learn.
     */
    public static Optional<Component> reveal(ServerPlayer player, NemesisRecord record, RandomSource random) {
        NemesisKnowledge knowledge = NemesisKnowledge.of(player);
        NemesisKnowledge.Known known = knowledge.about(record.id).orElse(null);
        if (known == null) {
            player.setData(ModAttachments.NEMESIS_KNOWLEDGE, knowledge.update(record.id, k -> placed(k, record)));
            return Optional.of(Component.translatable("message.vtm.nemesis.heard_of", record.name));
        }
        if (known.tier() < 1) {
            player.setData(ModAttachments.NEMESIS_KNOWLEDGE, knowledge.update(record.id, k -> placed(k.withTier(1), record)));
            return Optional.of(Component.translatable("message.vtm.nemesis.learned_of", record.name));
        }
        List<Trait> unknown = new ArrayList<>(record.traits.stream().filter(t -> !known.knows(t)).toList());
        if (!unknown.isEmpty()) {
            Trait trait = unknown.get(random.nextInt(unknown.size()));
            player.setData(ModAttachments.NEMESIS_KNOWLEDGE, knowledge.update(record.id, k -> k.withTrait(trait)));
            return Optional.of(Component.translatable("message.vtm.nemesis.learned_trait", record.name, Component.translatable(trait.nameKey())));
        }
        if (known.tier() < 2) {
            player.setData(ModAttachments.NEMESIS_KNOWLEDGE, knowledge.update(record.id, k -> placed(k.withTier(2), record)));
            return Optional.of(Component.translatable("message.vtm.nemesis.learned_kin", record.name));
        }
        return Optional.empty();
    }

    /**
     * Reveals something about one vampire chosen from {@code pool}, preferring the ones the player knows least.
     * Returns the message to show, or empty if there was nothing new.
     */
    public static Optional<Component> revealAbout(ServerPlayer player, List<NemesisRecord> pool, RandomSource random) {
        List<NemesisRecord> candidates = new ArrayList<>(pool.stream().filter(NemesisRecord::alive).toList());
        candidates.sort(java.util.Comparator.comparingInt(r -> NemesisKnowledge.of(player).about(r.id)
                .map(k -> 1 + k.tier() * 3 + k.traits().size()).orElse(0)));
        for (NemesisRecord record : candidates) {
            Optional<Component> learned = reveal(player, record, random);
            if (learned.isPresent()) {
                return learned;
            }
        }
        return Optional.empty();
    }

    public static List<NemesisRecord> inFaction(NemesisData data, ResourceLocation faction) {
        return data.inFaction(faction);
    }

    private Intel() {}
}

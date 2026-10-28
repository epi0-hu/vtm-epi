package com.epi0.vtm.nemesis;

import com.epi0.vtm.VtmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Gives every lair a master: a named vampire of the lair's faction, existing or newly come to the city. */
public final class LairRegistry {

    /** How many named vampires beyond the usual population a faction will take on to fill its lairs. */
    private static final int EXTRA_FOR_LAIRS = 6;

    /** The coffin at {@code pos} becomes the lair of a vampire of {@code faction}. Returns who, if anyone. */
    public static Optional<UUID> claim(ServerLevel level, BlockPos pos, ResourceLocation faction) {
        NemesisData data = NemesisManager.data(level.getServer());
        BlockPos spot = pos.above();
        for (NemesisRecord r : data.all()) {
            if (r.alive() && spot.equals(r.lair) && level.dimension().equals(r.lairDimension)) {
                return Optional.of(r.id);
            }
        }
        NemesisRecord tenant = data.inFaction(faction).stream()
                .filter(r -> r.lair == null && r.status != Status.BOUND)
                .max(Comparator.comparingInt((NemesisRecord r) -> r.role.power() * 100 + r.level)).orElse(null);
        if (tenant == null) {
            if (data.inFaction(faction).size() >= VtmConfig.NEMESIS_POPULATION.get() + EXTRA_FOR_LAIRS) {
                return Optional.empty();
            }
            tenant = NemesisManager.create(level.getServer(), faction, Role.AGENT, level.random);
        }
        tenant.lair = spot;
        tenant.lairDimension = level.dimension();
        tenant.seenDimension = level.dimension();
        tenant.lastSeen = spot;
        data.setDirty();
        return Optional.of(tenant.id);
    }

    private LairRegistry() {}
}

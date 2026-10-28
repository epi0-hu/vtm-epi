package com.epi0.vtm.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @param targetNemesis the vampire a HUNT is aimed at
 * @param targetFaction the faction a SPY watches
 */
public record Mission(MissionType type, long endsAt, @Nullable UUID targetNemesis, @Nullable ResourceLocation targetFaction) {

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id());
        tag.putLong("EndsAt", endsAt);
        if (targetNemesis != null) {
            tag.putUUID("Target", targetNemesis);
        }
        if (targetFaction != null) {
            tag.putString("Faction", targetFaction.toString());
        }
        return tag;
    }

    @Nullable
    static Mission load(CompoundTag tag) {
        return MissionType.byId(tag.getString("Type")).map(type -> new Mission(type, tag.getLong("EndsAt"),
                tag.hasUUID("Target") ? tag.getUUID("Target") : null,
                tag.contains("Faction") ? ResourceLocation.tryParse(tag.getString("Faction")) : null)).orElse(null);
    }
}

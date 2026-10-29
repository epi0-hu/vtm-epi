package com.epi0.vtm.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every hunters' lodge in the world. Lives with the overworld. */
public final class LodgeData extends SavedData {
    private static final String NAME = "vtm_lodges";

    /** One lodge: where its standard flies, who leads it, and until when it has been broken. */
    public static final class Lodge {
        public final UUID id;
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;
        @Nullable
        public UUID master;
        /** The game time at which a broken lodge raises its garrison again (0: it was never broken). */
        public long brokenUntil;

        public Lodge(UUID id, ResourceKey<Level> dimension, BlockPos pos) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos;
        }

        public boolean standing(long now) {
            return now >= brokenUntil;
        }
    }

    private final List<Lodge> lodges = new ArrayList<>();

    public static LodgeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LodgeData::new, LodgeData::load, null), NAME);
    }

    public List<Lodge> all() {
        return List.copyOf(lodges);
    }

    public void clear() {
        lodges.clear();
        setDirty();
    }

    public Lodge add(Lodge lodge) {
        lodges.add(lodge);
        setDirty();
        return lodge;
    }

    @Nullable
    public Lodge at(ResourceKey<Level> dimension, BlockPos pos) {
        return lodges.stream().filter(l -> l.dimension.equals(dimension) && l.pos.equals(pos)).findFirst().orElse(null);
    }

    @Nullable
    public Lodge get(UUID id) {
        return lodges.stream().filter(l -> l.id.equals(id)).findFirst().orElse(null);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Lodge lodge : lodges) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", lodge.id);
            t.putString("Dimension", lodge.dimension.location().toString());
            t.putLong("Pos", lodge.pos.asLong());
            if (lodge.master != null) {
                t.putUUID("Master", lodge.master);
            }
            t.putLong("BrokenUntil", lodge.brokenUntil);
            list.add(t);
        }
        tag.put("Lodges", list);
        return tag;
    }

    public static LodgeData load(CompoundTag tag, HolderLookup.Provider registries) {
        LodgeData data = new LodgeData();
        for (Tag raw : tag.getList("Lodges", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Lodge lodge = new Lodge(t.getUUID("Id"), ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("Dimension"))),
                    BlockPos.of(t.getLong("Pos")));
            lodge.master = t.hasUUID("Master") ? t.getUUID("Master") : null;
            lodge.brokenUntil = t.getLong("BrokenUntil");
            data.lodges.add(lodge);
        }
        return data;
    }
}

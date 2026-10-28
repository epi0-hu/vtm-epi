package com.epi0.vtm.court;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A player's rule over a faction: who rules, what they have decreed, what they have to spend, and what awaits a decision. */
public final class Reign {
    public static final int MAX_DECREES = 2;
    public static final int MAX_INFLUENCE = 200;
    public static final int MAX_PETITIONS = 3;
    public static final int STARTING_INFLUENCE = 20;

    public final ResourceLocation faction;
    public final UUID ruler;
    public String rulerName;
    public long since;
    public int influence = STARTING_INFLUENCE;
    /** Experience waiting to be collected from the cities' tribute. */
    public int tribute;
    public final Set<Decree> decrees = EnumSet.noneOf(Decree.class);
    public final List<Petition> petitions = new ArrayList<>();
    /** Faction id to the game time its truce with us lasts until. */
    public final Map<String, Long> truces = new HashMap<>();
    public boolean autoAppoint = true;

    public Reign(ResourceLocation faction, UUID ruler, String rulerName, long since) {
        this.faction = faction;
        this.ruler = ruler;
        this.rulerName = rulerName;
        this.since = since;
    }

    public boolean has(Decree decree) {
        return decrees.contains(decree);
    }

    public boolean truceWith(ResourceLocation other, long now) {
        return truces.getOrDefault(other.toString(), 0L) > now;
    }

    public void gain(int amount) {
        influence = Math.max(0, Math.min(MAX_INFLUENCE, influence + amount));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Faction", faction.toString());
        tag.putUUID("Ruler", ruler);
        tag.putString("Name", rulerName);
        tag.putLong("Since", since);
        tag.putInt("Influence", influence);
        tag.putInt("Tribute", tribute);
        tag.putBoolean("Auto", autoAppoint);
        ListTag decreeTags = new ListTag();
        decrees.forEach(d -> decreeTags.add(StringTag.valueOf(d.id())));
        tag.put("Decrees", decreeTags);
        ListTag petitionTags = new ListTag();
        petitions.forEach(p -> petitionTags.add(p.save()));
        tag.put("Petitions", petitionTags);
        CompoundTag truceTag = new CompoundTag();
        truces.forEach(truceTag::putLong);
        tag.put("Truces", truceTag);
        return tag;
    }

    static Reign load(CompoundTag tag) {
        Reign reign = new Reign(ResourceLocation.parse(tag.getString("Faction")), tag.getUUID("Ruler"), tag.getString("Name"), tag.getLong("Since"));
        reign.influence = tag.getInt("Influence");
        reign.tribute = tag.getInt("Tribute");
        reign.autoAppoint = !tag.contains("Auto") || tag.getBoolean("Auto");
        for (Tag t : tag.getList("Decrees", Tag.TAG_STRING)) {
            Decree.byId(t.getAsString()).ifPresent(reign.decrees::add);
        }
        for (Tag t : tag.getList("Petitions", Tag.TAG_COMPOUND)) {
            reign.petitions.add(Petition.load((CompoundTag) t));
        }
        CompoundTag truceTag = tag.getCompound("Truces");
        for (String key : truceTag.getAllKeys()) {
            reign.truces.put(key, truceTag.getLong(key));
        }
        return reign;
    }
}

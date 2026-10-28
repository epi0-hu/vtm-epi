package com.epi0.vtm.nemesis;

import net.minecraft.nbt.CompoundTag;

/**
 * What one named vampire and one player have been to each other. Enmity drives hunts and ambushes; respect is earned
 * by mercy and by being a worthy foe, and softens a grudge into something a conversation can use.
 */
public final class Feud {
    public int enmity;
    public int respect;
    public int encounters;
    /** Times the player has defeated this vampire. */
    public int defeats;
    /** Times this vampire has killed the player. */
    public int losses;
    public int spared;
    public int humiliations;
    /** Progress towards the next ambush, 0 to 100. */
    public int hunt;
    /** The faction has declared a blood hunt on the player and this vampire answers it. */
    public boolean bloodHunt;
    public long lastEncounter;

    public void clamp() {
        enmity = Math.max(0, Math.min(100, enmity));
        respect = Math.max(0, Math.min(100, respect));
        hunt = Math.max(0, Math.min(100, hunt));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Enmity", enmity);
        tag.putInt("Respect", respect);
        tag.putInt("Encounters", encounters);
        tag.putInt("Defeats", defeats);
        tag.putInt("Losses", losses);
        tag.putInt("Spared", spared);
        tag.putInt("Humiliations", humiliations);
        tag.putInt("Hunt", hunt);
        tag.putBoolean("BloodHunt", bloodHunt);
        tag.putLong("Last", lastEncounter);
        return tag;
    }

    static Feud load(CompoundTag tag) {
        Feud f = new Feud();
        f.enmity = tag.getInt("Enmity");
        f.respect = tag.getInt("Respect");
        f.encounters = tag.getInt("Encounters");
        f.defeats = tag.getInt("Defeats");
        f.losses = tag.getInt("Losses");
        f.spared = tag.getInt("Spared");
        f.humiliations = tag.getInt("Humiliations");
        f.hunt = tag.getInt("Hunt");
        f.bloodHunt = tag.getBoolean("BloodHunt");
        f.lastEncounter = tag.getLong("Last");
        return f;
    }
}

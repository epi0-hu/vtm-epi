package com.epi0.vtm.court;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Something that needs the ruler's decision. Each has two ways to answer it; leaving it unanswered is the same as
 * the milder one. The lang keys are {@code petition.vtm.<type>} (with the arguments as parameters) and
 * {@code petition.vtm.<type>.a} / {@code .b} for the answers.
 */
public record Petition(UUID id, Type type, long created, List<UUID> subjects, List<String> args) {
    /** Steps a petition waits before it lapses. */
    public static final long LIFETIME_TICKS = 36000L;

    public enum Type {
        QUARREL, ENVOY, SANCTUARY, FORTIFY, TRIBUNAL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Influence the second answer costs (the first never does, except fortifying). */
        public int costOf(int choice) {
            return switch (this) {
                case QUARREL -> choice == 1 ? 5 : 0;
                case FORTIFY -> choice == 0 ? 8 : 0;
                default -> 0;
            };
        }

        static Type byId(String id) {
            for (Type t : values()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            return QUARREL;
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.id());
        tag.putLong("Created", created);
        ListTag subjectTags = new ListTag();
        subjects.forEach(u -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("U", u);
            subjectTags.add(t);
        });
        tag.put("Subjects", subjectTags);
        ListTag argTags = new ListTag();
        args.forEach(a -> argTags.add(StringTag.valueOf(a)));
        tag.put("Args", argTags);
        return tag;
    }

    static Petition load(CompoundTag tag) {
        List<UUID> subjects = new ArrayList<>();
        for (Tag t : tag.getList("Subjects", Tag.TAG_COMPOUND)) {
            subjects.add(((CompoundTag) t).getUUID("U"));
        }
        List<String> args = new ArrayList<>();
        for (Tag t : tag.getList("Args", Tag.TAG_STRING)) {
            args.add(t.getAsString());
        }
        return new Petition(tag.getUUID("Id"), Type.byId(tag.getString("Type")), tag.getLong("Created"), subjects, args);
    }
}

package com.epi0.vtm.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Who shares a roof. People whose beds are on the same floor, a few blocks apart, are a household: they share a
 * surname, they are kin to one another, and they hear at once what befalls each other.
 */
public final class Household {
    private static final int REACH = 7;

    /** The other living members of {@code npc}'s household. */
    public static List<CivilianNpc> members(CivilianNpc npc) {
        BlockPos home = npc.home();
        if (home == null || npc.level().isClientSide) {
            return List.of();
        }
        return npc.level().getEntitiesOfClass(CivilianNpc.class, new AABB(home).inflate(REACH, 1.5, REACH),
                other -> other != npc && other.isAlive() && other.home() != null && Objects.equals(other.cityId(), npc.cityId())
                        && Math.abs(other.home().getY() - home.getY()) <= 1);
    }

    /** The surname a new resident takes: that of whoever already lives there. */
    public static void takeFamilyName(CivilianNpc npc) {
        for (CivilianNpc other : members(npc)) {
            String surname = surnameOf(other);
            if (!surname.isEmpty()) {
                String first = firstNameOf(npc);
                npc.setCustomName(net.minecraft.network.chat.Component.literal(first + " " + surname));
                return;
            }
        }
    }

    public static String firstNameOf(VtmNpc npc) {
        String name = npc.hasCustomName() ? npc.getCustomName().getString() : "";
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(0, space);
    }

    public static String surnameOf(VtmNpc npc) {
        String name = npc.hasCustomName() ? npc.getCustomName().getString() : "";
        int space = name.indexOf(' ');
        return space < 0 ? "" : name.substring(space + 1);
    }

    /** What {@code other} is to {@code npc}: the suffix of {@code relation.vtm.family.*}. */
    public static String relation(CivilianNpc npc, CivilianNpc other) {
        int gap = other.persona().age().ordinal() - npc.persona().age().ordinal();
        if (gap >= 2) {
            return "grandparent";
        }
        if (gap == 1) {
            return "parent";
        }
        if (gap == -1) {
            return "child";
        }
        if (gap <= -2) {
            return "grandchild";
        }
        if (npc.persona().age() == Persona.Age.YOUNG) {
            return "sibling";
        }
        return Math.floorMod(npc.getUUID().hashCode() ^ other.getUUID().hashCode(), 2) == 0 ? "partner" : "sibling";
    }

    /** The household member who speaks for the family: the first one found. */
    @Nullable
    public static CivilianNpc relative(CivilianNpc npc) {
        return members(npc).stream().findFirst().orElse(null);
    }

    private Household() {}
}

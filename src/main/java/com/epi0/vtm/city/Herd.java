package com.epi0.vtm.city;

import com.epi0.vtm.npc.CivilianNpc;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Keeps the roll of a vampire's vessels and ghouls up to date as the mortals themselves are met, drunk from, and lost. */
public final class Herd {

    /** Brings the records of everyone who has {@code npc} down as a vessel up to date. */
    public static void sync(CivilianNpc npc) {
        MinecraftServer server = npc.level().getServer();
        if (server == null) {
            return;
        }
        long now = npc.level().getGameTime();
        HerdData data = HerdData.get(server);
        for (UUID owner : npc.memory().players()) {
            if (!npc.memory().hasFlag(owner, "vessel") && !owner.equals(npc.ghoulOwner())) {
                continue;
            }
            HerdData.Retainer r = data.entry(owner, npc.getUUID(), npc.getName().getString());
            r.name = npc.getName().getString();
            r.ghoul = owner.equals(npc.ghoulOwner());
            r.dead = false;
            r.rapport = npc.memory().rapport(owner);
            r.place = GlobalPos.of(npc.level().dimension(), npc.blockPosition());
            City city = npc.city();
            r.city = city == null ? "" : city.name;
            long today = now / 24000L;
            r.drankDay = npc.memory().hasFlag(owner, com.epi0.vtm.dialogue.Condition.todayFlag(npc, "drank")) ? today : r.drankDay;
            data.setDirty();
        }
    }

    /** Someone in the herd has died. */
    public static void died(CivilianNpc npc) {
        MinecraftServer server = npc.level().getServer();
        if (server == null) {
            return;
        }
        HerdData data = HerdData.get(server);
        for (HerdData.Retainer r : data.about(npc.getUUID())) {
            r.dead = true;
            data.setDirty();
        }
    }

    /** A player has just made someone their vessel, or their ghoul, or drunk from them. */
    public static void touch(ServerPlayer player, CivilianNpc npc) {
        sync(npc);
    }

    private Herd() {}
}

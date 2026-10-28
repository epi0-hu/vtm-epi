package com.epi0.vtm.npc;

import net.minecraft.server.level.ServerPlayer;

/** Lets NPCs notice what a player does in front of them. */
public final class NpcWitness {

    /**
     * Everyone within {@code radius} who can see the player remembers {@code event}; civilians also panic.
     */
    public static void broadcast(ServerPlayer player, NpcMemory.Event event, double radius) {
        boolean mortalSaw = false;
        for (VtmNpc npc : player.serverLevel().getEntitiesOfClass(VtmNpc.class, player.getBoundingBox().inflate(radius),
                n -> n.isAlive() && n.hasLineOfSight(player))) {
            npc.witness(player, event);
            if (npc instanceof CivilianNpc) {
                npc.setLastHurtByMob(player);
                mortalSaw = true;
            }
        }
        if (mortalSaw) {
            com.epi0.vtm.city.City city = com.epi0.vtm.city.CityLife.at(player);
            if (city != null) {
                com.epi0.vtm.city.CityLife.crime(player, city, event == NpcMemory.Event.WITNESSED_FRENZY ? 18 : 12);
                com.epi0.vtm.city.Territory.masqueradeBreach(player, city);
            }
        }
        if (mortalSaw && event == NpcMemory.Event.WITNESSED_FEEDING) {
            com.epi0.vtm.sect.SectManager.masqueradeBreach(player);
        }
    }

    private NpcWitness() {}
}

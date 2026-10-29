package com.epi0.vtm.npc;

import com.epi0.vtm.city.City;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * What mortals tell each other. A person who has been hurt, or who has seen something they should not have, passes it on
 * to anyone they stop to talk to; so does a person who has taken to a stranger. A city learns what a player is.
 */
public final class Gossip {
    /** Rumours older than this, with no fresh word of the player, are forgotten. */
    public static final long RUMOUR_LIFE = 48000L;
    /** Once a second, the chance that someone stops to talk to a neighbour. */
    private static final int ONE_IN = 12;

    /** Called once a second for each civilian: sometimes they stop and talk. */
    public static void tick(CivilianNpc npc) {
        long now = npc.level().getGameTime();
        for (UUID player : npc.memory().players()) {
            expire(npc, player, now);
        }
        if (npc.isResting() || npc.getTarget() != null || npc.getRandom().nextInt(ONE_IN) != 0) {
            return;
        }
        CivilianNpc other = npc.level().getEntitiesOfClass(CivilianNpc.class, npc.getBoundingBox().inflate(4),
                c -> c != npc && c.isAlive() && !c.isResting() && c.getTarget() == null)
                .stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        if (other != null) {
            exchange(npc, other, now);
            exchange(other, npc, now);
        }
    }

    /** {@code teller} tells {@code listener} what they know of every player. */
    public static void exchange(CivilianNpc teller, CivilianNpc listener, long now) {
        for (UUID player : teller.memory().players()) {
            NpcMemory teller_ = teller.memory();
            NpcMemory heard = listener.memory();
            boolean frightening = teller_.isThreat(player) || teller_.hasFlag(player, "rumour");
            if (frightening && !heard.isThreat(player) && !heard.hasFlag(player, "rumour")
                    && teller_.rapport(player) < teller.persona().friendRapport()) {
                heard.setFlag(player, "rumour", true, now);
                City city = listener.city();
                if (city != null) {
                    city.addHeat(player, 2);
                }
            } else if (teller_.rapport(player) >= teller.persona().friendRapport() && heard.rapport(player) < listener.persona().friendRapport()
                    && !heard.hasFlag(player, "recommended") && !heard.isThreat(player)) {
                heard.setFlag(player, "recommended", true, now);
                heard.adjustRapport(player, 1, now);
            }
        }
    }

    private static void expire(CivilianNpc npc, UUID player, long now) {
        npc.memory().about(player).ifPresent(e -> {
            if (e.flags().contains("rumour") && now - e.lastSeen() > RUMOUR_LIFE) {
                npc.memory().setFlag(player, "rumour", false, now);
            }
        });
    }

    /** Whether this person has heard something bad about the player, without having seen it. */
    public static boolean hasHeardBad(CivilianNpc npc, Player player) {
        return npc.memory().hasFlag(player.getUUID(), "rumour");
    }

    private Gossip() {}
}

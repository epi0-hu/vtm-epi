package com.epi0.vtm.nemesis;

import com.epi0.vtm.npc.VampireNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/** What a named vampire says, chosen by what has passed between them and the player. Lines are lang keys. */
public final class NemesisTaunts {
    public static final int VARIANTS = 2;
    private static final long COOLDOWN = 600L;
    private static final double HEARING = 40.0;

    public enum Situation {
        ENCOUNTER, REMATCH, SCARRED, MERCY, VICTOR, PLAYER_LOW, SELF_LOW, YIELD, FLEE, VICTORY, AMBUSH, BOUND;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The remark a vampire opens with on seeing the player, from the most telling thing between them. */
    public static Situation greeting(NemesisRecord record, Feud feud) {
        if (feud == null) {
            return Situation.ENCOUNTER;
        }
        if (feud.losses > 0) {
            return Situation.VICTOR;
        }
        if (feud.defeats > 0 && !record.scars.isEmpty()) {
            return Situation.SCARRED;
        }
        if (feud.defeats > 0) {
            return Situation.REMATCH;
        }
        return feud.spared > 0 ? Situation.MERCY : Situation.ENCOUNTER;
    }

    public static void say(VampireNpc npc, NemesisRecord record, Situation situation, Player target) {
        long now = npc.level().getGameTime();
        if (!npc.mayTaunt(situation.id(), now, COOLDOWN)) {
            return;
        }
        int variant = npc.getRandom().nextInt(VARIANTS);
        Component line = Component.empty()
                .append(npc.getDisplayName().copy().withStyle(Style.EMPTY.withColor(
                        npc.faction().map(f -> f.color()).orElse(0xFFFFFF))))
                .append(": ")
                .append(Component.translatable("nemesis.vtm.taunt." + situation.id() + "." + variant,
                        target.getDisplayName(), record.name));
        for (ServerPlayer player : npc.level().getEntitiesOfClass(ServerPlayer.class, npc.getBoundingBox().inflate(HEARING))) {
            player.sendSystemMessage(line);
        }
    }

    private NemesisTaunts() {}
}

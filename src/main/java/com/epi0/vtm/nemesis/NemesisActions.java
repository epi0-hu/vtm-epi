package com.epi0.vtm.nemesis;

import com.epi0.vtm.network.NemesisActionPayload;
import com.epi0.vtm.network.NemesisViewsPayload;
import com.epi0.vtm.network.VtmNetwork;
import net.minecraft.server.level.ServerPlayer;

/** Validates and carries out what the Nemesis screen asks for. The client can only ever ask about its own vampires. */
public final class NemesisActions {

    public static void handle(ServerPlayer player, NemesisActionPayload request) {
        NemesisRecord r = NemesisManager.data(player.getServer()).get(request.nemesis());
        String action = request.action();
        if (r != null && r.status == Status.BOUND && player.getUUID().equals(r.boundTo)) {
            if (action.startsWith("mission:")) {
                MissionType.byId(action.substring("mission:".length())).ifPresent(type ->
                        NemesisManager.assign(player, r, type, request.target().orElse(null), request.faction().orElse(null)));
            } else if (action.equals("recall")) {
                NemesisManager.recall(player, r);
            } else if (action.equals("tithe")) {
                NemesisManager.tithe(player, r);
            } else if (action.equals("release")) {
                NemesisManager.release(player, r);
            }
        }
        send(player);
    }

    public static void send(ServerPlayer player) {
        VtmNetwork.send(player, new NemesisViewsPayload(NemesisViews.build(player.getServer(), player)));
    }

    private NemesisActions() {}
}

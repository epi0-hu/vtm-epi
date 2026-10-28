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
        if (action.startsWith("court:")) {
            court(player, request);
            send(player);
            return;
        }
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

    /** Court actions: {@code court:claim}, {@code abdicate}, {@code collect}, {@code decree:<id>}, {@code appoint:<role>},
     *  {@code dismiss}, {@code petition:<0|1>} (the nemesis field carries the record or petition id). */
    private static void court(ServerPlayer player, NemesisActionPayload request) {
        String[] parts = request.action().split(":");
        switch (parts[1]) {
            case "claim" -> com.epi0.vtm.court.Court.claim(player);
            case "abdicate" -> com.epi0.vtm.court.Court.abdicate(player);
            case "collect" -> com.epi0.vtm.court.Court.collect(player);
            case "decree" -> {
                if (parts.length > 2) {
                    com.epi0.vtm.court.Decree.byId(parts[2]).ifPresent(d -> com.epi0.vtm.court.Court.toggleDecree(player, d));
                }
            }
            case "appoint" -> {
                if (parts.length > 2) {
                    Role.byId(parts[2]).ifPresent(role -> com.epi0.vtm.court.Court.appoint(player, request.nemesis(), role));
                }
            }
            case "dismiss" -> com.epi0.vtm.court.Court.dismiss(player, request.nemesis());
            case "petition" -> {
                if (parts.length > 2) {
                    try {
                        com.epi0.vtm.court.Court.resolve(player, request.nemesis(), Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {
                        // not a choice
                    }
                }
            }
            default -> { }
        }
    }

    public static void send(ServerPlayer player) {
        VtmNetwork.send(player, new NemesisViewsPayload(NemesisViews.build(player.getServer(), player)));
    }

    private NemesisActions() {}
}

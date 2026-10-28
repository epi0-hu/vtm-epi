package com.epi0.vtm.nemesis;

import net.minecraft.server.MinecraftServer;

/** Keeps a standing entity in step with its record after the simulation changed the record. */
final class VampireNpcRefresh {
    static void apply(MinecraftServer server, NemesisRecord record) {
        var body = NemesisManager.entityOf(server, record);
        if (body != null) {
            body.setRank(record.role.rank());
            NemesisManager.refresh(body, record);
        }
    }

    private VampireNpcRefresh() {}
}

package com.epi0.vtm.client.ui;

import com.epi0.vtm.nemesis.NemesisViews;
import net.minecraft.client.Minecraft;

/** What the client has been told about the world: the latest snapshot of the Nemesis views. */
public final class ClientWorld {
    private static NemesisViews.Snapshot snapshot = NemesisViews.Snapshot.EMPTY;

    public static NemesisViews.Snapshot snapshot() {
        return snapshot;
    }

    public static void receive(NemesisViews.Snapshot fresh) {
        snapshot = fresh;
        if (Minecraft.getInstance().screen instanceof Codex codex) {
            codex.onSnapshot();
        }
    }

    private ClientWorld() {}
}

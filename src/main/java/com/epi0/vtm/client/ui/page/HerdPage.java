package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.NemesisViews;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/** The mortals who give you their blood, or who have drunk yours: how far they trust you, and whether they have given today. */
public final class HerdPage implements Page {
    private static final int GHOUL = 0xFFB07AD0;
    private static final int CARD = 40;
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.HERD;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.herd");
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        List<NemesisViews.Retainer> herd = ClientWorld.snapshot().herd().stream()
                .sorted(Comparator.comparing(NemesisViews.Retainer::dead).thenComparing(r -> !r.ghoul()).thenComparing(NemesisViews.Retainer::name)).toList();
        int w = area.w() - 8;
        if (herd.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.herd.none"), area.x() + 2, area.y() + 3, w, Theme.MUTED);
            return;
        }
        long today = ClientWorld.snapshot().now() / 24000L;
        scroll.begin(ui, area, 18 + herd.size() * (CARD + 4));
        int x = area.x();
        int y = scroll.top();
        long vessels = herd.stream().filter(r -> !r.ghoul() && !r.dead()).count();
        long ghouls = herd.stream().filter(r -> r.ghoul() && !r.dead()).count();
        ui.text(Component.translatable("gui.vtm.herd.count", vessels, ghouls), x + 2, y + 2, Theme.MUTED);
        y += 16;
        for (NemesisViews.Retainer r : herd) {
            Rect card = new Rect(x, y, w, CARD);
            Draw.inset(ui.g(), card);
            int accent = r.dead() ? Theme.DIM : r.ghoul() ? GHOUL : Theme.ACCENT;
            ui.g().fill(card.x(), card.y(), card.x() + 3, card.bottom(), accent);
            ui.shadow(Component.literal(r.name()), card.x() + 8, card.y() + 5, r.dead() ? Theme.DIM : Theme.TEXT);
            Component kind = Component.translatable(r.ghoul() ? "gui.vtm.herd.ghoul" : "gui.vtm.herd.vessel");
            int chipW = ui.font().width(kind) + 8;
            ui.chip(kind, card.right() - chipW - 6, card.y() + 4, accent);

            ui.text(Component.translatable("gui.vtm.herd.trust"), card.x() + 8, card.y() + 19, Theme.MUTED);
            Draw.pips(ui.g(), card.x() + 50, card.y() + 18, Math.max(0, r.rapport()), 10, r.dead() ? Theme.DIM : Theme.GOLD);
            ui.tooltip(new Rect(card.x() + 8, card.y() + 17, 140, 10), Component.translatable("gui.vtm.herd.trust_tip", r.rapport()));

            Component status;
            int statusColor;
            if (r.dead()) {
                status = Component.translatable("gui.vtm.herd.lost");
                statusColor = Theme.BAD;
            } else if (r.drankDay() >= today) {
                status = Component.translatable("gui.vtm.herd.drank");
                statusColor = Theme.MUTED;
            } else {
                status = Component.translatable("gui.vtm.herd.ready");
                statusColor = Theme.GOOD;
            }
            ui.right(status, card.right() - 6, card.y() + 19, statusColor);

            Component where = place(r);
            ui.clipped(where, card.x() + 8, card.y() + 29, card.w() - 16, Theme.DIM);
            y += CARD + 4;
        }
        scroll.end(ui);
    }

    private static Component place(NemesisViews.Retainer r) {
        String city = r.city().isEmpty() ? "" : r.city() + ", ";
        if (r.place().isEmpty()) {
            return Component.translatable("gui.vtm.herd.unknown_place");
        }
        GlobalPos p = r.place().get();
        var player = Minecraft.getInstance().player;
        String distance = player != null && player.level().dimension().equals(p.dimension())
                ? " (" + (int) Math.sqrt(player.distanceToSqr(p.pos().getX(), player.getY(), p.pos().getZ())) + " blocks)" : "";
        return Component.literal(city + p.pos().getX() + ", " + p.pos().getZ() + distance);
    }
}

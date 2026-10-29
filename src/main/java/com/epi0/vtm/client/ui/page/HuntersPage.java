package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.city.Hunters;
import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.NemesisViews;
import net.minecraft.network.chat.Component;

import java.util.List;

/** What the hunters know of you, city by city: how exposed you are, what they have sent, and what would lower it. */
public final class HuntersPage implements Page {
    private static final int HUNTER_BLUE = 0xFF2E86C1;
    private static final int CARD = 42;
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.HUNTERS;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.hunters");
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    static String stage(int exposure) {
        if (exposure >= Hunters.ASSAULT) {
            return "besieged";
        }
        if (exposure >= Hunters.HUNTED) {
            return "hunted";
        }
        return exposure >= Hunters.WATCHED ? "watched" : "unnoticed";
    }

    static int stageColor(int exposure) {
        if (exposure >= Hunters.ASSAULT) {
            return Theme.BAD;
        }
        if (exposure >= Hunters.HUNTED) {
            return 0xFFE08A4D;
        }
        return exposure >= Hunters.WATCHED ? Theme.WARN : Theme.GOOD;
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        List<NemesisViews.Hunting> cities = ClientWorld.snapshot().hunting();
        int w = area.w() - 8;
        int tipsH = ui.measure(Component.translatable("gui.vtm.hunters.tips"), w);
        scroll.begin(ui, area, 24 + Math.max(1, cities.size()) * (CARD + 4) + 14 + tipsH);
        int x = area.x();
        int y = scroll.top();
        int worst = cities.stream().mapToInt(NemesisViews.Hunting::exposure).max().orElse(0);
        ui.text(Component.translatable("gui.vtm.hunters.summary." + stage(worst)), x + 2, y + 2, stageColor(worst));
        y += 16;
        if (cities.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.hunters.none"), x + 2, y, w, Theme.MUTED);
            y += 26;
        }
        for (NemesisViews.Hunting h : cities) {
            Rect card = new Rect(x, y, w, CARD);
            Draw.inset(ui.g(), card);
            int color = Common.factionColor(h.owner());
            ui.g().fill(card.x(), card.y(), card.x() + 3, card.bottom(), color);
            ui.shadow(Component.literal(h.name()), card.x() + 8, card.y() + 5, color);
            Component status = Component.translatable("gui.vtm.hunters.stage." + stage(h.exposure()));
            ui.right(status, card.right() - 6, card.y() + 5, stageColor(h.exposure()));

            // The exposure bar, with the thresholds marked: watched, hunted, besieged.
            Rect bar = new Rect(card.x() + 8, card.y() + 18, card.w() - 16, 8);
            Draw.bar(ui.g(), bar, h.exposure() / 100F, HUNTER_BLUE);
            for (int mark : new int[]{Hunters.WATCHED, Hunters.HUNTED, Hunters.ASSAULT}) {
                int mx = bar.x() + bar.w() * mark / 100;
                ui.g().fill(mx, bar.y() - 1, mx + 1, bar.bottom() + 1, Theme.alpha(0xFFFFFF, 0x80));
            }
            ui.centred(Component.literal(String.valueOf(h.exposure())), bar.x() + bar.w() / 2, bar.y(), 0xFFFFFFFF);
            ui.tooltip(bar, Component.translatable("gui.vtm.hunters.bar_tip", Hunters.WATCHED, Hunters.HUNTED, Hunters.ASSAULT));

            int cx = card.x() + 8;
            int cy = card.y() + 30;
            if (h.inquisitor()) {
                cx += ui.chip(Component.translatable("gui.vtm.hunters.inquisitor"), cx, cy, Theme.BAD) + 4;
            }
            if (h.backed()) {
                cx += ui.chip(Component.translatable("gui.vtm.hunters.backed"), cx, cy, HUNTER_BLUE) + 4;
            }
            if (h.heat() > 0) {
                ui.chip(Component.translatable("gui.vtm.hunters.heat", h.heat()), cx, cy, Theme.WARN);
            }
            y += CARD + 4;
        }
        y += 6;
        ui.heading(Component.translatable("gui.vtm.hunters.tips_title"), x, y, w);
        ui.paragraph(Component.translatable("gui.vtm.hunters.tips"), x + 2, y + 16, w - 4, Theme.MUTED);
        scroll.end(ui);
    }
}

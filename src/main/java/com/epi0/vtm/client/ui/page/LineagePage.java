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
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/** Your generation and what it grants, and the elders you have heard of, oldest first. */
public final class LineagePage implements Page {
    private static final int CARD = 30;
    private static final int ELDER = 6;
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.LINEAGE;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.lineage");
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        VampireData me = VampireData.of(Minecraft.getInstance().player);
        List<NemesisViews.Core> known = ClientWorld.snapshot().entries().stream().map(NemesisViews.Entry::core)
                .filter(c -> c.generation() > 0)
                .sorted(Comparator.comparingInt(NemesisViews.Core::generation).thenComparing(NemesisViews.Core::name)).toList();
        int w = area.w() - 8;
        scroll.begin(ui, area, 162 + Math.max(1, known.size()) * (CARD + 4) + 24);
        int x = area.x();
        int y = scroll.top();

        Rect self = new Rect(x, y, w, 112);
        Rect in = Common.card(ui, self, Component.translatable("gui.vtm.lineage.yours"));
        if (!me.isVampire()) {
            ui.paragraph(Component.translatable("gui.vtm.lineage.mortal"), in.x(), in.y() + 2, in.w(), Theme.MUTED);
        } else {
            int gen = me.generation();
            ui.shadow(Component.translatable("gui.vtm.nemesis.generation", Common.ordinal(gen)), in.x(), in.y(), gen <= 6 ? Theme.GOLD : Theme.TEXT);
            // The ladder: thirteen rungs, the first the oldest; yours is lit.
            int rung = (in.w() - 4) / 13;
            for (int g = 1; g <= 13; g++) {
                int rx = in.x() + (g - 1) * rung;
                boolean mine = g == Math.max(1, Math.min(13, gen));
                ui.g().fill(rx, in.y() + 14, rx + rung - 2, in.y() + 22, mine ? Theme.GOLD : g < gen ? Theme.ACCENT : Theme.DIM);
            }
            ui.right(Component.translatable("gui.vtm.lineage.ladder"), in.x() + in.w(), in.y(), Theme.DIM);
            int ry = in.y() + 28;
            ry = row(ui, in, ry, "gui.vtm.lineage.potency", VampireRules.maxBloodPotency(gen) + "");
            ry = row(ui, in, ry, "gui.vtm.lineage.pool", String.format("%.0f", VampireRules.maxBlood(me)));
            ry = row(ui, in, ry, "gui.vtm.lineage.cost", pct(VampireRules.costFactor(gen)));
            ry = row(ui, in, ry, "gui.vtm.lineage.cooldown", pct(VampireRules.cooldownFactor(gen)));
            row(ui, in, ry, "gui.vtm.lineage.dice", "+" + Math.max(0, VampireRules.poolBonus(gen)));
        }
        y += 118;
        y += ui.heading(Component.translatable("gui.vtm.lineage.elders"), x, y, w);
        if (known.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.lineage.none"), x + 2, y, w, Theme.MUTED);
        }
        for (NemesisViews.Core c : known) {
            Rect card = new Rect(x, y, w, CARD);
            Draw.inset(ui.g(), card);
            boolean elder = c.generation() <= ELDER;
            int accent = Common.factionColor(c.faction());
            ui.g().fill(card.x(), card.y(), card.x() + 3, card.bottom(), accent);
            ui.shadow(Common.displayName(c), card.x() + 8, card.y() + 4, c.status().equals("dead") ? Theme.DIM : Theme.TEXT);
            ui.right(Component.translatable("gui.vtm.nemesis.generation", Common.ordinal(c.generation())), card.right() - 6, card.y() + 4, elder ? Theme.GOLD : Theme.MUTED);
            Component stand = c.generation() < me.generation()
                    ? Component.translatable("gui.vtm.lineage.older", me.generation() - c.generation())
                    : c.generation() > me.generation()
                    ? Component.translatable("gui.vtm.lineage.younger", c.generation() - me.generation())
                    : Component.translatable("gui.vtm.lineage.peer");
            ui.clipped(Component.empty().append(Common.factionName(c.faction())).append("  ").append(stand), card.x() + 8, card.y() + 17, card.w() - 16, Theme.DIM);
            y += CARD + 4;
        }
        scroll.end(ui);
    }

    private static int row(Ui ui, Rect in, int y, String key, String value) {
        ui.text(Component.translatable(key), in.x(), y, Theme.MUTED);
        ui.right(Component.literal(value), in.x() + in.w(), y, Theme.TEXT);
        return y + 11;
    }

    private static String pct(float factor) {
        return Math.round(factor * 100) + "%";
    }
}

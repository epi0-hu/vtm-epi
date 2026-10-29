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
import com.epi0.vtm.nemesis.Trait;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

/** Dossiers on every named vampire you know of: a list on the left, the chosen vampire's file on the right. */
public final class DossiersPage implements Page {
    private final Scroll list = new Scroll();
    private final Scroll file = new Scroll();
    private Rect listRect = new Rect(0, 0, 0, 0);

    @Override
    public PageId id() {
        return PageId.DOSSIERS;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.dossiers");
    }

    @Override
    public boolean wheel(double notches) {
        return (listHovered() ? list : file).wheel(notches);
    }

    private boolean listHovered() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        return listRect.contains(mx, 0 + mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
    }

    static List<NemesisViews.Entry> sorted() {
        return ClientWorld.snapshot().entries().stream().sorted(Comparator
                .comparing((NemesisViews.Entry e) -> e.core().status().equals("destroyed"))
                .thenComparing(e -> -e.core().tier())
                .thenComparing(e -> -e.core().level())
                .thenComparing(e -> e.core().name())).toList();
    }

    @Override
    public boolean key(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            List<NemesisViews.Entry> all = sorted();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(Common.selected)) {
                    int next = Math.max(0, Math.min(all.size() - 1, i + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1)));
                    Common.selected = all.get(next).id();
                    file.reset();
                    list.scrollTo(Math.max(0, next * 14 - 28));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        List<NemesisViews.Entry> all = sorted();
        if (all.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.nemesis.empty"), area.x(), area.y() + 2, area.w(), Theme.MUTED);
            return;
        }
        if (Common.entry(Common.selected).isEmpty()) {
            Common.selected = all.get(0).id();
        }
        int listW = Math.min(138, area.w() / 3);
        listRect = new Rect(area.x(), area.y(), listW, area.h());
        list.begin(ui, listRect, all.size() * 14);
        int y = list.top();
        for (NemesisViews.Entry e : all) {
            Rect row = new Rect(listRect.x(), y, listW - 5, 13);
            boolean sel = e.id().equals(Common.selected);
            boolean dead = e.core().status().equals("destroyed");
            ui.row(row, sel, () -> {
                Common.selected = e.id();
                file.reset();
            }, null);
            int color = dead ? Theme.DIM : Common.factionColor(e.core().faction());
            String mark = e.bond().bound() ? "+" : e.history().enmity() >= 50 ? "!" : "";
            if (!mark.isEmpty()) {
                ui.text(Component.literal(mark), row.x() + 4, row.y() + 3, e.bond().bound() ? Theme.GOOD : Theme.BAD);
            }
            ui.clipped(Component.literal(e.core().name()), row.x() + 12, row.y() + 3, row.w() - 14, color);
            y += 14;
        }
        list.end(ui);
        Rect detail = new Rect(listRect.right() + 6, area.y(), area.w() - listW - 6, area.h());
        Common.entry(Common.selected).ifPresent(e -> dossier(ui, detail, e));
    }

    private void dossier(Ui ui, Rect view, NemesisViews.Entry e) {
        NemesisViews.Core c = e.core();
        int x = view.x();
        int w = view.w() - 8;
        Rect body = new Rect(view.x(), view.y(), view.w(), view.h() - 20);
        file.begin(ui, body, 240 + c.traits().size() * 10 + e.kin().size() * 10);
        int y = file.top();
        ui.shadow(Common.displayName(c), x, y, Common.factionColor(c.faction()));
        y += 11;
        MutableComponent sub = Component.empty().append(Common.factionName(c.faction()));
        c.clan().ifPresent(clan -> sub.append(" | ").append(VtmRegistries.name("clan", clan)));
        c.role().ifPresent(role -> sub.append(" | ").append(Common.roleTitle(c.faction(), role)));
        ui.clipped(sub, x, y, w, Theme.MUTED);
        y += 11;
        Component status = c.tier() >= 1 ? Component.translatable("gui.vtm.nemesis.level_status", c.level(), Common.statusName(c.status()))
                : Common.statusName(c.status());
        ui.text(status, x, y, c.status().equals("destroyed") ? Theme.BAD : Theme.TEXT);
        if (c.generation() > 0) {
            ui.right(Component.translatable("gui.vtm.nemesis.generation", com.epi0.vtm.vampire.VampireRules.ordinal(c.generation())), x + w, y,
                    c.generation() <= 6 ? Theme.GOLD : Theme.MUTED);
        }
        y += 13;
        if (c.tier() < 1) {
            y += ui.paragraph(Component.translatable("gui.vtm.nemesis.only_heard"), x, y, w, Theme.MUTED) + 4;
        }
        y += ui.heading(Component.translatable("gui.vtm.nemesis.traits"), x, y, w);
        for (String id : c.traits()) {
            Trait trait = Trait.byId(id).orElse(null);
            if (trait == null) {
                continue;
            }
            int color = trait.kind() == Trait.Kind.STRENGTH ? Theme.BAD : trait.kind() == Trait.Kind.WEAKNESS ? Theme.GOOD : Theme.WARN;
            ui.text(Component.translatable(trait.nameKey()), x + 4, y, color);
            y += 10;
        }
        if (c.unknownTraits() > 0) {
            ui.text(Component.translatable("gui.vtm.nemesis.unknown_traits", c.unknownTraits()), x + 4, y, Theme.DIM);
            y += 10;
        }
        if (!c.scars().isEmpty()) {
            MutableComponent scars = Component.empty();
            for (String s : c.scars()) {
                if (!scars.getSiblings().isEmpty()) {
                    scars.append(", ");
                }
                scars.append(Component.translatable("scar.vtm." + s));
            }
            y += 3;
            y += ui.paragraph(Component.translatable("gui.vtm.nemesis.scars", scars), x, y, w, Theme.TEXT) + 2;
        }
        if (c.place().isPresent()) {
            GlobalPos place = c.place().get();
            ui.text(Component.translatable("gui.vtm.nemesis.last_seen", place.pos().getX(), place.pos().getY(), place.pos().getZ()), x, y, Theme.MUTED);
            y += 12;
        }
        NemesisViews.History h = e.history();
        if (h.enmity() > 0 || h.respect() > 0 || h.defeats() > 0 || h.losses() > 0 || h.spared() > 0) {
            y += 3;
            y += ui.heading(Component.translatable("gui.vtm.nemesis.between"), x, y, w);
            Common.meterRow(ui, new Rect(x + 4, y, w - 4, 10), Component.translatable("gui.vtm.nemesis.enmity"), h.enmity() / 100F, Theme.BAD, String.valueOf(h.enmity()));
            y += 12;
            Common.meterRow(ui, new Rect(x + 4, y, w - 4, 10), Component.translatable("gui.vtm.nemesis.respect"), h.respect() / 100F, Theme.GOOD, String.valueOf(h.respect()));
            y += 13;
            ui.text(Component.translatable("gui.vtm.nemesis.tally", h.defeats(), h.losses(), h.spared()), x + 4, y, Theme.TEXT);
            y += 11;
            if (h.bloodHunt()) {
                ui.text(Component.translatable("gui.vtm.nemesis.blood_hunt"), x + 4, y, Theme.BAD);
                y += 11;
            }
        }
        if (!e.kin().isEmpty()) {
            y += 3;
            y += ui.heading(Component.translatable("gui.vtm.nemesis.kin"), x, y, w);
            for (NemesisViews.Kin kin : e.kin()) {
                ui.text(Component.translatable("gui.vtm.nemesis.kin_line", Component.translatable("relation.vtm." + kin.type()), kin.name()), x + 4, y, Theme.TEXT);
                y += 10;
            }
        }
        file.end(ui);

        boolean marked = e.id().equals(Common.huntTarget);
        boolean live = c.status().equals("active") || c.status().equals("torpor");
        Rect mark = new Rect(view.right() - 118, view.bottom() - 16, 114, 15);
        ui.button(mark, Component.translatable(marked ? "gui.vtm.nemesis.unmark" : "gui.vtm.nemesis.mark"),
                marked ? Ui.Style.TOGGLE_ON : Ui.Style.NORMAL, live,
                () -> Common.huntTarget = marked ? null : e.id(), Component.translatable("gui.vtm.nemesis.mark.tip"));
        if (marked) {
            ui.text(Component.translatable("gui.vtm.nemesis.marked"), x, view.bottom() - 13, Theme.WARN);
        }
    }
}

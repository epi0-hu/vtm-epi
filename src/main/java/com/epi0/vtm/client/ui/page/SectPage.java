package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

/** Your place in vampire society: sect, rank, how close the next rank is, and where you stand with everyone. */
public final class SectPage implements Page {
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.SECT;
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        Membership m = Membership.of(Minecraft.getInstance().player);
        var access = Common.access();
        ResourceLocation id = m.faction().orElse(null);
        Faction faction = id == null ? null : VtmRegistries.faction(access, id).orElse(null);
        List<ResourceLocation> all = access.registry(VtmRegistries.FACTION)
                .map(r -> r.keySet().stream().sorted(Comparator.comparing(ResourceLocation::getPath)).toList()).orElse(List.of());
        scroll.begin(ui, area, 120 + all.size() * 16);
        int x = area.x();
        int w = area.w() - 8;
        int y = scroll.top();
        if (faction == null) {
            ui.text(Component.translatable("gui.vtm.sect.none"), x, y + 1, Theme.MUTED);
            y += 13 + ui.paragraph(Component.translatable("gui.vtm.sect.none_hint"), x, y + 13, w, Theme.TEXT) + 6;
        } else {
            int color = Common.factionColor(id);
            ui.shadow(Common.factionName(id), x, y + 1, color);
            ui.right(SectManager.rankTitle(access, id, m.rank()), x + w, y + 1, Theme.GOLD);
            y += 14;
            Draw.divider(ui.g(), x, y, w);
            y += 5;
            ui.text(Component.translatable("gui.vtm.sect.rank", SectManager.rankTitle(access, id, m.rank()), m.rank() + 1, faction.rankCount()), x, y, Theme.TEXT);
            y += 12;
            int standing = m.standingWith(id);
            if (m.rank() + 1 < faction.rankCount()) {
                int need = faction.standingFor(m.rank() + 1);
                int base = faction.standingFor(m.rank());
                Draw.bar(ui.g(), new Rect(x, y + 2, w, 7), need == base ? 1F : (standing - base) / (float) (need - base), color);
                y += 12;
                ui.text(Component.translatable("gui.vtm.sect.next", SectManager.rankTitle(access, id, m.rank() + 1), standing, need), x, y, Theme.MUTED);
            } else {
                ui.text(Component.translatable("gui.vtm.sect.top"), x, y, Theme.GOOD);
            }
            y += 14;
            y = relations(ui, "gui.vtm.sect.allies", faction.allies(), x, y, w, Theme.GOOD);
            y = relations(ui, "gui.vtm.sect.enemies", faction.enemies(), x, y, w, Theme.BAD);
            ui.button(new Rect(x, y + 2, 120, 14), Component.translatable("gui.vtm.open_court"), Ui.Style.QUIET, true,
                    () -> hub.show(PageId.COURT), null);
            y += 20;
        }
        y += ui.heading(Component.translatable("gui.vtm.sect.standing"), x, y, w);
        for (ResourceLocation other : all) {
            Faction def = VtmRegistries.faction(access, other).orElse(null);
            if (def == null || !def.joinable() && m.standingWith(other) == 0) {
                continue;
            }
            int value = m.standingWith(other);
            int c = Common.factionColor(other);
            ui.text(Common.factionName(other), x, y + 1, c);
            Rect bar = new Rect(x + 110, y + 2, w - 110 - 28, 7);
            Draw.centredBar(ui.g(), bar, value / (float) Membership.MAX_STANDING, c, Theme.BAD);
            ui.right(Component.literal(String.valueOf(value)), x + w, y + 1, value <= SectManager.OUTCAST ? Theme.BAD : Theme.TEXT);
            y += 14;
        }
        scroll.end(ui);
    }

    private static int relations(Ui ui, String key, List<ResourceLocation> list, int x, int y, int w, int color) {
        if (list.isEmpty()) {
            return y;
        }
        int cx = x;
        ui.text(Component.translatable(key), cx, y + 1, Theme.MUTED);
        cx += ui.font().width(Component.translatable(key)) + 6;
        for (ResourceLocation f : list) {
            cx += ui.chip(Common.factionName(f), cx, y, color) + 3;
        }
        return y + 15;
    }
}

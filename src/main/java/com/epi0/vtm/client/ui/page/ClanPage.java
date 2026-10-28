package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Your blood: what your clan is, the curse it carries, and the disciplines that come naturally to it. */
public final class ClanPage implements Page {
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.CLAN;
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        VampireData data = VampireData.of(Minecraft.getInstance().player);
        ResourceLocation id = data.clan().orElse(null);
        Clan clan = id == null ? null : VtmRegistries.clan(Common.access(), id).orElse(null);
        if (!data.isVampire() || clan == null) {
            ui.centred(Component.translatable("gui.vtm.no_clan"), area.x() + area.w() / 2, area.y() + area.h() / 2, Theme.MUTED);
            return;
        }
        int w = area.w() - 8;
        Component desc = VtmRegistries.text("clan", id, "desc");
        Component bane = VtmRegistries.text("clan", id, "bane");
        int height = 24 + ui.measure(desc, w) + 12 + 16 + ui.measure(bane, w - 12) + 12 + 18 + clan.disciplines().size() * 13 + 20;
        scroll.begin(ui, area, height);
        int x = area.x();
        int y = scroll.top();
        int color = Theme.readable(clan.color());
        ui.shadow(VtmRegistries.name("clan", id), x, y + 2, color);
        ui.text(Component.translatable("gui.vtm.clan_sect"), x, y + 14, Theme.DIM);
        ui.right(Common.factionName(clan.faction()), x + w, y + 14, Common.factionColor(clan.faction()));
        y += 28;
        y += ui.paragraph(desc, x, y, w, Theme.TEXT) + 8;

        int baneH = 14 + ui.measure(bane, w - 12) + 6;
        Rect box = new Rect(x, y, w, baneH);
        ui.g().fill(box.x(), box.y(), box.right(), box.bottom(), Theme.alpha(Theme.ACCENT, 0x28));
        ui.g().renderOutline(box.x(), box.y(), box.w(), box.h(), Theme.ACCENT_DIM);
        ui.text(Component.translatable("gui.vtm.bane"), box.x() + 6, box.y() + 4, Theme.BAD);
        ui.paragraph(bane, box.x() + 6, box.y() + 15, w - 12, Theme.TEXT);
        y += baneH + 10;

        y += ui.heading(Component.translatable("gui.vtm.clan_disciplines"), x, y, w);
        if (clan.disciplines().isEmpty()) {
            ui.text(Component.translatable("gui.vtm.none"), x, y, Theme.MUTED);
        }
        for (ResourceLocation d : clan.disciplines()) {
            int dc = Theme.readable(VtmRegistries.discipline(Common.access(), d).map(Discipline::color).orElse(0xAAAAAA));
            ui.g().fill(x, y + 1, x + 3, y + 9, dc);
            ui.text(VtmRegistries.name("discipline", d), x + 8, y + 1, dc);
            ui.right(Component.translatable("gui.vtm.level_short", data.disciplineLevel(d)), x + w, y + 1, Theme.MUTED);
            y += 13;
        }
        scroll.end(ui);
    }
}

package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.NemesisViews;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/** A map of every city you know of, who holds it, how strong the hold is and how much attention it draws. */
public final class DomainsPage implements Page {
    private static UUID selected;

    @Override
    public PageId id() {
        return PageId.DOMAINS;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.domains");
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        List<NemesisViews.Domain> domains = ClientWorld.snapshot().domains();
        if (domains.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.nemesis.no_domains"), area.x(), area.y() + 2, area.w(), Theme.MUTED);
            return;
        }
        if (selected == null || domains.stream().noneMatch(d -> d.id().equals(selected))) {
            selected = domains.get(0).id();
        }
        int mapH = Math.max(60, Math.min(110, area.h() - 66));
        Rect map = new Rect(area.x(), area.y(), area.w(), mapH);
        g(ui, map);
        Player player = Minecraft.getInstance().player;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (NemesisViews.Domain d : domains) {
            minX = Math.min(minX, d.place().pos().getX());
            maxX = Math.max(maxX, d.place().pos().getX());
            minZ = Math.min(minZ, d.place().pos().getZ());
            maxZ = Math.max(maxZ, d.place().pos().getZ());
        }
        if (player != null) {
            minX = Math.min(minX, player.getX());
            maxX = Math.max(maxX, player.getX());
            minZ = Math.min(minZ, player.getZ());
            maxZ = Math.max(maxZ, player.getZ());
        }
        int pad = 14;
        double scale = Math.min((map.w() - 2 * pad - 40) / Math.max(64, maxX - minX), (map.h() - 2 * pad) / Math.max(64, maxZ - minZ));
        final double fx = minX, fz = minZ;
        for (NemesisViews.Domain d : domains) {
            int px = map.x() + pad + (int) ((d.place().pos().getX() - fx) * scale);
            int pz = map.y() + pad + (int) ((d.place().pos().getZ() - fz) * scale);
            boolean sel = d.id().equals(selected);
            int color = Common.factionColor(d.owner());
            ui.zone(new Rect(px - 5, pz - 5, 11, 11), () -> selected = d.id(), Component.literal(d.name()));
            ui.g().fill(px - 3, pz - 3, px + 4, pz + 4, color);
            if (sel) {
                ui.g().renderOutline(px - 5, pz - 5, 11, 11, 0xFFFFFFFF);
            }
            if (d.heat() >= 60) {
                ui.g().fill(px - 1, pz - 1, px + 2, pz + 2, Theme.BAD);
            }
            ui.text(Component.literal(d.name()), px + 7, pz - 4, sel ? Theme.TEXT : Theme.MUTED);
        }
        if (player != null) {
            int px = map.x() + pad + (int) ((player.getX() - fx) * scale);
            int pz = map.y() + pad + (int) ((player.getZ() - fz) * scale);
            ui.g().fill(px - 1, pz - 4, px + 1, pz + 5, 0xFFFFFFFF);
            ui.g().fill(px - 4, pz - 1, px + 5, pz + 1, 0xFFFFFFFF);
            ui.tooltip(new Rect(px - 4, pz - 4, 9, 9), Component.translatable("gui.vtm.nemesis.you_are_here"));
        }

        NemesisViews.Domain d = domains.stream().filter(x -> x.id().equals(selected)).findFirst().orElseThrow();
        int x = area.x() + 2;
        int y = map.bottom() + 6;
        int half = area.w() / 2;
        ui.shadow(Component.literal(d.name()), x, y, Common.factionColor(d.owner()));
        ui.right(Component.translatable("gui.vtm.nemesis.ruled_by", Common.factionName(d.owner())), area.right() - 4, y, Theme.TEXT);
        y += 13;
        Common.meterRow(ui, new Rect(x, y, half - 8, 10), Component.translatable("gui.vtm.nemesis.strength"), d.strength() / 100F, Common.factionColor(d.owner()), String.valueOf(d.strength()));
        ui.clipped(d.warden().map(w -> Component.translatable("gui.vtm.nemesis.warden", w)).orElse(Component.translatable("gui.vtm.nemesis.warden_unknown")),
                x + half, y + 1, half - 8, Theme.MUTED);
        y += 13;
        Common.meterRow(ui, new Rect(x, y, half - 8, 10), Component.translatable("gui.vtm.nemesis.heat"), d.heat() / 100F, d.heat() >= 60 ? Theme.BAD : Theme.WARN, String.valueOf(d.heat()));
        double dist = player == null ? 0 : Math.sqrt(player.distanceToSqr(d.place().pos().getX(), player.getY(), d.place().pos().getZ()));
        ui.clipped(Component.translatable("gui.vtm.nemesis.city_at", d.place().pos().getX(), d.place().pos().getZ(), (int) dist), x + half, y + 1, half - 8, Theme.MUTED);
    }

    private static void g(Ui ui, Rect map) {
        ui.g().fill(map.x(), map.y(), map.right(), map.bottom(), 0xFF0C080A);
        ui.g().renderOutline(map.x(), map.y(), map.w(), map.h(), Theme.BORDER_DIM);
    }
}

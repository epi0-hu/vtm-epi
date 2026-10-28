package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.court.Court;
import com.epi0.vtm.court.CourtView;
import com.epi0.vtm.court.Decree;
import com.epi0.vtm.network.NemesisActionPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

/** The court you rule, if you rule one: decrees, seats, petitions and tribute. Otherwise, how to claim one. */
public final class CourtPage implements Page {
    private static int appointIndex;
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.COURT;
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    private static void court(String action, UUID target) {
        PacketDistributor.sendToServer(new NemesisActionPayload("court:" + action, target == null ? NemesisActionPayload.NONE : target,
                Optional.empty(), Optional.empty()));
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        CourtView view = ClientWorld.snapshot().court().orElse(null);
        if (view == null) {
            ui.text(Component.translatable("gui.vtm.loading"), area.x() + 2, area.y() + 3, Theme.DIM);
            return;
        }
        int x = area.x() + 2;
        int w = area.w() - 10;
        if (!view.ruling()) {
            int y = area.y() + 3;
            ui.text(view.faction().map(f -> (Component) Component.translatable("gui.vtm.court.sect", Common.factionName(f)))
                    .orElse(Component.translatable("gui.vtm.court.no_sect")), x, y, Theme.MUTED);
            y += 13 + ui.paragraph(Component.translatable("gui.vtm.court.intro"), x, y + 13, w, Theme.TEXT) + 6;
            Component status = view.block().map(b -> (Component) Component.translatable("gui.vtm.court.block." + b))
                    .orElse(Component.translatable("gui.vtm.court.can_claim"));
            y += ui.paragraph(status, x, y, w, view.block().isPresent() ? Theme.WARN : Theme.GOOD) + 8;
            ui.button(new Rect(x, y, 150, 17), Component.translatable("gui.vtm.court.claim"), Ui.Style.PRIMARY, view.block().isEmpty(),
                    () -> court("claim", null), null);
            return;
        }
        var faction = view.faction().orElse(null);
        int candidates = view.candidates().size();
        int decreeRows = (Decree.values().length + 1) / 2;
        int petitionH = Math.max(1, view.petitions().size()) * 46;
        scroll.begin(ui, area, 24 + 16 + decreeRows * 17 + 22 + view.seats().size() * 18 + 16 + petitionH + 26);
        int y = scroll.top();

        // Header
        if (faction != null) {
            ui.shadow(Component.translatable("gui.vtm.court.of", Common.factionName(faction)), x, y + 2, Common.factionColor(faction));
        }
        ui.right(Component.translatable("gui.vtm.court.influence", view.influence()), x + w - 120, y + 2, Theme.TEXT);
        ui.button(new Rect(x + w - 114, y, 114, 15), Component.translatable("gui.vtm.court.collect", view.tribute()), Ui.Style.PRIMARY,
                view.tribute() > 0, () -> court("collect", null), Component.translatable("gui.vtm.court.collect.tip"));
        y += 22;

        // Decrees
        y += ui.heading(Component.translatable("gui.vtm.court.decrees"), x, y, w);
        Decree[] decrees = Decree.values();
        int colW = (w - 4) / 2;
        for (int i = 0; i < decrees.length; i++) {
            Decree decree = decrees[i];
            boolean on = view.decrees().contains(decree.id());
            boolean allowed = on || view.decrees().size() < view.maxDecrees() && view.influence() >= Decree.COST
                    && view.decrees().stream().noneMatch(d -> Decree.byId(d).map(decree::conflictsWith).orElse(false));
            Rect r = new Rect(x + (i % 2) * (colW + 4), y + (i / 2) * 17, colW, 15);
            ui.button(r, Component.translatable("decree.vtm." + decree.id()), on ? Ui.Style.TOGGLE_ON : Ui.Style.NORMAL, allowed,
                    () -> court("decree:" + decree.id(), null), Component.translatable("decree.vtm." + decree.id() + ".desc"));
        }
        y += decreeRows * 17 + 6;

        // Seats
        y += ui.heading(Component.translatable("gui.vtm.court.seats"), x, y, w);
        if (candidates > 0) {
            appointIndex = Math.floorMod(appointIndex, candidates);
        }
        for (var seat : view.seats()) {
            Component role = faction == null ? Component.literal(seat.role()) : Common.roleTitle(faction, seat.role());
            ui.text(role, x, y + 3, Theme.MUTED);
            int nx = x + 70;
            if (seat.id().isPresent()) {
                ui.clipped(Component.literal(seat.name()), nx, y + 1, Math.min(90, w - 170), seat.rebel() ? Theme.BAD : Theme.TEXT);
                Rect loyal = new Rect(nx, y + 11, 56, 3);
                Draw.bar(ui.g(), loyal, seat.loyalty() / 100F, seat.loyalty() < 25 ? Theme.BAD : seat.loyalty() < 50 ? Theme.WARN : Theme.GOOD);
                ui.tooltip(loyal, Component.translatable("gui.vtm.court.loyalty_tip", seat.loyalty()));
                ui.button(new Rect(x + w - 62, y, 58, 14), Component.translatable("gui.vtm.court.dismiss"), Ui.Style.DANGER, true,
                        () -> court("dismiss", seat.id().get()), null);
            } else if (candidates > 0) {
                var who = view.candidates().get(appointIndex);
                ui.button(new Rect(nx, y, Math.min(140, w - 70 - 22), 14), Component.translatable("gui.vtm.court.appoint", who.name()),
                        Ui.Style.NORMAL, view.influence() >= Court.APPOINT_COST,
                        () -> court("appoint:" + seat.role(), who.id()), Component.translatable("gui.vtm.court.appoint.tip", who.level(), Court.APPOINT_COST));
                ui.button(new Rect(nx + Math.min(140, w - 70 - 22) + 3, y, 16, 14), Component.literal(">"), () -> appointIndex++);
            } else {
                ui.text(Component.translatable("gui.vtm.nemesis.vacant"), nx, y + 3, Theme.DIM);
            }
            y += 18;
        }
        y += 6;

        // Petitions
        y += ui.heading(Component.translatable("gui.vtm.court.petitions"), x, y, w);
        if (view.petitions().isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.court.no_petitions"), x + 2, y, w, Theme.DIM);
            y += 16;
        }
        for (var petition : view.petitions()) {
            Object[] args = petition.args().stream().map(a -> a.startsWith("faction.") ? (Object) Component.translatable(a) : a).toArray();
            int h = ui.paragraph(Component.translatable("petition.vtm." + petition.type(), args), x + 2, y, w - 4, Theme.TEXT);
            int by = y + Math.max(h, 18) + 2;
            for (int choice = 0; choice < 2; choice++) {
                int c = choice;
                int cost = choice == 0 ? petition.costA() : petition.costB();
                Component label = Component.translatable("petition.vtm." + petition.type() + (choice == 0 ? ".a" : ".b"));
                if (cost > 0) {
                    label = Component.empty().append(label).append(Component.literal(" (" + cost + ")"));
                }
                ui.button(new Rect(x + choice * (w / 2), by, w / 2 - 4, 14), label, Ui.Style.NORMAL, view.influence() >= cost,
                        () -> court("petition:" + c, petition.id()), null);
            }
            y = by + 20;
        }

        y += 4;
        ui.clipped(Component.translatable("gui.vtm.court.holds", view.cities().isEmpty() ? "-" : String.join(", ", view.cities())), x, y, w, Theme.MUTED);
        scroll.end(ui);
    }
}

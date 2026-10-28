package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisViews;
import com.epi0.vtm.network.NemesisActionPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The vampires bound to you: what they are doing, how loyal they are, and what you can send them to do. */
public final class CoteriePage implements Page {
    private static int spyIndex;
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.COTERIE;
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    private static void act(String action, NemesisViews.Entry e, UUID target, ResourceLocation faction) {
        PacketDistributor.sendToServer(new NemesisActionPayload(action, e.id(), Optional.ofNullable(target), Optional.ofNullable(faction)));
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        NemesisViews.Snapshot snap = ClientWorld.snapshot();
        List<NemesisViews.Entry> bound = snap.entries().stream().filter(e -> e.bond().bound()).toList();
        List<ResourceLocation> targets = snap.courts().stream().map(NemesisViews.Court::faction).toList();
        ui.text(Component.translatable("gui.vtm.nemesis.coterie_count", bound.size(), NemesisManager.MAX_COTERIE), area.x() + 2, area.y() + 3, Theme.MUTED);
        ResourceLocation spyFaction = null;
        if (!targets.isEmpty()) {
            spyIndex = Math.floorMod(spyIndex, targets.size());
            spyFaction = targets.get(spyIndex);
            ui.button(new Rect(area.right() - 168, area.y(), 164, 15), Component.translatable("gui.vtm.nemesis.spy_target", Common.factionName(spyFaction)),
                    Ui.Style.NORMAL, true, () -> spyIndex++, Component.translatable("gui.vtm.nemesis.spy_target.tip"));
        }
        Rect list = new Rect(area.x(), area.y() + 20, area.w(), area.h() - 20);
        if (bound.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.nemesis.no_coterie"), list.x() + 2, list.y() + 4, list.w() - 4, Theme.MUTED);
            return;
        }
        scroll.begin(ui, list, bound.size() * 58);
        int y = scroll.top();
        long now = snap.now();
        for (NemesisViews.Entry e : bound) {
            Rect card = new Rect(list.x(), y, list.w() - 6, 54);
            ui.g().fill(card.x(), card.y(), card.right(), card.bottom(), Theme.alpha(0xFFFFFF, 0x14));
            int x = card.x() + 5;
            ui.shadow(Common.displayName(e.core()), x, y + 3, Common.factionColor(e.core().faction()));
            ui.right(Component.translatable("gui.vtm.nemesis.level_short", e.core().level()), card.right() - 4, y + 3, Theme.TEXT);
            Common.meterRow(ui, new Rect(card.right() - 150, y + 14, 146, 10), Component.translatable("gui.vtm.nemesis.loyalty"),
                    e.bond().loyalty() / 100F, e.bond().loyalty() < 25 ? Theme.BAD : Theme.GOOD, String.valueOf(e.bond().loyalty()));
            Component state;
            if (e.bond().mission().isPresent()) {
                long hours = Math.max(0, (e.bond().missionEnds() - now) / 1000L);
                state = Component.translatable("gui.vtm.nemesis.on_mission", Component.translatable("gui.vtm.nemesis.mission." + e.bond().mission().get()), hours);
            } else if (e.bond().wakeAt() > now) {
                state = Component.translatable("gui.vtm.nemesis.recovering");
            } else {
                state = Component.translatable("gui.vtm.nemesis.at_side");
            }
            ui.clipped(state, x, y + 15, card.w() - 160, Theme.MUTED);
            MutableComponent traits = Component.empty();
            for (String id : e.core().traits()) {
                if (!traits.getSiblings().isEmpty()) {
                    traits.append(", ");
                }
                traits.append(Component.translatable("trait.vtm." + id));
            }
            ui.clipped(traits, x, y + 26, card.w() - 10, Theme.DIM);

            int by = y + 37;
            int bx = x;
            if (e.bond().mission().isPresent()) {
                ui.button(new Rect(bx, by, 90, 14), Component.translatable("gui.vtm.nemesis.recall"), () -> act("recall", e, null, null));
            } else {
                final ResourceLocation target = spyFaction;
                if (target != null) {
                    ui.button(new Rect(bx, by, 48, 14), Component.translatable("gui.vtm.nemesis.spy"), () -> act("mission:spy", e, null, target));
                    bx += 51;
                }
                UUID mark = Common.huntTarget;
                ui.button(new Rect(bx, by, 70, 14), Component.translatable("gui.vtm.nemesis.hunt"), Ui.Style.NORMAL, mark != null,
                        () -> act("mission:hunt", e, mark, null),
                        Component.translatable(mark == null ? "gui.vtm.nemesis.hunt.none" : "gui.vtm.nemesis.hunt.tip",
                                Common.entry(mark).map(t -> t.core().name()).orElse("?")));
                bx += 73;
                ui.button(new Rect(bx, by, 56, 14), Component.translatable("gui.vtm.nemesis.tribute"), () -> act("mission:tribute", e, null, null));
            }
            ui.button(new Rect(card.right() - 118, by, 56, 14), Component.translatable("gui.vtm.nemesis.tithe"), () -> act("tithe", e, null, null));
            ui.button(new Rect(card.right() - 60, by, 56, 14), Component.translatable("gui.vtm.nemesis.release"), Ui.Style.DANGER, true,
                    () -> act("release", e, null, null), null);
            y += 58;
        }
        scroll.end(ui);
    }
}

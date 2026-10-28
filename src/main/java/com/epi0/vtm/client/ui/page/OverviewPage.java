package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ClientModEvents;
import com.epi0.vtm.client.PowerSelection;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Icon;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.registry.ModEffects;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Who you are at a glance: blood, hunger, humanity, standing, and what state you are in. */
public final class OverviewPage implements Page {
    @Override
    public PageId id() {
        return PageId.OVERVIEW;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.overview");
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        var player = Minecraft.getInstance().player;
        VampireData data = VampireData.of(player);
        if (!data.isVampire()) {
            ui.centred(Component.translatable("gui.vtm.mortal"), area.x() + area.w() / 2, area.y() + area.h() / 2 - 8, Theme.MUTED);
            ui.paragraph(Component.translatable("gui.vtm.mortal.hint"), area.x() + 20, area.y() + area.h() / 2 + 6, area.w() - 40, Theme.DIM);
            return;
        }
        int half = (area.w() - Theme.GAP) / 2;
        int topH = 84;
        Rect blood = new Rect(area.x(), area.y(), half, topH);
        Rect soul = new Rect(area.x() + half + Theme.GAP, area.y(), area.w() - half - Theme.GAP, topH);
        Rect standing = new Rect(area.x(), area.y() + topH + Theme.GAP, half, area.h() - topH - Theme.GAP);
        Rect condition = new Rect(soul.x(), standing.y(), soul.w(), standing.h());
        renderBlood(ui, blood, data);
        renderSoul(ui, soul, data);
        renderStanding(ui, standing, data, hub);
        renderCondition(ui, condition, data, player);
    }

    private void renderBlood(Ui ui, Rect r, VampireData data) {
        Rect in = Common.card(ui, r, Component.translatable("gui.vtm.blood"));
        float fraction = data.maxBlood() <= 0 ? 0 : data.blood() / data.maxBlood();
        int color = data.hunger() >= 4 ? 0xFF8B1515 : Theme.ACCENT;
        Rect bar = new Rect(in.x(), in.y() + 1, in.w(), 12);
        Draw.bar(ui.g(), bar, fraction, color);
        ui.centred(Component.translatable("gui.vtm.blood_short", String.format("%.1f", data.blood()), Math.round(data.maxBlood())),
                bar.x() + bar.w() / 2, bar.y() + 2, 0xFFFFFFFF);
        Component word = Component.translatable("gui.vtm.hunger." + Math.min(5, data.hunger()));
        ui.text(word, in.x(), in.y() + 20, data.hunger() >= 4 ? Theme.BAD : data.hunger() >= 2 ? Theme.WARN : Theme.GOOD);
        Draw.pips(ui.g(), in.x() + 80, in.y() + 19, data.hunger(), VampireRules.MAX_HUNGER, 0xFFC63B3B);
        ui.tooltip(new Rect(in.x(), in.y() + 18, in.w(), 11), Component.translatable("gui.vtm.hunger"));
        ui.text(Component.translatable("gui.vtm.blood_potency"), in.x(), in.y() + 34, Theme.MUTED);
        Draw.pips(ui.g(), in.x() + 80, in.y() + 33, data.bloodPotency(), VampireRules.maxBloodPotency(data.generation()), 0xFFD8A5A5);
    }

    private void renderSoul(Ui ui, Rect r, VampireData data) {
        Rect in = Common.card(ui, r, Component.translatable("gui.vtm.soul"));
        ui.text(Component.translatable("gui.vtm.humanity"), in.x(), in.y() + 1, Theme.MUTED);
        Draw.pips(ui.g(), in.x() + 56, in.y(), data.humanity(), VampireRules.MAX_HUMANITY, 0xFFD8C7A0);
        ui.text(Component.translatable("gui.vtm.stains"), in.x(), in.y() + 17, Theme.MUTED);
        Draw.pips(ui.g(), in.x() + 56, in.y() + 16, data.stains(), VampireRules.MAX_STAINS, 0xFF8A3AA0);
        ui.paragraph(Component.translatable("gui.vtm.soul.note"), in.x(), in.y() + 31, in.w(), Theme.DIM);
    }

    private void renderStanding(Ui ui, Rect r, VampireData data, Hub hub) {
        Rect in = Common.card(ui, r, Component.translatable("gui.vtm.you"));
        int y = in.y();
        data.clan().ifPresent(id -> ui.stat(Component.translatable("gui.vtm.clan"), VtmRegistries.name("clan", id), in.x(), y, in.w(), Common.clanColor(id)));
        ui.stat(Component.translatable("gui.vtm.generation"), Component.literal(Common.ordinal(data.generation())), in.x(), y + 13, in.w(), Theme.TEXT);
        Membership m = Membership.of(Minecraft.getInstance().player);
        var access = Common.access();
        Component sect = m.faction().map(f -> (Component) Component.empty().append(Common.factionName(f)).append(" | ")
                .append(SectManager.rankTitle(access, f, m.rank()))).orElse(Component.translatable("gui.vtm.none"));
        ui.stat(Component.translatable("gui.vtm.sect"), sect, in.x(), y + 26, in.w(), m.faction().map(Common::factionColor).orElse(Theme.MUTED));
        ui.stat(Component.translatable("gui.vtm.experience"), Component.literal(String.valueOf(data.experience())), in.x(), y + 39, in.w(), Theme.GOLD);
        if (in.h() > 66) {
            ui.button(new Rect(in.x(), in.bottom() - 16, in.w(), 14), Component.translatable("gui.vtm.spend_xp"), Ui.Style.PRIMARY,
                    data.experience() > 0, () -> hub.show(PageId.DISCIPLINES), null);
        }
    }

    private void renderCondition(Ui ui, Rect r, VampireData data, net.minecraft.world.entity.player.Player player) {
        Rect in = Common.card(ui, r, Component.translatable("gui.vtm.condition"));
        int x = in.x();
        int y = in.y();
        int w = in.w();
        boolean any = false;
        if (player.hasEffect(ModEffects.FRENZY)) {
            x += ui.chip(Component.translatable("gui.vtm.cond.frenzy"), x, y, Theme.BAD) + 3;
            any = true;
        }
        if (data.hunger() >= 4) {
            x += ui.chip(Component.translatable("gui.vtm.cond.ravenous"), x, y, Theme.WARN) + 3;
            any = true;
        }
        if (data.bestialFeatures() > 0) {
            ui.chip(Component.translatable("gui.vtm.cond.beast", data.bestialFeatures()), Math.min(x, in.right() - 60), y, 0xFFB07AD0);
            any = true;
        }
        if (!any) {
            ui.chip(Component.translatable("gui.vtm.cond.steady"), in.x(), y, Theme.GOOD);
        }
        Power armed = PowerSelection.current(data);
        y += 18;
        ui.text(Component.translatable("gui.vtm.armed_label"), in.x(), y, Theme.MUTED);
        if (armed != null) {
            ui.clipped(Component.empty().append(armed.name()).append(" (" + armed.bloodCost() + ")"), in.x(), y + 11, w, Theme.GOLD);
        } else {
            ui.text(Component.translatable("gui.vtm.armed_none"), in.x(), y + 11, Theme.DIM);
        }
        ui.paragraph(Component.translatable("gui.vtm.power_keys", ClientModEvents.PREVIOUS_POWER.getTranslatedKeyMessage(),
                ClientModEvents.NEXT_POWER.getTranslatedKeyMessage(), ClientModEvents.USE_POWER.getTranslatedKeyMessage()),
                in.x(), y + 26, w, Theme.DIM);
    }
}

package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.network.LearnDisciplinePayload;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;

/** Learn disciplines with experience: every one of them, what the next level gives, and what it costs. */
public final class DisciplinesPage implements Page {
    private static final int ROW_H = 30;
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.DISCIPLINES;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.disciplines");
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    private static List<ResourceLocation> ids() {
        return Common.access().registry(VtmRegistries.DISCIPLINE)
                .map(r -> r.keySet().stream().sorted(Comparator.comparing(ResourceLocation::getPath)).toList()).orElse(List.of());
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        VampireData data = VampireData.of(Minecraft.getInstance().player);
        if (!data.isVampire()) {
            ui.centred(Component.translatable("gui.vtm.mortal"), area.x() + area.w() / 2, area.y() + area.h() / 2, Theme.MUTED);
            return;
        }
        ui.text(Component.translatable("gui.vtm.xp_available", data.experience()), area.x(), area.y() + 1, Theme.GOLD);
        ui.right(Component.translatable("gui.vtm.legend_clan"), area.right(), area.y() + 1, Theme.DIM);
        Draw.divider(ui.g(), area.x(), area.y() + 12, area.w());

        List<ResourceLocation> ids = ids();
        Rect view = new Rect(area.x(), area.y() + 16, area.w(), area.h() - 16);
        scroll.begin(ui, view, ids.size() * ROW_H);
        int y = scroll.top();
        for (ResourceLocation id : ids) {
            if (y + ROW_H > view.y() && y < view.bottom()) {
                row(ui, new Rect(view.x(), y, view.w() - 6, ROW_H - 2), id, data);
            }
            y += ROW_H;
        }
        scroll.end(ui);
    }

    private void row(Ui ui, Rect r, ResourceLocation id, VampireData data) {
        Discipline discipline = VtmRegistries.discipline(Common.access(), id).orElse(null);
        if (discipline == null) {
            return;
        }
        Draw.inset(ui.g(), r);
        boolean inClan = VampireRules.isInClan(Common.access(), data, id);
        int level = data.disciplineLevel(id);
        int color = Theme.readable(discipline.color());
        ui.g().fill(r.x(), r.y(), r.x() + 3, r.bottom(), color);
        Component name = VtmRegistries.name("discipline", id);
        ui.text(inClan ? Component.literal("★ ").append(name) : name, r.x() + 8, r.y() + 4, color);
        Draw.pips(ui.g(), r.x() + 8, r.y() + 17, level, VampireRules.MAX_DISCIPLINE_LEVEL, color);

        int next = level + 1;
        boolean maxed = level >= VampireRules.MAX_DISCIPLINE_LEVEL;
        List<Power> unlocks = maxed ? List.of() : Powers.at(id, next);
        int textX = r.x() + 98;
        int textW = r.w() - 98 - 74;
        if (maxed) {
            ui.text(Component.translatable("gui.vtm.mastered"), textX, r.y() + 10, Theme.GOOD);
        } else if (!unlocks.isEmpty()) {
            MutableComponent names = Component.empty();
            for (Power p : unlocks) {
                if (!names.getSiblings().isEmpty()) {
                    names.append(", ");
                }
                names.append(p.name());
            }
            ui.text(Component.translatable("gui.vtm.next_level", next), textX, r.y() + 4, Theme.DIM);
            ui.clipped(names, textX, r.y() + 16, textW, Theme.TEXT);
            Rect hover = new Rect(textX, r.y(), textW, r.h());
            MutableComponent tip = Component.empty();
            for (Power p : unlocks) {
                if (!tip.getSiblings().isEmpty()) {
                    tip.append("\n\n");
                }
                tip.append(Component.translatable("gui.vtm.unlocks", p.name())).append("\n").append(p.description());
            }
            ui.tooltip(hover, tip);
        }
        if (!maxed) {
            boolean learnable = !discipline.clanOnly() || inClan;
            int cost = VampireRules.learnCost(next, inClan);
            boolean afford = learnable && data.experience() >= cost;
            Component label = learnable ? Component.translatable("gui.vtm.learn", cost) : Component.translatable("gui.vtm.clan_only");
            ui.button(new Rect(r.right() - 70, r.y() + 7, 66, 14), label, afford ? Ui.Style.PRIMARY : Ui.Style.NORMAL, afford,
                    () -> PacketDistributor.sendToServer(new LearnDisciplinePayload(id)),
                    learnable ? (afford ? Component.translatable("gui.vtm.learn.tip", cost) : Component.translatable("gui.vtm.learn.cannot", cost))
                            : Component.translatable("gui.vtm.clan_only.tip"));
        }
    }
}

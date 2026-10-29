package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ClientModEvents;
import com.epi0.vtm.client.PowerSelection;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.PowerKind;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Every power you have, what it does, and which one is armed on the use key. */
public final class PowersPage implements Page {
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.POWERS;
    }

    @Override
    public Component hint() {
        return Component.translatable("gui.vtm.hint.powers");
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        VampireData data = VampireData.of(Minecraft.getInstance().player);
        List<Power> powers = Powers.unlocked(data);
        if (!data.isVampire() || powers.isEmpty()) {
            ui.centred(Component.translatable("gui.vtm.no_powers"), area.x() + area.w() / 2, area.y() + area.h() / 2, Theme.MUTED);
            return;
        }
        Power armed = PowerSelection.current(data);
        ui.paragraph(Component.translatable("gui.vtm.power_keys", ClientModEvents.PREVIOUS_POWER.getTranslatedKeyMessage(),
                ClientModEvents.NEXT_POWER.getTranslatedKeyMessage(), ClientModEvents.USE_POWER.getTranslatedKeyMessage()),
                area.x(), area.y() + 1, area.w(), Theme.DIM);
        Draw.divider(ui.g(), area.x(), area.y() + 12, area.w());
        Rect view = new Rect(area.x(), area.y() + 16, area.w(), area.h() - 16);

        int inner = view.w() - 14;
        int total = 0;
        int[] heights = new int[powers.size()];
        for (int i = 0; i < powers.size(); i++) {
            heights[i] = 30 + ui.measure(powers.get(i).description(), inner - 4);
            total += heights[i] + 2;
        }
        scroll.begin(ui, view, total);
        int y = scroll.top();
        for (int i = 0; i < powers.size(); i++) {
            Power power = powers.get(i);
            if (y + heights[i] > view.y() && y < view.bottom()) {
                card(ui, new Rect(view.x(), y, view.w() - 6, heights[i]), power, armed != null && armed.id().equals(power.id()));
            }
            y += heights[i] + 2;
        }
        scroll.end(ui);
    }

    private void card(Ui ui, Rect r, Power power, boolean armed) {
        com.epi0.vtm.vampire.VampireData mine = com.epi0.vtm.vampire.VampireData.of(net.minecraft.client.Minecraft.getInstance().player);
        Draw.inset(ui.g(), r);
        int color = Theme.readable(VtmRegistries.discipline(Common.access(), power.discipline()).map(Discipline::color).orElse(0xAAAAAA));
        ui.g().fill(r.x(), r.y(), r.x() + 3, r.bottom(), color);
        ui.text(power.name(), r.x() + 8, r.y() + 4, armed ? Theme.GOLD : Theme.TEXT);
        int cx = r.x() + 8;
        cx += ui.chip(VtmRegistries.name("discipline", power.discipline()), cx, r.y() + 15, color) + 3;
        if (power.kind() == PowerKind.PASSIVE) {
            ui.chip(Component.translatable("gui.vtm.passive"), cx, r.y() + 15, Theme.MUTED);
        } else {
            cx += ui.chip(Component.translatable("gui.vtm.blood_cost", com.epi0.vtm.power.PowerManager.scaledCost(mine, power)), cx, r.y() + 15, Theme.ACCENT) + 3;
            if (power.cooldownTicks() > 0) {
                ui.chip(Component.translatable("gui.vtm.cooldown", String.format("%.0f", power.cooldownTicks() * com.epi0.vtm.vampire.VampireRules.cooldownFactor(mine.generation()) / 20.0)), cx, r.y() + 15, Theme.DIM);
            }
        }
        ui.paragraph(power.description(), r.x() + 8, r.y() + 29, r.w() - 14, Theme.MUTED);
        if (power.kind() == PowerKind.ACTIVE) {
            ui.button(new Rect(r.right() - 50, r.y() + 3, 46, 13), Component.translatable(armed ? "gui.vtm.armed_short" : "gui.vtm.select"),
                    armed ? Ui.Style.TOGGLE_ON : Ui.Style.NORMAL, true, () -> PowerSelection.select(power), null);
        }
    }
}

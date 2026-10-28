package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.NemesisViews;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/** Helpers the pages share. */
final class Common {
    /** The vampire picked in the dossiers, and the one marked as a hunt target; both outlive the screen. */
    static UUID selected;
    static UUID huntTarget;

    static Optional<NemesisViews.Entry> entry(UUID id) {
        return id == null ? Optional.empty() : ClientWorld.snapshot().entries().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    static Component statusName(String status) {
        return Component.translatable("gui.vtm.nemesis.status." + status);
    }

    static RegistryAccess access() {
        return Minecraft.getInstance().level.registryAccess();
    }

    static int factionColor(ResourceLocation id) {
        return Theme.readable(VtmRegistries.faction(access(), id).map(Faction::color).orElse(0xAAAAAA));
    }

    static int clanColor(ResourceLocation id) {
        return Theme.readable(VtmRegistries.clan(access(), id).map(Clan::color).orElse(0xAAAAAA));
    }

    static Component factionName(ResourceLocation id) {
        return VtmRegistries.name("faction", id);
    }

    static Component roleTitle(ResourceLocation faction, String role) {
        return Component.translatable("role.vtm." + faction.getPath() + "." + role);
    }

    /** A named vampire's name with the epithet they have earned. */
    static Component displayName(NemesisViews.Core core) {
        MutableComponent name = Component.literal(core.name());
        core.epithet().ifPresent(e -> name.append(" ").append(Component.translatable("epithet.vtm." + e)));
        return name;
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return n + switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /** A titled box. Returns the area inside it, below the title. */
    static Rect card(Ui ui, Rect r, Component title) {
        Draw.inset(ui.g(), r);
        ui.text(title, r.x() + 6, r.y() + 5, Theme.GOLD);
        Draw.divider(ui.g(), r.x() + 5, r.y() + 15, r.w() - 10);
        return new Rect(r.x() + 6, r.y() + 20, r.w() - 12, r.h() - 24);
    }

    /** A labelled meter row: label on the left, bar to its right, value written on the bar. */
    static void meterRow(Ui ui, Rect row, Component label, float fraction, int color, String value) {
        ui.text(label, row.x(), row.y() + 1, Theme.MUTED);
        int labelW = Math.min(row.w() / 2, 84);
        Rect bar = new Rect(row.x() + labelW, row.y() + 1, row.w() - labelW, 8);
        Draw.bar(ui.g(), bar, fraction, color);
        ui.centred(Component.literal(value), bar.x() + bar.w() / 2, row.y() + 1, 0xFFFFFFFF);
    }

    private Common() {}
}

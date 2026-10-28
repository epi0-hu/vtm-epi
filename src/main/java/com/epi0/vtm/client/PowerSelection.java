package com.epi0.vtm.client;

import com.epi0.vtm.network.ActivatePowerPayload;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.PowerKind;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Client-only: which active power is currently armed on the "use" key. Not persisted. */
public final class PowerSelection {
    private static ResourceLocation selected;

    public static List<Power> usable(VampireData data) {
        return Powers.unlocked(data).stream().filter(p -> p.kind() == PowerKind.ACTIVE).toList();
    }

    public static ResourceLocation selectedId() {
        return selected;
    }

    /** The armed power, or null if none is selected or it is no longer unlocked. */
    public static Power current(VampireData data) {
        if (selected == null || !data.isVampire()) {
            return null;
        }
        return Powers.get(selected).filter(p -> data.disciplineLevel(p.discipline()) >= p.level()).orElse(null);
    }

    public static void select(Power power) {
        selected = power.id();
    }

    public static void cycle(int direction) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        List<Power> usable = usable(VampireData.of(mc.player));
        if (usable.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < usable.size(); i++) {
            if (usable.get(i).id().equals(selected)) {
                index = i;
            }
        }
        Power next = usable.get(Math.floorMod(index + direction, usable.size()));
        select(next);
        mc.player.displayClientMessage(Component.translatable("message.vtm.power_selected", next.name()), true);
    }

    public static void useSelected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Power power = current(VampireData.of(mc.player));
        if (power == null) {
            mc.player.displayClientMessage(Component.translatable("message.vtm.no_power_selected"), true);
            return;
        }
        PacketDistributor.sendToServer(new ActivatePowerPayload(power.id()));
    }

    private PowerSelection() {}
}

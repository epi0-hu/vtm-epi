package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.Optional;

/** Data-driven registries. Both are synced to clients so the GUI can read them. */
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VtmRegistries {
    public static final ResourceKey<Registry<Clan>> CLAN = ResourceKey.createRegistryKey(VtmMod.id("clan"));
    public static final ResourceKey<Registry<Faction>> FACTION = ResourceKey.createRegistryKey(VtmMod.id("faction"));
    public static final ResourceKey<Registry<Discipline>> DISCIPLINE = ResourceKey.createRegistryKey(VtmMod.id("discipline"));

    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(CLAN, Clan.CODEC, Clan.CODEC);
        event.dataPackRegistry(DISCIPLINE, Discipline.CODEC, Discipline.CODEC);
        event.dataPackRegistry(FACTION, Faction.CODEC, Faction.CODEC);
    }

    public static Optional<Clan> clan(RegistryAccess access, ResourceLocation id) {
        return access.registry(CLAN).map(r -> r.get(id));
    }

    public static Optional<Faction> faction(RegistryAccess access, ResourceLocation id) {
        return access.registry(FACTION).map(r -> r.get(id));
    }

    public static Optional<Discipline> discipline(RegistryAccess access, ResourceLocation id) {
        return access.registry(DISCIPLINE).map(r -> r.get(id));
    }

    /** {@code clan.vtm.brujah}, {@code discipline.vtm.celerity}, ... */
    public static Component name(String kind, ResourceLocation id) {
        return Component.translatable(Util.makeDescriptionId(kind, id));
    }

    public static Component text(String kind, ResourceLocation id, String suffix) {
        return Component.translatable(Util.makeDescriptionId(kind, id) + "." + suffix);
    }

    private VtmRegistries() {}
}

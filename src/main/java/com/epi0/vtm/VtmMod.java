package com.epi0.vtm;

import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.registry.ModEffects;
import com.epi0.vtm.registry.ModEntities;
import com.epi0.vtm.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(VtmMod.MOD_ID)
public class VtmMod {
    public static final String MOD_ID = "vtm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VtmMod(IEventBus modBus, ModContainer container) {
        ModAttachments.ATTACHMENTS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, VtmConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

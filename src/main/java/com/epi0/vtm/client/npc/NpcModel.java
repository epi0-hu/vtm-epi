package com.epi0.vtm.client.npc;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.VtmNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/** One shared humanoid rig and animation set; only the skin changes per NPC. */
public class NpcModel extends GeoModel<VtmNpc> {
    private static final ResourceLocation MODEL = VtmMod.id("geo/entity/npc.geo.json");
    private static final ResourceLocation ANIMATIONS = VtmMod.id("animations/entity/npc.animation.json");
    private static final ResourceLocation FALLBACK_SKIN = VtmMod.id("textures/entity/npc/civilian_0.png");

    private final Map<ResourceLocation, Boolean> exists = new HashMap<>();

    @Override
    public ResourceLocation getModelResource(VtmNpc npc) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(VtmNpc npc) {
        ResourceLocation skin = VtmMod.id("textures/entity/npc/" + npc.textureName() + ".png");
        boolean present = exists.computeIfAbsent(skin,
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
        return present ? skin : FALLBACK_SKIN;
    }

    @Override
    public ResourceLocation getAnimationResource(VtmNpc npc) {
        return ANIMATIONS;
    }
}

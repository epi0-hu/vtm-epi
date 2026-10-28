package com.epi0.vtm.client.npc;

import com.epi0.vtm.npc.VtmNpc;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NpcRenderer extends GeoEntityRenderer<VtmNpc> {
    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel());
        this.shadowRadius = 0.4F;
    }
}

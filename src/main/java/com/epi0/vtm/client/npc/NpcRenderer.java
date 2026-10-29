package com.epi0.vtm.client.npc;

import com.epi0.vtm.npc.VtmNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class NpcRenderer extends GeoEntityRenderer<VtmNpc> {
    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel());
        this.shadowRadius = 0.4F;
        // What an NPC holds is in their right hand: a watchman's sword, a hunter's blade.
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, VtmNpc animatable) {
                return bone.getName().equals("right_arm") ? animatable.getMainHandItem() : null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, VtmNpc animatable) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, VtmNpc animatable,
                                              MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
                poseStack.pushPose();
                poseStack.translate(0.0D, -0.6D, 0.0D); // from the shoulder down to the hand
                poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
                poseStack.popPose();
            }
        });
    }
}

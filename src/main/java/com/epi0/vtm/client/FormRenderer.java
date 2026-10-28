package com.epi0.vtm.client;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.power.Form;
import com.epi0.vtm.power.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Draws a shapechanged player as the animal they became. A throwaway, never-spawned entity of the
 * animal's type is posed from the player each frame and rendered in their place. Mist draws nothing.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID, value = Dist.CLIENT)
public final class FormRenderer {
    private record Puppet(Form form, LivingEntity entity, int lastTick) {}

    private static final Map<UUID, Puppet> PUPPETS = new HashMap<>();

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Optional<Form> form = Forms.of(player);
        if (form.isEmpty()) {
            PUPPETS.remove(player.getUUID());
            return;
        }
        event.setCanceled(true);
        if (form.get() == Form.MIST) {
            return;
        }
        LivingEntity puppet = puppetFor(player, form.get());
        if (puppet == null) {
            return;
        }
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(puppet);
        renderer.render(puppet, player.getYRot(), event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }

    private static LivingEntity puppetFor(Player player, Form form) {
        Minecraft mc = Minecraft.getInstance();
        Puppet existing = PUPPETS.get(player.getUUID());
        LivingEntity entity;
        int lastTick;
        if (existing != null && existing.form == form && existing.entity.level() == mc.level) {
            entity = existing.entity;
            lastTick = existing.lastTick;
        } else {
            Entity created = (form == Form.WOLF ? EntityType.WOLF : EntityType.BAT).create(mc.level);
            if (!(created instanceof LivingEntity living)) {
                return null;
            }
            entity = living;
            lastTick = player.tickCount - 1;
        }

        entity.setPos(player.getX(), player.getY(), player.getZ());
        entity.xo = player.xo;
        entity.yo = player.yo;
        entity.zo = player.zo;
        entity.xOld = player.xOld;
        entity.yOld = player.yOld;
        entity.zOld = player.zOld;
        entity.setYRot(player.getYRot());
        entity.yRotO = player.yRotO;
        entity.setXRot(player.getXRot());
        entity.xRotO = player.xRotO;
        entity.yBodyRot = player.yBodyRot;
        entity.yBodyRotO = player.yBodyRotO;
        entity.yHeadRot = player.yHeadRot;
        entity.yHeadRotO = player.yHeadRotO;
        entity.tickCount = player.tickCount;
        entity.hurtTime = player.hurtTime;
        entity.hurtDuration = player.hurtDuration;
        entity.deathTime = player.deathTime;
        entity.setPose(Pose.STANDING);
        entity.setInvisible(player.isInvisible());
        entity.setCustomName(player != mc.player ? player.getDisplayName() : null);
        entity.setCustomNameVisible(player != mc.player);
        if (player.tickCount != lastTick) {
            entity.walkAnimation.update(player.walkAnimation.speed(), 1.0F);
        }
        PUPPETS.put(player.getUUID(), new Puppet(form, entity, player.tickCount));
        return entity;
    }

    /** No floating human hand while you are an animal or a cloud. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && Forms.of(player).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PUPPETS.clear();
    }

    private FormRenderer() {}
}

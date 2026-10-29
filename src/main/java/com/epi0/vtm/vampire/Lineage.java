package com.epi0.vtm.vampire;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.registry.ModAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The blood a soul is born with. Rolled once, when a player first joins the server, and kept for good: if the player is
 * ever Embraced, this is the generation they are Embraced at (a thin-blood is always a long way down the line).
 *
 * @param generation 0 until rolled
 */
public record Lineage(int generation) {
    public static final Lineage UNROLLED = new Lineage(0);
    public static final Codec<Lineage> CODEC = Codec.INT.xmap(Lineage::new, Lineage::generation);
    public static final ResourceLocation THIN_BLOOD = VtmMod.id("thin_blood");

    public static Lineage of(Player player) {
        return player.getData(ModAttachments.LINEAGE);
    }

    public boolean rolled() {
        return generation > 0;
    }

    /** The generation a player Embraced into {@code clan} takes. */
    public static int embracedAs(Player player, ResourceLocation clan) {
        Lineage lineage = of(player);
        int born = lineage.rolled() ? lineage.generation() : VampireRules.DEFAULT_GENERATION;
        return clan.equals(THIN_BLOOD) ? Math.max(14, born) : born;
    }

    /** Rolls a player's generation if they have not got one yet. Returns whether it just did. */
    public static boolean roll(ServerPlayer player) {
        if (of(player).rolled()) {
            return false;
        }
        int generation = VampireRules.rollGeneration(player.getRandom());
        player.setData(ModAttachments.LINEAGE, new Lineage(generation));
        // Someone who was a vampire before the roll existed keeps the blood they were given, unless it was the common default.
        VampireData data = VampireData.of(player);
        if (data.isVampire() && data.generation() == VampireRules.DEFAULT_GENERATION) {
            VampireManager.set(player, data.withGeneration(Math.min(generation, VampireRules.MAX_GENERATION)));
        }
        return true;
    }

    /**
     * An elder of {@code elderGeneration} shares their blood with the player: their generation falls by one, but never to
     * the elder's own or below. Returns the new generation, or 0 if nothing changed.
     */
    public static int ascend(ServerPlayer player, int elderGeneration) {
        VampireData data = VampireData.of(player);
        if (!data.isVampire() || data.generation() < elderGeneration + 2) {
            return 0;
        }
        int next = data.generation() - 1;
        VampireManager.set(player, VampireData.of(player).withGeneration(next));
        player.setData(ModAttachments.LINEAGE, new Lineage(next));
        player.displayClientMessage(Component.translatable("message.vtm.lineage.ascended", VampireRules.ordinal(next)), false);
        return next;
    }

    @EventBusSubscriber(modid = VtmMod.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
            // (the test server's mock players must stay thirteenth)
            if (event.getEntity() instanceof ServerPlayer player && !(player.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
                    && roll(player) && of(player).generation() <= 3) {
                player.displayClientMessage(Component.translatable("message.vtm.lineage.ancient", VampireRules.ordinal(of(player).generation())), false);
            }
        }
    }
}

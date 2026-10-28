package com.epi0.vtm.vampire;

import com.epi0.vtm.registry.Bane;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies the per-second mechanical effects of a clan's {@link Bane}. Feeding and frenzy banes live in their own classes. */
public final class Banes {
    private static final int SECOND = 20;
    private static final float DRAIN_PER_SECOND = 0.05F;
    private static final double DOOMED_RADIUS = 32.0;
    private static final int MAX_UNEASE_GOSSIP = 200;

    private record Anchor(Vec3 pos, long since) {}

    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();

    public static Bane of(ServerPlayer player) {
        return VampireData.of(player).clan()
                .flatMap(id -> VtmRegistries.clan(player.serverLevel().registryAccess(), id))
                .map(Clan::bane)
                .orElse(Bane.NONE);
    }

    /** @return the (possibly drained) data. */
    public static VampireData tick(ServerPlayer player, VampireData data, Bane bane) {
        return tick(player, data, bane, player.level().getGameTime());
    }

    public static VampireData tick(ServerPlayer player, VampireData data, Bane bane, long now) {
        data = doomed(player, data, bane, now);
        data = grounded(player, data, bane);
        if (bane.derangement()) {
            derangement(player);
        }
        if (bane.darkFatigue()) {
            darkFatigue(player);
        }
        int unease = bane.unease() + (bane.bestialFeatures() ? data.bestialFeatures() : 0);
        if (unease > 0 && player.tickCount % (SECOND * 10) == 0) {
            unsettleVillagers(player, unease);
        }
        return data;
    }

    /** Ravnos: a wanderer's curse. Linger near one spot for too long and the blood sours. */
    private static VampireData doomed(ServerPlayer player, VampireData data, Bane bane, long now) {
        if (bane.doomedMinutes() <= 0) {
            return data;
        }
        Anchor anchor = ANCHORS.get(player.getUUID());
        if (anchor == null || anchor.pos.distanceTo(player.position()) > DOOMED_RADIUS) {
            ANCHORS.put(player.getUUID(), new Anchor(player.position(), now));
            return data;
        }
        if (now - anchor.since < (long) bane.doomedMinutes() * 60 * SECOND) {
            return data;
        }
        if (player.tickCount % (SECOND * 30) == 0) {
            player.displayClientMessage(Component.translatable("message.vtm.doomed"), true);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SECOND * 6, 0, true, false));
        }
        return data.withBlood(Math.max(0F, data.blood() - DRAIN_PER_SECOND));
    }

    /** Tzimisce: bound to the soil of their haven (here: their respawn point). Straying far saps them. */
    private static VampireData grounded(ServerPlayer player, VampireData data, Bane bane) {
        if (bane.groundedBlocks() <= 0) {
            return data;
        }
        ServerLevel level = player.serverLevel();
        boolean away = !level.dimension().equals(player.getRespawnDimension());
        if (!away) {
            BlockPos home = player.getRespawnPosition() != null ? player.getRespawnPosition() : level.getSharedSpawnPos();
            double dx = player.getX() - home.getX();
            double dz = player.getZ() - home.getZ();
            away = dx * dx + dz * dz > (double) bane.groundedBlocks() * bane.groundedBlocks();
        }
        if (!away) {
            return data;
        }
        if (player.tickCount % (SECOND * 60) == 0) {
            player.displayClientMessage(Component.translatable("message.vtm.ungrounded"), true);
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SECOND * 3, 0, true, false));
        return data.withBlood(Math.max(0F, data.blood() - DRAIN_PER_SECOND));
    }

    /** Malkavian: now and then the world slips. Harmless, but you cannot trust what you hear. */
    private static void derangement(ServerPlayer player) {
        if (player.tickCount % (SECOND * 60) != 0 || player.getRandom().nextFloat() >= 0.5F) {
            return;
        }
        switch (player.getRandom().nextInt(4)) {
            case 0 -> player.playNotifySound(SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1F, 1F);
            case 1 -> player.playNotifySound(SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1F, 0.8F);
            case 2 -> player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SECOND * 6, 0, true, false));
            default -> player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1F, 1F);
        }
        player.displayClientMessage(Component.translatable("message.vtm.episode"), true);
    }

    /** Toreador: no beauty, no focus. Lightless underground places dull the senses. */
    private static void darkFatigue(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (level.getBrightness(LightLayer.SKY, pos) == 0 && level.getBrightness(LightLayer.BLOCK, pos) <= 4) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, SECOND * 5, 0, true, false));
            if (player.tickCount % (SECOND * 60) == 0) {
                player.displayClientMessage(Component.translatable("message.vtm.squalor"), true);
            }
        }
    }

    /** Lasombra, Nosferatu, Gangrel features: villagers keep a share of distrust towards you. */
    private static void unsettleVillagers(ServerPlayer player, int unease) {
        int target = Math.min(MAX_UNEASE_GOSSIP, unease * 10);
        for (Villager villager : player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16))) {
            int current = -villager.getGossips().getReputation(player.getUUID(), t -> t == GossipType.MINOR_NEGATIVE);
            if (current < target) {
                villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, target - current);
            }
        }
    }

    public static void forget(UUID player) {
        ANCHORS.remove(player);
    }

    private Banes() {}
}

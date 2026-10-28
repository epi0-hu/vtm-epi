package com.epi0.vtm.nemesis;

import com.epi0.vtm.VtmConfig;
import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The hooks that tie named vampires into combat, the world clock and the entity lifecycle. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class NemesisEvents {
    private static final double LAIR_RANGE = 56.0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        // The test server runs the simulation by hand, step by step, so that tests control what happens.
        if (!VtmConfig.NEMESIS_ENABLED.get() || server.getPlayerList().getPlayerCount() == 0
                || server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        int tick = server.getTickCount();
        if (tick % VtmConfig.NEMESIS_STEP_TICKS.get() == 0) {
            NemesisSimulator.step(server, server.overworld().random);
        }
        if (tick % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                NemesisManager.coterieTick(player);
                manifestLairs(player);
            }
        }
    }

    /** A vampire whose lair the player approaches is home, and asleep or awake as the hour dictates. */
    public static void manifestLairs(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        NemesisData data = NemesisManager.data(level.getServer());
        for (NemesisRecord r : data.all()) {
            if (r.status != Status.ACTIVE || r.lair == null || r.lairDimension != level.dimension()
                    || r.lair.distSqr(player.blockPosition()) > LAIR_RANGE * LAIR_RANGE || !level.isLoaded(r.lair)
                    || NemesisManager.manifested(level.getServer(), r)) {
                continue;
            }
            NemesisManager.manifest(level, r, Vec3.atBottomCenterOf(r.lair));
        }
    }

    // ---------------------------------------------------------------- combat

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        float amount = event.getAmount();
        Level level = event.getEntity().level();
        if (level.isClientSide) {
            return;
        }
        if (event.getEntity() instanceof VampireNpc npc) {
            NemesisRecord r = NemesisManager.recordOf(npc);
            if (r != null) {
                DamageKind kind = DamageKind.of(event.getSource(), level);
                float factor = 1F - 0.1F * r.resistance(kind);
                switch (kind) {
                    case FIRE, SUN -> {
                        if (r.has(Trait.FLAMMABLE)) {
                            factor *= 1.8F;
                            NemesisManager.revealNearby(npc, r, Trait.FLAMMABLE);
                        } else if (r.has(Trait.FIRE_HARDENED)) {
                            factor *= 0.4F;
                            NemesisManager.revealNearby(npc, r, Trait.FIRE_HARDENED);
                        }
                    }
                    case PROJECTILE -> {
                        if (r.has(Trait.BRITTLE)) {
                            factor *= 1.6F;
                            NemesisManager.revealNearby(npc, r, Trait.BRITTLE);
                        } else if (r.has(Trait.DEFLECTOR)) {
                            factor *= 0.4F;
                            NemesisManager.revealNearby(npc, r, Trait.DEFLECTOR);
                        }
                    }
                    default -> { }
                }
                if (r.has(Trait.RECKLESS)) {
                    factor *= 1.15F;
                }
                amount *= factor;
            }
        }
        if (event.getSource().getEntity() instanceof VampireNpc attacker && attacker.nemesisId() != null) {
            NemesisRecord r = NemesisManager.recordOf(attacker);
            if (r != null && r.has(Trait.HUNGER_BOUND) && attacker.blood() < 4F) {
                amount *= 0.6F;
                NemesisManager.revealNearby(attacker, r, Trait.HUNGER_BOUND);
            }
        }
        event.setAmount(amount);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getSource().getEntity() instanceof VampireNpc attacker && attacker.nemesisId() != null) {
            NemesisRecord r = NemesisManager.recordOf(attacker);
            if (r != null && r.has(Trait.BLOODTHIRSTY) && event.getNewDamage() > 0) {
                attacker.heal(event.getNewDamage() * 0.3F);
                NemesisManager.revealNearby(attacker, r, Trait.BLOODTHIRSTY);
            }
        }
        if (event.getEntity() instanceof VampireNpc npc && npc.isAlive() && !npc.isYielding()) {
            NemesisRecord r = NemesisManager.recordOf(npc);
            if (r != null && npc.boundOwner() == null) {
                if (event.getSource().getEntity() instanceof ServerPlayer player) {
                    Feud feud = r.feud(player.getUUID());
                    feud.lastEncounter = npc.level().getGameTime();
                    Intel.meet(player, r);
                }
                if (r.has(Trait.COWARD) && npc.getHealth() < npc.getMaxHealth() * 0.35F) {
                    NemesisManager.revealNearby(npc, r, Trait.COWARD);
                    NemesisManager.escape(npc, "coward");
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof VampireNpc npc && npc.nemesisId() != null) {
            NemesisRecord r = NemesisManager.recordOf(npc);
            if (r != null && NemesisManager.defeat(npc, r, event.getSource())) {
                event.setCanceled(true);
            }
        }
        if (event.getEntity() instanceof ServerPlayer player && VampireData.of(player).isVampire()
                && event.getSource().getEntity() instanceof VampireNpc killer && killer.nemesisId() != null) {
            NemesisRecord r = NemesisManager.recordOf(killer);
            if (r != null) {
                NemesisManager.playerKilledBy(player, killer, r);
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /** One body per named vampire: stale or duplicate bodies (from unloaded chunks) are removed as they load. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof VampireNpc npc) || npc.nemesisId() == null
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        NemesisRecord r = NemesisManager.data(level.getServer()).get(npc.nemesisId());
        if (r == null || r.status == Status.DESTROYED || r.status == Status.TORPOR
                || (r.status == Status.BOUND && (npc.boundOwner() == null || !npc.boundOwner().equals(r.boundTo)))) {
            event.setCanceled(true);
            return;
        }
        if (r.entity != null && !r.entity.equals(npc.getUUID())) {
            Entity other = level.getEntity(r.entity);
            if (other != null && other.isAlive()) {
                event.setCanceled(true);
                return;
            }
        }
        r.entity = npc.getUUID();
    }

    private NemesisEvents() {}
}

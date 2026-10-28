package com.epi0.vtm.power;

import com.epi0.vtm.VtmMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mind-affecting state shared by Dominate, Presence, Obfuscate and friends:
 * <ul>
 *   <li><b>calm</b>: a mob will not pick any player as a target for a while;</li>
 *   <li><b>hidden</b>: no mob will pick this player as a target for a while.</li>
 * </ul>
 * Enforced by rewriting target changes, so it holds against vanilla's retargeting goals.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class MobControl {
    private static final Map<UUID, Long> CALMED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HIDDEN = new ConcurrentHashMap<>();

    public static void calm(Mob mob, int ticks) {
        CALMED.put(mob.getUUID(), mob.level().getGameTime() + ticks);
        if (mob.getTarget() instanceof ServerPlayer) {
            mob.setTarget(null);
        }
        mob.setLastHurtByMob(null);
    }

    public static void hide(ServerPlayer player, int ticks) {
        HIDDEN.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    /** Make every mob currently hunting {@code player} lose interest. */
    public static void dropAggro(ServerPlayer player, int radius, int calmTicks) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                m -> m.getTarget() == player)) {
            calm(mob, calmTicks);
        }
    }

    public static boolean isCalm(Mob mob) {
        return active(CALMED, mob.getUUID(), mob.level().getGameTime());
    }

    public static boolean isHidden(ServerPlayer player) {
        return active(HIDDEN, player.getUUID(), player.level().getGameTime());
    }

    private static boolean active(Map<UUID, Long> map, UUID id, long now) {
        Long expiry = map.get(id);
        if (expiry == null) {
            return false;
        }
        if (expiry <= now) {
            map.remove(id);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next instanceof ServerPlayer player && event.getEntity() instanceof Mob mob
                && (isHidden(player) || isCalm(mob) || Forms.isIn(player, Form.MIST))) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    private MobControl() {}
}

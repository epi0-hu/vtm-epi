package com.epi0.vtm.sect;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.network.SyncMembershipPayload;
import com.epi0.vtm.network.VtmNetwork;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

/**
 * The politics of the night. Joining, leaving and promotion are validated here; standing moves with deeds: killing
 * the sect's enemies earns it, killing its members costs it, and Camarilla members are punished for breaking the
 * Masquerade. Standing is kept per faction, so a player who wronged a sect stays an outcast to it even after leaving.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class SectManager {
    /** At or below this standing a faction treats the player as an enemy, and will not take them in. */
    public static final int OUTCAST = -30;
    public static final int LEAVE_PENALTY = 20;
    public static final int KILL_ENEMY = 5;
    public static final int KILL_MEMBER = -15;
    public static final int KILL_RESENTMENT = -10;
    public static final int MASQUERADE_BREACH = -3;

    public static void set(ServerPlayer player, Membership membership) {
        player.setData(ModAttachments.MEMBERSHIP, membership);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        VtmNetwork.send(player, new SyncMembershipPayload(Membership.of(player)));
    }

    private static Optional<Faction> faction(RegistryAccess access, ResourceLocation id) {
        return VtmRegistries.faction(access, id);
    }

    public static boolean canJoin(ServerPlayer player, ResourceLocation faction) {
        Membership m = Membership.of(player);
        return VampireData.of(player).isVampire() && m.faction().isEmpty() && m.standingWith(faction) > OUTCAST
                && faction(player.level().registryAccess(), faction).map(Faction::joinable).orElse(false);
    }

    public static boolean join(ServerPlayer player, ResourceLocation faction) {
        if (!canJoin(player, faction)) {
            return false;
        }
        Membership m = Membership.of(player);
        set(player, m.withFaction(Optional.of(faction), 0).withStanding(faction, Math.max(0, m.standingWith(faction))));
        player.displayClientMessage(Component.translatable("message.vtm.sect.joined", VtmRegistries.name("faction", faction)), false);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        Membership m = Membership.of(player);
        if (m.faction().isEmpty()) {
            return false;
        }
        ResourceLocation left = m.faction().get();
        set(player, m.withStanding(left, m.standingWith(left) - LEAVE_PENALTY).withFaction(Optional.empty(), 0));
        player.displayClientMessage(Component.translatable("message.vtm.sect.left", VtmRegistries.name("faction", left)), false);
        return true;
    }

    /** Whether the player holds enough standing for the next rank (not whether anyone is there to grant it). */
    public static boolean canPromote(Membership m, RegistryAccess access) {
        return m.faction().flatMap(id -> faction(access, id))
                .map(f -> m.rank() + 1 < f.rankCount() && m.standingWith(m.faction().get()) >= f.standingFor(m.rank() + 1))
                .orElse(false);
    }

    public static boolean promote(ServerPlayer player) {
        Membership m = Membership.of(player);
        if (!canPromote(m, player.level().registryAccess())) {
            return false;
        }
        set(player, m.withRank(m.rank() + 1));
        player.displayClientMessage(Component.translatable("message.vtm.sect.promoted",
                rankTitle(player.level().registryAccess(), m.faction().get(), m.rank() + 1)), false);
        return true;
    }

    /** Sets the rank directly (commands). Clamped to the faction's ranks. */
    public static void setRank(ServerPlayer player, int rank) {
        Membership m = Membership.of(player);
        m.faction().flatMap(id -> faction(player.level().registryAccess(), id))
                .ifPresent(f -> set(player, m.withRank(Math.max(0, Math.min(rank, f.rankCount() - 1)))));
    }

    public static void adjustStanding(ServerPlayer player, ResourceLocation faction, int delta) {
        if (delta == 0) {
            return;
        }
        Membership m = Membership.of(player);
        int before = m.standingWith(faction);
        Membership next = m.withStanding(faction, before + delta);
        set(player, next);
        int after = next.standingWith(faction);
        if (after != before) {
            player.displayClientMessage(Component.translatable(after > before ? "message.vtm.sect.standing_up" : "message.vtm.sect.standing_down",
                    VtmRegistries.name("faction", faction), after), true);
        }
    }

    public static Component rankTitle(RegistryAccess access, ResourceLocation faction, int rank) {
        int clamped = faction(access, faction).map(f -> Math.max(0, Math.min(rank, f.rankCount() - 1))).orElse(0);
        return VtmRegistries.text("faction", faction, "rank." + clamped);
    }

    // ---------------------------------------------------------------- deeds

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity() instanceof VampireNpc victim && event.getSource().getEntity() instanceof ServerPlayer player
                && VampireData.of(player).isVampire()) {
            killed(player, victim);
        }
    }

    public static void killed(ServerPlayer player, VampireNpc victim) {
        ResourceLocation victimSect = victim.factionId();
        Membership m = Membership.of(player);
        if (m.faction().isPresent()) {
            ResourceLocation mine = m.faction().get();
            Optional<Faction> def = faction(player.level().registryAccess(), mine);
            Faction.Relation relation = def.map(f -> f.relationTo(mine, victimSect)).orElse(Faction.Relation.NEUTRAL);
            switch (relation) {
                case ENEMY -> adjustStanding(player, mine, KILL_ENEMY);
                case ALLY -> {
                    adjustStanding(player, mine, KILL_MEMBER); // already the whole price of killing your own
                    return;
                }
                default -> { }
            }
        }
        adjustStanding(player, victimSect, KILL_RESENTMENT);
    }

    /** Mortals saw a vampire feed: a Masquerade faction does not forgive carelessness. */
    public static void masqueradeBreach(ServerPlayer player) {
        Membership m = Membership.of(player);
        m.faction().filter(id -> com.epi0.vtm.court.Court.masquerade(player.getServer(), player.level().registryAccess(), id))
                .ifPresent(id -> adjustStanding(player, id, MASQUERADE_BREACH));
    }

    private SectManager() {}
}

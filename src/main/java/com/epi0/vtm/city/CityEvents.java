package com.epi0.vtm.city;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.network.chat.Component;
import com.epi0.vtm.network.CityNoticePayload;
import com.epi0.vtm.network.VtmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ties city life to the server clock, and tells players when they cross a city's border. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class CityEvents {
    public static final int LIFE_INTERVAL = 100;
    private static final Map<UUID, UUID> INSIDE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HEAT_SENT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        // The test server drives city life by hand.
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        int tick = server.getTickCount();
        if (tick % LIFE_INTERVAL == 0) {
            CityLife.tick(server);
        }
        if (tick % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                announce(player);
            }
        }
    }

    /** Tells the player which city they have just entered or left, and keeps their heat there current. */
    public static void announce(ServerPlayer player) {
        City city = CityLife.at(player);
        UUID now = city == null ? null : city.id;
        UUID before = INSIDE.get(player.getUUID());
        boolean changed = !java.util.Objects.equals(now, before);
        if (city != null) {
            int heat = city.heatOf(player.getUUID());
            int exposure = city.exposureOf(player.getUUID());
            int summary = heat * 1000 + exposure; // (either changing is worth telling the client about)
            if (changed || !java.util.Objects.equals(HEAT_SENT.put(player.getUUID(), summary), summary)) {
                VtmNetwork.send(player, new CityNoticePayload(java.util.Optional.of(city.name), city.owner, heat, exposure));
            }
        } else if (changed) {
            HEAT_SENT.remove(player.getUUID());
            VtmNetwork.send(player, new CityNoticePayload(java.util.Optional.empty(), VtmMod.id("none"), 0, 0));
        }
        if (!changed) {
            return;
        }
        if (now == null) {
            INSIDE.remove(player.getUUID());
        } else {
            INSIDE.put(player.getUUID(), now);
            Territory.discover(player, city);
            if (player.connection == null || !player.connection.hasChannel(CityNoticePayload.TYPE)) {
                player.displayClientMessage(Component.translatable("message.vtm.city.enter", city.name,
                        VtmRegistries.name("faction", city.owner)), true);
            }
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity() instanceof CivilianNpc victim && victim.cityId() != null && victim.job() != com.epi0.vtm.npc.Job.HUNTER
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            City city = victim.city();
            if (city != null) {
                CityLife.crime(killer, city, 30);
                Hunters.expose(killer, city, 10);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
        HEAT_SENT.remove(event.getEntity().getUUID());
    }

    private CityEvents() {}
}

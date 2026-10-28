package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.vampire.VampireManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VtmNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // On the client ctx.player() is the local player, so no client-only classes are needed here.
        registrar.playToClient(SyncVampirePayload.TYPE, SyncVampirePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.player().setData(ModAttachments.VAMPIRE, payload.data()));

        registrar.playToServer(LearnDisciplinePayload.TYPE, LearnDisciplinePayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        VampireManager.tryLearnDiscipline(player, payload.discipline());
                    }
                });
    }

    private VtmNetwork() {}
}

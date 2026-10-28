package com.epi0.vtm.network;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.registry.ModAttachments;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.vampire.VampireManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
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

        registrar.playToClient(SyncMembershipPayload.TYPE, SyncMembershipPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.player().setData(ModAttachments.MEMBERSHIP, payload.membership()));

        registrar.playToServer(LearnDisciplinePayload.TYPE, LearnDisciplinePayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        VampireManager.tryLearnDiscipline(player, payload.discipline());
                    }
                });

        // Lambda (not a method reference) so the client-only class is only resolved when a client runs it.
        registrar.playToClient(SenseLinkPayload.TYPE, SenseLinkPayload.STREAM_CODEC,
                (payload, ctx) -> com.epi0.vtm.client.SenseLinkClient.apply(payload.entityId()));

        registrar.playToServer(ActivatePowerPayload.TYPE, ActivatePowerPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        PowerManager.activate(player, payload.power());
                    }
                });

        registrar.playToClient(DialogueNodePayload.TYPE, DialogueNodePayload.STREAM_CODEC,
                (payload, ctx) -> com.epi0.vtm.client.DialogueScreen.receive(payload));

        registrar.playToServer(DialogueChoicePayload.TYPE, DialogueChoicePayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        com.epi0.vtm.dialogue.DialogueManager.choose(player, payload.option());
                    }
                });
    }

    /** Sends only to connections that negotiated our channels (not e.g. test fakes). */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private VtmNetwork() {}
}

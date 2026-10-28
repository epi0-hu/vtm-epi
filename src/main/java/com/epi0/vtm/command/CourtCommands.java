package com.epi0.vtm.command;

import com.epi0.vtm.court.Court;
import com.epi0.vtm.court.CourtData;
import com.epi0.vtm.court.Reign;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * <pre>
 * /vtm court claim &lt;player&gt;                      (if they qualify)
 * /vtm court crown &lt;player&gt; &lt;faction&gt;            (ops: seat them on that throne, whatever stands in the way)
 * /vtm court abdicate &lt;player&gt;
 * /vtm court influence &lt;player&gt; &lt;amount&gt;
 * /vtm court petition &lt;player&gt;                   (raise a petition now)
 * </pre>
 */
final class CourtCommands {

    private static Reign reign(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        Reign reign = CourtData.get(ctx.getSource().getServer()).reignOf(player.getUUID());
        if (reign == null) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.court.none")).create();
        }
        return reign;
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("court")
                .then(Commands.literal("claim").then(Commands.argument("player", EntityArgument.player()).executes(ctx ->
                        Court.claim(EntityArgument.getPlayer(ctx, "player")) ? 1 : 0)))
                .then(Commands.literal("crown").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("faction", ResourceLocationArgument.id()).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            ResourceLocation faction = ResourceLocationArgument.getId(ctx, "faction");
                            if ("minecraft".equals(faction.getNamespace())) {
                                faction = com.epi0.vtm.VtmMod.id(faction.getPath());
                            }
                            var server = ctx.getSource().getServer();
                            // Clear the way: whoever sits there is gone, and the player belongs to the sect.
                            for (var leader : NemesisManager.data(server).inRole(faction, com.epi0.vtm.nemesis.Role.LEADER)) {
                                leader.role = com.epi0.vtm.nemesis.Role.AGENT;
                            }
                            CourtData.get(server).remove(faction);
                            Membership m = Membership.of(player);
                            if (!m.isMemberOf(faction)) {
                                SectManager.set(player, m.withFaction(java.util.Optional.of(faction), 1));
                            }
                            SectManager.set(player, Membership.of(player).withRank(Math.max(1, Membership.of(player).rank()))
                                    .withStanding(faction, Math.max(Court.CLAIM_STANDING, Membership.of(player).standingWith(faction))));
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.court.done", player.getDisplayName()), true);
                            return Court.claim(player) ? 1 : 0;
                        }))))
                .then(Commands.literal("abdicate").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    Court.abdicate(EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })))
                .then(Commands.literal("influence").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-Reign.MAX_INFLUENCE, Reign.MAX_INFLUENCE)).executes(ctx -> {
                            Reign reign = reign(ctx, EntityArgument.getPlayer(ctx, "player"));
                            reign.gain(IntegerArgumentType.getInteger(ctx, "amount"));
                            CourtData.get(ctx.getSource().getServer()).setDirty();
                            return reign.influence;
                        }))))
                .then(Commands.literal("petition").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    Reign reign = reign(ctx, EntityArgument.getPlayer(ctx, "player"));
                    var server = ctx.getSource().getServer();
                    return Court.spawnPetition(server, NemesisManager.data(server), reign, server.overworld().getGameTime(), server.overworld().random).isPresent() ? 1 : 0;
                })));
    }

    private CourtCommands() {}
}

package com.epi0.vtm.command;

import com.epi0.vtm.nemesis.Intel;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.NemesisSimulator;
import com.epi0.vtm.nemesis.Status;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * <pre>
 * /vtm nemesis list
 * /vtm nemesis step [count]
 * /vtm nemesis spawn &lt;name&gt;                          (appears where you stand)
 * /vtm nemesis ambush &lt;player&gt; &lt;name&gt;
 * /vtm nemesis feud &lt;player&gt; &lt;name&gt; &lt;enmity&gt;
 * /vtm nemesis reveal &lt;player&gt; &lt;name&gt;               (the player learns everything about them)
 * /vtm nemesis destroy &lt;name&gt;
 * </pre>
 */
final class NemesisCommands {

    private static NemesisRecord find(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        return NemesisManager.data(ctx.getSource().getServer()).all().stream()
                .filter(r -> r.name.equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("commands.vtm.nemesis.unknown", name)).create());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("name", StringArgumentType.string()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                NemesisManager.data(ctx.getSource().getServer()).all().stream().map(r -> "\"" + r.name + "\""), builder));
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("nemesis")
                .then(Commands.literal("list").executes(ctx -> {
                    NemesisData data = NemesisManager.data(ctx.getSource().getServer());
                    List<NemesisRecord> all = data.all().stream().sorted(Comparator
                            .comparing((NemesisRecord r) -> r.faction.toString()).thenComparing(r -> r.role).thenComparing(r -> -r.level)).toList();
                    for (NemesisRecord r : all) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.nemesis.line", r.name,
                                r.faction.getPath(), r.role.id(), r.level, r.status.id()), false);
                    }
                    return all.size();
                }))
                .then(Commands.literal("step")
                        .executes(ctx -> step(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2000))
                                .executes(ctx -> step(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("spawn").then(name().executes(ctx -> {
                    NemesisRecord r = find(ctx);
                    if (r.status != Status.ACTIVE) {
                        throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.nemesis.unknown", r.name)).create();
                    }
                    NemesisManager.manifest(ctx.getSource().getLevel(), r, ctx.getSource().getPosition());
                    return done(ctx, r.name);
                })))
                .then(Commands.literal("ambush").then(Commands.argument("player", EntityArgument.player()).then(name().executes(ctx -> {
                    NemesisRecord r = find(ctx);
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    return NemesisManager.ambush(player, r) ? done(ctx, r.name) : 0;
                }))))
                .then(Commands.literal("feud").then(Commands.argument("player", EntityArgument.player()).then(name()
                        .then(Commands.argument("enmity", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            NemesisRecord r = find(ctx);
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            r.feud(player.getUUID()).enmity = IntegerArgumentType.getInteger(ctx, "enmity");
                            NemesisManager.data(ctx.getSource().getServer()).setDirty();
                            return done(ctx, r.name);
                        })))))
                .then(Commands.literal("reveal").then(Commands.argument("player", EntityArgument.player()).then(name().executes(ctx -> {
                    NemesisRecord r = find(ctx);
                    Intel.knowFully(EntityArgument.getPlayer(ctx, "player"), r);
                    return done(ctx, r.name);
                }))))
                .then(Commands.literal("destroy").then(name().executes(ctx -> {
                    NemesisRecord r = find(ctx);
                    var body = NemesisManager.entityOf(ctx.getSource().getServer(), r);
                    NemesisManager.destroy(ctx.getSource().getServer(), r, null, null);
                    if (body != null) {
                        body.discard();
                    }
                    return done(ctx, r.name);
                })));
    }

    private static int step(CommandContext<CommandSourceStack> ctx, int count) {
        var server = ctx.getSource().getServer();
        for (int i = 0; i < count; i++) {
            NemesisSimulator.step(server, server.overworld().random);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.nemesis.stepped", count), true);
        return count;
    }

    private static int done(CommandContext<CommandSourceStack> ctx, String name) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.nemesis.done", name), true);
        return 1;
    }

    private NemesisCommands() {}
}

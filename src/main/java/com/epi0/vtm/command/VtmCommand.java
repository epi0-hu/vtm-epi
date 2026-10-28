package com.epi0.vtm.command;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import com.epi0.vtm.vampire.VampireRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * <pre>
 * /vtm embrace &lt;targets&gt; &lt;clan&gt;
 * /vtm mortal &lt;targets&gt;
 * /vtm set &lt;targets&gt; (generation|potency|blood|humanity|stains|xp) &lt;value&gt;
 * /vtm discipline &lt;targets&gt; &lt;discipline&gt; &lt;level&gt;
 * /vtm sect join &lt;targets&gt; &lt;faction&gt; | leave &lt;targets&gt; | rank &lt;targets&gt; &lt;n&gt; | standing &lt;targets&gt; &lt;faction&gt; &lt;value&gt;
 * /vtm info &lt;target&gt;
 * </pre>
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class VtmCommand {
    private static final SuggestionProvider<CommandSourceStack> CLANS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ctx.getSource().registryAccess().registry(VtmRegistries.CLAN)
                    .map(r -> r.keySet().stream()).orElseGet(Stream::empty), builder);

    private static final SuggestionProvider<CommandSourceStack> DISCIPLINES = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ctx.getSource().registryAccess().registry(VtmRegistries.DISCIPLINE)
                    .map(r -> r.keySet().stream()).orElseGet(Stream::empty), builder);

    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ctx.getSource().registryAccess().registry(VtmRegistries.FACTION)
                    .map(r -> r.keySet().stream()).orElseGet(Stream::empty), builder);

    private static final SimpleCommandExceptionType NOT_VAMPIRE =
            new SimpleCommandExceptionType(Component.translatable("commands.vtm.not_vampire"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, ?> setTargets = Commands.argument("targets", EntityArgument.players());
        setTargets.then(stat("generation", VampireRules.MIN_GENERATION, VampireRules.MAX_GENERATION,
                (d, v) -> d.withGeneration(v)
                        .withBloodPotency(Math.min(d.bloodPotency(), VampireRules.maxBloodPotency(v)))));
        setTargets.then(stat("potency", 0, 10,
                (d, v) -> d.withBloodPotency(Math.min(v, VampireRules.maxBloodPotency(d.generation())))));
        setTargets.then(stat("blood", 0, 100, (d, v) -> d.withBlood(Math.min(v, d.maxBlood()))));
        setTargets.then(stat("humanity", 0, VampireRules.MAX_HUMANITY, VampireData::withHumanity));
        setTargets.then(stat("stains", 0, VampireRules.MAX_STAINS, VampireData::withStains));
        setTargets.then(stat("xp", 0, 100000, VampireData::withExperience));

        dispatcher.register(Commands.literal("vtm")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("embrace")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("clan", ResourceLocationArgument.id())
                                        .suggests(CLANS)
                                        .executes(VtmCommand::embrace))))
                .then(Commands.literal("mortal")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(VtmCommand::mortal)))
                .then(Commands.literal("set").then(setTargets))
                .then(Commands.literal("discipline")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("discipline", ResourceLocationArgument.id())
                                        .suggests(DISCIPLINES)
                                        .then(Commands.argument("level",
                                                        IntegerArgumentType.integer(0, VampireRules.MAX_DISCIPLINE_LEVEL))
                                                .executes(VtmCommand::discipline)))))
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("civilian").executes(VtmCommand::spawnCivilian))
                                .then(Commands.literal("vampire").executes(ctx -> spawnVampire(ctx, null))
                                        .then(Commands.argument("clan", ResourceLocationArgument.id())
                                                .suggests(CLANS)
                                                .executes(ctx -> spawnVampire(ctx,
                                                        vtmId(ResourceLocationArgument.getId(ctx, "clan"))))))))
                .then(NemesisCommands.node())
                .then(CityCommands.node())
                .then(Commands.literal("sect")
                        .then(Commands.literal("join")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("faction", ResourceLocationArgument.id()).suggests(FACTIONS)
                                                .executes(ctx -> sect(ctx, (player, faction) -> SectManager.join(player, faction))))))
                        .then(Commands.literal("leave")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> sect(ctx, (player, faction) -> SectManager.leave(player)))))
                        .then(Commands.literal("rank")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("rank", IntegerArgumentType.integer(0, 9))
                                                .executes(ctx -> sect(ctx, (player, faction) -> {
                                                    SectManager.setRank(player, IntegerArgumentType.getInteger(ctx, "rank"));
                                                    return true;
                                                })))))
                        .then(Commands.literal("standing")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("faction", ResourceLocationArgument.id()).suggests(FACTIONS)
                                                .then(Commands.argument("value", IntegerArgumentType.integer(-Membership.MAX_STANDING, Membership.MAX_STANDING))
                                                        .executes(ctx -> sect(ctx, (player, faction) -> {
                                                            int value = IntegerArgumentType.getInteger(ctx, "value");
                                                            SectManager.set(player, Membership.of(player).withStanding(faction, value));
                                                            return true;
                                                        })))))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(VtmCommand::info))));
    }

    /** Builds one {@code <stat> <value>} branch that rewrites the data of every target. */
    private static LiteralArgumentBuilder<CommandSourceStack> stat(
            String name, int min, int max, BiFunction<VampireData, Integer, VampireData> setter) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                    for (ServerPlayer player : targets) {
                        VampireData data = VampireData.of(player);
                        if (!data.isVampire()) {
                            throw NOT_VAMPIRE.create();
                        }
                        VampireManager.set(player, setter.apply(data, value));
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "commands.vtm.set", name, value, targets.size()), true);
                    return targets.size();
                }));
    }

    private static ResourceLocation vtmId(ResourceLocation id) {
        return "minecraft".equals(id.getNamespace()) ? VtmMod.id(id.getPath()) : id;
    }

    private static int embrace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation clan = vtmId(ResourceLocationArgument.getId(ctx, "clan"));
        if (VtmRegistries.clan(ctx.getSource().registryAccess(), clan).isEmpty()) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.unknown_clan", clan.toString())).create();
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            VampireManager.embrace(player, clan);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.vtm.embrace", targets.size(), VtmRegistries.name("clan", clan)), true);
        return targets.size();
    }

    private static int spawnCivilian(CommandContext<CommandSourceStack> ctx) {
        CivilianNpc npc = NpcSpawner.civilian(ctx.getSource().getLevel(), ctx.getSource().getPosition());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.npc_spawned", npc.getDisplayName()), true);
        return 1;
    }

    private static int spawnVampire(CommandContext<CommandSourceStack> ctx, ResourceLocation clan) throws CommandSyntaxException {
        if (clan != null && VtmRegistries.clan(ctx.getSource().registryAccess(), clan).isEmpty()) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.unknown_clan", clan.toString())).create();
        }
        VampireNpc npc = NpcSpawner.vampire(ctx.getSource().getLevel(), ctx.getSource().getPosition(), clan);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.npc_spawned", npc.getDisplayName()), true);
        return 1;
    }

    private static int mortal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            VampireManager.makeMortal(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.mortal", targets.size()), true);
        return targets.size();
    }

    private static int discipline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = vtmId(ResourceLocationArgument.getId(ctx, "discipline"));
        if (VtmRegistries.discipline(ctx.getSource().registryAccess(), id).isEmpty()) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.unknown_discipline", id.toString())).create();
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            VampireData data = VampireData.of(player);
            if (!data.isVampire()) {
                throw NOT_VAMPIRE.create();
            }
            VampireManager.set(player, data.withDiscipline(id, level));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.vtm.discipline", VtmRegistries.name("discipline", id), level, targets.size()), true);
        return targets.size();
    }

    private static int sect(CommandContext<CommandSourceStack> ctx, java.util.function.BiPredicate<ServerPlayer, ResourceLocation> action)
            throws CommandSyntaxException {
        ResourceLocation faction = null;
        try {
            faction = vtmId(ResourceLocationArgument.getId(ctx, "faction"));
        } catch (IllegalArgumentException absent) {
            // not every sect subcommand names a faction
        }
        if (faction != null && VtmRegistries.faction(ctx.getSource().registryAccess(), faction).isEmpty()) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.unknown_faction", faction.toString())).create();
        }
        int done = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            done += action.test(player, faction) ? 1 : 0;
        }
        int count = done;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.sect", count), true);
        return done;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        VampireData data = VampireData.of(player);
        Component clan = data.clan().map(id -> VtmRegistries.name("clan", id)).orElse(Component.translatable("gui.vtm.none"));
        Membership membership = Membership.of(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.info_sect", membership.faction()
                .map(id -> (Component) Component.empty().append(VtmRegistries.name("faction", id)).append(" | ")
                        .append(SectManager.rankTitle(ctx.getSource().registryAccess(), id, membership.rank())))
                .orElse(Component.translatable("gui.vtm.none"))), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.info",
                player.getDisplayName(), data.species().getSerializedName(), clan, data.generation(),
                data.bloodPotency(), String.format("%.1f/%.0f", data.blood(), data.maxBlood()),
                data.humanity(), data.stains(), data.experience()), false);
        return 1;
    }

    private VtmCommand() {}
}

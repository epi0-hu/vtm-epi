package com.epi0.vtm.command;

import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.city.CityLife;
import com.epi0.vtm.city.Territory;
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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * <pre>
 * /vtm city list
 * /vtm city here                                   (the city you are standing in)
 * /vtm city owner &lt;name&gt; &lt;faction&gt;
 * /vtm city strength &lt;name&gt; &lt;0-100&gt;
 * /vtm city heat &lt;player&gt; &lt;name&gt; &lt;0-100&gt;
 * /vtm city populate &lt;name&gt;
 * /vtm city discover &lt;player&gt; &lt;name&gt;
 * </pre>
 */
final class CityCommands {

    private static City find(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        return CityData.get(ctx.getSource().getServer()).all().stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("commands.vtm.city.unknown", name)).create());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("name", StringArgumentType.string()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                CityData.get(ctx.getSource().getServer()).all().stream().map(c -> "\"" + c.name + "\""), builder));
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("city")
                .then(Commands.literal("list").executes(ctx -> {
                    var cities = CityData.get(ctx.getSource().getServer()).all();
                    for (City c : cities) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.city.line", c.name, c.owner.getPath(),
                                c.strength, c.center.getX(), c.center.getZ()), false);
                    }
                    return cities.size();
                }))
                .then(Commands.literal("here").executes(ctx -> {
                    City c = CityData.get(ctx.getSource().getServer()).at(ctx.getSource().getLevel(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
                    if (c == null) {
                        throw new SimpleCommandExceptionType(Component.translatable("commands.vtm.city.none_here")).create();
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.vtm.city.line", c.name, c.owner.getPath(),
                            c.strength, c.center.getX(), c.center.getZ()), false);
                    return 1;
                }))
                .then(Commands.literal("owner").then(name().then(Commands.argument("faction", ResourceLocationArgument.id()).executes(ctx -> {
                    City c = find(ctx);
                    ResourceLocation faction = ResourceLocationArgument.getId(ctx, "faction");
                    c.owner = "minecraft".equals(faction.getNamespace()) ? com.epi0.vtm.VtmMod.id(faction.getPath()) : faction;
                    c.warden = null;
                    CityData.get(ctx.getSource().getServer()).setDirty();
                    return 1;
                }))))
                .then(Commands.literal("strength").then(name().then(Commands.argument("value", IntegerArgumentType.integer(0, City.MAX_STRENGTH)).executes(ctx -> {
                    City c = find(ctx);
                    c.strength = IntegerArgumentType.getInteger(ctx, "value");
                    CityData.get(ctx.getSource().getServer()).setDirty();
                    return 1;
                }))))
                .then(Commands.literal("heat").then(Commands.argument("player", EntityArgument.player()).then(name()
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, City.MAX_HEAT)).executes(ctx -> {
                            City c = find(ctx);
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            c.heat.put(player.getUUID(), IntegerArgumentType.getInteger(ctx, "value"));
                            CityData.get(ctx.getSource().getServer()).setDirty();
                            return 1;
                        })))))
                .then(Commands.literal("populate").then(name().executes(ctx -> {
                    City c = find(ctx);
                    return CityLife.populate(ctx.getSource().getLevel(), c);
                })))
                .then(Commands.literal("discover").then(Commands.argument("player", EntityArgument.player()).then(name().executes(ctx -> {
                    Territory.discover(EntityArgument.getPlayer(ctx, "player"), find(ctx));
                    return 1;
                }))));
    }

    private CityCommands() {}
}

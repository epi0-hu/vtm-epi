package com.epi0.vtm.dialogue;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.VtmNpc;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All loaded dialogue trees. Server-side only: clients receive the lines they need as the conversation unfolds. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class Dialogues extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public record Entry(ResourceLocation id, DialogueTree tree) {}

    private static volatile List<Entry> trees = List.of();

    private Dialogues() {
        super(GSON, "vtm/dialogue");
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new Dialogues());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> loaded = new java.util.ArrayList<>();
        files.forEach((id, json) -> DialogueTree.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> VtmMod.LOGGER.error("Bad dialogue {}: {}", id, error))
                .filter(tree -> validate(id, tree))
                .ifPresent(tree -> loaded.add(new Entry(id, tree))));
        loaded.sort(Comparator.<Entry>comparingInt(e -> e.tree().priority()).reversed()
                .thenComparing(e -> e.id().toString()));
        trees = List.copyOf(loaded);
        VtmMod.LOGGER.info("Loaded {} dialogue trees", trees.size());
    }

    private static boolean validate(ResourceLocation id, DialogueTree tree) {
        for (String ref : tree.references()) {
            if (!tree.nodes().containsKey(ref)) {
                VtmMod.LOGGER.error("Dialogue {} refers to missing node '{}'", id, ref);
                return false;
            }
        }
        return true;
    }

    public static Optional<Entry> byId(ResourceLocation id) {
        return trees.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** The most specific tree that applies to this pair. */
    public static Optional<Entry> select(ServerPlayer player, VtmNpc npc) {
        return trees.stream().filter(e -> e.tree().when().test(player, npc)).findFirst();
    }

    /** Adds a tree that is not in any datapack (until the next reload); for tests and tools. */
    public static void install(ResourceLocation id, DialogueTree tree) {
        List<Entry> next = new java.util.ArrayList<>(trees.stream().filter(e -> !e.id().equals(id)).toList());
        next.add(new Entry(id, tree));
        trees = List.copyOf(next);
    }

    public static int count() {
        return trees.size();
    }
}

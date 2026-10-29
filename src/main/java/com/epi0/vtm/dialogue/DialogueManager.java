package com.epi0.vtm.dialogue;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.network.DialogueNodePayload;
import com.epi0.vtm.network.VtmNetwork;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs conversations. The server owns all state: it picks the tree, evaluates conditions and dice, applies effects,
 * and tells the client only what to show. A client can ask for an option by index and nothing else.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class DialogueManager {
    public static final double MAX_DISTANCE = 6.0;

    public record Session(UUID npc, ResourceLocation tree, String node) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    @Nullable
    public static Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public enum Refusal { HOSTILE, BUSY, ASLEEP, NOTHING_TO_SAY, FRIGHTENED }

    /** Why this NPC will not talk to this player right now, if it won't. */
    public static Optional<Refusal> refusal(ServerPlayer player, VtmNpc npc) {
        if (!npc.isAlive()) {
            return Optional.of(Refusal.NOTHING_TO_SAY);
        }
        if (npc.talker() != null && !player.getUUID().equals(npc.talker())) {
            return Optional.of(Refusal.BUSY);
        }
        boolean yielding = npc instanceof VampireNpc v && v.isYielding();
        if (npc.isResting()) {
            return Optional.of(Refusal.ASLEEP);
        }
        if (npc.state() == VtmNpc.State.COWERING || npc.state() == VtmNpc.State.FEEDING) {
            return Optional.of(Refusal.FRIGHTENED);
        }
        if (!yielding && (npc.attitudeTo(player) == Attitude.HOSTILE || npc.getTarget() == player)) {
            return Optional.of(Refusal.HOSTILE);
        }
        return Dialogues.select(player, npc).isEmpty() ? Optional.of(Refusal.NOTHING_TO_SAY) : Optional.empty();
    }

    /** Starts the conversation that suits this pair. Returns false (and tells the player why) when the NPC won't talk. */
    public static boolean open(ServerPlayer player, VtmNpc npc) {
        return open(player, npc, null);
    }

    /** Starts a specific tree ({@code null} to pick the best one). */
    public static boolean open(ServerPlayer player, VtmNpc npc, @Nullable ResourceLocation tree) {
        end(player);
        Optional<Refusal> refusal = refusal(player, npc);
        if (refusal.isPresent()) {
            player.displayClientMessage(Component.translatable("message.vtm.refuses." + refusal.get().name().toLowerCase(java.util.Locale.ROOT),
                    npc.getDisplayName()), true);
            return false;
        }
        Dialogues.Entry entry = tree != null ? Dialogues.byId(tree).orElse(null) : Dialogues.select(player, npc).orElse(null);
        if (entry == null) {
            return false;
        }
        npc.beginTalk(player);
        SESSIONS.put(player.getUUID(), new Session(npc.getUUID(), entry.id(), entry.tree().start()));
        present(player, npc, entry.tree(), entry.tree().start(), Optional.empty());
        return true;
    }

    /** The player answered with option {@code index} of the current node. */
    public static void choose(ServerPlayer player, int index) {
        Session session = session(player);
        if (session == null) {
            return;
        }
        VtmNpc npc = npcOf(player, session);
        Optional<Dialogues.Entry> entry = Dialogues.byId(session.tree());
        if (npc == null || entry.isEmpty() || index < 0) {
            end(player);
            return;
        }
        DialogueTree tree = entry.get().tree();
        DialogueTree.Node node = tree.nodes().get(session.node());
        if (node == null || index >= node.options().size()) {
            end(player);
            return;
        }
        DialogueTree.Option option = node.options().get(index);
        if (!option.show().test(player, npc) || !option.require().test(player, npc)) {
            return; // stale or forged request: ignore
        }

        Optional<String> next = option.next();
        Optional<Component> note = Optional.empty();
        if (option.check().isPresent()) {
            Check check = option.check().get();
            Roll roll = roll(player, check);
            note = Optional.of(describe(roll, check));
            boolean success = roll.succeeded(check.difficulty());
            if (success) {
                option.effects().forEach(e -> e.apply(player, npc));
            } else {
                option.failEffects().forEach(e -> e.apply(player, npc));
                next = check.fail();
            }
            if (roll.messyCritical() || roll.bestialFailure()) {
                npc.witness(player, com.epi0.vtm.npc.NpcMemory.Event.WITNESSED_FRENZY); // the Beast showed
            }
        } else {
            option.effects().forEach(e -> e.apply(player, npc));
        }

        if (next.isEmpty() || !tree.nodes().containsKey(next.get()) || !npc.isAlive()) {
            end(player);
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(session.npc(), session.tree(), next.get()));
        present(player, npc, tree, next.get(), note);
    }

    public static void end(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        VtmNpc npc = npcOf(player, session);
        if (npc != null) {
            npc.endTalk();
        }
        VtmNetwork.send(player, DialogueNodePayload.close());
    }

    /** The NPC was hurt, or is otherwise lost to the conversation: hang up on whoever was talking to it. */
    public static void endFor(VtmNpc npc) {
        SESSIONS.entrySet().removeIf(e -> {
            if (!e.getValue().npc().equals(npc.getUUID())) {
                return false;
            }
            npc.endTalk();
            if (npc.level() instanceof net.minecraft.server.level.ServerLevel level
                    && level.getServer().getPlayerList().getPlayer(e.getKey()) instanceof ServerPlayer player) {
                VtmNetwork.send(player, DialogueNodePayload.close());
            }
            return true;
        });
        npc.endTalk();
    }

    @Nullable
    private static VtmNpc npcOf(ServerPlayer player, Session session) {
        Entity entity = player.serverLevel().getEntity(session.npc());
        return entity instanceof VtmNpc npc && npc.isAlive() ? npc : null;
    }

    private static Roll roll(ServerPlayer player, Check check) {
        VampireData data = VampireData.of(player);
        int pool = check.pool() + check.discipline().map(data::disciplineLevel).orElse(0) + (data.isVampire() ? com.epi0.vtm.vampire.VampireRules.poolBonus(data.generation()) : 0);
        int hunger = data.isVampire() ? data.hunger() : 0;
        return Roll.make(player.getRandom(), pool, hunger, check.difficulty());
    }

    private static Component describe(Roll roll, Check check) {
        String outcome = roll.bestialFailure() ? "bestial" : roll.messyCritical() ? "messy"
                : roll.succeeded(check.difficulty()) ? "success" : "fail";
        return Component.translatable("dialogue.vtm.roll", roll.pool(), roll.hunger(), roll.successes(), check.difficulty(),
                Component.translatable("dialogue.vtm.roll." + outcome));
    }

    // ---------------------------------------------------------------- presenting

    private static void present(ServerPlayer player, VtmNpc npc, DialogueTree tree, String nodeId, Optional<Component> note) {
        DialogueTree.Node node = tree.nodes().get(nodeId);
        Object[] args = args(player, npc);

        String key = node.text().stream().filter(l -> l.when().test(player, npc)).map(DialogueTree.Line::key)
                .findFirst().orElse(node.text().get(node.text().size() - 1).key());

        java.util.ArrayList<DialogueNodePayload.Choice> choices = new java.util.ArrayList<>();
        for (DialogueTree.Option option : node.options()) {
            // Hidden options keep their index so a choice always names the same option; they are sent disabled-and-empty.
            boolean shown = option.show().test(player, npc);
            boolean enabled = shown && option.require().test(player, npc);
            Component hint = option.locked().filter(k -> shown && !enabled).map(k -> (Component) Component.translatable(k)).orElse(null);
            choices.add(new DialogueNodePayload.Choice(shown ? Component.translatable(option.text(), args) : Component.empty(),
                    enabled, Optional.ofNullable(hint)));
        }

        int color = npc.faction().map(com.epi0.vtm.registry.Faction::color).orElse(0xFFFFFF);
        VtmNetwork.send(player, new DialogueNodePayload(Optional.of(new DialogueNodePayload.Node(
                npc.getId(), speaker(npc), color, Component.translatable(key, args), note,
                npc.attitudeTo(player).ordinal(), npc.memory().rapport(player.getUUID()), choices))));
    }

    /** The NPC's name, with their rank in their sect when it has more than one. */
    private static Component speaker(VtmNpc npc) {
        boolean ranked = npc.faction().map(f -> f.rankCount() > 1).orElse(false) && !npc.getDisplayName().getString().isEmpty();
        if (npc instanceof com.epi0.vtm.npc.CivilianNpc civilian && civilian.job() != com.epi0.vtm.npc.Job.RESIDENT) {
            return Component.empty().append(npc.getDisplayName()).append(" | ")
                    .append(Component.translatable("job.vtm." + civilian.job().id()));
        }
        return ranked ? Component.empty().append(npc.getDisplayName()).append(" | ")
                .append(com.epi0.vtm.sect.SectManager.rankTitle(npc.level().registryAccess(), npc.factionId(), npc.rank()))
                : npc.getDisplayName();
    }

    /** {@code %1$s} NPC name, {@code %2$s} player, {@code %3$s} clan, {@code %4$s} faction, {@code %5$s} clan description, {@code %6$s} city, {@code %7$s} the city's ruling faction, {@code %8$s} a housemate's first name, {@code %9$s} what they are to the NPC, {@code %10$s} a vampire's generation ("8th"). */
    private static Object[] args(ServerPlayer player, VtmNpc npc) {
        Component clan = Component.translatable("dialogue.vtm.no_clan");
        Component description = Component.empty();
        if (npc instanceof VampireNpc vampire && vampire.clanId() != null) {
            clan = VtmRegistries.name("clan", vampire.clanId());
            description = VtmRegistries.text("clan", vampire.clanId(), "desc");
        }
        com.epi0.vtm.city.City city = DialogueCity.of(npc);
        Component relative = Component.empty();
        Component relation = Component.translatable("relation.vtm.family.none");
        if (npc instanceof com.epi0.vtm.npc.CivilianNpc civilian) {
            var kin = com.epi0.vtm.npc.Household.relative(civilian);
            if (kin != null) {
                relative = Component.literal(com.epi0.vtm.npc.Household.firstNameOf(kin));
                relation = Component.translatable("relation.vtm.family." + com.epi0.vtm.npc.Household.relation(civilian, kin));
            }
        }
        return new Object[]{npc.getDisplayName(), player.getDisplayName(), clan,
                VtmRegistries.name("faction", npc.factionId()), description,
                city == null ? Component.translatable("dialogue.vtm.no_city") : Component.literal(city.name),
                city == null ? Component.translatable("dialogue.vtm.no_city") : VtmRegistries.name("faction", city.owner),
                relative, relation,
                Component.literal(npc instanceof VampireNpc vampire ? com.epi0.vtm.vampire.VampireRules.ordinal(vampire.generation()) : "")};
    }

    // ---------------------------------------------------------------- housekeeping

    /** Twice a second: hang up on anyone who walked away, died, or whose partner turned hostile or vanished. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0 || SESSIONS.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            Session session = SESSIONS.get(id);
            if (player == null) {
                SESSIONS.remove(id);
                continue;
            }
            VtmNpc npc = session == null ? null : npcOf(player, session);
            if (npc == null || !player.isAlive() || player.distanceToSqr(npc) > MAX_DISTANCE * MAX_DISTANCE
                    || npc.attitudeTo(player) == Attitude.HOSTILE && !(npc instanceof VampireNpc v && v.isYielding())) {
                end(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player);
        }
    }

    private DialogueManager() {}
}

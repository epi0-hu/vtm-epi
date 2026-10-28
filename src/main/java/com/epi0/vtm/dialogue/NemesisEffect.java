package com.epi0.vtm.dialogue;

import com.epi0.vtm.nemesis.Intel;
import com.epi0.vtm.nemesis.NemesisData;
import com.epi0.vtm.nemesis.NemesisManager;
import com.epi0.vtm.nemesis.NemesisRecord;
import com.epi0.vtm.nemesis.Relation;
import com.epi0.vtm.nemesis.Status;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a conversation does about the named vampires of the world.
 *
 * @param execute     end a kneeling vampire for good
 * @param spare       let them go with their life, and their debt
 * @param humiliate   let them go with their pride broken
 * @param interrogate learn what they know, then let them go
 * @param bind        bind them to the player with the player's blood (needs blood, a free place, a vampire player)
 * @param tithe       feed a bound vampire some blood to keep them loyal
 * @param release     free a bound vampire
 * @param intel       tell the player something: of the NPC itself ({@code self}), its kin and rivals ({@code kin}), the
 *                    NPC's faction's court ({@code faction}), its enemies' courts ({@code enemies}), or anyone ({@code any})
 * @param count       how many things {@code intel} reveals
 * @param enmity      change to the NPC's hatred of the player
 * @param respect     change to the NPC's respect for the player
 */
public record NemesisEffect(boolean execute, boolean spare, boolean humiliate, boolean interrogate, boolean bind,
                            boolean tithe, boolean release, Optional<String> intel, int count, Optional<Integer> enmity,
                            Optional<Integer> respect) {
    public static final Codec<NemesisEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("execute", false).forGetter(NemesisEffect::execute),
            Codec.BOOL.optionalFieldOf("spare", false).forGetter(NemesisEffect::spare),
            Codec.BOOL.optionalFieldOf("humiliate", false).forGetter(NemesisEffect::humiliate),
            Codec.BOOL.optionalFieldOf("interrogate", false).forGetter(NemesisEffect::interrogate),
            Codec.BOOL.optionalFieldOf("bind", false).forGetter(NemesisEffect::bind),
            Codec.BOOL.optionalFieldOf("tithe", false).forGetter(NemesisEffect::tithe),
            Codec.BOOL.optionalFieldOf("release", false).forGetter(NemesisEffect::release),
            Codec.STRING.optionalFieldOf("intel").forGetter(NemesisEffect::intel),
            Codec.INT.optionalFieldOf("count", 1).forGetter(NemesisEffect::count),
            Codec.INT.optionalFieldOf("enmity").forGetter(NemesisEffect::enmity),
            Codec.INT.optionalFieldOf("respect").forGetter(NemesisEffect::respect)
    ).apply(i, NemesisEffect::new));

    void apply(ServerPlayer player, VtmNpc npc) {
        var server = player.getServer();
        NemesisData data = NemesisManager.data(server);
        VampireNpc body = npc instanceof VampireNpc v ? v : null;
        NemesisRecord r = body == null ? null : NemesisManager.recordOf(body);

        intel.ifPresent(target -> {
            List<NemesisRecord> pool = pool(target, npc, r, data, player);
            for (int i = 0; i < count; i++) {
                Optional<Component> learned = Intel.revealAbout(player, pool, player.getRandom());
                player.displayClientMessage(learned.orElse(Component.translatable("message.vtm.nemesis.nothing_new")), false);
                if (learned.isEmpty()) {
                    break;
                }
            }
        });

        if (r != null) {
            enmity.ifPresent(d -> {
                r.feud(player.getUUID()).enmity += d;
                r.feud(player.getUUID()).clamp();
            });
            respect.ifPresent(d -> {
                r.feud(player.getUUID()).respect += d;
                r.feud(player.getUUID()).clamp();
            });
            if (tithe && NemesisManager.tithe(player, r)) {
                player.displayClientMessage(Component.translatable("message.vtm.nemesis.tithed", r.name), true);
            }
            if (release) {
                NemesisManager.release(player, r);
            }
            if (bind && body != null && body.isYielding()) {
                if (NemesisManager.bind(player, body, r)) {
                    player.displayClientMessage(Component.translatable("message.vtm.nemesis.bound", r.name), false);
                } else {
                    player.displayClientMessage(Component.translatable("message.vtm.nemesis.cannot_bind"), true);
                }
            }
            if (body != null && body.isYielding() && r.status != Status.BOUND) {
                // Only one verdict, and it ends the scene (the vampire leaves or dies).
                if (execute) {
                    NemesisManager.execute(player, body, r);
                } else if (spare) {
                    NemesisManager.spare(player, body, r);
                } else if (humiliate) {
                    NemesisManager.humiliate(player, body, r);
                } else if (interrogate) {
                    NemesisManager.interrogate(player, body, r);
                }
            }
        }
    }

    private static List<NemesisRecord> pool(String target, VtmNpc npc, NemesisRecord self, NemesisData data, ServerPlayer player) {
        List<NemesisRecord> pool = new ArrayList<>();
        switch (target) {
            case "self" -> {
                if (self != null) {
                    pool.add(self);
                }
            }
            case "kin" -> {
                if (self != null) {
                    self.relations.forEach(rel -> {
                        NemesisRecord other = data.get(rel.other());
                        if (other != null) {
                            pool.add(other);
                        }
                    });
                }
            }
            case "faction" -> pool.addAll(data.inFaction(npc.factionId()));
            case "enemies" -> VtmRegistries.faction(npc.level().registryAccess(), npc.factionId())
                    .map(Faction::enemies).orElse(List.of()).forEach(f -> pool.addAll(data.inFaction(f)));
            default -> pool.addAll(data.all());
        }
        pool.removeIf(r -> r.status == Status.BOUND && !player.getUUID().equals(r.boundTo));
        return pool;
    }
}

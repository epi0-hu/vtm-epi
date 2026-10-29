package com.epi0.vtm.nemesis;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.Attitude;
import com.epi0.vtm.npc.NpcNames;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.npc.VampireNpc;
import com.epi0.vtm.npc.VtmNpc;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything that happens to a named vampire: how they are made, how they appear in the world, how a defeat is
 * resolved, what the player can do with a beaten one, and how a blood bond works. The offscreen world is
 * {@link NemesisSimulator}; the combat hooks are {@link NemesisEvents}.
 */
public final class NemesisManager {
    public static final long NIGHT = 24000L;
    public static final int MAX_COTERIE = 3;
    public static final float BIND_COST = 3F;
    public static final float TITHE_COST = 2F;
    public static final int HOSTILE_ENMITY = 50;
    public static final long YIELD_TICKS = 900L;
    public static final float YIELD_CHANCE = 0.6F;
    public static final int STARTING_LOYALTY = 60;

    private static final ResourceLocation HEALTH = VtmMod.id("nemesis_health");
    private static final ResourceLocation DAMAGE = VtmMod.id("nemesis_damage");
    private static final ResourceLocation SPEED = VtmMod.id("nemesis_speed");
    private static final ResourceLocation ARMOR = VtmMod.id("nemesis_armor");
    private static final ResourceLocation KNOCKBACK = VtmMod.id("nemesis_knockback");

    private static final Map<String, Trait> CLAN_LEANINGS = Map.ofEntries(
            Map.entry("brujah", Trait.BRUTE), Map.entry("gangrel", Trait.SWIFT), Map.entry("nosferatu", Trait.DEFLECTOR),
            Map.entry("ventrue", Trait.IRON_WILL), Map.entry("tzimisce", Trait.BLOODTHIRSTY), Map.entry("lasombra", Trait.UNDYING),
            Map.entry("ravnos", Trait.RECKLESS), Map.entry("tremere", Trait.PATIENT), Map.entry("toreador", Trait.PROUD),
            Map.entry("malkavian", Trait.RECKLESS), Map.entry("banu_haqim", Trait.PATIENT), Map.entry("hecata", Trait.VENGEFUL));

    public static NemesisData data(MinecraftServer server) {
        return NemesisData.get(server);
    }

    @Nullable
    public static NemesisRecord recordOf(VampireNpc npc) {
        UUID id = npc.nemesisId();
        MinecraftServer server = npc.level().getServer();
        return id == null || server == null ? null : data(server).get(id);
    }

    // ---------------------------------------------------------------- creation

    /** A new named vampire in {@code faction}, seated at {@code role}, with a name, a history and a temperament. */
    public static NemesisRecord create(MinecraftServer server, ResourceLocation faction, Role role, RandomSource random) {
        return create(server, faction, null, role, random);
    }

    /** As above, of a particular clan ({@code null} to draw one of the faction's). */
    public static NemesisRecord create(MinecraftServer server, ResourceLocation faction, @org.jetbrains.annotations.Nullable ResourceLocation wanted,
                                       Role role, RandomSource random) {
        NemesisData data = data(server);
        ResourceLocation clan = wanted != null ? wanted : clanFor(server, faction, random);
        String name = NpcNames.random(random);
        for (int i = 0; i < 12 && nameTaken(data, name); i++) {
            name = NpcNames.random(random);
        }
        NemesisRecord r = new NemesisRecord(UUID.randomUUID(), name, clan, faction);
        r.role = role;
        r.generation = com.epi0.vtm.vampire.VampireRules.npcGeneration(role, clan.getPath().equals("thin_blood"), random);
        r.level = switch (role) {
            case LEADER -> 8 + random.nextInt(5);
            case LIEUTENANT -> 5 + random.nextInt(5);
            case ENFORCER -> 3 + random.nextInt(4);
            case AGENT -> 1 + random.nextInt(3);
        };
        r.ambition = 20 + random.nextInt(61);
        pickTraits(r, random);
        relate(data, r, server, random);
        data.add(r);
        return r;
    }

    private static boolean nameTaken(NemesisData data, String name) {
        return data.all().stream().anyMatch(r -> r.name.equals(name));
    }

    /** A clan that belongs to {@code faction}, or any clan if none does. */
    public static ResourceLocation clanFor(MinecraftServer server, ResourceLocation faction, RandomSource random) {
        var registry = server.registryAccess().registry(VtmRegistries.CLAN).orElseThrow();
        List<ResourceLocation> members = registry.entrySet().stream()
                .filter(e -> e.getValue().faction().equals(faction)).map(e -> e.getKey().location()).sorted().toList();
        List<ResourceLocation> pool = members.isEmpty() ? registry.keySet().stream().sorted().toList() : members;
        return pool.get(random.nextInt(pool.size()));
    }

    static void pickTraits(NemesisRecord r, RandomSource random) {
        Trait leaning = CLAN_LEANINGS.get(r.clan.getPath());
        if (leaning != null && random.nextFloat() < 0.5F) {
            addTrait(r, leaning);
        }
        int strengths = 1 + (r.role == Role.LEADER || r.role == Role.LIEUTENANT || random.nextFloat() < 0.35F ? 1 : 0);
        draw(r, Trait.Kind.STRENGTH, strengths, random);
        draw(r, Trait.Kind.WEAKNESS, 1 + (random.nextFloat() < 0.4F ? 1 : 0), random);
        if (random.nextFloat() < 0.75F && r.traits.stream().noneMatch(t -> t.kind() == Trait.Kind.QUIRK)) {
            draw(r, Trait.Kind.QUIRK, 1, random);
        }
    }

    private static void draw(NemesisRecord r, Trait.Kind kind, int count, RandomSource random) {
        List<Trait> pool = new ArrayList<>(List.of(Trait.values()).stream().filter(t -> t.kind() == kind).toList());
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        long have = r.traits.stream().filter(t -> t.kind() == kind).count();
        for (Trait trait : pool) {
            if (have >= count) {
                return;
            }
            if (addTrait(r, trait)) {
                have++;
            }
        }
    }

    public static boolean addTrait(NemesisRecord r, Trait trait) {
        if (r.traits.contains(trait) || r.traits.stream().anyMatch(t -> t.conflictsWith(trait))) {
            return false;
        }
        r.traits.add(trait);
        return true;
    }

    private static void relate(NemesisData data, NemesisRecord r, MinecraftServer server, RandomSource random) {
        List<NemesisRecord> kin = data.inFaction(r.faction).stream().filter(o -> o.status != Status.BOUND).toList();
        List<NemesisRecord> elders = kin.stream().filter(o -> o.level > r.level).toList();
        if (!elders.isEmpty() && random.nextFloat() < 0.5F) {
            link(elders.get(random.nextInt(elders.size())), r, Relation.Type.CHILDE);
        }
        if (!kin.isEmpty() && random.nextFloat() < 0.3F) {
            NemesisRecord friend = kin.get(random.nextInt(kin.size()));
            if (!friend.related(r.id, Relation.Type.CHILDE) && !friend.related(r.id, Relation.Type.SIRE)) {
                link(friend, r, Relation.Type.ALLY);
            }
        }
        var faction = VtmRegistries.faction(server.registryAccess(), r.faction);
        List<NemesisRecord> foes = faction.map(f -> data.all().stream()
                .filter(o -> o.alive() && f.enemies().contains(o.faction)).toList()).orElse(List.of());
        if (!foes.isEmpty() && random.nextFloat() < 0.45F) {
            link(foes.get(random.nextInt(foes.size())), r, Relation.Type.RIVAL);
        }
    }

    /** {@code a} stands to {@code b} as {@code type} says ({@code CHILDE}: b is a's childe); both sides record it. */
    public static void link(NemesisRecord a, NemesisRecord b, Relation.Type type) {
        if (!a.related(b.id, type)) {
            a.relations.add(new Relation(b.id, type));
        }
        if (!b.related(a.id, type.mirror())) {
            b.relations.add(new Relation(a.id, type.mirror()));
        }
    }

    // ---------------------------------------------------------------- appearing in the world

    /** Puts a named vampire into the world at {@code pos}. */
    public static VampireNpc manifest(ServerLevel level, NemesisRecord r, Vec3 pos) {
        VampireNpc npc = NpcSpawner.vampire(level, pos, r.clan);
        bind(npc, r);
        return npc;
    }

    /** Makes {@code npc} the body of {@code r}: its stats, rank, name and standing in the world. */
    public static void bind(VampireNpc npc, NemesisRecord r) {
        npc.setNemesisId(r.id);
        npc.setClan(r.clan);
        npc.setGeneration(r.generation);
        npc.setFaction(r.faction);
        npc.setRank(r.role.rank());
        npc.setPersistenceRequired();
        npc.setCustomNameVisible(true);
        npc.setBlood(8F);
        r.entity = npc.getUUID();
        r.seenDimension = npc.level().dimension();
        r.lastSeen = npc.blockPosition();
        if (r.lair == null && r.status != Status.BOUND) {
            r.lair = npc.blockPosition();
            r.lairDimension = npc.level().dimension();
            npc.setHome(npc.blockPosition());
        }
        refresh(npc, r);
        npc.setHealth(npc.getMaxHealth());
        data(npc.level().getServer()).setDirty();
    }

    /** Re-applies everything derived from the record (after a level-up, a scar, a trait gained). */
    public static void refresh(VampireNpc npc, NemesisRecord r) {
        npc.setCustomName(nameOf(r));
        double health = 0.07 * (r.level - 1) + (r.role == Role.LEADER ? 0.2 : 0) - (r.has(Trait.GLASS_JAW) ? 0.25 : 0);
        double damage = 0.05 * (r.level - 1) + (r.has(Trait.BRUTE) ? 0.4 : 0) + (r.has(Trait.RECKLESS) ? 0.2 : 0);
        double speed = (r.has(Trait.SWIFT) ? 0.25 : 0) - (r.has(Trait.BRUTE) ? 0.1 : 0);
        modify(npc, Attributes.MAX_HEALTH, HEALTH, health, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(npc, Attributes.ATTACK_DAMAGE, DAMAGE, damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(npc, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(npc, Attributes.ARMOR, ARMOR, r.has(Trait.IRON_WILL) ? 4 : 0, AttributeModifier.Operation.ADD_VALUE);
        modify(npc, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, r.has(Trait.IRON_WILL) ? 0.6 : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void modify(VampireNpc npc, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                               ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = npc.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, op));
        }
    }

    public static Component nameOf(NemesisRecord r) {
        Component name = Component.literal(r.name);
        return epithet(r).map(key -> (Component) Component.empty().append(name).append(" ")
                .append(Component.translatable("epithet.vtm." + key))).orElse(name);
    }

    /** A name earned by deeds, never by temperament (that would give the traits away). */
    public static Optional<String> epithet(NemesisRecord r) {
        if (r.kills >= 3) {
            return Optional.of("reaper");
        }
        if (r.kills >= 1) {
            return Optional.of("slayer");
        }
        if (r.deaths >= 2) {
            return Optional.of("undying");
        }
        return r.scars.isEmpty() ? Optional.empty() : Optional.of("scar_" + r.scars.get(r.scars.size() - 1).id());
    }

    /** Whether an entity for {@code r} currently stands in {@code server}'s world. */
    public static boolean manifested(MinecraftServer server, NemesisRecord r) {
        if (r.entity == null) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(r.entity);
            if (e != null && e.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static VampireNpc entityOf(MinecraftServer server, NemesisRecord r) {
        if (r.entity == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(r.entity) instanceof VampireNpc npc && npc.isAlive()) {
                return npc;
            }
        }
        return null;
    }

    /** A freshly spawned natural vampire may turn out to be one of the named. */
    public static void tryAdopt(VampireNpc npc) {
        MinecraftServer server = npc.level().getServer();
        if (server == null || npc.getRandom().nextFloat() > 0.35F) {
            return;
        }
        NemesisData data = data(server);
        List<NemesisRecord> free = data.all().stream().filter(r -> r.status == Status.ACTIVE && !manifested(server, r)).toList();
        if (free.isEmpty()) {
            return;
        }
        bind(npc, free.get(npc.getRandom().nextInt(free.size())));
    }

    // ---------------------------------------------------------------- the passing of a moment

    /** Once a second, for every named vampire standing in the world. */
    public static void secondTick(VampireNpc npc) {
        NemesisRecord r = recordOf(npc);
        if (r == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        r.seenDimension = level.dimension();
        r.lastSeen = npc.blockPosition();

        if (npc.boundOwner() != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(npc.boundOwner());
            if (owner == null || owner.level() != level || !VtmNpc.isNight(level) || r.wakeAt > now || r.mission != null) {
                retire(npc, r);
            }
            return;
        }

        if (npc.getHealth() < npc.getMaxHealth() * 0.3F && npc.getTarget() instanceof ServerPlayer p) {
            NemesisTaunts.say(npc, r, NemesisTaunts.Situation.SELF_LOW, p);
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, npc.getBoundingBox().inflate(24),
                p -> !p.isSpectator() && npc.hasLineOfSight(p))) {
            if (Intel.knows(player, r) && npc.tickCount % 100 != 0) {
                // already known: only refresh where they were seen now and then
            } else {
                Intel.meet(player, r);
            }
            if (npc.distanceToSqr(player) < 256 && !npc.isYielding()) {
                Feud feud = r.feud(player.getUUID());
                if (now - feud.lastEncounter > 6000L || feud.encounters == 0) {
                    boolean first = feud.encounters == 0;
                    feud.encounters++;
                    feud.lastEncounter = now;
                    if (npc.getTarget() == player || first || feud.enmity >= HOSTILE_ENMITY) {
                        NemesisTaunts.say(npc, r, NemesisTaunts.greeting(r, first ? null : feud), player);
                    }
                }
            }
            if (player.getHealth() < player.getMaxHealth() * 0.3F && npc.getTarget() == player) {
                NemesisTaunts.say(npc, r, NemesisTaunts.Situation.PLAYER_LOW, player);
            }
        }
        data(level.getServer()).setDirty();
    }

    /** Sends a bound vampire back to their rest: out of the world, still bound. */
    public static void retire(VampireNpc npc, NemesisRecord r) {
        if (r.entity != null && r.entity.equals(npc.getUUID())) {
            r.entity = null;
        }
        npc.discard();
    }

    /** Everyone who can see {@code npc} learns that it has {@code trait}. */
    public static void revealNearby(VampireNpc npc, NemesisRecord r, Trait trait) {
        for (ServerPlayer player : npc.level().getEntitiesOfClass(ServerPlayer.class, npc.getBoundingBox().inflate(20),
                p -> !p.isSpectator() && npc.hasLineOfSight(p))) {
            Intel.witness(player, r, trait);
        }
    }

    // ---------------------------------------------------------------- attitude

    /** Overrides how a named vampire regards a player, or null to leave it to the ordinary rules. */
    @Nullable
    public static Attitude attitude(VampireNpc npc, Player player) {
        if (player.getUUID().equals(npc.boundOwner())) {
            return Attitude.FRIENDLY;
        }
        if (npc.nemesisId() == null) {
            return null;
        }
        NemesisRecord r = recordOf(npc);
        if (r == null) {
            return null;
        }
        if (npc.isYielding()) {
            return Attitude.NEUTRAL;
        }
        Feud feud = r.feudOrNull(player.getUUID());
        if (feud == null) {
            return null;
        }
        if (feud.enmity >= HOSTILE_ENMITY) {
            return Attitude.HOSTILE;
        }
        return feud.respect >= 50 && feud.enmity < 20 ? Attitude.FRIENDLY : null;
    }

    // ---------------------------------------------------------------- defeat

    /**
     * A named vampire has taken a lethal blow. Returns true if they survive it (the death is cancelled): they escape
     * into torpor or kneel, beaten, and wait for the victor's verdict. Fire and sunlight finish them, as does a
     * defeat past what they can survive.
     */
    public static boolean defeat(VampireNpc npc, NemesisRecord r, DamageSource source) {
        if (r.status == Status.DESTROYED || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        DamageKind kind = DamageKind.of(source, level);
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;

        boolean fireKill = (kind == DamageKind.FIRE || kind == DamageKind.SUN) && !r.has(Trait.FIRE_HARDENED);
        if (fireKill || r.deaths >= r.resilience()) {
            destroy(server, r, player, null); // the kill itself is credited to the sect by its own death hook
            return false;
        }

        r.deaths++;
        learn(server, r, kind);
        grantXp(r, 2);
        npc.setHealth(Math.max(2F, npc.getMaxHealth() * 0.2F));
        npc.clearFire();
        npc.setTarget(null);
        if (player != null && npc.boundOwner() == null) {
            Feud feud = r.feud(player.getUUID());
            feud.defeats++;
            feud.enmity += r.has(Trait.VENGEFUL) ? 50 : 25;
            feud.respect += 10;
            feud.lastEncounter = now;
            feud.clamp();
            Intel.meet(player, r);
        }
        refresh(npc, r);
        boolean yield = player != null && npc.boundOwner() == null && !r.has(Trait.PROUD) && !r.has(Trait.COWARD)
                && npc.getRandom().nextFloat() < YIELD_CHANCE;
        if (yield) {
            npc.yieldTo(player, now + YIELD_TICKS);
            NemesisTaunts.say(npc, r, NemesisTaunts.Situation.YIELD, player);
        } else {
            escape(npc, "wounded");
        }
        data(server).setDirty();
        return true;
    }

    /** A defeat teaches: resistance to what beat them, a scar to remember it by, sometimes a new nature. */
    static void learn(MinecraftServer server, NemesisRecord r, DamageKind kind) {
        int resist = Math.min(NemesisRecord.MAX_RESIST, r.resistance(kind) + 1);
        r.resist.put(kind, resist);
        if (!r.scars.contains(kind.scar())) {
            r.scars.add(kind.scar());
            while (r.scars.size() > 4) {
                r.scars.remove(0);
            }
        }
        if (resist == NemesisRecord.MAX_RESIST) {
            Trait gained = switch (kind) {
                case FIRE, SUN -> Trait.FIRE_HARDENED;
                case PROJECTILE -> Trait.DEFLECTOR;
                default -> null;
            };
            if (gained != null) {
                r.traits.removeIf(t -> t.conflictsWith(gained) && t.kind() == Trait.Kind.WEAKNESS);
                if (addTrait(r, gained)) {
                    log(server, "adapted", r.faction, false, r);
                }
            }
        }
    }

    /** They get away: into torpor (or, for a bound vampire, a spell of recovery) and out of the world. */
    public static void escape(VampireNpc npc, String reason) {
        NemesisRecord r = recordOf(npc);
        if (r == null || !(npc.level() instanceof ServerLevel level)) {
            npc.discard();
            return;
        }
        long now = level.getGameTime();
        if (r.status == Status.BOUND) {
            r.wakeAt = now + 6000L;
        } else {
            r.status = Status.TORPOR;
            r.wakeAt = now + NIGHT * (1 + r.deaths);
            log(level.getServer(), reason.equals("spared") ? "spared_away" : "torpor", r.faction, false, r);
        }
        r.entity = null;
        ServerPlayer witness = level.getNearestPlayer(npc, 40) instanceof ServerPlayer p ? p : null;
        if (witness != null && !reason.equals("spared")) {
            NemesisTaunts.say(npc, r, NemesisTaunts.Situation.FLEE, witness);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, npc.getX(), npc.getY() + 1, npc.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
        npc.discard();
        data(level.getServer()).setDirty();
    }

    /** Final death. {@code killer} may be null (sunlight, another vampire, offscreen). */
    public static void destroy(MinecraftServer server, NemesisRecord r, @Nullable ServerPlayer killer, @Nullable VampireNpc body) {
        if (r.status == Status.DESTROYED) {
            return;
        }
        boolean wasBound = r.status == Status.BOUND;
        if (killer != null && r.role == Role.LEADER) {
            data(server).holdThrone(r.faction, server.overworld().getGameTime() + com.epi0.vtm.court.Court.VACANCY_TICKS);
        }
        com.epi0.vtm.city.Territory.wardenLost(server, r, killer != null);
        r.status = Status.DESTROYED;
        r.entity = null;
        r.mission = null;
        r.boundTo = null;
        if (killer != null) {
            log(server, r.role == Role.LEADER ? "leader_destroyed_by" : "destroyed_by", r.faction, true, r, killer.getGameProfile().getName());
            Intel.meet(killer, r);
            VampireData vampire = VampireData.of(killer);
            if (vampire.isVampire()) {
                VampireManager.set(killer, vampire.withExperience(vampire.experience() + r.level));
            }
            for (Relation relation : r.relations) {
                NemesisRecord other = data(server).get(relation.other());
                if (other == null || !other.alive()) {
                    continue;
                }
                Feud feud = other.feud(killer.getUUID());
                switch (relation.type()) {
                    case ALLY, SIRE, CHILDE -> feud.enmity += other.has(Trait.VENGEFUL) ? 40 : 20;
                    case RIVAL -> feud.respect += 10;
                }
                feud.clamp();
            }
            if (body != null) {
                SectManager.killed(killer, body);
            }
        } else {
            log(server, r.role == Role.LEADER ? "leader_destroyed" : "destroyed", r.faction, true, r);
        }
        if (wasBound) {
            log(server, "bound_lost", r.faction, false, r);
        }
        data(server).setDirty();
    }

    // ---------------------------------------------------------------- a player falls

    /** A named vampire has killed a player: they grow from it, and will remember it. */
    public static void playerKilledBy(ServerPlayer player, VampireNpc killer, NemesisRecord r) {
        r.kills++;
        grantXp(r, 6);
        Feud feud = r.feud(player.getUUID());
        feud.losses++;
        feud.respect += 10;
        feud.enmity -= 10;
        feud.hunt = 0;
        feud.clamp();
        log(player.getServer(), "slew_player", r.faction, true, r, player.getGameProfile().getName());
        refresh(killer, r);
        NemesisTaunts.say(killer, r, NemesisTaunts.Situation.VICTORY, player);
        data(player.getServer()).setDirty();
    }

    // ---------------------------------------------------------------- growth

    /** Returns how many levels were gained. */
    public static int grantXp(NemesisRecord r, int amount) {
        r.xp += amount;
        int gained = 0;
        while (r.level < NemesisRecord.MAX_LEVEL && r.xp >= r.xpForNextLevel()) {
            r.xp -= r.xpForNextLevel();
            r.level++;
            r.ambition = Math.min(100, r.ambition + 3);
            gained++;
        }
        return gained;
    }

    // ---------------------------------------------------------------- the victor's verdict

    /** Ends a kneeling vampire for good. */
    public static void execute(ServerPlayer player, VampireNpc npc, NemesisRecord r) {
        destroy(player.getServer(), r, player, npc);
        npc.setState(com.epi0.vtm.npc.VtmNpc.State.NORMAL);
        npc.kill();
    }

    public static void spare(ServerPlayer player, VampireNpc npc, NemesisRecord r) {
        Feud feud = r.feud(player.getUUID());
        feud.spared++;
        feud.respect += r.has(Trait.GRATEFUL) ? 50 : 30;
        feud.enmity -= 40;
        feud.clamp();
        log(player.getServer(), "spared", r.faction, false, r, player.getGameProfile().getName());
        escape(npc, "spared");
    }

    public static void humiliate(ServerPlayer player, VampireNpc npc, NemesisRecord r) {
        Feud feud = r.feud(player.getUUID());
        feud.humiliations++;
        feud.enmity += r.has(Trait.PROUD) || r.has(Trait.VENGEFUL) ? 45 : 25;
        feud.respect -= 20;
        feud.clamp();
        escape(npc, "humiliated");
    }

    /** Learns what the beaten vampire knows: about themselves and about those close to them. */
    public static void interrogate(ServerPlayer player, VampireNpc npc, NemesisRecord r) {
        NemesisData data = data(player.getServer());
        List<NemesisRecord> pool = new ArrayList<>();
        pool.add(r);
        for (Relation relation : r.relations) {
            NemesisRecord other = data.get(relation.other());
            if (other != null) {
                pool.add(other);
            }
        }
        data.inRole(r.faction, Role.LEADER).forEach(pool::add);
        for (int i = 0; i < 2; i++) {
            Intel.revealAbout(player, pool, player.getRandom()).ifPresent(msg -> player.displayClientMessage(msg, false));
        }
        r.feud(player.getUUID()).enmity += 15;
        r.feud(player.getUUID()).clamp();
        escape(npc, "interrogated");
    }

    // ---------------------------------------------------------------- blood bonds

    public static boolean canBind(ServerPlayer player, NemesisRecord r) {
        VampireData data = VampireData.of(player);
        return data.isVampire() && data.blood() >= BIND_COST && r.status == Status.ACTIVE
                && data(player.getServer()).boundTo(player.getUUID()).size() < MAX_COTERIE;
    }

    /** Binds a beaten vampire to the player with a draught of the player's blood. */
    public static boolean bind(ServerPlayer player, VampireNpc npc, NemesisRecord r) {
        if (!canBind(player, r)) {
            return false;
        }
        VampireData data = VampireData.of(player);
        VampireManager.set(player, data.withBlood(data.blood() - BIND_COST));
        NemesisData world = data(player.getServer());
        com.epi0.vtm.city.Territory.wardenLost(player.getServer(), r, true);
        if (r.role == Role.LEADER) {
            data(player.getServer()).holdThrone(r.faction, player.getServer().overworld().getGameTime() + com.epi0.vtm.court.Court.VACANCY_TICKS);
        }
        if (r.role != Role.AGENT) {
            log(player.getServer(), "seat_abandoned", r.faction, false, r);
            r.role = Role.AGENT;
        }
        r.status = Status.BOUND;
        r.boundTo = player.getUUID();
        r.loyalty = STARTING_LOYALTY + (r.has(Trait.GRATEFUL) ? 20 : 0) + (r.feud(player.getUUID()).spared > 0 ? 10 : 0);
        r.mission = null;
        Intel.knowFully(player, r);
        Feud feud = r.feud(player.getUUID());
        feud.enmity = 0;
        feud.respect = Math.max(feud.respect, 40);
        log(player.getServer(), "bound", r.faction, false, r, player.getGameProfile().getName());
        npc.setBoundOwner(player.getUUID());
        npc.setState(com.epi0.vtm.npc.VtmNpc.State.NORMAL);
        npc.setTarget(null);
        npc.setRank(0);
        world.setDirty();
        return true;
    }

    public static boolean tithe(ServerPlayer player, NemesisRecord r) {
        VampireData data = VampireData.of(player);
        if (!data.isVampire() || data.blood() < TITHE_COST || r.status != Status.BOUND || !player.getUUID().equals(r.boundTo)) {
            return false;
        }
        VampireManager.set(player, data.withBlood(data.blood() - TITHE_COST));
        r.loyalty = Math.min(100, r.loyalty + 15);
        data(player.getServer()).setDirty();
        return true;
    }

    public static void release(ServerPlayer player, NemesisRecord r) {
        if (r.status != Status.BOUND || !player.getUUID().equals(r.boundTo)) {
            return;
        }
        r.status = Status.ACTIVE;
        r.boundTo = null;
        r.mission = null;
        r.feud(player.getUUID()).respect += 20;
        r.feud(player.getUUID()).clamp();
        log(player.getServer(), "released", r.faction, false, r, player.getGameProfile().getName());
        VampireNpc body = entityOf(player.getServer(), r);
        if (body != null) {
            body.setBoundOwner(null);
            retire(body, r);
        }
        data(player.getServer()).setDirty();
    }

    /** Sends a bound vampire away on an errand. */
    public static boolean assign(ServerPlayer player, NemesisRecord r, MissionType type, @Nullable UUID target, @Nullable ResourceLocation faction) {
        if (r.status != Status.BOUND || !player.getUUID().equals(r.boundTo) || r.mission != null) {
            return false;
        }
        NemesisData data = data(player.getServer());
        if (type == MissionType.HUNT) {
            NemesisRecord prey = target == null ? null : data.get(target);
            if (prey == null || !prey.alive() || prey.status == Status.BOUND || !Intel.knows(player, prey)) {
                return false;
            }
        }
        if (type == MissionType.SPY && (faction == null || VtmRegistries.faction(player.server.registryAccess(), faction).isEmpty())) {
            return false;
        }
        r.mission = new Mission(type, player.level().getGameTime() + type.duration(), type == MissionType.HUNT ? target : null,
                type == MissionType.SPY ? faction : null);
        VampireNpc body = entityOf(player.getServer(), r);
        if (body != null) {
            retire(body, r);
        }
        data.setDirty();
        return true;
    }

    /** Calls a vampire home early; the errand is abandoned. */
    public static void recall(ServerPlayer player, NemesisRecord r) {
        if (r.status == Status.BOUND && player.getUUID().equals(r.boundTo) && r.mission != null) {
            r.mission = null;
            data(player.getServer()).setDirty();
        }
    }

    /** Once a second for each online player: delivers tribute and brings the bound home from their rest. */
    public static void coterieTick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        NemesisData data = data(server);
        List<NemesisRecord> bound = data.boundTo(player.getUUID());
        if (bound.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        for (NemesisRecord r : bound) {
            if (r.pendingTribute > 0) {
                VampireData vampire = VampireData.of(player);
                if (vampire.isVampire()) {
                    VampireManager.set(player, vampire.withExperience(vampire.experience() + r.pendingTribute));
                    player.displayClientMessage(Component.translatable("message.vtm.nemesis.tribute", r.name, r.pendingTribute), false);
                }
                r.pendingTribute = 0;
                data.setDirty();
            }
            if (r.mission != null || r.wakeAt > now || !VtmNpc.isNight(level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                    || player.isSpectator() || manifested(server, r)) {
                continue;
            }
            Vec3 spot = Placement.spot(level, player.position(), 3, 6, player.getRandom());
            if (spot != null) {
                VampireNpc body = manifest(level, r, spot);
                body.setBoundOwner(player.getUUID());
                body.setRank(0);
            }
        }
    }

    // ---------------------------------------------------------------- hunts and ambushes

    /** The named vampire arrives to settle things, with followers, at night, out of the dark. */
    public static boolean ambush(ServerPlayer player, NemesisRecord r) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        if (manifested(server, r)) {
            return false;
        }
        Vec3 spot = Placement.spot(level, player.position(), 24, 38, player.getRandom());
        if (spot == null) {
            return false;
        }
        VampireNpc boss = manifest(level, r, spot);
        boss.setTarget(player);
        int retinue = Math.min(5, 1 + r.level / 6 + (r.has(Trait.COMMANDER) ? 2 : 0));
        for (int i = 0; i < retinue; i++) {
            Vec3 pos = Placement.spot(level, spot, 1, 5, player.getRandom());
            if (pos == null) {
                continue;
            }
            VampireNpc follower = NpcSpawner.vampire(level, pos, clanFor(server, r.faction, player.getRandom()));
            follower.setFaction(r.faction);
            follower.setTarget(player);
            follower.setExpiry(level.getGameTime() + 6000L);
        }
        player.displayClientMessage(Component.translatable("message.vtm.nemesis.ambush"), true);
        NemesisTaunts.say(boss, r, NemesisTaunts.Situation.AMBUSH, player);
        log(server, "ambush", r.faction, false, r, player.getGameProfile().getName());
        return true;
    }

    // ---------------------------------------------------------------- the chronicle

    /** Writes a line of history; the names of {@code involved} (then any {@code extra} text) are its arguments. */
    public static void log(MinecraftServer server, String key, ResourceLocation faction, boolean publicNews,
                           NemesisRecord first, String... extra) {
        log(server, key, faction, publicNews, List.of(first), extra);
    }

    public static void log(MinecraftServer server, String key, ResourceLocation faction, boolean publicNews,
                           List<NemesisRecord> involved, String... extra) {
        List<String> args = new ArrayList<>(involved.stream().map(r -> r.name).toList());
        args.addAll(List.of(extra));
        data(server).log(new ChronicleEntry(server.overworld().getGameTime(), key, args, Optional.of(faction),
                involved.stream().map(r -> r.id).toList(), publicNews));
    }

    private NemesisManager() {}
}

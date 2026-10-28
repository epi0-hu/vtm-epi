package com.epi0.vtm.npc;

import com.epi0.vtm.npc.goal.FeedOnPreyGoal;
import com.epi0.vtm.npc.goal.HomeStrollGoal;
import com.epi0.vtm.power.PassiveEffects;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * A vampire of the world: a clan, a sect, a hunger, and a hatred of the sun. Sleeps through the day in whatever
 * shelter it can find, prowls at night, and feeds on mortals, preferably where nobody will see.
 */
public class VampireNpc extends VtmNpc {
    public static final int SKIN_VARIANTS = 2;
    private static final float MAX_BLOOD = 10F;
    private static final float HUNGRY_BELOW = 4F;
    private static final int NPC_DISCIPLINE_LEVEL = 2;
    /** Rapport at which a vampire stops being a stranger, or becomes an enemy. */
    public static final int FRIENDLY_RAPPORT = 3;
    public static final int HOSTILE_RAPPORT = -5;

    private static final EntityDataAccessor<String> DATA_CLAN = SynchedEntityData.defineId(VampireNpc.class, EntityDataSerializers.STRING);

    private float blood = 8F;
    @Nullable
    private BlockPos shelter;

    public VampireNpc(EntityType<? extends VampireNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CLAN, "");
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new FeedOnPreyGoal(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(5, new HomeStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                player -> attitudeTo(player) == Attitude.HOSTILE));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, VampireNpc.class, 10, true, false,
                other -> attitudeTo(other) == Attitude.HOSTILE && !isResting()));
    }

    // ---------------------------------------------------------------- identity

    @Nullable
    public ResourceLocation clanId() {
        String raw = entityData.get(DATA_CLAN);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }

    public void setClan(ResourceLocation clan) {
        entityData.set(DATA_CLAN, clan.toString());
        clanDefinition().ifPresent(def -> setFaction(def.faction()));
        applyClanBonuses();
    }

    public java.util.Optional<Clan> clanDefinition() {
        ResourceLocation id = clanId();
        return id == null ? java.util.Optional.empty() : VtmRegistries.clan(level().registryAccess(), id);
    }

    @Override
    protected ResourceLocation defaultFaction() {
        return Clan.DEFAULT_FACTION;
    }

    @Override
    public String textureName() {
        ResourceLocation clan = clanId();
        return "vampire_" + (clan == null ? "default" : clan.getPath()) + "_" + Math.floorMod(variant(), SKIN_VARIANTS);
    }

    /** NPC vampires get the passive half of their clan's disciplines, so a Brujah hits harder and a Gangrel is tougher. */
    private void applyClanBonuses() {
        if (level().isClientSide) {
            return;
        }
        int celerity = 0;
        int potence = 0;
        int fortitude = 0;
        for (ResourceLocation discipline : clanDefinition().map(Clan::disciplines).orElse(List.of())) {
            switch (discipline.getPath()) {
                case "celerity" -> celerity = NPC_DISCIPLINE_LEVEL;
                case "potence" -> potence = NPC_DISCIPLINE_LEVEL;
                case "fortitude" -> fortitude = NPC_DISCIPLINE_LEVEL;
                default -> { }
            }
        }
        PassiveEffects.applyLevels(this, celerity, potence, fortitude);
    }

    // ---------------------------------------------------------------- hunger

    public float blood() {
        return blood;
    }

    public void setBlood(float blood) {
        this.blood = Math.max(0F, Math.min(MAX_BLOOD, blood));
    }

    public boolean isHungry() {
        return blood < HUNGRY_BELOW;
    }

    /** Actively stalking mortals: visible to civilians, who will keep away. */
    public boolean isHunting() {
        return state() == State.FEEDING || (isHungry() && isNight(level()) && !isResting());
    }

    /** A civilian within reach whose death would go unwitnessed (unless the sect doesn't care). */
    @Nullable
    public CivilianNpc findPrey() {
        boolean brazen = faction().map(f -> f.huntsMortals()).orElse(false);
        List<CivilianNpc> candidates = level().getEntitiesOfClass(CivilianNpc.class, getBoundingBox().inflate(24),
                c -> c.isAlive() && (brazen || witnessCount(c) == 0));
        return candidates.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    private int witnessCount(CivilianNpc prey) {
        return level().getEntitiesOfClass(LivingEntity.class, prey.getBoundingBox().inflate(12),
                e -> e != prey && e != this && e.isAlive()
                        && (e instanceof CivilianNpc civilian && !civilian.isResting()
                        || e instanceof Player player && !player.isSpectator())).size();
    }

    /** The kill (or near-kill). Anyone watching remembers. */
    public void feedOn(CivilianNpc prey) {
        prey.hurt(damageSources().mobAttack(this), 4F);
        setBlood(blood + 4F);
        prey.setLastHurtByMob(this);
        for (CivilianNpc witness : level().getEntitiesOfClass(CivilianNpc.class, getBoundingBox().inflate(16),
                c -> c != prey && c.isAlive() && c.hasLineOfSight(this))) {
            witness.setLastHurtByMob(this);
        }
    }

    // ---------------------------------------------------------------- daily rhythm

    @Override
    public boolean shouldRestNow() {
        return !isNight(level()) && getTarget() == null && state() != State.FEEDING;
    }

    @Nullable
    @Override
    public BlockPos restSpot() {
        return shelter != null ? shelter : home();
    }

    /** Somewhere the sun cannot reach, within a short walk. */
    @Nullable
    private BlockPos findShelter() {
        BlockPos origin = blockPosition();
        for (int i = 0; i < 40; i++) {
            BlockPos pos = origin.offset(random.nextInt(29) - 14, random.nextInt(7) - 3, random.nextInt(29) - 14);
            if (level().isLoaded(pos) && level().getBlockState(pos).isAir() && level().getBlockState(pos.above()).isAir()
                    && level().getBlockState(pos.below()).isSolid() && !level().canSeeSky(pos.above())) {
                return pos;
            }
        }
        return null;
    }

    @Override
    protected void serverSecond() {
        super.serverSecond();
        if (!isNight(level()) && (shelter == null || level().canSeeSky(shelter.above()))) {
            shelter = findShelter();
        }
        if (!isResting() && tickCount % 60 == 0) {
            setBlood(blood - 0.25F);
        }
    }

    public float sunMultiplier() {
        return clanDefinition().map(Clan::sunDamageMultiplier).orElse(1F);
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide && isAlive() && level().isDay() && isSunBurnTick()
                && getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !hasEffect(MobEffects.FIRE_RESISTANCE)) {
            igniteForSeconds(Math.max(1, Math.round(4F * sunMultiplier())));
        }
        super.aiStep();
    }

    // ---------------------------------------------------------------- attitudes

    @Override
    public Attitude attitudeTo(LivingEntity other) {
        if (other instanceof Player player) {
            int rapport = memory().rapport(player.getUUID());
            if (memory().isThreat(player.getUUID()) || rapport <= HOSTILE_RAPPORT) {
                return Attitude.HOSTILE;
            }
            if (rapport >= FRIENDLY_RAPPORT) {
                return Attitude.FRIENDLY;
            }
            boolean mortal = !VampireData.of(player).isVampire();
            boolean predatory = faction().map(f -> f.huntsMortals()).orElse(false);
            return mortal && predatory && isHungry() && isNight(level()) ? Attitude.HOSTILE : Attitude.NEUTRAL;
        }
        if (other instanceof VampireNpc vampire) {
            return faction().map(f -> f.relationTo(factionId(), vampire.factionId())).orElse(null)
                    == com.epi0.vtm.registry.Faction.Relation.ENEMY ? Attitude.HOSTILE : Attitude.NEUTRAL;
        }
        return Attitude.NEUTRAL;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim(CONTROLLER_ACTION, "attack");
        return super.doHurtTarget(target);
    }

    // ---------------------------------------------------------------- lifecycle & persistence

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnData);
        if (clanId() == null) {
            List<ResourceLocation> clans = level.registryAccess().registry(VtmRegistries.CLAN)
                    .map(r -> r.keySet().stream().sorted().toList()).orElse(List.of());
            if (!clans.isEmpty()) {
                setClan(clans.get(random.nextInt(clans.size())));
            }
        }
        setBlood(5F + random.nextInt(5));
        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Clan", entityData.get(DATA_CLAN));
        tag.putFloat("Blood", blood);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_CLAN, tag.getString("Clan"));
        blood = tag.contains("Blood") ? tag.getFloat("Blood") : blood;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        applyClanBonuses();
    }

}

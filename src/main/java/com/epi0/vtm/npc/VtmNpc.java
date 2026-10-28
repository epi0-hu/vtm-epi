package com.epi0.vtm.npc;

import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.npc.goal.RestGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;

/**
 * Shared base for every person in the world: a name, a look, a faction, a home, a daily rhythm, a memory of
 * the players it has met, and GeckoLib animations. Civilians and vampires differ in goals, not in structure.
 */
public abstract class VtmNpc extends PathfinderMob implements GeoEntity {
    public enum State {
        NORMAL, SLEEPING, COWERING, FEEDING;

        static State of(int ordinal) {
            return values()[Math.floorMod(ordinal, values().length)];
        }
    }

    public static final String CONTROLLER_MOVEMENT = "movement";
    public static final String CONTROLLER_ACTION = "action";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation COWER = RawAnimation.begin().thenLoop("cower");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("talk");

    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(VtmNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_FACTION = SynchedEntityData.defineId(VtmNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(VtmNpc.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final NpcMemory memory = new NpcMemory();
    @Nullable
    private BlockPos home;

    protected VtmNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder baseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    // ---------------------------------------------------------------- identity

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_FACTION, "");
        builder.define(DATA_STATE, State.NORMAL.ordinal());
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        entityData.set(DATA_VARIANT, variant);
    }

    public ResourceLocation factionId() {
        String raw = entityData.get(DATA_FACTION);
        ResourceLocation id = raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
        return id != null ? id : defaultFaction();
    }

    public void setFaction(ResourceLocation faction) {
        entityData.set(DATA_FACTION, faction.toString());
    }

    protected abstract ResourceLocation defaultFaction();

    public Optional<Faction> faction() {
        return VtmRegistries.faction(level().registryAccess(), factionId());
    }

    public State state() {
        return State.of(entityData.get(DATA_STATE));
    }

    public void setState(State state) {
        entityData.set(DATA_STATE, state.ordinal());
    }

    public boolean isResting() {
        return state() == State.SLEEPING;
    }

    public NpcMemory memory() {
        return memory;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(BlockPos home) {
        this.home = home;
    }

    /** Client + server: path of this NPC's skin under {@code textures/}. */
    public abstract String textureName();

    // ---------------------------------------------------------------- daily rhythm

    public static boolean isNight(Level level) {
        long time = level.getDayTime() % 24000L;
        return time >= 12500L && time < 23500L;
    }

    /** Whether the NPC should be curled up out of the way right now (civilians by night, vampires by day). */
    public abstract boolean shouldRestNow();

    /** Where to rest. */
    @Nullable
    public BlockPos restSpot() {
        return home;
    }

    public abstract Attitude attitudeTo(LivingEntity other);

    // ---------------------------------------------------------------- lifecycle

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnData);
        setVariant(random.nextInt(1 << 16));
        if (!hasCustomName()) {
            setCustomName(Component.literal(NpcNames.random(random)));
        }
        setHome(blockPosition());
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            setPersistenceRequired();
        }
        return data;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new RestGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            if (isResting()) {
                setState(State.NORMAL);
            }
            if (source.getEntity() instanceof Player player) {
                memory.remember(player.getUUID(), NpcMemory.Event.HARMED, level().getGameTime());
            }
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 20 == 0) {
            serverSecond();
        }
    }

    /** Once-a-second upkeep on the server. */
    protected void serverSecond() {
        if (isResting() && !shouldRestNow()) {
            setState(State.NORMAL);
        }
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(12),
                p -> !p.isSpectator() && hasLineOfSight(p))) {
            if (tickCount % 200 == 0) {
                memory.remember(player.getUUID(), NpcMemory.Event.SEEN, level().getGameTime());
            }
        }
    }

    /** Called by witnesses' logic when a player does something an NPC would notice. */
    public void witness(Player player, NpcMemory.Event event) {
        memory.remember(player.getUUID(), event, level().getGameTime());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putString("Faction", entityData.get(DATA_FACTION));
        tag.putInt("State", state().ordinal());
        tag.put("Memory", memory.save());
        if (home != null) {
            tag.putLong("Home", home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
        entityData.set(DATA_FACTION, tag.getString("Faction"));
        setState(State.of(tag.getInt("State")));
        if (tag.contains("Memory")) {
            memory.load(tag.get("Memory"));
        }
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
    }

    // ---------------------------------------------------------------- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER_MOVEMENT, 5, this::movement));
        // One-shots and forced poses; nothing plays here until triggered.
        controllers.add(new AnimationController<>(this, CONTROLLER_ACTION, 3, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("talk", TALK)
                .triggerableAnim("walk", WALK)
                .triggerableAnim("run", RUN)
                .triggerableAnim("idle", IDLE));
    }

    private PlayState movement(AnimationState<VtmNpc> animationState) {
        return switch (state()) {
            case SLEEPING -> animationState.setAndContinue(SLEEP);
            case COWERING -> animationState.setAndContinue(COWER);
            default -> {
                if (animationState.isMoving()) {
                    yield animationState.setAndContinue(walkAnimation.speed() > 0.55F ? RUN : WALK);
                }
                yield animationState.setAndContinue(IDLE);
            }
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    protected ServerLevel serverLevel() {
        return (ServerLevel) level();
    }
}

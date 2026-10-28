package com.epi0.vtm.npc;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.npc.goal.HomeStrollGoal;
import com.epi0.vtm.npc.goal.ScheduleGoal;
import com.epi0.vtm.npc.goal.WatchmanAttackGoal;
import com.epi0.vtm.vampire.Frenzy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * An ordinary mortal. Sleeps at night, strolls near home by day, and bolts from anything frightening:
 * monsters, hungry vampires, and players who have hurt, fed on, or frenzied in front of them.
 */
public class CivilianNpc extends VtmNpc {
    public static final ResourceLocation MORTALS = VtmMod.id("mortals");
    public static final int SKIN_COUNT = 8;
    /** Rapport at which a civilian counts a player as a friend. */
    public static final int FRIENDLY_RAPPORT = 3;
    /** Rapport at which a civilian forgives a player who has hurt or frightened them. */
    public static final int FORGIVEN_RAPPORT = 5;
    /** Heat at which the watch starts watching, and at which it attacks; and at which ordinary people take fright. */
    public static final int SUSPICIOUS = 30;
    public static final int HOT = 60;
    public static final int FRIGHTENED = 40;

    private static final EntityDataAccessor<Integer> DATA_JOB = SynchedEntityData.defineId(CivilianNpc.class, EntityDataSerializers.INT);

    @Nullable
    private UUID cityId;
    @Nullable
    private BlockPos work;
    private long expireAt;

    public CivilianNpc(EntityType<? extends CivilianNpc> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_JOB, Job.RESIDENT.ordinal());
    }

    // ---------------------------------------------------------------- city life

    public Job job() {
        return Job.of(entityData.get(DATA_JOB));
    }

    @Nullable
    public UUID cityId() {
        return cityId;
    }

    @Nullable
    public BlockPos work() {
        return work;
    }

    /** Makes this person a resident of {@code city}, with a bed, and perhaps a place of work. */
    public void settle(City city, BlockPos bed, Job job, @Nullable BlockPos work) {
        this.cityId = city.id;
        this.work = work;
        entityData.set(DATA_JOB, job.ordinal());
        setHome(bed);
        if (job == Job.WATCHMAN) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            setDropChance(EquipmentSlot.MAINHAND, 0F);
            setDropChance(EquipmentSlot.HEAD, 0F);
            setDropChance(EquipmentSlot.CHEST, 0F);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30);
            setHealth(30F);
        }
    }

    public void setExpiry(long gameTime) {
        this.expireAt = gameTime;
    }

    @Nullable
    public City city() {
        MinecraftServer server = level().getServer();
        return cityId == null || server == null ? null : CityData.get(server).get(cityId);
    }

    /** The part of the day, as the city sees it. */
    public enum Slot { WORK, LEISURE, NIGHTLIFE, SLEEP }

    public Slot slot() {
        long time = level().getDayTime() % 24000L;
        if (job() == Job.WATCHMAN) {
            return time >= 18000L && time < 22000L ? Slot.SLEEP : Slot.WORK;
        }
        if (time < 9000L) {
            return Slot.WORK;
        }
        if (time < 12500L) {
            return Slot.LEISURE;
        }
        return time < 17000L ? Slot.NIGHTLIFE : Slot.SLEEP;
    }

    /** Whether this person goes out in the evening (some do, most do not). */
    private boolean nightOwl() {
        return Math.floorMod(getUUID().hashCode(), 3) == 0;
    }

    /** Where the hour calls this person to be. */
    @Nullable
    public BlockPos anchor() {
        City city = city();
        if (city == null) {
            return home();
        }
        return switch (slot()) {
            case WORK -> work != null ? work : city.plaza != null ? city.plaza : home();
            case LEISURE -> job() == Job.SHOPKEEPER && work != null ? work : city.plaza != null ? city.plaza : home();
            case NIGHTLIFE -> job() == Job.BARTENDER && work != null ? work
                    : nightOwl() && city.club != null ? city.club : home();
            case SLEEP -> home();
        };
    }

    @Nullable
    @Override
    public BlockPos strollCentre() {
        return anchor();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5) {
            @Override
            public boolean canUse() {
                return job() != Job.WATCHMAN && super.canUse();
            }
        });
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 12F, 1.0, 1.5,
                player -> job() != Job.WATCHMAN && attitudeTo(player).compareTo(Attitude.WARY) >= 0));
        goalSelector.addGoal(2, new WatchmanAttackGoal(this, 1.2));
        goalSelector.addGoal(3, new ScheduleGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                player -> job() == Job.WATCHMAN && attitudeTo(player) == Attitude.HOSTILE));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                monster -> job() == Job.WATCHMAN));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, VampireNpc.class, 14F, 1.0, 1.5,
                vampire -> attitudeTo(vampire).compareTo(Attitude.WARY) >= 0));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, LivingEntity.class, 8F, 1.0, 1.4,
                other -> job() != Job.WATCHMAN && other instanceof Enemy));
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new HomeStrollGoal(this, 0.8));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected ResourceLocation defaultFaction() {
        return MORTALS;
    }

    @Override
    public boolean shouldRestNow() {
        return cityId != null ? slot() == Slot.SLEEP : isNight(level());
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && cityId != null && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            City city = city();
            if (city != null) {
                com.epi0.vtm.city.CityLife.crime(player, city, 8);
            }
        }
        return hurt;
    }

    @Override
    protected void serverSecond() {
        super.serverSecond();
        if (expireAt > 0 && level().getGameTime() > expireAt && getTarget() == null) {
            discard();
        }
    }

    @Override
    public Attitude attitudeTo(LivingEntity other) {
        if (other instanceof Player player) {
            City city = city();
            int heat = city == null ? 0 : city.heatOf(player.getUUID());
            if (job() == Job.WATCHMAN) {
                if (heat >= HOT) {
                    return Attitude.HOSTILE;
                }
                if (heat >= SUSPICIOUS) {
                    return Attitude.WARY;
                }
            } else if (heat >= FRIGHTENED) {
                return Attitude.WARY;
            }
            int rapport = memory().rapport(player.getUUID());
            // Fear is forgotten only once the player has genuinely made amends.
            boolean afraid = Frenzy.isFrenzied(player) || memory().isThreat(player.getUUID()) && rapport < FORGIVEN_RAPPORT;
            if (afraid) {
                return Attitude.WARY;
            }
            return rapport >= FRIENDLY_RAPPORT ? Attitude.FRIENDLY : Attitude.NEUTRAL;
        }
        if (other instanceof VampireNpc vampire) {
            return vampire.isHunting() ? Attitude.WARY : Attitude.NEUTRAL;
        }
        return other instanceof Enemy ? Attitude.WARY : Attitude.NEUTRAL;
    }

    @Override
    public String textureName() {
        return "civilian_" + Math.floorMod(variant(), SKIN_COUNT);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Job", job().ordinal());
        if (cityId != null) {
            tag.putUUID("City", cityId);
        }
        if (work != null) {
            tag.putLong("Work", work.asLong());
        }
        tag.putLong("ExpireAt", expireAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_JOB, tag.getInt("Job"));
        cityId = tag.hasUUID("City") ? tag.getUUID("City") : null;
        work = tag.contains("Work") ? BlockPos.of(tag.getLong("Work")) : null;
        expireAt = tag.getLong("ExpireAt");
    }
}

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

    private Persona persona;
    @Nullable
    private UUID ghoulOwner;
    /** The hunters' lodge this person belongs to, if they are a hunter of one. */
    @Nullable
    private UUID lodgeId;

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

    /** The vampire whose blood this person drinks and whom they serve, if any. */
    @Nullable
    public UUID ghoulOwner() {
        return ghoulOwner;
    }

    /** The vampire gives this mortal their blood; they are stronger for it, and theirs from then on. */
    public void makeGhoul(Player master) {
        this.ghoulOwner = master.getUUID();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(30);
        setHealth(30F);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0F);
        memory().adjustRapport(master.getUUID(), NpcMemory.MAX_RAPPORT, level().getGameTime());
    }

    /** Gives this person a particular personality (tests and tools; it is not saved). */
    public void usePersona(Persona persona) {
        this.persona = persona;
    }

    @Nullable
    public UUID lodgeId() {
        return lodgeId;
    }

    /** Makes this person a hunter of a lodge: it is where they live, and it is who they answer to. */
    public void settleAtLodge(BlockPos standard, UUID lodge) {
        this.lodgeId = lodge;
        entityData.set(DATA_JOB, Job.HUNTER.ordinal());
        setHome(standard);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(26);
        setHealth(26F);
    }

    /** Hunters dress as anyone until they are sure; then they arm themselves. */
    public void armAsHunter() {
        if (getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(com.epi0.vtm.registry.ModItems.WARD_VEST.get()));
            setDropChance(EquipmentSlot.MAINHAND, 0F);
            setDropChance(EquipmentSlot.CHEST, 0F);
        }
    }

    /** The hunters' leader in a city: tougher, in iron from head to chest, and already armed. */
    public void makeInquisitor() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(44);
        setHealth(44F);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        armAsHunter();
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setDropChance(EquipmentSlot.HEAD, 0F);
    }

    /** Who this person is. */
    public Persona persona() {
        if (persona == null) {
            persona = Persona.of(getUUID());
        }
        return persona;
    }

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
        Household.takeFamilyName(this);
        if (job == Job.HUNTER) {
            getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(26);
            setHealth(26F);
        }
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
        goalSelector.addGoal(2, new com.epi0.vtm.npc.goal.FollowMasterGoal(this));
        goalSelector.addGoal(3, new ScheduleGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                player -> job() == Job.WATCHMAN && attitudeTo(player) == Attitude.HOSTILE));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                monster -> job() == Job.WATCHMAN || ghoulOwner != null));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                player -> job() == Job.HUNTER && attitudeTo(player) == Attitude.HOSTILE));
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
            for (CivilianNpc kin : Household.members(this)) { // what befalls one is told to the whole household
                kin.witness(player, NpcMemory.Event.HARMED);
            }
        }
        return hurt;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            com.epi0.vtm.city.Herd.died(this);
        }
        if (!level().isClientSide && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer slayer && job() == Job.HUNTER) {
            com.epi0.vtm.city.Hunters.onHunterKilled(this, slayer);
            com.epi0.vtm.city.Lodges.onHunterKilled(this, slayer);
        }
        if (!level().isClientSide && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            long now = level().getGameTime();
            for (CivilianNpc kin : Household.members(this)) {
                kin.memory().setFlag(player.getUUID(), "kin_slain", true, now);
                kin.memory().adjustRapport(player.getUUID(), -10, now);
                kin.witness(player, NpcMemory.Event.HARMED);
            }
        }
    }

    /** A hunter who knows what a player is goes after them, wherever in earshot they are. */
    private void hunt() {
        if (getTarget() != null && getTarget().isAlive()) {
            return;
        }
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(48), p -> !p.isSpectator() && !p.isCreative())) {
            if (attitudeTo(player) == Attitude.HOSTILE) {
                armAsHunter();
                setTarget(player);
                return;
            }
        }
    }

    @Override
    protected void serverSecond() {
        super.serverSecond();
        Gossip.tick(this);
        if (tickCount % 200 == 0) {
            com.epi0.vtm.city.Herd.sync(this);
        }
        if (job() == Job.HUNTER) {
            hunt();
        }
        if (expireAt > 0 && level().getGameTime() > expireAt && getTarget() == null) {
            discard();
        }
    }

    @Override
    public Attitude attitudeTo(LivingEntity other) {
        if (other instanceof Player player) {
            if (player.getUUID().equals(ghoulOwner)) {
                return Attitude.FRIENDLY;
            }
            City city = city();
            int heat = city == null ? 0 : city.heatOf(player.getUUID());
            if (job() == Job.HUNTER) {
                // Hunters care about one thing: whether you are what they hunt, and whether they know it.
                MinecraftServer server = level().getServer();
                int exposure = city != null ? city.exposureOf(player.getUUID())
                        : lodgeId != null && server != null ? com.epi0.vtm.city.Lodges.notoriety(server, level().dimension(), blockPosition(), player.getUUID()) : 0;
                if (com.epi0.vtm.vampire.VampireData.of(player).isVampire()) {
                    if (exposure >= com.epi0.vtm.city.Hunters.HUNTED) {
                        return Attitude.HOSTILE;
                    }
                    if (exposure >= 25) {
                        return Attitude.WARY;
                    }
                }
                return memory().rapport(player.getUUID()) >= persona().friendRapport() ? Attitude.FRIENDLY : Attitude.NEUTRAL;
            }
            if (job() == Job.WATCHMAN) {
                if (heat >= HOT) {
                    return Attitude.HOSTILE;
                }
                if (heat >= SUSPICIOUS) {
                    return Attitude.WARY;
                }
            } else if (heat >= persona().fearHeat()) {
                return Attitude.WARY;
            }
            int rapport = memory().rapport(player.getUUID());
            // Fear is forgotten only once the player has genuinely made amends.
            boolean afraid = Frenzy.isFrenzied(player) || memory().isThreat(player.getUUID()) && rapport < FORGIVEN_RAPPORT
                    || memory().hasFlag(player.getUUID(), "kin_slain")
                    || memory().hasFlag(player.getUUID(), "rumour") && rapport < FRIENDLY_RAPPORT;
            if (afraid) {
                return Attitude.WARY;
            }
            // The devout can feel what has become of a soul that has slipped far.
            if (persona().temperament() == Persona.Temperament.DEVOUT && rapport < FORGIVEN_RAPPORT
                    && com.epi0.vtm.vampire.VampireData.of(player).isVampire() && com.epi0.vtm.vampire.VampireData.of(player).humanity() <= 3) {
                return Attitude.WARY;
            }
            return rapport >= persona().friendRapport() ? Attitude.FRIENDLY : Attitude.NEUTRAL;
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
        if (ghoulOwner != null) {
            tag.putUUID("GhoulOwner", ghoulOwner);
        }
        if (lodgeId != null) {
            tag.putUUID("Lodge", lodgeId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_JOB, tag.getInt("Job"));
        cityId = tag.hasUUID("City") ? tag.getUUID("City") : null;
        work = tag.contains("Work") ? BlockPos.of(tag.getLong("Work")) : null;
        expireAt = tag.getLong("ExpireAt");
        ghoulOwner = tag.hasUUID("GhoulOwner") ? tag.getUUID("GhoulOwner") : null;
        lodgeId = tag.hasUUID("Lodge") ? tag.getUUID("Lodge") : null;
    }
}

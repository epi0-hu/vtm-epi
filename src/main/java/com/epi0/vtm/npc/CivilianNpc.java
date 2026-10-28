package com.epi0.vtm.npc;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.goal.HomeStrollGoal;
import com.epi0.vtm.vampire.Frenzy;
import net.minecraft.resources.ResourceLocation;
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

    public CivilianNpc(EntityType<? extends CivilianNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 12F, 1.0, 1.5,
                player -> attitudeTo(player).compareTo(Attitude.WARY) >= 0));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, VampireNpc.class, 14F, 1.0, 1.5,
                vampire -> attitudeTo(vampire).compareTo(Attitude.WARY) >= 0));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, LivingEntity.class, 8F, 1.0, 1.4,
                other -> other instanceof Enemy));
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
        return isNight(level());
    }

    @Override
    public Attitude attitudeTo(LivingEntity other) {
        if (other instanceof Player player) {
            return memory().isThreat(player.getUUID()) || Frenzy.isFrenzied(player) ? Attitude.WARY : Attitude.NEUTRAL;
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
}

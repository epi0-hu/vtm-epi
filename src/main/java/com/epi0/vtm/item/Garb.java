package com.epi0.vtm.item;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.Job;
import com.epi0.vtm.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What the clothes do. Street clothes and evening wear let a vampire pass among the living, evening wear opens doors at court,
 * robes steady the hand of the occult, and ward gear is worth a great deal against hunters.
 */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class Garb {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Pieces of street or formal wear it takes to pass for one of the living. */
    public static final int BLEND_PIECES = 3;
    /** Pieces of evening wear it takes to be taken seriously at court. */
    public static final int FORMAL_PIECES = 3;
    /** How much of the exposure a blending vampire causes is noticed. */
    public static final float BLEND_FACTOR = 0.6F;
    /** What a robed vampire's powers cost. */
    public static final float ROBE_COST_FACTOR = 0.85F;
    /** What hunters' bolts and bullets do against ward gear. */
    public static final float WARD_SHOT_FACTOR = 0.65F;
    /** What hunters' blows do against ward gear. */
    public static final float WARD_FACTOR = 0.75F;

    public static int pieces(LivingEntity wearer, Attire attire) {
        int n = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (wearer.getItemBySlot(slot).getItem() instanceof Garment g && g.attire() == attire) {
                n++;
            }
        }
        return n;
    }

    /** Dressed to pass among mortals. */
    public static boolean blendsIn(LivingEntity wearer) {
        return pieces(wearer, Attire.STREET) + pieces(wearer, Attire.FORMAL) >= BLEND_PIECES;
    }

    /** Dressed for court. */
    public static boolean formal(LivingEntity wearer) {
        return pieces(wearer, Attire.FORMAL) >= FORMAL_PIECES;
    }

    /** Cowled and robed. */
    public static boolean robed(LivingEntity wearer) {
        return wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof Garment h && h.attire() == Attire.ROBE
                && wearer.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof Garment c && c.attire() == Attire.ROBE;
    }

    /** Extra dice on a conversation. */
    public static int socialDice(LivingEntity wearer) {
        return formal(wearer) ? 1 : 0;
    }

    /** What a power costs, given what its caster wears. */
    public static float powerCostFactor(LivingEntity caster) {
        return robed(caster) ? ROBE_COST_FACTOR : 1F;
    }

    /** How much of {@code amount} of exposure a vampire in these clothes is actually seen to cause. */
    public static int exposure(LivingEntity wearer, int amount) {
        return amount > 0 && blendsIn(wearer) ? Math.max(1, Math.round(amount * BLEND_FACTOR)) : amount;
    }

    private static boolean wardWorn(LivingEntity wearer) {
        return pieces(wearer, Attire.WARD) > 0;
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof CivilianNpc hunter && hunter.job() == Job.HUNTER
                && event.getEntity() instanceof Player player && wardWorn(player)) {
            event.setAmount(event.getAmount() * (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) ? WARD_SHOT_FACTOR : WARD_FACTOR));
        }
    }

    /** Hunters wear ward gear, and now and then a dead one's can be taken. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof CivilianNpc hunter && hunter.job() == Job.HUNTER && !hunter.level().isClientSide
                && hunter.getRandom().nextFloat() < (hunter.getMaxHealth() > 40 ? 0.5F : 0.12F)) {
            ItemStack drop = new ItemStack(hunter.getRandom().nextBoolean() ? ModItems.WARD_VEST.get() : ModItems.COMBAT_BOOTS.get());
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(hunter.level(), hunter.getX(), hunter.getY(), hunter.getZ(), drop));
        }
    }

    private Garb() {}
}

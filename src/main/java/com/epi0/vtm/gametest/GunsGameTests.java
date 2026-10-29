package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.item.HunterArms;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hunters shoot: what they carry, when they fire, and what the shot is. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GunsGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(GunsGameTests.class);
    }

    @GameTest(template = "empty", batch = "guns")
    public static void huntersCarryCrossbowsAndInquisitorsFirearms(GameTestHelper helper) {
        CivilianNpc hunter = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        hunter.armAsHunter();
        helper.assertTrue(HunterArms.profile(hunter.getOffhandItem()) == HunterArms.Profile.CROSSBOW, "an ordinary hunter has a crossbow");
        CivilianNpc inquisitor = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2, 2.5)));
        inquisitor.makeInquisitor();
        helper.assertTrue(HunterArms.profile(inquisitor.getOffhandItem()) == HunterArms.Profile.RIFLE, "an inquisitor has a firearm");
        helper.assertTrue(new ItemStack(ModItems.HUNTER_RIFLE.get()).is(HunterArms.FIREARMS), "ours is on the list a modpack can extend");
        helper.assertTrue(HunterArms.profile(new ItemStack(Items.STICK)) == null && HunterArms.profile(ItemStack.EMPTY) == null, "nothing else is a weapon");
        helper.assertTrue(HunterArms.Profile.RIFLE.damage * HunterArms.Profile.RIFLE.speed > HunterArms.Profile.CROSSBOW.damage * HunterArms.Profile.CROSSBOW.speed
                && HunterArms.Profile.RIFLE.inaccuracy < HunterArms.Profile.CROSSBOW.inaccuracy, "the rifle hits harder and truer");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "guns")
    public static void aHunterShootsFromADistance(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        CivilianNpc hunter = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        hunter.settleAtLodge(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)), java.util.UUID.randomUUID());
        hunter.armAsHunter();
        player.teleportTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)).x, helper.absoluteVec(new Vec3(6.5, 2, 6.5)).y, helper.absoluteVec(new Vec3(6.5, 2, 6.5)).z);
        hunter.setTarget(player);
        helper.succeedWhen(() -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AbstractArrow.class, hunter.getBoundingBox().inflate(40),
                a -> a.getOwner() == hunter).isEmpty(), "the hunter opens fire"));
    }

    @GameTest(template = "empty", batch = "guns")
    public static void aPlayerCanFireAHuntersWeaponToo(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "ventrue");
        ItemStack rifle = new ItemStack(ModItems.HUNTER_RIFLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rifle);
        var result = rifle.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "the shot goes off");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(6), a -> a.getOwner() == player).isEmpty(), "and a shot is in the air");
        helper.assertTrue(rifle.getDamageValue() == 1, "it wears");
        helper.assertTrue(player.getCooldowns().isOnCooldown(rifle.getItem()), "and must be reloaded");
        helper.assertTrue(!rifle.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "no second shot before then");
        helper.succeed();
    }
}

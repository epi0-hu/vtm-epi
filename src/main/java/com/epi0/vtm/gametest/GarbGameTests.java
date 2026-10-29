package com.epi0.vtm.gametest;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.item.Attire;
import com.epi0.vtm.item.Garb;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.NpcSpawner;
import com.epi0.vtm.power.PowerManager;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.registry.ModItems;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Clothes, robes and armor: what each kind does, and only when enough of it is worn. */
@GameTestHolder(VtmMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = VtmMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GarbGameTests {

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(GarbGameTests.class);
    }

    private static void dress(ServerPlayer p, net.minecraft.world.item.Item head, net.minecraft.world.item.Item chest,
                              net.minecraft.world.item.Item legs, net.minecraft.world.item.Item feet) {
        p.setItemSlot(EquipmentSlot.HEAD, head == null ? ItemStack.EMPTY : new ItemStack(head));
        p.setItemSlot(EquipmentSlot.CHEST, chest == null ? ItemStack.EMPTY : new ItemStack(chest));
        p.setItemSlot(EquipmentSlot.LEGS, legs == null ? ItemStack.EMPTY : new ItemStack(legs));
        p.setItemSlot(EquipmentSlot.FEET, feet == null ? ItemStack.EMPTY : new ItemStack(feet));
    }

    @GameTest(template = "empty", batch = "garb")
    public static void streetClothesAndEveningWearLetAVampirePassForTheLiving(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "gangrel");
        dress(player, null, null, null, null);
        helper.assertTrue(!Garb.blendsIn(player) && Garb.exposure(player, 10) == 10, "naked of clothes, seen as they are");
        dress(player, null, ModItems.JACKET.get(), ModItems.TROUSERS.get(), null);
        helper.assertTrue(!Garb.blendsIn(player), "two pieces are not enough");
        dress(player, ModItems.CAP.get(), ModItems.JACKET.get(), ModItems.TROUSERS.get(), null);
        helper.assertTrue(Garb.blendsIn(player) && Garb.exposure(player, 10) == 6, "three pieces of street wear pass: " + Garb.exposure(player, 10));
        helper.assertTrue(Garb.exposure(player, -10) == -10 && Garb.exposure(player, 1) == 1, "only getting caught is softened, and never to nothing");
        dress(player, ModItems.TOP_HAT.get(), ModItems.JACKET.get(), ModItems.DRESS_TROUSERS.get(), null);
        helper.assertTrue(Garb.blendsIn(player) && !Garb.formal(player), "street and formal mix for blending, not for court");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "garb")
    public static void eveningWearOpensDoorsAndRobesSteadyTheHand(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "ventrue");
        dress(player, ModItems.TOP_HAT.get(), ModItems.TAILCOAT.get(), ModItems.DRESS_TROUSERS.get(), ModItems.DRESS_SHOES.get());
        helper.assertTrue(Garb.formal(player) && Garb.socialDice(player) == 1, "formal wear gives a die");
        dress(player, ModItems.TOP_HAT.get(), ModItems.TAILCOAT.get(), null, null);
        helper.assertTrue(Garb.socialDice(player) == 0, "but not half of it");

        var power = Powers.get(VtmMod.id("sense_the_beast")).orElseThrow();
        var data = VampireData.of(player);
        float bare = PowerManager.scaledCost(player, data, power);
        dress(player, ModItems.COWL.get(), ModItems.ROBE.get(), null, null);
        helper.assertTrue(Garb.robed(player), "cowl and robe together");
        helper.assertTrue(PowerManager.scaledCost(player, data, power) <= bare, "robes never cost more");
        dress(player, ModItems.COWL.get(), null, null, null);
        helper.assertTrue(!Garb.robed(player) && Garb.powerCostFactor(player) == 1F, "a cowl alone does nothing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "garb")
    public static void wardGearIsWhatHuntersCarry(GameTestHelper helper) {
        ServerPlayer player = VtmGameTests.vampire(helper, "brujah");
        dress(player, null, ModItems.WARD_VEST.get(), null, ModItems.COMBAT_BOOTS.get());
        helper.assertTrue(Garb.pieces(player, Attire.WARD) == 2, "ward pieces are counted");
        helper.assertTrue(ModItems.WARD_VEST.get().getDefense() >= 5 && ModItems.COMBAT_BOOTS.get().getDefense() >= 2, "and armored");
        CivilianNpc hunter = NpcSpawner.civilian(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        hunter.armAsHunter();
        helper.assertTrue(hunter.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.WARD_VEST.get()), "hunters wear the vest");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "garb")
    public static void garmentsAreDyeable(GameTestHelper helper) {
        for (var item : java.util.List.of(ModItems.CAP, ModItems.JACKET, ModItems.TAILCOAT, ModItems.ROBE, ModItems.DUSTER, ModItems.SHOES)) {
            helper.assertTrue(new ItemStack(item.get()).get(DataComponents.DYED_COLOR) != null, item.getId() + " carries a default dye");
            helper.assertTrue(new ItemStack(item.get()).is(net.minecraft.tags.ItemTags.DYEABLE), item.getId() + " can be dyed");
        }
        helper.assertTrue(new ItemStack(ModItems.WARD_VEST.get()).get(DataComponents.DYED_COLOR) == null, "ward gear is not for dyeing");
        helper.succeed();
    }
}

package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.item.Attire;
import com.epi0.vtm.item.Garment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VtmMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VtmMod.MOD_ID);

    public static final DeferredItem<DeferredSpawnEggItem> CIVILIAN_SPAWN_EGG = ITEMS.register("civilian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CIVILIAN, 0xD7C9A8, 0x7F6A4F, new net.minecraft.world.item.Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> VAMPIRE_SPAWN_EGG = ITEMS.register("vampire_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VAMPIRE, 0x1B1B24, 0x8B0000, new net.minecraft.world.item.Item.Properties()));

    // ---------------------------------------------------------------- garments

    /** Default dye of each dyeable piece, as 0xRRGGBB. */
    public static final java.util.Map<String, Integer> DEFAULT_DYE = new java.util.LinkedHashMap<>();

    private static DeferredItem<Garment> garment(String name, net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material,
                                                 ArmorItem.Type type, Attire attire, int dye) {
        DEFAULT_DYE.put(name, dye);
        return ITEMS.register(name, () -> new Garment(material, type, attire, new Item.Properties()
                .component(DataComponents.DYED_COLOR, new DyedItemColor(dye, false))));
    }

    private static DeferredItem<Garment> plain(String name, net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material,
                                               ArmorItem.Type type, Attire attire) {
        return ITEMS.register(name, () -> new Garment(material, type, attire, new Item.Properties()));
    }

    public static final DeferredItem<Garment> CAP = garment("cap", ModArmor.STREET, ArmorItem.Type.HELMET, Attire.STREET, 0x5C5F66);
    public static final DeferredItem<Garment> JACKET = garment("jacket", ModArmor.STREET, ArmorItem.Type.CHESTPLATE, Attire.STREET, 0x4A5568);
    public static final DeferredItem<Garment> TROUSERS = garment("trousers", ModArmor.STREET, ArmorItem.Type.LEGGINGS, Attire.STREET, 0x3B3F4A);
    public static final DeferredItem<Garment> SHOES = garment("shoes", ModArmor.STREET, ArmorItem.Type.BOOTS, Attire.STREET, 0x57402D);

    public static final DeferredItem<Garment> TOP_HAT = garment("top_hat", ModArmor.FORMAL, ArmorItem.Type.HELMET, Attire.FORMAL, 0x17171C);
    public static final DeferredItem<Garment> TAILCOAT = garment("tailcoat", ModArmor.FORMAL, ArmorItem.Type.CHESTPLATE, Attire.FORMAL, 0x1B1B22);
    public static final DeferredItem<Garment> DRESS_TROUSERS = garment("dress_trousers", ModArmor.FORMAL, ArmorItem.Type.LEGGINGS, Attire.FORMAL, 0x1B1B22);
    public static final DeferredItem<Garment> DRESS_SHOES = garment("dress_shoes", ModArmor.FORMAL, ArmorItem.Type.BOOTS, Attire.FORMAL, 0x101014);

    public static final DeferredItem<Garment> COWL = garment("cowl", ModArmor.ROBE, ArmorItem.Type.HELMET, Attire.ROBE, 0x2A1F33);
    public static final DeferredItem<Garment> ROBE = garment("robe", ModArmor.ROBE, ArmorItem.Type.CHESTPLATE, Attire.ROBE, 0x2A1F33);

    public static final DeferredItem<Garment> DUSTER = garment("duster", ModArmor.DUSTER, ArmorItem.Type.CHESTPLATE, Attire.STREET, 0x6B4A2E);
    public static final DeferredItem<Garment> WARD_VEST = plain("ward_vest", ModArmor.WARD, ArmorItem.Type.CHESTPLATE, Attire.WARD);
    public static final DeferredItem<Garment> COMBAT_BOOTS = plain("combat_boots", ModArmor.WARD, ArmorItem.Type.BOOTS, Attire.WARD);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("vtm",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.vtm"))
                    .icon(() -> new ItemStack(VAMPIRE_SPAWN_EGG.get()))
                    .displayItems((params, output) -> {
                        output.accept(CIVILIAN_SPAWN_EGG.get());
                        output.accept(VAMPIRE_SPAWN_EGG.get());
                        output.accept(ModBlocks.COFFIN.get());
                        for (var garment : new DeferredItem<?>[]{CAP, JACKET, TROUSERS, SHOES, DUSTER, TOP_HAT, TAILCOAT, DRESS_TROUSERS, DRESS_SHOES,
                                COWL, ROBE, WARD_VEST, COMBAT_BOOTS}) {
                            output.accept(garment.get());
                        }
                    })
                    .build());

    private ModItems() {}
}

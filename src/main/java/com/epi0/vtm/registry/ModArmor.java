package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** What the garments are made of. Dyeable materials draw a grayscale base tinted by the dye, with an undyed overlay of fastenings and trim. */
public final class ModArmor {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, VtmMod.MOD_ID);

    public static final Holder<ArmorMaterial> STREET = cloth("street", 0, 1, 1, 0, () -> Ingredient.of(Items.WHITE_WOOL));
    public static final Holder<ArmorMaterial> FORMAL = cloth("formal", 0, 2, 1, 0, () -> Ingredient.of(Items.BLACK_WOOL));
    public static final Holder<ArmorMaterial> ROBE = cloth("robe", 0, 2, 1, 0, () -> Ingredient.of(Items.BLACK_WOOL));
    public static final Holder<ArmorMaterial> DUSTER = cloth("duster", 0, 4, 2, 0, () -> Ingredient.of(Items.LEATHER));
    public static final Holder<ArmorMaterial> WARD = MATERIALS.register("ward", () -> material(
            "ward", false, 2, 5, 3, 0.5F, () -> Ingredient.of(Items.IRON_INGOT)));

    private static Holder<ArmorMaterial> cloth(String name, int boots, int chest, int head, float toughness, Supplier<Ingredient> repair) {
        return MATERIALS.register(name, () -> material(name, true, boots, chest, head, toughness, repair));
    }

    private static ArmorMaterial material(String name, boolean dyeable, int boots, int chest, int head, float toughness, Supplier<Ingredient> repair) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, boots);
        defense.put(ArmorItem.Type.LEGGINGS, Math.max(0, chest - 1));
        defense.put(ArmorItem.Type.CHESTPLATE, chest);
        defense.put(ArmorItem.Type.HELMET, head);
        defense.put(ArmorItem.Type.BODY, chest);
        var id = VtmMod.id(name);
        List<ArmorMaterial.Layer> layers = dyeable
                ? List.of(new ArmorMaterial.Layer(id, "", true), new ArmorMaterial.Layer(id, "_overlay", false))
                : List.of(new ArmorMaterial.Layer(id));
        return new ArmorMaterial(defense, 12, SoundEvents.ARMOR_EQUIP_LEATHER, repair, layers, toughness, 0F);
    }

    private ModArmor() {}
}

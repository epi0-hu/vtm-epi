package com.epi0.vtm.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/** A piece of clothing or armor, of one {@link Attire}. */
public class Garment extends ArmorItem {
    private final Attire attire;

    public Garment(Holder<ArmorMaterial> material, Type type, Attire attire, Properties properties) {
        super(material, type, properties.durability(type.getDurability(attire == Attire.WARD ? 26 : 12)));
        this.attire = attire;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("item.vtm.attire." + attire.id() + ".tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    public Attire attire() {
        return attire;
    }
}

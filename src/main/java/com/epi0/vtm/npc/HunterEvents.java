package com.epi0.vtm.npc;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** What hunters carry: weapons that hurt the undead more than they should. */
@EventBusSubscriber(modid = VtmMod.MOD_ID)
public final class HunterEvents {
    /** Hunters' blows on a vampire land a half as hard again. */
    public static final float VAMPIRE_BONUS = 1.5F;

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof CivilianNpc hunter && hunter.job() == Job.HUNTER
                && event.getEntity() instanceof Player player && VampireData.of(player).isVampire()) {
            event.setAmount(event.getAmount() * VAMPIRE_BONUS);
        }
    }

    private HunterEvents() {}
}

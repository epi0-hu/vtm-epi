package com.epi0.vtm.dialogue;

import com.epi0.vtm.city.City;
import com.epi0.vtm.city.CityData;
import com.epi0.vtm.npc.CivilianNpc;
import com.epi0.vtm.npc.VtmNpc;
import org.jetbrains.annotations.Nullable;

/** Which city an NPC is part of, for conversations. */
final class DialogueCity {

    @Nullable
    static City of(VtmNpc npc) {
        if (npc.level().getServer() == null) {
            return null;
        }
        if (npc instanceof CivilianNpc civilian && civilian.cityId() != null) {
            return civilian.city();
        }
        return CityData.get(npc.level().getServer()).at(npc.level(), npc.blockPosition());
    }

    private DialogueCity() {}
}

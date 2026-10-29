package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.nemesis.NemesisKnowledge;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.vampire.VampireData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VtmMod.MOD_ID);

    /** Persisted on the player, kept through death. Synced to the owning client by {@code SyncVampirePayload}. */
    public static final Supplier<AttachmentType<VampireData>> VAMPIRE = ATTACHMENTS.register("vampire",
            () -> AttachmentType.builder(() -> VampireData.DEFAULT)
                    .serialize(VampireData.CODEC)
                    .copyOnDeath()
                    .build());

    /** The player's sect, rank and standing. Persisted, kept through death, synced by {@code SyncMembershipPayload}. */
    public static final Supplier<AttachmentType<Membership>> MEMBERSHIP = ATTACHMENTS.register("membership",
            () -> AttachmentType.builder(() -> Membership.DEFAULT)
                    .serialize(Membership.CODEC)
                    .copyOnDeath()
                    .build());

    /** What the player knows of the named vampires. Server-side only: the screen is sent views of it on request. */
    public static final Supplier<AttachmentType<NemesisKnowledge>> NEMESIS_KNOWLEDGE = ATTACHMENTS.register("nemesis_knowledge",
            () -> AttachmentType.builder(() -> NemesisKnowledge.EMPTY)
                    .serialize(NemesisKnowledge.CODEC)
                    .copyOnDeath()
                    .build());

    /** The cities the player knows of. Server-side only. */
    public static final Supplier<AttachmentType<com.epi0.vtm.city.CityKnowledge>> CITY_KNOWLEDGE = ATTACHMENTS.register("city_knowledge",
            () -> AttachmentType.builder(() -> com.epi0.vtm.city.CityKnowledge.EMPTY)
                    .serialize(com.epi0.vtm.city.CityKnowledge.CODEC)
                    .copyOnDeath()
                    .build());

    /** The generation the player was born to; used if they are ever Embraced. */
    public static final Supplier<AttachmentType<com.epi0.vtm.vampire.Lineage>> LINEAGE = ATTACHMENTS.register("lineage",
            () -> AttachmentType.builder(() -> com.epi0.vtm.vampire.Lineage.UNROLLED)
                    .serialize(com.epi0.vtm.vampire.Lineage.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {}
}

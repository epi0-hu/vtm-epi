package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
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

    private ModAttachments() {}
}

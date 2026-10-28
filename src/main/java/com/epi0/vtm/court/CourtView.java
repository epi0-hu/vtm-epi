package com.epi0.vtm.court;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the Court tab shows: for a ruler, their court; for anyone else in a sect, what stands between them and the
 * throne.
 *
 * @param block    when not ruling, the reason the throne cannot be claimed (a lang suffix), or empty if it can be
 * @param decrees  ids of decrees in force
 * @param seats    the lieutenants' and enforcers' seats, vacant ones included
 * @param candidates members of the faction who could be given a seat
 */
public record CourtView(Optional<ResourceLocation> faction, boolean ruling, Optional<String> block, int influence, int tribute,
                        List<String> decrees, int maxDecrees, List<SeatView> seats, List<Candidate> candidates,
                        List<PetitionView> petitions, List<String> cities) {

    public record SeatView(String role, Optional<UUID> id, String name, int level, int loyalty, boolean rebel) {
        static final Codec<SeatView> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(SeatView::role),
                UUIDUtil.CODEC.optionalFieldOf("id").forGetter(SeatView::id),
                Codec.STRING.fieldOf("name").forGetter(SeatView::name),
                Codec.INT.fieldOf("level").forGetter(SeatView::level),
                Codec.INT.fieldOf("loyalty").forGetter(SeatView::loyalty),
                Codec.BOOL.fieldOf("rebel").forGetter(SeatView::rebel)
        ).apply(i, SeatView::new));
    }

    public record Candidate(UUID id, String name, int level, int ambition) {
        static final Codec<Candidate> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Candidate::id),
                Codec.STRING.fieldOf("name").forGetter(Candidate::name),
                Codec.INT.fieldOf("level").forGetter(Candidate::level),
                Codec.INT.fieldOf("ambition").forGetter(Candidate::ambition)
        ).apply(i, Candidate::new));
    }

    public record PetitionView(UUID id, String type, List<String> args, long remaining, int costA, int costB) {
        static final Codec<PetitionView> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(PetitionView::id),
                Codec.STRING.fieldOf("type").forGetter(PetitionView::type),
                Codec.STRING.listOf().fieldOf("args").forGetter(PetitionView::args),
                Codec.LONG.fieldOf("remaining").forGetter(PetitionView::remaining),
                Codec.INT.fieldOf("cost_a").forGetter(PetitionView::costA),
                Codec.INT.fieldOf("cost_b").forGetter(PetitionView::costB)
        ).apply(i, PetitionView::new));
    }

    public static final Codec<CourtView> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(CourtView::faction),
            Codec.BOOL.fieldOf("ruling").forGetter(CourtView::ruling),
            Codec.STRING.optionalFieldOf("block").forGetter(CourtView::block),
            Codec.INT.fieldOf("influence").forGetter(CourtView::influence),
            Codec.INT.fieldOf("tribute").forGetter(CourtView::tribute),
            Codec.STRING.listOf().fieldOf("decrees").forGetter(CourtView::decrees),
            Codec.INT.fieldOf("max_decrees").forGetter(CourtView::maxDecrees),
            SeatView.CODEC.listOf().fieldOf("seats").forGetter(CourtView::seats),
            Candidate.CODEC.listOf().fieldOf("candidates").forGetter(CourtView::candidates),
            PetitionView.CODEC.listOf().fieldOf("petitions").forGetter(CourtView::petitions),
            Codec.STRING.listOf().fieldOf("cities").forGetter(CourtView::cities)
    ).apply(i, CourtView::new));
}

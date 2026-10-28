package com.epi0.vtm.registry;

import com.epi0.vtm.VtmMod;
import com.epi0.vtm.world.LairPiece;
import com.epi0.vtm.world.LairStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation: our structure types and the pieces they are built from. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, VtmMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, VtmMod.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<LairStructure>> LAIR = TYPES.register("lair", () -> () -> LairStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> LAIR_PIECE = PIECES.register("lair",
            () -> (StructurePieceType.ContextlessType) LairPiece::new);

    public static final DeferredHolder<StructureType<?>, StructureType<com.epi0.vtm.world.CityStructure>> CITY = TYPES.register("city", () -> () -> com.epi0.vtm.world.CityStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> CITY_PIECE = PIECES.register("city",
            () -> (StructurePieceType.ContextlessType) com.epi0.vtm.world.CityPiece::new);

    public static final DeferredHolder<StructureType<?>, StructureType<com.epi0.vtm.world.MetropolisStructure>> METROPOLIS = TYPES.register("metropolis",
            () -> () -> com.epi0.vtm.world.MetropolisStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> METROPOLIS_PIECE = PIECES.register("metropolis",
            () -> (StructurePieceType.ContextlessType) com.epi0.vtm.world.MetropolisPiece::new);

    private ModStructures() {}
}

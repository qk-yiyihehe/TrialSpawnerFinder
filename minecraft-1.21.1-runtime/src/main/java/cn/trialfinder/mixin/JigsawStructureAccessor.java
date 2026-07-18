package cn.trialfinder.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.alias.StructurePoolAliasBinding;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Optional;

@Mixin(JigsawStructure.class)
public interface JigsawStructureAccessor {
    @Accessor("startPool")
    RegistryEntry<StructurePool> trialFinder$getStartPool();

    @Accessor("startJigsawName")
    Optional<Identifier> trialFinder$getStartJigsawName();

    @Accessor("size")
    int trialFinder$getSize();

    @Accessor("startHeight")
    HeightProvider trialFinder$getStartHeight();

    @Accessor("useExpansionHack")
    boolean trialFinder$getUseExpansionHack();

    @Accessor("projectStartToHeightmap")
    Optional<Heightmap.Type> trialFinder$getProjectStartToHeightmap();

    @Accessor("maxDistanceFromCenter")
    int trialFinder$getMaxDistanceFromCenter();

    @Accessor("poolAliasBindings")
    List<StructurePoolAliasBinding> trialFinder$getPoolAliasBindings();

    @Accessor("dimensionPadding")
    DimensionPadding trialFinder$getDimensionPadding();

    @Accessor("liquidSettings")
    StructureLiquidSettings trialFinder$getLiquidSettings();
}

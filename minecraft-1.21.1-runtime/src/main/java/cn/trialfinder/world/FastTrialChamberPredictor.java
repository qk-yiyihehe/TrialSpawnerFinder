package cn.trialfinder.world;

import cn.minecraftfinder.core.BlockPoint;
import cn.trialfinder.mixin.JigsawStructureAccessor;
import cn.trialfinder.mixin.ListPoolElementAccessor;
import cn.trialfinder.mixin.SinglePoolElementAccessor;
import cn.trialfinder.mixin.StructurePoolAccessor;
import cn.trialfinder.mixin.StructureTemplateAccessor;
import cn.trialfinder.model.SpawnerPoint;
import net.minecraft.block.Blocks;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.entity.JigsawBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.EmptyPoolElement;
import net.minecraft.structure.pool.ListPoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.pool.StructurePools;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.collection.PriorityIterator;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class FastTrialChamberPredictor {
    private final ServerWorld world;
    private final JigsawStructure structure;
    private final JigsawStructureAccessor settings;
    private final ChunkGenerator chunkGenerator;
    private final StructureTemplateManager templateManager;
    private final NoiseConfig noiseConfig;
    private final Registry<StructurePool> pools;
    private static final ConcurrentMap<StructurePoolElement, ElementMetadata[]> METADATA_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<StructurePool, StructurePoolElement[]> POOL_ELEMENTS =
            new ConcurrentHashMap<>();
    private static final BlockRotation[] ROTATIONS = BlockRotation.values();
    private static final StructurePoolElement[] NO_ELEMENTS = new StructurePoolElement[0];
    private final IdentityHashMap<StructurePoolElement, ElementMetadata[]> localMetadata =
            new IdentityHashMap<>();
    private final IdentityHashMap<StructurePool, StructurePoolElement[]> localPoolElements =
            new IdentityHashMap<>();
    private final int maxDepth;
    private final boolean useExpansionHack;
    public FastTrialChamberPredictor(ServerWorld world) {
        this.world = world;
        Structure registered = world.getRegistryManager().get(RegistryKeys.STRUCTURE)
                .get(Identifier.of("minecraft", "trial_chambers"));
        if (!(registered instanceof JigsawStructure jigsaw)) {
            throw new IllegalStateException("Minecraft 注册表中的 trial_chambers 不是 Jigsaw 结构");
        }
        this.structure = jigsaw;
        this.settings = (JigsawStructureAccessor) (Object) jigsaw;
        this.chunkGenerator = world.getChunkManager().getChunkGenerator();
        this.templateManager = world.getStructureTemplateManager();
        this.noiseConfig = world.getChunkManager().getNoiseConfig();
        this.pools = world.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);
        this.maxDepth = settings.trialFinder$getSize();
        this.useExpansionHack = settings.trialFinder$getUseExpansionHack();
    }

    public Prediction predict(BlockPoint candidate) {
        int chunkX = Math.floorDiv(candidate.x(), 16);
        int chunkZ = Math.floorDiv(candidate.z(), 16);
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        Structure.Context context = new Structure.Context(
                world.getRegistryManager(), chunkGenerator, chunkGenerator.getBiomeSource(),
                noiseConfig, templateManager, world.getSeed(), chunkPos, world,
                structure.getValidBiomes()::contains);
        ChunkRandom random = context.random();
        int startY = settings.trialFinder$getStartHeight().get(
                random, new HeightContext(chunkGenerator, world));
        BlockPos start = new BlockPos(chunkPos.getStartX(), startY, chunkPos.getStartZ());
        StructurePoolAliasLookup aliases = StructurePoolAliasLookup.create(
                settings.trialFinder$getPoolAliasBindings(), start, world.getSeed());
        StartLayout startLayout = createStart(context, start, random, aliases);
        if (startLayout == null || maxDepth <= 0) {
            return new Prediction(candidate, false, List.of());
        }
        // Structure.getValidStructurePosition checks the generated center, not the raw chunk start.
        boolean exists = structure.getValidBiomes().contains(
                chunkGenerator.getBiomeSource().getBiome(
                        BiomeCoords.fromBlock(startLayout.biomePosition().getX()),
                        BiomeCoords.fromBlock(startLayout.biomePosition().getY()),
                        BiomeCoords.fromBlock(startLayout.biomePosition().getZ()),
                        noiseConfig.getMultiNoiseSampler()));
        if (!exists) {
            return new Prediction(candidate, false, List.of());
        }

        List<LightPiece> pieces = new ArrayList<>();
        pieces.add(startLayout.piece());
        generateChildren(startLayout, pieces, random, aliases);

        Set<SpawnerPoint> spawners = new HashSet<>();
        for (LightPiece piece : pieces) collectSpawners(piece, spawners);
        List<SpawnerPoint> sorted = new ArrayList<>(spawners);
        sorted.sort(SpawnerPoint::compareTo);
        return new Prediction(candidate, exists, sorted);
    }

    private StartLayout createStart(
            Structure.Context context, BlockPos start, ChunkRandom random,
            StructurePoolAliasLookup aliases) {
        BlockRotation rotation = BlockRotation.random(random);
        RegistryEntry<StructurePool> configuredPool = settings.trialFinder$getStartPool();
        StructurePool startPool = configuredPool.getKey()
                .flatMap(key -> pools.getOrEmpty(aliases.lookup(key)))
                .orElse(configuredPool.value());
        StructurePoolElement element = startPool.getRandomElement(random);
        if (element == EmptyPoolElement.INSTANCE) return null;

        BlockPos selectedJigsaw = start;
        Optional<Identifier> requiredName = settings.trialFinder$getStartJigsawName();
        if (requiredName.isPresent()) {
            selectedJigsaw = findStartingJigsaw(element, requiredName.get(), start, rotation, random)
                    .orElse(null);
            if (selectedJigsaw == null) return null;
        }

        Vec3i offset = selectedJigsaw.subtract(start);
        BlockPos piecePosition = start.subtract(offset);
        BlockBox box = boundingBox(element, piecePosition, rotation);
        int centerX = (box.getMaxX() + box.getMinX()) / 2;
        int centerZ = (box.getMaxZ() + box.getMinZ()) / 2;
        int groundY = settings.trialFinder$getProjectStartToHeightmap().isPresent()
                ? start.getY() + chunkGenerator.getHeightOnGround(
                        centerX, centerZ, settings.trialFinder$getProjectStartToHeightmap().orElseThrow(),
                        world, noiseConfig)
                : piecePosition.getY();
        int boxGroundY = box.getMinY() + element.getGroundLevelDelta();
        int verticalOffset = groundY - boxGroundY;
        piecePosition = piecePosition.add(0, verticalOffset, 0);
        box = box.offset(0, verticalOffset, 0);
        int biomeY = groundY + offset.getY();
        LightPiece piece = new LightPiece(
                element, piecePosition, element.getGroundLevelDelta(), rotation, box);
        return new StartLayout(piece, new BlockPos(centerX, biomeY, centerZ));
    }

    private Optional<BlockPos> findStartingJigsaw(
            StructurePoolElement element, Identifier name, BlockPos start,
            BlockRotation rotation, Random random) {
        for (ConnectorTemplate connector :
                connectors(element, start, rotation, random)) {
            if (name.equals(connector.name())) {
                return Optional.of(start.add(connector.relativePos()));
            }
        }
        return Optional.empty();
    }

    private void generateChildren(
            StartLayout start, List<LightPiece> pieces, Random random,
            StructurePoolAliasLookup aliases) {
        LightPiece first = start.piece();
        BlockPos center = start.biomePosition();
        int distance = settings.trialFinder$getMaxDistanceFromCenter();
        int bottom = Math.max(center.getY() - distance,
                world.getBottomY() + settings.trialFinder$getDimensionPadding().bottom());
        int top = Math.min(center.getY() + distance + 1,
                world.getTopY() - settings.trialFinder$getDimensionPadding().top());
        Box allowed = new Box(
                center.getX() - distance, bottom, center.getZ() - distance,
                center.getX() + distance + 1, top, center.getZ() + distance + 1);
        FreeSpace shape = new FreeSpace(allowed, first.box());
        PriorityIterator<QueuedPiece> queue = new PriorityIterator<>();
        generatePiece(first, shape, 0, pieces, queue, random, aliases);
        while (queue.hasNext()) {
            QueuedPiece next = queue.next();
            generatePiece(next.piece(), next.shape(), next.depth(), pieces, queue, random, aliases);
        }
    }

    private void generatePiece(
            LightPiece piece, FreeSpace pieceShape, int depth,
            List<LightPiece> pieces, PriorityIterator<QueuedPiece> queue,
            Random random, StructurePoolAliasLookup aliases) {
        StructurePoolElement parentElement = piece.element();
        StructurePool.Projection parentProjection = parentElement.getProjection();
        boolean parentRigid = parentProjection == StructurePool.Projection.RIGID;
        FreeSpace internalShape = null;
        int parentMinY = piece.box().getMinY();

        connectorLoop:
        for (ConnectorTemplate parentConnector :
                connectors(parentElement, piece.position(), piece.rotation(), random)) {
            Direction direction = parentConnector.facing();
            BlockPos connectorPos = piece.position().add(parentConnector.relativePos());
            BlockPos attachmentPos = connectorPos.offset(direction);
            int parentRelativeY = connectorPos.getY() - parentMinY;
            int terrainY = -1;
            RegistryKey<StructurePool> poolKey = aliases.lookup(parentConnector.pool());
            Optional<? extends RegistryEntry<StructurePool>> selectedEntry = pools.getEntry(poolKey);
            if (selectedEntry.isEmpty()) continue;
            RegistryEntry<StructurePool> selected = selectedEntry.get();
            RegistryEntry<StructurePool> fallback = selected.value().getFallback();
            boolean internal = piece.box().contains(attachmentPos);
            FreeSpace availableShape;
            if (internal) {
                if (internalShape == null) {
                    internalShape = new FreeSpace(Box.from(piece.box()), null);
                }
                availableShape = internalShape;
            } else {
                availableShape = pieceShape;
            }

            StructurePoolElement[] selectedCandidates = depth == maxDepth
                    ? NO_ELEMENTS : shuffledElements(selected.value(), random);
            StructurePoolElement[] fallbackCandidates = shuffledElements(fallback.value(), random);
            int priority = parentConnector.placementPriority();

            int candidateCount = selectedCandidates.length + fallbackCandidates.length;
            for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
                StructurePoolElement childElement = candidateIndex < selectedCandidates.length
                        ? selectedCandidates[candidateIndex]
                        : fallbackCandidates[candidateIndex - selectedCandidates.length];
                if (childElement == EmptyPoolElement.INSTANCE) break;
                for (BlockRotation childRotation : shuffledRotations(random)) {
                    List<ConnectorTemplate> childConnectors =
                            connectors(childElement, BlockPos.ORIGIN, childRotation, random);
                    BlockBox childOriginBox = boundingBox(
                            childElement, BlockPos.ORIGIN, childRotation);
                    for (ConnectorTemplate childConnector : childConnectors) {
                        if (!attachmentMatches(parentConnector, childConnector)) continue;
                        BlockPos childConnectorPos = childConnector.relativePos();
                        BlockPos childPosition = attachmentPos.subtract(childConnectorPos);
                        BlockBox childBox = boundingBox(
                                childElement, childPosition, childRotation);
                        int childMinY = childBox.getMinY();
                        boolean childRigid = childElement.getProjection() == StructurePool.Projection.RIGID;
                        int childRelativeY = childConnectorPos.getY();
                        int connectionOffset = parentRelativeY - childRelativeY + direction.getOffsetY();
                        int targetY;
                        if (parentRigid && childRigid) {
                            targetY = parentMinY + connectionOffset;
                        } else {
                            if (terrainY == -1) {
                                terrainY = chunkGenerator.getHeightOnGround(
                                        connectorPos.getX(), connectorPos.getZ(),
                                        net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG,
                                        world, noiseConfig);
                            }
                            targetY = terrainY - childRelativeY;
                        }
                        int verticalOffset = targetY - childMinY;
                        BlockBox placedBox = childBox.offset(0, verticalOffset, 0);
                        BlockPos placedPosition = childPosition.add(0, verticalOffset, 0);
                        if (useExpansionHack
                                && childOriginBox.getBlockCountY() <= 16) {
                            throw new UnsupportedOperationException(
                                    "试炼密室快速预测不支持 expansion hack");
                        }
                        if (!availableShape.contains(placedBox)) {
                            continue;
                        }
                        availableShape.occupy(placedBox);
                        int childGroundDelta = childRigid
                                ? piece.groundLevelDelta() - connectionOffset
                                : childElement.getGroundLevelDelta();
                        LightPiece child = new LightPiece(
                                childElement, placedPosition, childGroundDelta,
                                childRotation, placedBox);
                        pieces.add(child);
                        if (depth + 1 <= maxDepth) {
                            queue.enqueue(new QueuedPiece(child, availableShape, depth + 1), priority);
                        }
                        continue connectorLoop;
                    }
                }
            }
        }
    }

    private static boolean attachmentMatches(
            ConnectorTemplate parent, ConnectorTemplate child) {
        return parent.facing() == child.facing().getOpposite()
                && (parent.rollable() || parent.rotation() == child.rotation())
                && parent.target().equals(child.nameString());
    }

    private StructurePoolElement[] shuffledElements(
            StructurePool pool, Random random) {
        StructurePoolElement[] elements = localPoolElements.computeIfAbsent(pool,
                key -> POOL_ELEMENTS.computeIfAbsent(
                        key, ignored -> ((StructurePoolAccessor) (Object) key)
                                .trialFinder$getElements().toArray(new StructurePoolElement[0])));
        StructurePoolElement[] shuffled = elements.clone();
        shuffle(shuffled, random);
        return shuffled;
    }

    private static BlockRotation[] shuffledRotations(Random random) {
        BlockRotation[] shuffled = ROTATIONS.clone();
        shuffle(shuffled, random);
        return shuffled;
    }

    private static <T> void shuffle(T[] values, Random random) {
        for (int remaining = values.length; remaining > 1; remaining--) {
            int selected = random.nextInt(remaining);
            T last = values[remaining - 1];
            values[remaining - 1] = values[selected];
            values[selected] = last;
        }
    }

    private void collectSpawners(LightPiece piece, Set<SpawnerPoint> output) {
        collectSpawners(piece.element(), piece.position(), piece.rotation(), output);
    }

    private void collectSpawners(
            StructurePoolElement element, BlockPos position,
            BlockRotation rotation, Set<SpawnerPoint> output) {
        ElementMetadata cached = metadata(element, rotation);
        if (cached.supported()) {
            for (BlockPos relative : cached.spawners()) {
                output.add(new SpawnerPoint(
                        position.getX() + relative.getX(),
                        position.getY() + relative.getY(),
                        position.getZ() + relative.getZ()));
            }
            return;
        }
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child :
                    ((ListPoolElementAccessor) list).trialFinder$getElements()) {
                collectSpawners(child, position, rotation, output);
            }
            return;
        }
        if (element instanceof net.minecraft.structure.pool.SinglePoolElement single) {
            com.mojang.datafixers.util.Either<Identifier, StructureTemplate> location =
                    ((SinglePoolElementAccessor) single).trialFinder$getLocation();
            StructureTemplate template = location.map(
                    templateManager::getTemplateOrBlank, embedded -> embedded);
            StructurePlacementData placement = new StructurePlacementData().setRotation(rotation);
            for (StructureTemplate.StructureBlockInfo block : template.getInfosForBlock(
                    position, placement, Blocks.TRIAL_SPAWNER)) {
                output.add(new SpawnerPoint(
                        block.pos().getX(), block.pos().getY(), block.pos().getZ()));
            }
        }
    }

    private List<ConnectorTemplate> connectors(
            StructurePoolElement element, BlockPos position,
            BlockRotation rotation, Random random) {
        ElementMetadata cached = metadata(element, rotation);
        if (!cached.supported()) {
            return toConnectorTemplates(
                    element.getStructureBlockInfos(
                            templateManager, position, rotation, random), position);
        }
        List<ConnectorTemplate> result = new ArrayList<>(cached.connectors());
        Util.shuffle(result, random);
        result.sort(Comparator.comparingInt(ConnectorTemplate::selectionPriority)
                .reversed());
        return result;
    }

    private BlockBox boundingBox(
            StructurePoolElement element, BlockPos position, BlockRotation rotation) {
        ElementMetadata cached = metadata(element, rotation);
        if (!cached.supported()) {
            return element.getBoundingBox(templateManager, position, rotation);
        }
        return cached.boundingBox().offset(position.getX(), position.getY(), position.getZ());
    }

    private ElementMetadata metadata(StructurePoolElement element, BlockRotation rotation) {
        if (element == EmptyPoolElement.INSTANCE) {
            return ElementMetadata.UNSUPPORTED;
        }
        ElementMetadata[] variants = localMetadata.computeIfAbsent(element,
                key -> METADATA_CACHE.computeIfAbsent(
                        key, ignored -> new ElementMetadata[ROTATIONS.length]));
        int index = rotation.ordinal();
        ElementMetadata cached = variants[index];
        if (cached != null) return cached;
        synchronized (variants) {
            cached = variants[index];
            if (cached == null) {
                cached = createMetadata(element, rotation);
                variants[index] = cached;
            }
        }
        return cached;
    }

    private ElementMetadata createMetadata(
            StructurePoolElement element, BlockRotation rotation) {
        if (element instanceof ListPoolElement list) {
            List<StructurePoolElement> children =
                    ((ListPoolElementAccessor) list).trialFinder$getElements();
            if (children.isEmpty()) return ElementMetadata.UNSUPPORTED;
            ElementMetadata first = metadata(children.getFirst(), rotation);
            if (!first.supported()) return ElementMetadata.UNSUPPORTED;
            List<BlockPos> spawners = new ArrayList<>();
            for (StructurePoolElement child : children) {
                ElementMetadata childMetadata = metadata(child, rotation);
                if (!childMetadata.supported()) return ElementMetadata.UNSUPPORTED;
                spawners.addAll(childMetadata.spawners());
            }
            return new ElementMetadata(
                    first.connectors(),
                    element.getBoundingBox(templateManager, BlockPos.ORIGIN, rotation),
                    List.copyOf(spawners), true);
        }
        if (element instanceof net.minecraft.structure.pool.SinglePoolElement single) {
            com.mojang.datafixers.util.Either<Identifier, StructureTemplate> location =
                    ((SinglePoolElementAccessor) single).trialFinder$getLocation();
            StructureTemplate template = location.map(
                    templateManager::getTemplateOrBlank, embedded -> embedded);
            List<StructureTemplate.PalettedBlockInfoList> palettes =
                    ((StructureTemplateAccessor) (Object) template).trialFinder$getBlockInfoLists();
            // A multi-palette template chooses a palette from the piece position.
            // Keep the original path for it instead of applying one cached variant everywhere.
            if (palettes.size() != 1) return ElementMetadata.UNSUPPORTED;
            StructurePlacementData placement = new StructurePlacementData().setRotation(rotation);
            List<ConnectorTemplate> connectors = toConnectorTemplates(
                    transformInfos(palettes.getFirst().getAllOf(Blocks.JIGSAW), placement),
                    BlockPos.ORIGIN);
            List<BlockPos> spawners = transformInfos(
                    palettes.getFirst().getAllOf(Blocks.TRIAL_SPAWNER), placement).stream()
                    .map(StructureTemplate.StructureBlockInfo::pos)
                    .toList();
            return new ElementMetadata(
                    connectors,
                    element.getBoundingBox(templateManager, BlockPos.ORIGIN, rotation),
                    spawners, true);
        }
        if (element instanceof net.minecraft.structure.pool.FeaturePoolElement) {
            List<ConnectorTemplate> connectors = toConnectorTemplates(
                    element.getStructureBlockInfos(
                            templateManager, BlockPos.ORIGIN, rotation, Random.create(0L)),
                    BlockPos.ORIGIN);
            return new ElementMetadata(
                    connectors,
                    element.getBoundingBox(templateManager, BlockPos.ORIGIN, rotation),
                    List.of(), true);
        }
        return ElementMetadata.UNSUPPORTED;
    }

    private static List<StructureTemplate.StructureBlockInfo> transformInfos(
            List<StructureTemplate.StructureBlockInfo> infos, StructurePlacementData placement) {
        List<StructureTemplate.StructureBlockInfo> transformed = new ArrayList<>(infos.size());
        for (StructureTemplate.StructureBlockInfo info : infos) {
            transformed.add(new StructureTemplate.StructureBlockInfo(
                    StructureTemplate.transform(placement, info.pos()),
                    info.state().rotate(placement.getRotation()), info.nbt()));
        }
        return List.copyOf(transformed);
    }

    private static List<ConnectorTemplate> toConnectorTemplates(
            List<StructureTemplate.StructureBlockInfo> infos, BlockPos origin) {
        List<ConnectorTemplate> connectors = new ArrayList<>(infos.size());
        for (StructureTemplate.StructureBlockInfo info : infos) {
            NbtCompound nbt = Objects.requireNonNull(info.nbt(), () -> info + " nbt was null");
            Direction facing = JigsawBlock.getFacing(info.state());
            JigsawBlockEntity.Joint joint = JigsawBlockEntity.Joint.byName(nbt.getString("joint"))
                    .orElseGet(() -> facing.getAxis().isHorizontal()
                            ? JigsawBlockEntity.Joint.ALIGNED
                            : JigsawBlockEntity.Joint.ROLLABLE);
            String name = nbt.getString("name");
            connectors.add(new ConnectorTemplate(
                    info.pos().subtract(origin), facing, JigsawBlock.getRotation(info.state()),
                    StructurePools.of(nbt.getString("pool")), Identifier.tryParse(name), name,
                    nbt.getString("target"), joint == JigsawBlockEntity.Joint.ROLLABLE,
                    nbt.getInt("selection_priority"), nbt.getInt("placement_priority")));
        }
        return List.copyOf(connectors);
    }

    public record Prediction(
            BlockPoint position, boolean exists, List<SpawnerPoint> theoreticalSpawners) {
        public Prediction {
            theoreticalSpawners = List.copyOf(theoreticalSpawners);
        }

        public List<SpawnerPoint> actualSpawners() {
            return exists ? theoreticalSpawners : List.of();
        }
    }

    private record StartLayout(LightPiece piece, BlockPos biomePosition) {
    }

    private record LightPiece(
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            BlockRotation rotation,
            BlockBox box) {
    }

    private record QueuedPiece(
            LightPiece piece, FreeSpace shape, int depth) {
    }

    private record ElementMetadata(
            List<ConnectorTemplate> connectors,
            BlockBox boundingBox,
            List<BlockPos> spawners,
            boolean supported) {
        private static final ElementMetadata UNSUPPORTED =
                new ElementMetadata(List.of(), null, List.of(), false);
    }

    private record ConnectorTemplate(
            BlockPos relativePos,
            Direction facing,
            Direction rotation,
            RegistryKey<StructurePool> pool,
            Identifier name,
            String nameString,
            String target,
            boolean rollable,
            int selectionPriority,
            int placementPriority) {
    }

    private static final class FreeSpace {
        private static final int CELL_SIZE = 16;
        private static final int AUDIT_SPACE_LIMIT = 1_024;
        private static final AtomicInteger AUDITED_SPACES = new AtomicInteger();
        private static volatile boolean auditBudgetExhausted;

        private final Box bounds;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final int cellsX;
        private final int cellsY;
        private final int cellsZ;
        private final List<BlockedBox> blocked = new ArrayList<>();
        private final List<BlockedBox>[] cells;
        private VoxelShape auditShape;
        private int queryId;

        @SuppressWarnings("unchecked")
        private FreeSpace(Box bounds, BlockBox initiallyBlocked) {
            this.bounds = bounds;
            this.baseX = (int) Math.floor(bounds.minX);
            this.baseY = (int) Math.floor(bounds.minY);
            this.baseZ = (int) Math.floor(bounds.minZ);
            this.cellsX = Math.max(1, Math.ceilDiv((int) Math.ceil(bounds.maxX) - baseX, CELL_SIZE));
            this.cellsY = Math.max(1, Math.ceilDiv((int) Math.ceil(bounds.maxY) - baseY, CELL_SIZE));
            this.cellsZ = Math.max(1, Math.ceilDiv((int) Math.ceil(bounds.maxZ) - baseZ, CELL_SIZE));
            this.cells = initiallyBlocked == null
                    ? null
                    : (List<BlockedBox>[]) new List<?>[cellsX * cellsY * cellsZ];
            if (takeAuditSlot()) {
                auditShape = initiallyBlocked == null
                        ? VoxelShapes.cuboid(bounds)
                        : VoxelShapes.combineAndSimplify(
                                VoxelShapes.cuboid(bounds),
                                VoxelShapes.cuboid(Box.from(initiallyBlocked)),
                                BooleanBiFunction.ONLY_FIRST);
            }
            if (initiallyBlocked != null) add(initiallyBlocked);
        }

        private static boolean takeAuditSlot() {
            if (auditBudgetExhausted) return false;
            int slot = AUDITED_SPACES.getAndIncrement();
            if (slot < AUDIT_SPACE_LIMIT) return true;
            auditBudgetExhausted = true;
            return false;
        }

        private boolean contains(BlockBox candidate) {
            boolean fast = containsFast(candidate);
            if (auditShape != null) {
                Box contracted = Box.from(candidate).contract(0.25);
                boolean vanilla = !VoxelShapes.matchesAnywhere(
                        auditShape, VoxelShapes.cuboid(contracted),
                        BooleanBiFunction.ONLY_SECOND);
                if (fast != vanilla) {
                    throw new IllegalStateException("快速碰撞判定与原版 VoxelShape 不一致");
                }
            }
            return fast;
        }

        private void occupy(BlockBox box) {
            add(box);
            if (auditShape != null) {
                auditShape = VoxelShapes.combine(
                        auditShape, VoxelShapes.cuboid(Box.from(box)),
                        BooleanBiFunction.ONLY_FIRST);
            }
        }

        private boolean containsFast(BlockBox candidate) {
            if (candidate.getMinX() + 0.25 < bounds.minX
                    || candidate.getMaxX() + 0.75 > bounds.maxX
                    || candidate.getMinY() + 0.25 < bounds.minY
                    || candidate.getMaxY() + 0.75 > bounds.maxY
                    || candidate.getMinZ() + 0.25 < bounds.minZ
                    || candidate.getMaxZ() + 0.75 > bounds.maxZ) {
                return false;
            }
            if (cells == null) {
                for (BlockedBox entry : blocked) {
                    if (intersects(candidate, entry.box)) return false;
                }
                return true;
            }

            int currentQuery = ++queryId;
            for (int x = cellX(candidate.getMinX()); x <= cellX(candidate.getMaxX()); x++) {
                for (int y = cellY(candidate.getMinY()); y <= cellY(candidate.getMaxY()); y++) {
                    for (int z = cellZ(candidate.getMinZ()); z <= cellZ(candidate.getMaxZ()); z++) {
                        List<BlockedBox> entries = cells[index(x, y, z)];
                        if (entries == null) continue;
                        for (BlockedBox entry : entries) {
                            if (entry.lastQuery == currentQuery) continue;
                            entry.lastQuery = currentQuery;
                            if (intersects(candidate, entry.box)) return false;
                        }
                    }
                }
            }
            return true;
        }

        private void add(BlockBox box) {
            BlockedBox entry = new BlockedBox(box);
            blocked.add(entry);
            if (cells == null) return;
            for (int x = cellX(box.getMinX()); x <= cellX(box.getMaxX()); x++) {
                for (int y = cellY(box.getMinY()); y <= cellY(box.getMaxY()); y++) {
                    for (int z = cellZ(box.getMinZ()); z <= cellZ(box.getMaxZ()); z++) {
                        int index = index(x, y, z);
                        List<BlockedBox> entries = cells[index];
                        if (entries == null) cells[index] = entries = new ArrayList<>();
                        entries.add(entry);
                    }
                }
            }
        }

        private int cellX(int x) {
            return Math.max(0, Math.min(cellsX - 1, Math.floorDiv(x - baseX, CELL_SIZE)));
        }

        private int cellY(int y) {
            return Math.max(0, Math.min(cellsY - 1, Math.floorDiv(y - baseY, CELL_SIZE)));
        }

        private int cellZ(int z) {
            return Math.max(0, Math.min(cellsZ - 1, Math.floorDiv(z - baseZ, CELL_SIZE)));
        }

        private int index(int x, int y, int z) {
            return (x * cellsY + y) * cellsZ + z;
        }

        private static boolean intersects(BlockBox first, BlockBox second) {
            return first.getMaxX() >= second.getMinX() && first.getMinX() <= second.getMaxX()
                    && first.getMaxY() >= second.getMinY() && first.getMinY() <= second.getMaxY()
                    && first.getMaxZ() >= second.getMinZ() && first.getMinZ() <= second.getMaxZ();
        }

        private static final class BlockedBox {
            private final BlockBox box;
            private int lastQuery;

            private BlockedBox(BlockBox box) {
                this.box = box;
            }
        }
    }
}

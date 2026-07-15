package cn.minecraftfinder.geode;

import java.util.ArrayList;
import java.util.List;

public final class ModernGeodeSimulator {
    private static final long SALT = 20_002L;
    private static final int CHANCE_BITS = (int) ((1.0f / 24.0f) * (1 << 24));
    private static final double AIR_DISTANCE = inverseSqrt(1.7);
    private static final double NOISE_MULTIPLIER = 0.05;
    private static final double BUDDING_CHANCE = 0.083;

    private final long seed;
    private final long xScale;
    private final long zScale;
    private final XoroshiroRandom random;
    private final NormalNoise noise;

    public ModernGeodeSimulator(long seed) {
        this.seed = seed;
        this.random = new XoroshiroRandom(seed);
        this.noise = NormalNoise.create(seed);
        this.xScale = (random.nextLong() | 1L) * 16L;
        this.zScale = (random.nextLong() | 1L) * 16L;
    }

    public boolean isGeodeChunk(int chunkX, int chunkZ) {
        setFeatureSeed(chunkX, chunkZ);
        return random.nextBits(24) < CHANCE_BITS;
    }

    public GeodeSimulation simulate(int chunkX, int chunkZ) {
        setFeatureSeed(chunkX, chunkZ);
        if (random.nextFloat() >= 1.0f / 24.0f) {
            return null;
        }

        int originX = random.nextInt(16) + chunkX * 16;
        int originZ = random.nextInt(16) + chunkZ * 16;
        int originY = random.nextBetween(-58, 30);
        int pointCount = random.nextBetween(3, 4);
        double pointAdjustment = pointCount / 6.0;
        double amethystDistance = inverseSqrt(2.2 + pointAdjustment);
        double basaltDistance = inverseSqrt(4.2 + pointAdjustment);
        double crackDistance = inverseSqrt(
                2.0 + random.nextDouble() / 2.0 + (pointCount > 3 ? pointAdjustment : 0.0));
        boolean generateCrack = random.nextFloat() < 0.95f;

        PointWithOffset[] points = new PointWithOffset[pointCount];
        for (int index = 0; index < pointCount; index++) {
            points[index] = new PointWithOffset(
                    originX + random.nextBetween(4, 6),
                    originY + random.nextBetween(4, 6),
                    originZ + random.nextBetween(4, 6),
                    random.nextBetween(1, 2));
        }

        Point[] crackPoints = generateCrack
                ? createCrackPoints(originX, originY, originZ, pointCount)
                : new Point[0];
        List<BuddingAmethyst> budding = new ArrayList<>();

        for (int z = originZ - 16; z <= originZ + 16; z++) {
            for (int y = originY - 16; y <= originY + 16; y++) {
                for (int x = originX - 16; x <= originX + 16; x++) {
                    double noiseOffset = noise.sample(x, y, z) * NOISE_MULTIPLIER;
                    double shell = 0.0;
                    for (PointWithOffset point : points) {
                        shell += inverseSqrt(distanceSquared(x, y, z, point) + point.offset);
                    }
                    shell += noiseOffset * pointCount;
                    if (shell < basaltDistance || shell >= AIR_DISTANCE) {
                        continue;
                    }

                    double crack = 0.0;
                    for (Point point : crackPoints) {
                        crack += inverseSqrt(distanceSquared(x, y, z, point) + 2.0);
                    }
                    crack += noiseOffset * 3.0;
                    if (generateCrack && crack >= crackDistance) {
                        continue;
                    }
                    if (shell >= amethystDistance && random.nextFloat() < BUDDING_CHANCE) {
                        budding.add(new BuddingAmethyst(x, y, z));
                        random.skip(1);
                    }
                }
            }
        }
        return new GeodeSimulation(originX, originY, originZ, budding);
    }

    private Point[] createCrackPoints(int originX, int originY, int originZ, int pointCount) {
        int offset = pointCount * 2 + 1;
        int[][] corners = {{offset, 0}, {0, offset}, {offset, offset}, {0, 0}};
        int[] corner = corners[random.nextInt(4)];
        return new Point[]{
                new Point(originX + corner[0], originY + 7, originZ + corner[1]),
                new Point(originX + corner[0], originY + 5, originZ + corner[1]),
                new Point(originX + corner[0], originY + 1, originZ + corner[1])
        };
    }

    private void setFeatureSeed(int chunkX, int chunkZ) {
        long decorationSeed = ((long) chunkX * xScale + (long) chunkZ * zScale) ^ seed;
        random.setSeed(decorationSeed + SALT);
    }

    private static double distanceSquared(int x, int y, int z, Point point) {
        long dx = (long) x - point.x;
        long dy = (long) y - point.y;
        long dz = (long) z - point.z;
        return (double) (dx * dx + dy * dy + dz * dz);
    }

    private static double distanceSquared(int x, int y, int z, PointWithOffset point) {
        long dx = (long) x - point.x;
        long dy = (long) y - point.y;
        long dz = (long) z - point.z;
        return (double) (dx * dx + dy * dy + dz * dz);
    }

    private static double inverseSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    private record Point(int x, int y, int z) {
    }

    private record PointWithOffset(int x, int y, int z, int offset) {
    }

    private interface BitRandom {
        int nextBits(int bits);

        void setSeed(long seed);

        default int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound 必须大于 0");
            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) nextBits(31)) >> 31);
            }
            int sample;
            int result;
            do {
                sample = nextBits(31);
                result = sample % bound;
            } while (sample - result + bound - 1 < 0);
            return result;
        }

        default int nextBetween(int minimum, int maximum) {
            return nextInt(maximum - minimum + 1) + minimum;
        }

        default long nextLong() {
            return ((long) nextBits(32) << 32) + nextBits(32);
        }

        default float nextFloat() {
            return nextBits(24) * 0x1.0p-24f;
        }

        default double nextDouble() {
            return (((long) nextBits(26) << 27) + nextBits(27)) * 0x1.0p-53;
        }

        default void skip(int steps) {
            for (int index = 0; index < steps; index++) nextBits(32);
        }
    }

    private static final class XoroshiroRandom implements BitRandom {
        private static final long GOLDEN_RATIO = 0x9E3779B97F4A7C15L;
        private static final long SILVER_RATIO = 0x6A09E667F3BCC909L;
        private long low;
        private long high;

        private XoroshiroRandom(long seed) {
            setSeed(seed);
        }

        @Override
        public void setSeed(long seed) {
            long seedLow = seed ^ SILVER_RATIO;
            long seedHigh = seedLow + GOLDEN_RATIO;
            low = mixStafford13(seedLow);
            high = mixStafford13(seedHigh);
        }

        @Override
        public int nextBits(int bits) {
            long oldLow = low;
            long oldHigh = high;
            long xor = oldHigh ^ oldLow;
            low = Long.rotateLeft(oldLow, 49) ^ xor ^ (xor << 21);
            high = Long.rotateLeft(xor, 28);
            long result = Long.rotateLeft(oldLow + oldHigh, 17) + oldLow;
            return (int) (result >>> (64 - bits));
        }

        private static long mixStafford13(long value) {
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }
    }

    private static final class LegacyRandom implements BitRandom {
        private static final long MASK = (1L << 48) - 1;
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private long state;

        private LegacyRandom(long seed) {
            setSeed(seed);
        }

        @Override
        public void setSeed(long seed) {
            state = (seed ^ MULTIPLIER) & MASK;
        }

        @Override
        public int nextBits(int bits) {
            state = (state * MULTIPLIER + 11) & MASK;
            return (int) (state >>> (48 - bits));
        }

        private LegacyRandom forkFromHash() {
            return new LegacyRandom(440_898_198L ^ nextLong());
        }
    }

    private static final class ImprovedNoise {
        private static final int[][] GRADIENT = {
                {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
                {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
                {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
                {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
        };

        private final double xOffset;
        private final double yOffset;
        private final double zOffset;
        private final byte[] permutation = new byte[256];

        private ImprovedNoise(LegacyRandom random) {
            xOffset = random.nextDouble() * 256.0;
            yOffset = random.nextDouble() * 256.0;
            zOffset = random.nextDouble() * 256.0;
            for (int index = 0; index < permutation.length; index++) {
                permutation[index] = (byte) index;
            }
            for (int index = 0; index < permutation.length; index++) {
                int swap = index + random.nextInt(256 - index);
                byte value = permutation[index];
                permutation[index] = permutation[swap];
                permutation[swap] = value;
            }
        }

        private double sample(double x, double y, double z) {
            double shiftedX = x + xOffset;
            double shiftedY = y + yOffset;
            double shiftedZ = z + zOffset;
            int floorX = floor(shiftedX);
            int floorY = floor(shiftedY);
            int floorZ = floor(shiftedZ);
            double localX = shiftedX - floorX;
            double localY = shiftedY - floorY;
            double localZ = shiftedZ - floorZ;
            double smoothX = smooth(localX);
            double smoothY = smooth(localY);
            double smoothZ = smooth(localZ);

            int px = p(floorX);
            int pX = p(floorX + 1);
            int xy = p(px + floorY);
            int Xy = p(pX + floorY);
            int xY = p(px + floorY + 1);
            int XY = p(pX + floorY + 1);

            return lerp(smoothZ,
                    lerp(smoothY,
                            lerp(smoothX,
                                    gradient(p(xy + floorZ), localX, localY, localZ),
                                    gradient(p(Xy + floorZ), localX - 1, localY, localZ)),
                            lerp(smoothX,
                                    gradient(p(xY + floorZ), localX, localY - 1, localZ),
                                    gradient(p(XY + floorZ), localX - 1, localY - 1, localZ))),
                    lerp(smoothY,
                            lerp(smoothX,
                                    gradient(p(xy + floorZ + 1), localX, localY, localZ - 1),
                                    gradient(p(Xy + floorZ + 1), localX - 1, localY, localZ - 1)),
                            lerp(smoothX,
                                    gradient(p(xY + floorZ + 1), localX, localY - 1, localZ - 1),
                                    gradient(p(XY + floorZ + 1), localX - 1, localY - 1, localZ - 1))));
        }

        private int p(int index) {
            return permutation[index & 255];
        }

        private static double gradient(int hash, double x, double y, double z) {
            int[] gradient = GRADIENT[hash & 15];
            return gradient[0] * x + gradient[1] * y + gradient[2] * z;
        }

        private static int floor(double value) {
            int integer = (int) value;
            return value < integer ? integer - 1 : integer;
        }

        private static double smooth(double value) {
            return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double delta, double start, double end) {
            return start + delta * (end - start);
        }
    }

    private record PerlinNoise(ImprovedNoise noise) {
        private double sample(double x, double y, double z) {
            return noise.sample(wrap(x / 16.0), wrap(y / 16.0), wrap(z / 16.0));
        }

        private static double wrap(double value) {
            return value - Math.floor(value / 67_108_864.0 + 0.5) * 67_108_864.0;
        }
    }

    private record NormalNoise(PerlinNoise first, PerlinNoise second) {
        private static NormalNoise create(long seed) {
            LegacyRandom random = new LegacyRandom(seed);
            return new NormalNoise(
                    new PerlinNoise(new ImprovedNoise(random.forkFromHash())),
                    new PerlinNoise(new ImprovedNoise(random.forkFromHash())));
        }

        private double sample(double x, double y, double z) {
            double factor = 1.0181268882175227;
            return (first.sample(x, y, z) + second.sample(x * factor, y * factor, z * factor))
                    * (5.0 / 6.0);
        }
    }
}

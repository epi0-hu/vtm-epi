package com.epi0.vtm.world;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where everything in a metropolis goes. A pure function of the structure's seed: the same seed always gives the
 * same plan, so chunks can be drawn in any order and nothing but the seed has to be saved.
 *
 * <p>The city is an 11 x 11 grid of 16-block lots. Streets are 4 wide, except the two avenues either side of the
 * central lot, which are 6. Coordinates are offsets from the north-west corner of the city.
 */
public final class CityPlan {
    public static final int LOT = 16;
    public static final int N = 11;
    public static final int CENTRE = N / 2;
    static final int STREET = 4;
    static final int AVENUE = 6;

    public enum Kind { PLAZA, HAVEN, WATCH, CLUB, SHOP, MARKET, HOUSE, TENEMENT, TOWER, CHURCH, PARK, DEPOT, ASYLUM }

    /** A building site. {@code span} is how many grid cells it covers on each side (2 for The Asylum). */
    public record Lot(int i, int j, int span, Kind kind, int x, int z, int size, int floors, Direction facing, long seed) {
        public int centreX() {
            return x + size / 2;
        }

        public int centreZ() {
            return z + size / 2;
        }
    }

    /** Streets at index k run between lot k - 1 and lot k; k = 0 and k = N are the outer streets. */
    static int streetWidth(int k) {
        return k == CENTRE || k == CENTRE + 1 ? AVENUE : STREET;
    }

    private static final int[] START = new int[N + 1];
    static {
        int at = 0;
        for (int k = 0; k <= N; k++) {
            at += streetWidth(k);
            START[k] = at; // where lot k begins (street k ends)
            at += LOT;
        }
    }

    public static int lotStart(int index) {
        return START[index];
    }

    /** Total width: every street plus every lot. */
    public static int size() {
        int total = 0;
        for (int k = 0; k <= N; k++) {
            total += streetWidth(k);
        }
        return total + N * LOT;
    }

    public final long seed;
    public final List<Lot> lots = new ArrayList<>();
    public final int size = size();

    private static final Map<Long, CityPlan> CACHE = new ConcurrentHashMap<>();

    public static CityPlan of(long seed) {
        if (CACHE.size() > 6) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(seed, CityPlan::new);
    }

    private CityPlan(long seed) {
        this.seed = seed;
        Random random = new Random(seed ^ 0x5DEECE66DL);
        boolean[][] taken = new boolean[N][N];

        // The Asylum takes a 2 x 2 corner of downtown.
        int[][] corners = {{2, 2}, {7, 2}, {2, 7}, {7, 7}};
        int[] corner = corners[random.nextInt(4)];
        for (int di = 0; di < 2; di++) {
            for (int dj = 0; dj < 2; dj++) {
                taken[corner[0] + di][corner[1] + dj] = true;
            }
        }
        int asylumSize = 2 * LOT + STREET;
        lots.add(new Lot(corner[0], corner[1], 2, Kind.ASYLUM, lotStart(corner[0]), lotStart(corner[1]), asylumSize, 0,
                Direction.SOUTH, seed)); // The Asylum is drawn facing south whichever corner it takes

        // The civic core: the plaza, and the eight lots around it.
        add(CENTRE, CENTRE, Kind.PLAZA, 0, taken, random);
        List<Kind> ring = new ArrayList<>(List.of(Kind.HAVEN, Kind.WATCH, Kind.CLUB, Kind.MARKET, Kind.SHOP, Kind.TOWER, Kind.TOWER, Kind.SHOP));
        java.util.Collections.shuffle(ring, random);
        int r = 0;
        for (int i = CENTRE - 1; i <= CENTRE + 1; i++) {
            for (int j = CENTRE - 1; j <= CENTRE + 1; j++) {
                if (i != CENTRE || j != CENTRE) {
                    Kind kind = ring.get(r++);
                    add(i, j, kind, kind == Kind.TOWER ? 9 + random.nextInt(5) : 0, taken, random);
                }
            }
        }
        // The rest, by distance from the plaza. One church, one more watch station, and the depots gather on one side.
        int church = 0;
        int depotSide = random.nextInt(4);
        boolean watch2 = false;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (taken[i][j]) {
                    continue;
                }
                int d = Math.max(Math.abs(i - CENTRE), Math.abs(j - CENTRE));
                int roll = random.nextInt(100);
                Kind kind;
                int floors = 0;
                if (d == 2) {
                    kind = roll < 55 ? Kind.TOWER : roll < 70 ? Kind.SHOP : roll < 82 ? Kind.CLUB : roll < 94 ? Kind.TENEMENT : Kind.PARK;
                    floors = 7 + random.nextInt(5);
                } else if (d == 3) {
                    if (church == 0 && roll < 12) {
                        kind = Kind.CHURCH;
                        church++;
                    } else {
                        kind = roll < 25 ? Kind.TOWER : roll < 45 ? Kind.SHOP : roll < 52 ? Kind.CLUB : roll < 85 ? Kind.TENEMENT : Kind.HOUSE;
                    }
                    floors = d == 3 && kind == Kind.TOWER ? 5 + random.nextInt(4) : 3 + random.nextInt(3);
                } else if (d == 4) {
                    kind = roll < 50 ? Kind.HOUSE : roll < 78 ? Kind.TENEMENT : roll < 90 ? Kind.PARK : Kind.SHOP;
                    floors = 3 + random.nextInt(3);
                } else {
                    boolean industrial = switch (depotSide) {
                        case 0 -> j <= 1;
                        case 1 -> j >= N - 2;
                        case 2 -> i <= 1;
                        default -> i >= N - 2;
                    };
                    if (!watch2 && roll < 6) {
                        kind = Kind.WATCH;
                        watch2 = true;
                    } else if (industrial && roll < 80) {
                        kind = Kind.DEPOT;
                    } else {
                        kind = roll < 55 ? Kind.HOUSE : roll < 80 ? Kind.PARK : Kind.TENEMENT;
                        floors = 3 + random.nextInt(2);
                    }
                }
                add(i, j, kind, floors, taken, random);
            }
        }
        if (church == 0) { // a city without a church is not a city
            for (int idx = 0; idx < lots.size(); idx++) {
                Lot lot = lots.get(idx);
                if ((lot.kind == Kind.TENEMENT || lot.kind == Kind.SHOP) && Math.max(Math.abs(lot.i - CENTRE), Math.abs(lot.j - CENTRE)) == 3) {
                    lots.set(idx, new Lot(lot.i, lot.j, 1, Kind.CHURCH, lot.x, lot.z, LOT, 0, lot.facing, lot.seed));
                    break;
                }
            }
        }
        if (!watch2) { // a second station somewhere on the outskirts
            for (int idx = 0; idx < lots.size(); idx++) {
                Lot lot = lots.get(idx);
                if (lot.kind == Kind.HOUSE && Math.max(Math.abs(lot.i - CENTRE), Math.abs(lot.j - CENTRE)) >= 4) {
                    lots.set(idx, new Lot(lot.i, lot.j, 1, Kind.WATCH, lot.x, lot.z, LOT, 0, lot.facing, lot.seed));
                    break;
                }
            }
        }
    }

    private void add(int i, int j, Kind kind, int floors, boolean[][] taken, Random random) {
        taken[i][j] = true;
        lots.add(new Lot(i, j, 1, kind, lotStart(i), lotStart(j), LOT, floors, facingFor(i, j), random.nextLong()));
    }

    /** The way a building on lot (i, j) faces: along the street that leads towards the plaza. */
    static Direction facingFor(double i, double j) {
        double dx = i - CENTRE;
        double dz = j - CENTRE;
        if (Math.abs(dz) >= Math.abs(dx) && dz != 0) {
            return dz < 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return dx < 0 ? Direction.EAST : Direction.WEST;
    }

    /** The lot with the given kind (the first one, for kinds that occur once). */
    public Lot first(Kind kind) {
        return lots.stream().filter(l -> l.kind == kind).findFirst().orElseThrow();
    }

    /** Whether an offset along one axis falls in a street rather than in a lot. */
    static boolean axisIsStreet(int offset) {
        for (int k = 0; k < N; k++) {
            if (offset >= START[k] && offset < START[k] + LOT) {
                return false;
            }
        }
        return true;
    }

    /** Where a street band starts and how wide it is, if {@code offset} is in one. */
    static int[] streetAt(int offset) {
        for (int k = 0; k <= N; k++) {
            int begin = k == 0 ? 0 : START[k - 1] + LOT;
            int end = k == N ? size() : START[k];
            if (offset >= begin && offset < end) {
                return new int[]{k, begin, end - begin};
            }
        }
        return null;
    }
}

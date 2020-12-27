import model.Entity;
import model.EntityType;
import model.Player;
import model.Position;

import java.util.*;

import static model.EntityType.*;

public class GlobalStrategy {
    private static final int LOW_TOTAL_RESOURCES = 10000;
    State state;

    static void updateWasInCorner(State state) {
        Position[] corners = new Position[wasInThatCorner.length];
        for (int i = 1; i < corners.length; i++) {
            corners[i] = getPositionByPlayerId(state.playerView.getMyId(), i, state.playerView.getMapSize());
        }
        for (Entity entity : state.myEntities) {
            for (int i = 1; i < corners.length; i++) {
                if (entity.getPosition().distTo(corners[i]) == 0) {
                    wasInThatCorner[i] = true;
                }
            }
        }
    }

    public GlobalStrategy(State state) {
        this.state = state;
        updateWasInCorner(state);
    }

    private boolean expectedNeedHouse(int currentUsage, int currentExpected) {
        if (currentUsage <= 10) {
            return false;
        }
        if (currentUsage < 20) {
            return currentExpected < currentUsage + 5;
        }
        return currentExpected < Math.max(currentUsage * 1.1, currentUsage + 10);
    }

    private boolean needMoreHouses() {
        boolean reallyNeed = state.populationTotal == state.populationUsed;
        final int used = state.populationUsed;
        boolean expectedNeed = expectedNeedHouse(used, state.populationExpected);
        boolean currentlyBuildingALot = state.populationExpected >= used + 15 ||
                (used == 15 && state.populationExpected >= 25) ||
                (used <= 10 && state.populationExpected >= used + 5);
        return (reallyNeed && !currentlyBuildingALot) || expectedNeed;
    }

    private boolean hasEnoughHousesToBuildUnits() {
        return state.populationUsed < state.populationTotal;
    }

    private boolean needRangedHouse() {
        return state.myEntitiesCount.get(RANGED_BASE) == 0;
    }

    private boolean needBuilderBase() {
        return state.myEntitiesCount.get(BUILDER_BASE) == 0;
    }

    boolean needMoreRangedUnits;
    boolean needMoreRangedUnitsCalculated = false;

    public void setNeedMoreRangedUnits(boolean needMoreRangedUnits) {
        this.needMoreRangedUnitsCalculated = true;
        this.needMoreRangedUnits = needMoreRangedUnits;
    }

    static class ExpectedEntitiesDistribution {
        Map<EntityType, Integer> count;

        ExpectedEntitiesDistribution(int builderNum, int turretsNum, int rangesNum, int meleeNum) {
            count = new HashMap<>();

            count.put(BUILDER_UNIT, builderNum);
            count.put(TURRET, turretsNum);
            count.put(RANGED_UNIT, rangesNum);
            count.put(MELEE_UNIT, meleeNum);
        }

        ExpectedEntitiesDistribution noMoreBuilders() {
            return new ExpectedEntitiesDistribution(0, count.get(TURRET), count.get(RANGED_UNIT), count.get(MELEE_UNIT));
        }

        ExpectedEntitiesDistribution noMoreRangedUnits() {
            return new ExpectedEntitiesDistribution(count.get(BUILDER_UNIT), count.get(TURRET), 0, count.get(MELEE_UNIT));
        }

        EntityType whatBuildingNeedToBuild(final EntityType unit) {
            return switch (unit) {
                case WALL, HOUSE, BUILDER_BASE, MELEE_BASE, RANGED_BASE, RESOURCE, TURRET -> BUILDER_UNIT;
                case BUILDER_UNIT -> BUILDER_BASE;
                case MELEE_UNIT -> MELEE_BASE;
                case RANGED_UNIT -> RANGED_BASE;
            };
        }

        private boolean hasBuildingToBuild(final State state, final EntityType unit) {
            EntityType neededBuilding = whatBuildingNeedToBuild(unit);
            List<Entity> whereToBuild = state.myEntitiesByType.get(neededBuilding);
            for (Entity building : whereToBuild) {
                if (building.isActive()) {
                    return true;
                }
            }
            return false;
        }

        static class BuildOption implements Comparable<BuildOption> {
            final EntityType entityType;
            final double coef;

            public BuildOption(EntityType entityType, double coef) {
                this.entityType = entityType;
                this.coef = coef;
            }

            @Override
            public int compareTo(BuildOption o) {
                return Double.compare(coef, o.coef);
            }
        }

        List<EntityType> chooseWhatToBuild(final State state) {
            // 0 -> not enough
            // 787788 -> too much
            List<BuildOption> options = new ArrayList<>();
            for (Map.Entry<EntityType, Integer> entry : count.entrySet()) {
                int currentCnt = state.myEntitiesCount.get(entry.getKey());
                if (entry.getValue() == 0) {
                    continue;
                }
                if (!hasBuildingToBuild(state, entry.getKey())) {
                    continue;
                }
                double curCoef = currentCnt / (double) entry.getValue();
                options.add(new BuildOption(entry.getKey(), curCoef));
            }
            Collections.sort(options);
            List<EntityType> res = new ArrayList<>();
            for (BuildOption option : options) {
                res.add(option.entityType);
            }
            return res;
        }


        static ExpectedEntitiesDistribution V1 = new ExpectedEntitiesDistribution(5, 1, 1, 0);
        static ExpectedEntitiesDistribution V2 = new ExpectedEntitiesDistribution(10, 0, 2, 1);
        static ExpectedEntitiesDistribution START_WITH_BUILDERS = new ExpectedEntitiesDistribution(4, 0, 1, 0);

        static ExpectedEntitiesDistribution TWO_TO_ONE = new ExpectedEntitiesDistribution(2, 0, 1, 0);
        static ExpectedEntitiesDistribution ONLY_BUILDERS = new ExpectedEntitiesDistribution(1, 0, 0, 0);
        static ExpectedEntitiesDistribution ALMOST_RANGED = new ExpectedEntitiesDistribution(1, 0, 2, 0);


    }

    final int MAX_BUILDERS = 100;
    final int MAX_RANGED_UNITS = 40;
    final int REALLY_MAX_RANGED_UNITS = 100;

    private boolean needMoreBuilders() {
        final int buildersNum = state.myEntitiesCount.get(BUILDER_UNIT);
        return buildersNum < 20 && state.playerView.getCurrentTick() < 100 || buildersNum < 5;
    }

    static class RequiresProtection implements Comparable<RequiresProtection> {
        final Entity building;
        final double dangerLevel;

        public RequiresProtection(Entity building, double dangerLevel) {
            this.building = building;
            this.dangerLevel = dangerLevel;
        }

        @Override
        public int compareTo(RequiresProtection o) {
            return -Double.compare(dangerLevel, o.dangerLevel);
        }
    }

    final int DIST_TO_CONSIDER_DANGER = 20;

    private double calcDangerLevel(final Entity building) {
        double dangerLevel = 0.0;
        for (Entity enemyUnit : state.allEnemiesWarUnits) {
            int dist = CellsUtils.distBetweenTwoEntities(state, enemyUnit, building);
            if (dist <= DIST_TO_CONSIDER_DANGER) {
                dangerLevel++;
            }
        }
        return dangerLevel;
    }

    static class ProtectSomething {
        final EntityType whatToBuild;
        final Position whereToGo;

        public ProtectSomething(EntityType whatToBuild, Position whereToGo) {
            this.whatToBuild = whatToBuild;
            this.whereToGo = whereToGo;
            if (whereToGo == null) {
                throw new AssertionError("strange - where to go = null");
            }
        }
    }

    private boolean calculatedProtectSomething;
    private EntityType protectSomethingCache;

    public EntityType needToProtectSomething() {
        if (!calculatedProtectSomething) {
            calculatedProtectSomething = true;
            protectSomethingCache = needToProtectSomethingWithoutCache();
        }
        return protectSomethingCache;
    }

    private EntityType needToProtectSomethingWithoutCache() {
        if (!needMoreRangedUnitsCalculated) {
            throw new AssertionError("Wrong order!");
        }
        if (state.myEntitiesByType.get(RANGED_BASE).isEmpty()) {
            return null;
        }
        if (needMoreRangedUnits) {
            return RANGED_UNIT;
        } else {
            return null;
        }
    }


    WantToBuild cachedWhatToBuild;

    WantToBuild whatNextToBuild() {
        if (cachedWhatToBuild == null) {
            cachedWhatToBuild = whatNextToBuildWithoutCache();
        }
        return cachedWhatToBuild;
    }

    final int FIRST_TICK_FOR_RANGED_BASE = 200;

    private boolean lowResourcesInTotal() {
        if (state.playerView.isFogOfWar()) {
            // TODO: smarter things
            return state.playerView.getCurrentTick() > 750;
        } else {
            return state.totalResources < LOW_TOTAL_RESOURCES;
        }
    }

    boolean shouldBeAggressive() {
        return state.playerView.getCurrentTick() * 2 > state.playerView.getMaxTickCount();
    }

    boolean existBuilderUnderAttack() {
        for (Entity builder : state.myEntitiesByType.get(BUILDER_UNIT)) {
            final Position pos = builder.getPosition();
            if (state.map.underAttack[pos.getX()][pos.getY()].isUnderAttack()) {
                return true;
            }
        }
        return false;
    }

    boolean shouldNotBuildMoreBuilders() {
        if (lowResourcesInTotal()) {
            return true;
        }
        if (existBuilderUnderAttack()) {
            return true;
        }
        if (state.map.safePositionsToMine.size() < state.myEntitiesByType.get(RANGED_UNIT).size()) {
            return false;
        }
        return state.myEntitiesCount.get(BUILDER_UNIT) > MAX_BUILDERS;
    }

    boolean alreadyTooMuchRangers() {
        int nRangers = state.myEntitiesCount.get(RANGED_UNIT);
        int nBuilders = state.myEntitiesCount.get(BUILDER_UNIT);
        if (nRangers * 3 > nBuilders * 2) {
            return true;
        }
        return false;
    }

    WantToBuild whatNextToBuildWithoutCache() {
        WantToBuild wantToBuild = new WantToBuild(state);
        EntityType toProtect = needToProtectSomething();
//        if (toProtect != null && hasEnoughHousesToBuildUnits() && !alreadyTooMuchRangers()) {
//            wantToBuild.add(toProtect);
//        }
        if (needRangedHouse() && state.playerView.getCurrentTick() > Math.min(OpponentTracker.tickWhenSomebodyBuildARangedBase, FIRST_TICK_FOR_RANGED_BASE)) {
            wantToBuild.forceAdd(RANGED_BASE);
        }
        if (needBuilderBase()) {
            wantToBuild.add(BUILDER_BASE);
        }
        if (needMoreHouses()) {
            wantToBuild.add(HOUSE);
        }
        if (needMoreBuilders() && hasEnoughHousesToBuildUnits()) {
            wantToBuild.add(BUILDER_UNIT);
        }
        final int nBuilders = state.myEntitiesByType.get(BUILDER_UNIT).size();
        final int nRangers = state.myEntitiesByType.get(RANGED_UNIT).size();

//        ExpectedEntitiesDistribution distribution;
//        if (buildersNum < 40) {
//            distribution = ExpectedEntitiesDistribution.START_WITH_BUILDERS;
//        } else {
//            distribution = ExpectedEntitiesDistribution.TWO_TO_ONE;
//        }
//        if (stateshouldBeAggressive() ?
//                ExpectedEntitiesDistribution.ALMOST_RANGED :
//                ExpectedEntitiesDistribution.START_WITH_BUILDERS;
//        if (shouldNotBuildMoreBuilders()) {
//            distribution = distribution.noMoreBuilders();
//        }
//        if (state.myEntitiesCount.get(RANGED_UNIT) > MAX_RANGED_UNITS && !muchResources()) {
//            distribution = distribution.noMoreRangedUnits();
//        }
//        if (state.myEntitiesCount.get(RANGED_UNIT) > REALLY_MAX_RANGED_UNITS) {
//            distribution = distribution.noMoreRangedUnits();
//        }
//        List<EntityType> distRes = distribution.chooseWhatToBuild(state);
//        for (EntityType entityType : distRes) {
//            wantToBuild.add(entityType);
//        }
        wantToBuild.add(commandosStrategy(nBuilders, nRangers));
        wantToBuild.add(RANGED_UNIT);
        wantToBuild.add(BUILDER_UNIT);
        // TODO: make it smarter
        return wantToBuild;
    }

    private EntityType commandosStrategy(int nBuilders, int nRangers) {
        if (needRangedHouse()) {
            return BUILDER_UNIT;
        }
        if (nBuilders > MAX_BUILDERS || shouldNotBuildMoreBuilders()) {
            return RANGED_UNIT;
        }
        if (nRangers > REALLY_MAX_RANGED_UNITS) {
            return BUILDER_UNIT;
        }
        if (nBuilders < 40) {
            return BUILDER_UNIT;
        }
        if (nBuilders <= 50) {
            if (nRangers * 5 < nBuilders * 2) {
                return RANGED_UNIT;
            } else {
                return BUILDER_UNIT;
            }
        }
        if (nBuilders <= 60) {
            if (nRangers <= 20) {
                return RANGED_UNIT;
            } else {
                return BUILDER_UNIT;
            }
        }
        if (nBuilders <= 75) {
            if (nRangers <= 30) {
                return RANGED_UNIT;
            } else {
                return BUILDER_UNIT;
            }
        }
        if (nRangers <= 50) {
            return RANGED_UNIT;
        }
        if (nRangers * 75 < nBuilders * 50) {
            return RANGED_UNIT;
        } else {
            return BUILDER_UNIT;
        }
    }

    private boolean muchResources() {
        return state.playerView.getMyPlayer().getResource() > 500;
    }

    static boolean[] wasInThatCorner = new boolean[5];

    private static Position getPositionByPlayerId1(int playerId, int mapSize) {
        return switch (playerId) {
            case 1 -> new Position(0, 0);
            case 2 -> new Position(mapSize - 1, mapSize - 1);
            case 3 -> new Position(mapSize - 1, 0);
            case 4 -> new Position(0, mapSize - 1);
            default -> throw new IllegalStateException("Unexpected value: " + playerId);
        };
    }


    private static Position getPositionByPlayerId2(int playerId, int mapSize) {
        return switch (playerId) {
            case 1 -> new Position(mapSize - 1, mapSize - 1);
            case 2 -> new Position(0, 0);
            case 3 -> new Position(0, mapSize - 1);
            case 4 -> new Position(mapSize - 1, 0);
            default -> throw new IllegalStateException("Unexpected value: " + playerId);
        };
    }

    private static Position getPositionByPlayerId3(int playerId, int mapSize) {
        return switch (playerId) {
            case 1 -> new Position(0, mapSize - 1);
            case 2 -> new Position(mapSize - 1, 0);
            case 3 -> new Position(0, 0);
            case 4 -> new Position(mapSize - 1, mapSize - 1);
            default -> throw new IllegalStateException("Unexpected value: " + playerId);
        };
    }

    private static Position getPositionByPlayerId4(int playerId, int mapSize) {
        return switch (playerId) {
            case 1 -> new Position(mapSize - 1, 0);
            case 2 -> new Position(0, mapSize - 1);
            case 3 -> new Position(mapSize - 1, mapSize - 1);
            case 4 -> new Position(0, 0);
            default -> throw new IllegalStateException("Unexpected value: " + playerId);
        };
    }


    private static Position getPositionByPlayerId(int myPlayerId, int playerId, int mapSize) {
        return switch (myPlayerId) {
            case 1 -> getPositionByPlayerId1(playerId, mapSize);
            case 2 -> getPositionByPlayerId2(playerId, mapSize);
            case 3 -> getPositionByPlayerId3(playerId, mapSize);
            case 4 -> getPositionByPlayerId4(playerId, mapSize);
            default -> throw new IllegalStateException("Unexpected value: " + playerId);
        };
    }

    Position whichPlayerToAttack() {
        Player[] players = state.playerView.getPlayers();
        final int mapSize = state.playerView.getMapSize();
        int bScore = 0;
        Player bPlayer = null;
        final Position topRight = new Position(mapSize - 1, mapSize - 1);
        for (Player player : players) {
            if (player.getId() == state.playerView.getMyId()) {
                continue;
            }
            if (wasInThatCorner[player.getId()]) {
                continue;
            }
            int score = player.getScore();
            final Position targetPos = getPositionByPlayerId(state.playerView.getMyId(), player.getId(), mapSize);
            if (targetPos.distTo(topRight) == 0) {
                score /= 2;
            }
            if (score > bScore) {
                bScore = score;
                bPlayer = player;
            }
        }
        if (bPlayer == null) {
            return topRight;
        }
        return getPositionByPlayerId(state.playerView.getMyId(), bPlayer.getId(), mapSize);
    }

}

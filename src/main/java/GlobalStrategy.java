import model.Entity;
import model.EntityType;
import model.Position;

import java.util.*;

import static model.EntityType.*;

public class GlobalStrategy {
    private static final int LOW_TOTAL_RESOURCES = 10000;
    State state;

    public GlobalStrategy(State state) {
        this.state = state;
    }

    private boolean expectedNeedHouse(int currentUsage, int currentExpected) {
        if (currentUsage < 10) {
            return currentExpected < currentUsage + 3;
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

        EntityType chooseWhatToBuild(final State state) {
            // 0 -> not enough
            // 787788 -> too much
            double smallestCoef = Double.MAX_VALUE;
            EntityType best = null;
            for (Map.Entry<EntityType, Integer> entry : count.entrySet()) {
                int currentCnt = state.myEntitiesCount.get(entry.getKey());
                if (entry.getValue() == 0) {
                    continue;
                }
                if (!hasBuildingToBuild(state, entry.getKey())) {
                    continue;
                }
                double curCoef = currentCnt / (double) entry.getValue();
                if (curCoef < smallestCoef) {
                    smallestCoef = curCoef;
                    best = entry.getKey();
                }
            }
            return best;
        }


        static ExpectedEntitiesDistribution V1 = new ExpectedEntitiesDistribution(5, 1, 1, 0);
        static ExpectedEntitiesDistribution V2 = new ExpectedEntitiesDistribution(10, 0, 2, 1);
        static ExpectedEntitiesDistribution V3 = new ExpectedEntitiesDistribution(40, 0, 9, 3);
        static ExpectedEntitiesDistribution ONLY_BUILDERS = new ExpectedEntitiesDistribution(1, 0, 0, 0);
        static ExpectedEntitiesDistribution ALMOST_RANGED = new ExpectedEntitiesDistribution(1, 0, 2, 0);


    }

    final int MAX_BUILDERS = 50;

    private boolean needMoreBuilders() {
        return state.myEntitiesCount.get(BUILDER_UNIT) < 20 && state.playerView.getCurrentTick() < 100;
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
    private ProtectSomething protectSomethingCache;

    public ProtectSomething needToProtectSomething() {
        if (!calculatedProtectSomething) {
            calculatedProtectSomething = true;
            protectSomethingCache = needToProtectSomethingWithoutCache();
        }
        return protectSomethingCache;
    }

    private ProtectSomething needToProtectSomethingWithoutCache() {
        List<RequiresProtection> requiresProtections = new ArrayList<>();
        for (EntityType buildingTypeToProtect : new EntityType[]{RANGED_BASE, MELEE_BASE, BUILDER_BASE}) {
            List<Entity> buildings = state.myEntitiesByType.get(buildingTypeToProtect);
            if (buildings.size() != 1) {
                continue;
            }
            Entity buildingToProtect = buildings.get(0);
            double dangerLevel = calcDangerLevel(buildingToProtect);
            if (dangerLevel == 0) {
                continue;
            }
            if (!state.map.existEnemyWarUnitsNearby(buildingToProtect, DIST_TO_CONSIDER_DANGER * 2)) {
                // Can't go through trees
                continue;
            }
            requiresProtections.add(new RequiresProtection(buildingToProtect, dangerLevel));
        }
        Collections.sort(requiresProtections);
        if (requiresProtections.isEmpty()) {
            return null;
        }
        Entity building = requiresProtections.get(0).building;
        Position target = PositionsPicker.getTarget(state, building, RANGED_UNIT);
        if (!state.myEntitiesByType.get(RANGED_BASE).isEmpty()) {
            return new ProtectSomething(RANGED_UNIT, target);
        }
        if (!state.myEntitiesByType.get(MELEE_BASE).isEmpty()) {
            return new ProtectSomething(MELEE_UNIT, target);
        }
        return new ProtectSomething(null, target);
    }


    EntityType cachedWhatToBuild;
    boolean whatToBuildWasCached;

    EntityType whatNextToBuild() {
        if (!whatToBuildWasCached) {
            cachedWhatToBuild = whatNextToBuildWithoutCache();
            whatToBuildWasCached = true;
        }
        return cachedWhatToBuild;
    }

    final int FIRST_TICK_FOR_RANGED_BASE = 150;

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

    EntityType whatNextToBuildWithoutCache() {
        ProtectSomething toProtect = needToProtectSomething();
        if (toProtect != null && toProtect.whatToBuild != null && hasEnoughHousesToBuildUnits()) {
            return toProtect.whatToBuild;
        }
        if (needRangedHouse() && state.playerView.getCurrentTick() > FIRST_TICK_FOR_RANGED_BASE) {
            return RANGED_BASE;
        }
        if (needBuilderBase()) {
            return BUILDER_BASE;
        }
        if (needMoreHouses()) {
            return HOUSE;
        }
        if (needMoreBuilders()) {
            return BUILDER_UNIT;
        }
        ExpectedEntitiesDistribution distribution = shouldBeAggressive() ?
                ExpectedEntitiesDistribution.ALMOST_RANGED :
                ExpectedEntitiesDistribution.ONLY_BUILDERS;
        if (state.myEntitiesCount.get(BUILDER_UNIT) > MAX_BUILDERS || lowResourcesInTotal()) {
            distribution = distribution.noMoreBuilders();
        }
        return distribution.chooseWhatToBuild(state);
        // TODO: make it smarter
    }
}

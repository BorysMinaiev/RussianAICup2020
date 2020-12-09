import model.BuildProperties;
import model.Entity;
import model.EntityType;

import java.util.*;

import static model.EntityType.*;

public class GlobalStrategy {
    State state;

    public GlobalStrategy(State state) {
        this.state = state;
    }

    private boolean expectedNeedHouse(int currentUsage, int currentExpected) {
        if (currentUsage < 10) {
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
                (used == 15 && state.populationExpected == 25);
        return (reallyNeed && !currentlyBuildingALot) || expectedNeed;
    }

    private boolean hasEnoughHousesToBuildUnits() {
        return state.populationUsed < state.populationTotal;
    }

    private boolean needRangedHouse() {
        return state.myEntitiesCount.get(RANGED_BASE) == 0;
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
    }

    final int MAX_BUILDERS = 50;

    private boolean needMoreBuilders() {
        return state.myEntitiesCount.get(BUILDER_UNIT) < 20 && state.playerView.getCurrentTick() < 100;
    }

    static class RequiresProtection implements Comparable<RequiresProtection> {
        final Entity building;
        final EntityType canProduce;
        final double dangerLevel;

        public RequiresProtection(Entity building, EntityType canProduce, double dangerLevel) {
            this.building = building;
            this.canProduce = canProduce;
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

    private EntityType needToProtectSomething() {

        List<RequiresProtection> requiresProtections = new ArrayList<>();
        for (EntityType buildingTypeToProtect : new EntityType[]{RANGED_BASE, MELEE_BASE}) {
            List<Entity> buildings = state.myEntitiesByType.get(buildingTypeToProtect);
            if (buildings.size() != 1) {
                continue;
            }
            Entity buildingToProtect = buildings.get(0);
            double dangerLevel = calcDangerLevel(buildingToProtect);
            if (dangerLevel == 0) {
                continue;
            }
            BuildProperties properties = state.getEntityProperties(buildingToProtect).getBuild();
            if (properties == null) {
                throw new AssertionError("Can't build anything?");
            }
            EntityType[] whatCanBuild = properties.getOptions();
            if (whatCanBuild.length < 1) {
                throw new AssertionError("very strange?");
            }
            requiresProtections.add(new RequiresProtection(buildingToProtect, whatCanBuild[0], dangerLevel));
        }
        Collections.sort(requiresProtections);
        if (requiresProtections.isEmpty()) {
            return null;
        }
        return requiresProtections.get(0).canProduce;
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

    EntityType whatNextToBuildWithoutCache() {
        EntityType toProtect = needToProtectSomething();
        if (toProtect != null && hasEnoughHousesToBuildUnits()) {
            return toProtect;
        }
        if (needRangedHouse()) {
            return RANGED_BASE;
        }
        if (needMoreHouses()) {
            return HOUSE;
        }
        if (needMoreBuilders()) {
            return BUILDER_UNIT;
        }
        ExpectedEntitiesDistribution distribution = ExpectedEntitiesDistribution.V2;
        if (state.myEntitiesCount.get(BUILDER_UNIT) > MAX_BUILDERS) {
            distribution = distribution.noMoreBuilders();
        }
        return distribution.chooseWhatToBuild(state);
        // TODO: make it smarter
    }
}

import model.EntityType;

import java.util.HashMap;
import java.util.Map;

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

    EntityType whatNextToBuild() {
        if (needRangedHouse()) {
            return RANGED_BASE;
        }
        if (needMoreHouses()) {
            return HOUSE;
        }
        if (needMoreBuilders()) {
            return BUILDER_UNIT;
        }
        // TODO: use V2
        ExpectedEntitiesDistribution distribution = ExpectedEntitiesDistribution.V2;
        if (state.myEntitiesCount.get(BUILDER_UNIT) > MAX_BUILDERS) {
            distribution = distribution.noMoreBuilders();
        }
        return distribution.chooseWhatToBuild(state);
        // TODO: make it smarter
    }
}

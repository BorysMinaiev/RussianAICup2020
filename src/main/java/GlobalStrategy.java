import model.EntityType;

import java.util.HashMap;
import java.util.Map;

import static model.EntityType.*;

public class GlobalStrategy {
    State state;

    public GlobalStrategy(State state) {
        this.state = state;
    }

    private boolean needMoreHouses() {
        // TODO: think about it?
        return state.populationTotal - state.populationUsed < 10;
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

    EntityType whatNextToBuild() {
        if (needRangedHouse()) {
            return RANGED_BASE;
        }
        if (needMoreHouses()) {
            return HOUSE;
        }
        // TODO: use V2
        return ExpectedEntitiesDistribution.V2.chooseWhatToBuild(state);
        // TODO: make it smarter
    }
}

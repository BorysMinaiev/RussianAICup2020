import model.EntityType;

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

    EntityType whatNextToBuild() {
        if (needMoreHouses()) {
            return HOUSE;
        }
        int buildersNum = state.myEntitiesCount.get(BUILDER_UNIT);
        int turretsNum = state.myEntitiesCount.get(TURRET);
        if (turretsNum * 5 < buildersNum) {
            return TURRET;
        }
        return BUILDER_UNIT;
    }
}

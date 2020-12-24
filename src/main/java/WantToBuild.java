import model.Entity;
import model.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class WantToBuild {
    int expectedMoreMoney;
    HashSet<EntityType> wantThis;
    final State state;
    boolean acceptMore;

    @Override
    public String toString() {
        return wantThis.toString();
    }

    WantToBuild(final State state) {
        this.state = state;
        expectedMoreMoney = state.playerView.getMyPlayer().getResource();
        int expectedMoreResources = 0;
        for (Entity builder : state.myEntitiesByType.get(EntityType.BUILDER_UNIT)) {
            if (BuilderStrategy.canMineRightNow(state, builder)) {
                expectedMoreResources++;
            }
        }
        expectedMoreMoney += Math.max(expectedMoreResources - 1, 0);
        wantThis = new HashSet<>();
        acceptMore = true;
    }

    int needMoney(EntityType entityType) {
        int initialCost = state.getEntityTypeProperties(entityType).getInitialCost();
        if (entityType.isBuilding()) {
            return initialCost;
        }
        int numAlready = state.myEntitiesCount.get(entityType);
        return initialCost + numAlready;
    }

    void add(EntityType entityType) {
        if (entityType == null) {
            return;
        }
        if (!acceptMore) {
            return;
        }
        if (wantThis.contains(entityType)) {
            return;
        }
        int needMoney = needMoney(entityType);
        if (expectedMoreMoney >= needMoney) {
            expectedMoreMoney -= needMoney;
            wantThis.add(entityType);
        } else {
            acceptMore = false;
        }
    }

    public List<EntityType> whichBuildings() {
        List<EntityType> buildings = new ArrayList<>();
        for (EntityType entityType : wantThis) {
            if (entityType.isBuilding()) {
                buildings.add(entityType);
            }
        }
        return buildings;
    }

    public List<EntityType> whichUnits() {
        List<EntityType> units = new ArrayList<>();
        for (EntityType entityType : wantThis) {
            if (!entityType.isBuilding()) {
                units.add(entityType);
            }
        }
        return units;
    }

}

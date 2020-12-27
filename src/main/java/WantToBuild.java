import model.Entity;
import model.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class WantToBuild {
    int expectedMoreMoney;
    final int initialExpectedMoreMoney;
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
        initialExpectedMoreMoney = expectedMoreMoney;
        wantThis = new HashSet<>();
        acceptMore = true;
    }

    public boolean enoughResourcesToBuild(EntityType entityType) {
        return state.getEntityTypeProperties(entityType).getInitialCost() <= initialExpectedMoreMoney;
    }

    int needMoney(EntityType entityType) {
        int initialCost = state.getEntityTypeProperties(entityType).getInitialCost();
        if (entityType.isBuilding()) {
            return initialCost;
        }
        int numAlready = state.myEntitiesCount.get(entityType);
        return initialCost + numAlready;
    }

    void forceAdd(EntityType entityType) {
        int needMoney = needMoney(entityType);
        if (expectedMoreMoney >= needMoney) {
            expectedMoreMoney -= needMoney;
        } else {
            acceptMore = false;
        }
        wantThis.add(entityType);
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
        if (entityType.isBuilding() && hasBuilding()) {
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

    private boolean hasBuilding() {
        return !whichBuildings().isEmpty();
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

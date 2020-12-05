import model.*;

import java.util.List;

import static model.EntityType.*;

public class MyStrategy {

    void spawnBuilder(final State state, final Entity building) {
        if (!state.isEnoughResourcesToBuild(BUILDER_UNIT)) {
            return;
        }
        Position pos = pickPositionToBuild(state, building, BUILDER_UNIT);
        state.buildSomething(building, BUILDER_UNIT, pos);
    }

    void spawnNewUnits(final State state, final Entity building) {
        if (building.getEntityType() == BUILDER_BASE) {
            spawnBuilder(state, building);
        }
    }

    Position pickPosition(List<Position> positions) {
        // TODO: smart things?
        if (positions.isEmpty()) {
            return null;
        }
        return positions.get(0);
    }

    Position pickPositionToBuild(final State state, final Entity who, final EntityType what) {
        List<Position> housePositions = state.findPossiblePositionToBuild(who, what);
        System.err.println("want to build: " + what + ", ways to put: " + housePositions.size());
        return pickPosition(housePositions);
    }

    void moveRandomly(final State state, final Entity unit) {
        MoveAction moveAction = null;
        BuildAction buildAction = null;
        moveAction = new MoveAction(
                new Position(state.playerView.getMapSize() - 1, state.playerView.getMapSize() - 1),
                true,
                true);
        EntityType[] validAutoAttackTargets;
        if (unit.getEntityType() == EntityType.BUILDER_UNIT) {
            validAutoAttackTargets = new EntityType[]{EntityType.RESOURCE};
        } else {
            validAutoAttackTargets = new EntityType[0];
        }
        EntityProperties properties = state.playerView.getEntityProperties().get(unit.getEntityType());
        state.actions.getEntityActions().put(unit.getId(), new EntityAction(
                moveAction,
                buildAction,
                new AttackAction(
                        null, new AutoAttack(properties.getSightRange(), validAutoAttackTargets)
                ),
                null
        ));
    }

    Position whereToBuildBuilding(final State state, final Entity builder, final EntityType what) {
        if (!state.isEnoughResourcesToBuild(what)) {
            return null;
        }
        return pickPositionToBuild(state, builder, what);
    }


    Integer getTargetToRepair(final State state, final Entity builder) {
        // TODO: repair something not close to me?
        for (Entity entity : state.myEntities) {
            if (entity.isActive()) {
                // TODO: think about this condition?
                continue;
            }
            if (state.isNearby(entity, builder)) {
                return entity.getId();
            }
        }
        return null;
    }

    void builderStrategy(final State state, final Entity builder) {
        Integer repairId = getTargetToRepair(state, builder);
        if (repairId != null) {
            state.repairSomething(builder, repairId);
            return;
        }
        boolean needMoreHouses = state.globalStrategy.needMoreHouses();
        Position pos = needMoreHouses ? whereToBuildBuilding(state, builder, HOUSE) : null;
        if (pos != null) {
            state.buildSomething(builder, HOUSE, pos);
        } else {
            moveRandomly(state, builder);
        }
    }

    void turretStrategy(final State state, final Entity turret) {
        // TODO: more clever thing?
        state.attackSomebody(turret);
    }

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        State state = new State(playerView);
        int myId = playerView.getMyId();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != myId) {
                continue;
            }
            EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

            if (properties.isBuilding()) {
                if (entity.getEntityType() == TURRET) {
                    turretStrategy(state, entity);
                } else {
                    spawnNewUnits(state, entity);
                }
            } else if (entity.getEntityType() == BUILDER_UNIT) {
                builderStrategy(state, entity);
            } else {
                MoveAction moveAction = null;
                BuildAction buildAction = null;
                moveAction = new MoveAction(
                        new Position(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                        true,
                        true);
                EntityType[] validAutoAttackTargets;
                if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
                    validAutoAttackTargets = new EntityType[]{EntityType.RESOURCE};
                } else {
                    validAutoAttackTargets = new EntityType[0];
                }
                state.actions.getEntityActions().put(entity.getId(), new EntityAction(
                        moveAction,
                        buildAction,
                        new AttackAction(
                                null, new AutoAttack(properties.getSightRange(), validAutoAttackTargets)
                        ),
                        null
                ));
            }
        }
        return state.actions;
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }
}
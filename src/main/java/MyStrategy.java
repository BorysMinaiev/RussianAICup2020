import model.*;

import static model.EntityType.*;

public class MyStrategy {
    void spawnUnit(final State state, final Entity building, final EntityType unitType) {
        if (!state.isEnoughResourcesToBuild(unitType)) {
            return;
        }
        Position pos = PositionsPicker.pickPositionToBuildUnit(state, building, unitType);
        if (pos == null) {
            return;
        }
        state.buildSomething(building, unitType, pos);
    }

    void spawnNewUnits(final State state, final Entity building) {
        EntityType nextToBuild = state.globalStrategy.whatNextToBuild();
        if (state.checkCanBuild(building.getEntityType(), nextToBuild)) {
            spawnUnit(state, building, nextToBuild);
        }
    }

    void turretStrategy(final State state, final Entity turret) {
        // TODO: more clever thing?
        state.attackSomebody(turret);
    }

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        State state = new State(playerView, debugInterface);
        int myId = playerView.getMyId();
        RangedUnitStrategy rangedUnitStrategy = new RangedUnitStrategy(state);
        rangedUnitStrategy.makeMoveForAll();
        state.globalStrategy.setNeedMoreRangedUnits(rangedUnitStrategy.needMoreUnitsForSupport);
        BuilderStrategy.makeMoveForAll(state);
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
            } else if (entity.getEntityType() == BUILDER_UNIT || entity.getEntityType() == RANGED_UNIT) {

//                BuilderStrategy.makeMove(state, entity);
            } else {
                MoveAction moveAction = null;
                BuildAction buildAction = null;
                moveAction = new MoveAction(
                        new Position(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                        true,
                        true);
                EntityType[] validAutoAttackTargets;
                if (entity.getEntityType() == BUILDER_UNIT) {
                    validAutoAttackTargets = new EntityType[]{RESOURCE};
                } else {
                    validAutoAttackTargets = new EntityType[0];
                }
                EntityAction action = new EntityAction(
                        moveAction,
                        buildAction,
                        new AttackAction(
                                null, new AutoAttack(properties.getSightRange() * 5, validAutoAttackTargets)
                        ),
                        null
                );
                state.movesPicker.addManualAction(entity, action, MovesPicker.PRIORITY_SMALL);
            }
        }
        Action action = state.movesPicker.buildActions();
        state.printSomeDebug(debugInterface, false, action);
        return action;
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        if (debugInterface == null) {
            return;
        }
        State state = new State(playerView, debugInterface);
        state.globalStrategy.setNeedMoreRangedUnits(false);
        state.printSomeDebug(debugInterface, true, null);
    }
}
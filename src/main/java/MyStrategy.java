import model.*;

import static model.EntityType.*;

public class MyStrategy {
    void spawnBuilder(final State state, final Entity building) {
        if (!state.isEnoughResourcesToBuild(BUILDER_UNIT)) {
            return;
        }
        Position pos = PositionsPicker.pickPositionToBuild(state, building, BUILDER_UNIT);
        state.buildSomething(building, BUILDER_UNIT, pos);
    }

    void spawnNewUnits(final State state, final Entity building) {
        if (building.getEntityType() == BUILDER_BASE) {
            spawnBuilder(state, building);
        }
    }

    void turretStrategy(final State state, final Entity turret) {
        // TODO: more clever thing?
        state.attackSomebody(turret);
    }

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        State state = new State(playerView, debugInterface);
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
                BuilderStrategy.makeMove(state, entity);
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
//        debugInterface.send(new DebugCommand.Clear());
//        debugInterface.getState();
    }
}
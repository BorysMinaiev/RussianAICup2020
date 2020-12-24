import model.*;

import java.util.HashMap;
import java.util.List;

import static model.EntityType.*;

public class MyStrategy {
    void spawnUnit(final State state, final Entity building, final EntityType unitType) {
        if (!state.isEnoughResourcesToBuild(unitType)) {
            return;
        }
        List<PositionsPicker.PositionWithScore> options = PositionsPicker.pickPositionToBuildUnit(state, building, unitType);
        for (PositionsPicker.PositionWithScore posWithScore : options) {
            state.buildSomething(building, unitType, posWithScore.pos, posWithScore.score + MovesPicker.PRIORITY_BUILD);
        }
    }

    void spawnNewUnits(final State state, final Entity building) {
        WantToBuild wantToBuild = state.globalStrategy.whatNextToBuild();
        List<EntityType> unitsToBuild = wantToBuild.whichUnits();
        for (EntityType nextToBuild : unitsToBuild) {
            if (state.checkCanBuild(building.getEntityType(), nextToBuild)) {
                spawnUnit(state, building, nextToBuild);
            }
        }
    }

    void turretStrategy(final State state, final Entity turret) {
        // TODO: more clever thing?
        state.attackSomebody(turret);
    }

    private boolean hackForTimeLimit(PlayerView playerView) {
        int myScore = playerView.getMyPlayer().getScore();
        int maxOtherScore = 0;
        for (Player player : playerView.getPlayers()) {
            if (player.getId() == playerView.getMyId()) {
                continue;
            }
            maxOtherScore = Math.max(maxOtherScore, player.getScore());
        }
        if (playerView.getCurrentTick() > 300 && maxOtherScore * 3 < myScore) {
            return true;
        }
        return false;
    }

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        if (hackForTimeLimit(playerView)) {
            Action action = new Action(new HashMap<>());
            for (Entity entity : playerView.getEntities()) {
                if (entity.getPlayerId() != null && entity.getPlayerId() == playerView.getMyId()) {
                    action.getEntityActions().put(entity.getId(), EntityAction.createAttackAction(null,
                            new AutoAttack(50, new EntityType[]{})));
                }
            }
            return action;
        }
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
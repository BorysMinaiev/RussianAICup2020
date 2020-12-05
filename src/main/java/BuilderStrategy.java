import model.*;

import java.util.Collections;
import java.util.List;

import static model.EntityType.HOUSE;

public class BuilderStrategy {
    static Integer getTargetToRepair(final State state, final Entity builder) {
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

    static Position whereToBuildBuilding(final State state, final Entity builder, final EntityType what) {
        if (!state.isEnoughResourcesToBuild(what)) {
            return null;
        }
        return PositionsPicker.pickPositionToBuild(state, builder, what);
    }


    static void moveRandomly(final State state, final Entity unit) {
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

    static boolean moveAwayFromAttack(final State state, final Entity builder) {
        List<Position> allPossibleMoves = state.getAllPossibleUnitMoves(builder);
        Position bestPosToGo = builder.getPosition();
        double currentAttackScore = state.attackedByPos.getOrDefault(bestPosToGo, 0.0);
        Collections.shuffle(allPossibleMoves, state.rnd);
        for (Position check : allPossibleMoves) {
            double scoreHere = state.attackedByPos.getOrDefault(check, 0.0);
            if (scoreHere < currentAttackScore) {
                currentAttackScore = scoreHere;
                bestPosToGo = check;
            }
        }
        if (bestPosToGo == builder.getPosition()) {
            return false;
        }
        state.move(builder, bestPosToGo);
        return true;
    }

    static void makeMove(final State state, final Entity builder) {
        Integer repairId = getTargetToRepair(state, builder);
        if (repairId != null) {
            state.repairSomething(builder, repairId);
            return;
        }
        if (state.attackedByPos.get(builder.getPosition()) != null) {
            if (moveAwayFromAttack(state, builder)) {
                return;
            }
        }
        boolean needMoreHouses = state.globalStrategy.needMoreHouses();
        Position pos = needMoreHouses ? whereToBuildBuilding(state, builder, HOUSE) : null;
        if (pos != null) {
            state.buildSomething(builder, HOUSE, pos);
        } else {
            moveRandomly(state, builder);
        }
    }
}


import model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static model.EntityType.BUILDER_UNIT;

public class BuilderStrategy {
    static Integer getTargetToRepair(final State state, final Entity builder) {
        // TODO: repair something not close to me?
        for (Entity entity : state.myEntities) {
            if (entity.getHealth() == state.getEntityProperties(entity).getMaxHealth() && entity.isActive()) {
                continue;
            }
            if (state.isNearby(entity, builder)) {
                return entity.getId();
            }
        }
        return null;
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
                        null, new AutoAttack(properties.getSightRange() * 10, validAutoAttackTargets)
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

    static class BuildOption implements Comparable<BuildOption> {
        final Entity builder;
        final Position where;
        final int buildingCellsNearby;
        final int distToZero;

        int computeBuildingCellsNearby(final State state, final EntityType what) {
            int occupiedCellsNearby = 0;

            int buildingSize = state.getEntityTypeProperties(what).getSize();
            Position bottomLeft = where.shift(-1, -1);
            Position topRight = where.shift(buildingSize, buildingSize);
            for (int x = bottomLeft.getX(); x <= topRight.getX(); x++) {
                for (int y = bottomLeft.getY(); y <= topRight.getY(); y++) {
                    if (!CellsUtils.isOnRectBorder(bottomLeft, topRight, x, y)) {
                        continue;
                    }
                    if (state.isOccupiedByBuilding(new Position(x, y))) {
                        occupiedCellsNearby++;
                    }
                }
            }

            return occupiedCellsNearby;
        }

        BuildOption(final State state, Entity builder, Position where, EntityType what) {
            this.builder = builder;
            this.where = where;
            this.buildingCellsNearby = computeBuildingCellsNearby(state, what);
            this.distToZero = where.getX() + where.getY();
        }

        @Override
        public int compareTo(BuildOption o) {
            if (buildingCellsNearby != o.buildingCellsNearby) {
                return Integer.compare(buildingCellsNearby, o.buildingCellsNearby);
            }
            return Integer.compare(distToZero, o.distToZero);
        }
    }

    static void makeMoveForAll(final State state) {
        final List<Entity> allBuilders = state.myEntitiesByType.get(BUILDER_UNIT);
        List<Entity> canBuildOrMineResources = new ArrayList<>();
        for (Entity builder : allBuilders) {
            Integer repairId = getTargetToRepair(state, builder);
            if (repairId != null) {
                state.repairSomething(builder, repairId);
                continue;
            }
            if (state.attackedByPos.get(builder.getPosition()) != null) {
                // TODO: smarter things!
                if (moveAwayFromAttack(state, builder)) {
                    continue;
                }
            }
            canBuildOrMineResources.add(builder);
        }
        if (canBuildOrMineResources.isEmpty()) {
            return;
        }
        EntityType toBuild = state.globalStrategy.whatNextToBuild();
        boolean needBuildSmth = toBuild != null && toBuild.isBuilding() && state.isEnoughResourcesToBuild(toBuild);
        if (needBuildSmth) {
            List<BuildOption> buildOptions = new ArrayList<>();
            for (Entity builder : canBuildOrMineResources) {
                List<Position> possiblePositions = state.findPossiblePositionToBuild(builder, toBuild);
                for (Position where : possiblePositions) {
                    if (where == null) {
                        throw new AssertionError();
                    }
                    buildOptions.add(new BuildOption(state, builder, where, toBuild));
                }
            }
            Collections.sort(buildOptions);
            if (!buildOptions.isEmpty()) {
                BuildOption option = buildOptions.get(0);
                state.buildSomething(option.builder, toBuild, option.where);
                canBuildOrMineResources.remove(option.builder);
            }
        }
        for (Entity builder : canBuildOrMineResources) {
            moveRandomly(state, builder);
        }
    }
}


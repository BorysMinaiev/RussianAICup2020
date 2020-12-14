import model.Entity;
import model.EntityType;
import model.Position;

import java.util.*;

import static model.EntityType.*;

public class BuilderStrategy {
    private static final int MAX_DIST_TO_SEARCH_BUILDERS_FOR_REPAIR = 5;

    static Entity getTargetToRepair(final State state, final Entity builder) {
        // TODO: repair something not close to me?
        for (Entity entity : state.myEntities) {
            if (entity.getHealth() == state.getEntityProperties(entity).getMaxHealth() && entity.isActive()) {
                continue;
            }
            if (entity.getEntityType() == BUILDER_UNIT) {
                continue;
            }
            if (state.isNearby(entity, builder)) {
                return entity;
            }
        }
        return null;
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

    static void findSafePathToResources(final State state, final Entity builder, final MapHelper.BfsQueue bfsQueue) {
        final Position pos = builder.getPosition();
        final int currentDist = bfsQueue.getDist(pos.getX(), pos.getY());
        final Position goTo = state.map.findFirstCellOnPath(pos, pos, currentDist, bfsQueue);
        if (goTo != null) {
            state.move(builder, goTo);
            if (state.debugInterface != null) {
                final Position targetCell = state.map.findLastCellOnPath(pos, currentDist, bfsQueue);
                state.debugTargets.put(builder.getPosition(), targetCell);
            }
        } else {
            if (!moveAwayFromAttack(state, builder)) {
                // I will die, but at least will do something!
                state.addDebugUnitInBadPosition(builder.getPosition());
                if (!mineRightNow(state, builder)) {
                    // TODO: do something?
                }
            }
        }
    }

    static void markBuilderAsWorking(final State state, final Entity builder) {
        final Position pos = builder.getPosition();
        state.map.updateCellCanGoThrough(pos, MapHelper.CAN_GO_THROUGH.MY_WORKING_BUILDER);
    }

    static boolean mineRightNow(final State state, final Entity builder) {
        final Position pos = builder.getPosition();
        int[] dx = new int[]{-1, 0, 0, 1};
        int[] dy = new int[]{0, -1, 1, 0};
        for (int it = 0; it < dx.length; it++) {
            int nx = pos.getX() + dx[it];
            int ny = pos.getY() + dy[it];
            if (state.map.canMineThisCell(nx, ny)) {
                markBuilderAsWorking(state, builder);
                state.attack(builder, state.map.entitiesByPos[nx][ny]);
                return true;
            }
        }
        return false;
    }

    static int getNumWorkersToHelpRepair(final EntityType type) {
        if (type == RANGED_BASE || type == BUILDER_BASE) {
            return 8;
        }
        if (type == HOUSE) {
            return 2;
        }
        return 1;
    }

    static void handleAllRepairings(final State state) {
        final List<Entity> allBuilders = state.myEntitiesByType.get(BUILDER_UNIT);
        final Map<Entity, Integer> alreadyRepairing = new HashMap<>();
        for (Entity builder : allBuilders) {
            Entity toRepair = getTargetToRepair(state, builder);
            if (toRepair != null) {
                state.repairSomething(builder, toRepair.getId());
                markBuilderAsWorking(state, builder);
                alreadyRepairing.put(toRepair, alreadyRepairing.getOrDefault(toRepair, 0) + 1);
            }
        }
        for (Entity entity : state.myEntities) {
            if (!entity.getEntityType().isBuilding()) {
                continue;
            }
            if (entity.isActive() && entity.getHealth() == state.getEntityProperties(entity).getMaxHealth()) {
                continue;
            }
            final int alreadyWorkers = alreadyRepairing.getOrDefault(entity, 0);
            final int needMoreWorkers = getNumWorkersToHelpRepair(entity.getEntityType()) - alreadyWorkers;
            if (needMoreWorkers <= 0) {
                continue;
            }
            final List<Position> initialPositions = allPositionsOfEntity(state, entity);
            MapHelper.PathsFromBuilders pathsForBuilders = state.map.findPathsToBuilding(initialPositions,
                    MAX_DIST_TO_SEARCH_BUILDERS_FOR_REPAIR, needMoreWorkers);
            for (Map.Entry<Entity, Position> entry : pathsForBuilders.firstCellsInPath.entrySet()) {
                final Entity builder = entry.getKey();
                final Position nextPos = entry.getValue();
                state.move(builder, nextPos);
                state.debugTargets.put(builder.getPosition(), entity.getPosition());
                // TODO: something else?
            }
        }
    }

    private static List<Position> allPositionsOfEntity(final State state, final Entity entity) {
        final int size = state.getEntityProperties(entity).getSize();
        List<Position> positions = new ArrayList<>();
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                positions.add(entity.getPosition().shift(dx, dy));
            }
        }
        return positions;
    }

    static void makeMoveForAll(final State state) {
        handleAllRepairings(state);
        final List<Entity> allBuilders = state.myEntitiesByType.get(BUILDER_UNIT);
        List<Entity> canBuildOrMineResources = new ArrayList<>();
        List<Entity> underAttack = new ArrayList<>();
        for (Entity builder : allBuilders) {
            if (state.alreadyHasAction(builder)) {
                continue;
            }
            final Position pos = builder.getPosition();
            if (state.map.underAttack[pos.getX()][pos.getY()] == MapHelper.UNDER_ATTACK.UNDER_ATTACK) {
                underAttack.add(builder);
            } else {
                canBuildOrMineResources.add(builder);
            }
        }

        EntityType toBuild = state.globalStrategy.whatNextToBuild();
        boolean needBuildSmth = toBuild != null && toBuild.isBuilding() && state.isEnoughResourcesToBuild(toBuild);
        if (needBuildSmth && !canBuildOrMineResources.isEmpty()) {
            List<BuildOption> buildOptions = new ArrayList<>();
            for (Entity builder : canBuildOrMineResources) {
                List<Position> possiblePositions = state.findPossiblePositionToBuild(builder, toBuild);
                for (Position where : possiblePositions) {
                    if (where == null) {
                        throw new AssertionError();
                    }
                    if (willBeUnderAttack(state, toBuild, where)) {
                        continue;
                    }
                    buildOptions.add(new BuildOption(state, builder, where, toBuild));
                }
            }
            Collections.sort(buildOptions);
            if (!buildOptions.isEmpty()) {
                BuildOption option = buildOptions.get(0);
                state.buildSomething(option.builder, toBuild, option.where);
                markBuilderAsWorking(state, option.builder);
                canBuildOrMineResources.remove(option.builder);
            }
        }
        List<Entity> shouldGoMine = new ArrayList<>();
        shouldGoMine.addAll(canBuildOrMineResources);
        shouldGoMine.addAll(underAttack);
        List<Entity> needPathToResources = new ArrayList<>();
        for (Entity builder : shouldGoMine) {
            final Position pos = builder.getPosition();
            if (state.map.underAttack[pos.getX()][pos.getY()] == MapHelper.UNDER_ATTACK.SAFE) {
                if (mineRightNow(state, builder)) {
                    continue;
                }
            }
            needPathToResources.add(builder);
        }
        MapHelper.BfsQueue bfsQueue = state.map.findPathsToResources();
        for (Entity builder : needPathToResources) {
            findSafePathToResources(state, builder, bfsQueue);
        }
    }

    private static boolean willBeUnderAttack(State state, EntityType toBuild, Position where) {
        final int size = state.getEntityTypeProperties(toBuild).getSize();
        final MapHelper map = state.map;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                if (map.underAttack[where.getX() + dx][where.getY() + dy] == MapHelper.UNDER_ATTACK.UNDER_ATTACK) {
                    return true;
                }
            }
        }
        return false;
    }
}


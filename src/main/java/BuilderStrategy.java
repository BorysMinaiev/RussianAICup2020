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
        List<Position> allPossibleMoves = state.getAllPossibleUnitMoves(builder, false, true);
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
        state.move(builder, bestPosToGo, MovesPicker.PRIORITY_GO_AWAY_FROM_ATTACK);
        return true;
    }

    static class BuildOption implements Comparable<BuildOption> {
        final Entity builder;
        final Position where;
        final int occupiedCellsNearby;
        final int distToZero;
        final int distToBuilder;

        @Override
        public String toString() {
            return "BuildOption{" +
                    "builder=" + builder +
                    ", where=" + where +
                    ", occupiedCellsNearby=" + occupiedCellsNearby +
                    ", distToZero=" + distToZero +
                    ", distToBuilder=" + distToBuilder +
                    '}';
        }

        List<Boolean> computeBuildingOrResourcesNearby(final State state, final EntityType what) {
            int buildingSize = state.getEntityTypeProperties(what).getSize();
            Position bottomLeft = where.shift(-1, -1);
            Position topRight = where.shift(buildingSize, buildingSize);
            List<Position> posToCheck = CellsUtils.getPositionsOnRectBorderCCW(bottomLeft, topRight);
            List<Boolean> occupied = new ArrayList<>();
            for (Position pos : posToCheck) {
                occupied.add(state.isOccupiedByBuilding(pos) || state.isOccupiedByResource(pos));
            }
            return occupied;
        }

        private int occupiedCellsNearby(List<Boolean> occupied) {
            int occupiedCellsNearby = 0;
            for (boolean occ : occupied) {
                if (occ) {
                    occupiedCellsNearby++;
                }
            }
            return occupiedCellsNearby;
        }

        BuildOption(final State state, Entity builder, Position where, EntityType what, final int distToBuilder) {
            this.builder = builder;
            this.where = where;
            final List<Boolean> occupiedCellsNearby = computeBuildingOrResourcesNearby(state, what);
            this.occupiedCellsNearby = occupiedCellsNearby(occupiedCellsNearby);
            final int distToZeroMultiplier = what == TURRET || what == RANGED_BASE ? (-1) : 1;
            this.distToZero = (where.getX() + where.getY()) * distToZeroMultiplier;
            this.distToBuilder = distToBuilder;
        }

        @Override
        public int compareTo(BuildOption o) {
            if (distToBuilder != o.distToBuilder) {
                return Integer.compare(distToBuilder, o.distToBuilder);
            }
//            if (occupiedCellsNearby != o.occupiedCellsNearby) {
//                return Integer.compare(occupiedCellsNearby, o.occupiedCellsNearby);
//            }
            return Integer.compare(distToZero, o.distToZero);
        }
    }

    static void markBuilderAsWorking(final State state, final Entity builder) {
        final Position pos = builder.getPosition();
        state.map.updateCellCanGoThrough(pos, MapHelper.CAN_GO_THROUGH.MY_WORKING_BUILDER);
    }

    static boolean mineRightNow(final State state, final Entity builder) {
        final Position pos = builder.getPosition();
        int[] dx = Directions.dx;
        int[] dy = Directions.dy;
        for (int it = 0; it < dx.length; it++) {
            int nx = pos.getX() + dx[it];
            int ny = pos.getY() + dy[it];
            if (state.map.canMineThisCell(nx, ny)) {
                markBuilderAsWorking(state, builder);
                state.attack(builder, state.map.entitiesByPos[nx][ny], MovesPicker.PRIORITY_MINE_RESOURCES);
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
                state.repairSomething(builder, toRepair);
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
            final int searchDistToRepair = searchDistToRepair(entity.getEntityType());
            final List<Position> initialPositions = allPositionsOfEntity(state, entity);
            MapHelper.PathsFromBuilders pathsForBuilders = state.map.findPathsToBuilding(initialPositions,
                    searchDistToRepair, needMoreWorkers);
            for (Map.Entry<Entity, List<Position>> entry : pathsForBuilders.firstCellsInPath.entrySet()) {
                final Entity builder = entry.getKey();
                for (Position nextPos : entry.getValue()) {
                    state.move(builder, nextPos, MovesPicker.PRIORITY_GO_TO_REPAIR);
                }
                state.debugTargets.put(builder.getPosition(), entity.getPosition());
                // TODO: something else?
            }
        }
    }

    private static int searchDistToRepair(EntityType entityType) {
        if (entityType == RANGED_BASE) {
            return MAX_DIST_TO_SEARCH_BUILDERS_FOR_REPAIR * 3;
        } else {
            return MAX_DIST_TO_SEARCH_BUILDERS_FOR_REPAIR;
        }
    }

    private static List<Position> allPositionsOfEntityType(final State state, final Position pos, final EntityType entityType) {
        final int size = state.getEntityTypeProperties(entityType).getSize();
        List<Position> positions = new ArrayList<>();
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                positions.add(pos.shift(dx, dy));
            }
        }
        return positions;
    }

    private static List<Position> allPositionsOfEntity(final State state, final Entity entity) {
        return allPositionsOfEntityType(state, entity.getPosition(), entity.getEntityType());
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
            if (state.map.underAttack[pos.getX()][pos.getY()].isUnderAttack()) {
                underAttack.add(builder);
            } else {
                canBuildOrMineResources.add(builder);
            }
        }

        EntityType toBuild = state.globalStrategy.whatNextToBuild();
        boolean needBuildSmth = toBuild != null && toBuild.isBuilding() && state.isEnoughResourcesToBuild(toBuild);
        if (needBuildSmth && !canBuildOrMineResources.isEmpty()) {
            final Entity builder = tryToBuildSomething(state, canBuildOrMineResources, toBuild);
            if (builder != null) {
                canBuildOrMineResources.remove(builder);
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
        findPathsToResources(state, needPathToResources);
        state.decidedWhatToWithBuilders = true;
    }

    private static boolean isGoodPositionForHouse(Position pos) {
        return pos.getX() % 4 == 2 && pos.getY() % 4 == 2;
    }

    private static List<Position> filterHousePositions(List<Position> allPossiblePositions) {
        List<Position> filteredPositions = new ArrayList<>();
        for (Position pos : allPossiblePositions) {
            if (isGoodPositionForHouse(pos)) {
                filteredPositions.add(pos);
            }
        }
        return filteredPositions;
    }

    private static List<Position> filterRangedBasePosition(final State state, List<Position> allPossiblePositions) {
        int curMaxSumCoord = 0;
        for (Entity entity : state.myEntities) {
            if (entity.getEntityType().isBuilding()) {
                final Position curPos = entity.getPosition();
                curMaxSumCoord = Math.max(curMaxSumCoord, curPos.getX() + curPos.getY());
            }
        }
        List<Position> filteredPositions = new ArrayList<>();
        for (Position pos : allPossiblePositions) {
            if (pos.getX() + pos.getY() >= curMaxSumCoord) {
                filteredPositions.add(pos);
            }
        }
        return filteredPositions;
    }

    private static Entity closestBuilder(final State state, Position pos, EntityType what, List<Entity> builders) {
        int bDist = Integer.MAX_VALUE;
        Entity bBuilder = null;
        for (Entity builder : builders) {
            final Position builderPos = builder.getPosition();
            int dist = CellsUtils.distBetweenEntityTypeAndPos(state, pos, what, builderPos.getX(), builderPos.getY());
            if (dist < bDist) {
                bDist = dist;
                bBuilder = builder;
            }
        }
        return bBuilder;
    }

    private static Entity tryToBuildSomething(State state, List<Entity> builders, EntityType what) {
        List<BuildOption> buildOptions = new ArrayList<>();
        List<Position> allPossiblePositions = state.findAllPossiblePositionsToBuild(what);
        if (what == HOUSE) {
            allPossiblePositions = filterHousePositions(allPossiblePositions);
        } else if (what == RANGED_BASE) {
            allPossiblePositions = filterRangedBasePosition(state, allPossiblePositions);
        }
        for (Position pos : allPossiblePositions) {
            final Entity builder = closestBuilder(state, pos, what, builders);
            if (builder == null) {
                continue;
            }
            final Position builderPos = builder.getPosition();
            final int distToBuilder = CellsUtils.distBetweenEntityTypeAndPos(state, pos, what, builderPos.getX(), builderPos.getY());
            BuildOption buildOption = new BuildOption(state, builder, pos, what, distToBuilder);
            buildOptions.add(buildOption);
        }
        Collections.sort(buildOptions);
        for (BuildOption option : buildOptions) {
            if (option.distToBuilder == 1) {
                state.buildSomething(option.builder, what, option.where);
                markBuilderAsWorking(state, option.builder);
                return option.builder;
            } else {
                final List<Position> initialPositions = allPositionsOfEntityType(state, option.where, what);
                // TODO: send more than one worker?
                // TODO: optimize position by number of workers nearby
                MapHelper.PathsFromBuilders pathsForBuilders = state.map.findPathsToBuilding(initialPositions,
                        option.distToBuilder * 2, 1);
                if (pathsForBuilders.firstCellsInPath.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Entity, List<Position>> entry : pathsForBuilders.firstCellsInPath.entrySet()) {
                    final Entity builder = entry.getKey();
                    for (Position nextPos : entry.getValue()) {
                        state.move(builder, nextPos, MovesPicker.PRIORITY_GO_TO_BUILD);
                    }
                    state.debugTargets.put(builder.getPosition(), option.where);
                    return builder;
                }
            }
        }
        return null;
    }


    private static void findPathsToResources(final State state, final List<Entity> builders) {
        final int MAX_OPTIONS = 20;
        final Map<Position, Integer> compressedCoords = new HashMap<>();
        List<MapHelper.PathSuggestion>[] suggestionsForBuilders = new List[builders.size()];
        for (int i = 0; i < builders.size(); i++) {
            final Entity builder = builders.get(i);
            final List<MapHelper.PathSuggestion> suggestions = state.map.findPathsToResourcesFromBuilder(builder.getPosition(), MAX_OPTIONS);
            for (MapHelper.PathSuggestion suggestion : suggestions) {
                final Position targetCell = suggestion.targetCell;
                if (!compressedCoords.containsKey(targetCell)) {
                    compressedCoords.put(targetCell, compressedCoords.size());
                }
            }
            suggestionsForBuilders[i] = suggestions;
        }
        MinCostMaxFlow minCostMaxFlow = new MinCostMaxFlow(1 + builders.size() + compressedCoords.size() + 1);
        MinCostMaxFlow.Edge[][] edges = new MinCostMaxFlow.Edge[builders.size()][];
        for (int i = 0; i < builders.size(); i++) {
            minCostMaxFlow.addEdge(0, 1 + i, 1, 0);
            final List<MapHelper.PathSuggestion> suggestions = suggestionsForBuilders[i];
            edges[i] = new MinCostMaxFlow.Edge[suggestions.size()];
            for (int j = 0; j < suggestions.size(); j++) {
                final MapHelper.PathSuggestion suggestion = suggestions.get(j);
                long weight = MinCostMaxFlow.pathDistToWeight(suggestion.dist);
                int coordId = compressedCoords.get(suggestion.targetCell);
                edges[i][j] = minCostMaxFlow.addEdge(1 + i, 1 + builders.size() + coordId, 1, weight);
            }
        }
        for (int i = 0; i < compressedCoords.size(); i++) {
            minCostMaxFlow.addEdge(1 + builders.size() + i, minCostMaxFlow.n - 1, 1, 0);
        }
        minCostMaxFlow.getMinCostMaxFlow(0, minCostMaxFlow.n - 1);
        for (int i = 0; i < builders.size(); i++) {
            final Entity builder = builders.get(i);
            boolean found = false;
            for (int j = 0; j < edges[i].length; j++) {
                final MinCostMaxFlow.Edge edge = edges[i][j];
                if (edge.flow > 0) {
                    MapHelper.PathSuggestion suggestion = suggestionsForBuilders[i].get(j);
                    final Position targetCell = suggestion.targetCell;
                    state.move(builder, suggestion.firstCellOnPath, MovesPicker.PRIORITY_GO_TO_MINE);
                    if (state.debugInterface != null) {
                        state.debugTargets.put(builder.getPosition(), targetCell);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                final Position pos = builder.getPosition();
//                if (state.map.underAttack[pos.getX()][pos.getY()].isUnderAttack()) {
                if (!moveAwayFromAttack(state, builder)) {
                    // I will die, but at least will do something!
                    blocked(state, builder);
                    if (!mineRightNow(state, builder)) {
                        // TODO: do something?
                    }
                }
//                } else {
//                    final int mapSize = state.playerView.getMapSize();
//                    Position targetPos = new Position(mapSize - 1, mapSize - 1);
//                    if (!goToPosition(state, builder, targetPos, Integer.MAX_VALUE, false, false, MovesPicker.PRIORITY_GO_TO_UNBLOCK, false)) {
//                        blocked(state, builder);
//                    }
//                }
            }
        }
    }

    private static void blocked(final State state, final Entity builder) {
        state.addDebugUnitInBadPosition(builder.getPosition());
    }

//    private static boolean goToPosition(final State state, final Entity unit, final Position goToPos, int maxDist, boolean okGoToNotGoThere, boolean okGoThroughMyBuilders, int priority, boolean okEatFood) {
//        Position firstCellInPath = state.map.findBestPathToTargetDijkstra(unit.getPosition(), goToPos, 0, maxDist, okGoToNotGoThere, okGoThroughMyBuilders, okEatFood);
//        if (firstCellInPath != null) {
//            state.addDebugTarget(unit.getPosition(), goToPos);
//            state.move(unit, firstCellInPath, priority);
//            return true;
//        }
//        return false;
//    }


}


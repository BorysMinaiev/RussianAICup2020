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
        final int occupiedCellsNearby;
        final int distToZero;

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

        BuildOption(final State state, Entity builder, Position where, EntityType what) {
            this.builder = builder;
            this.where = where;
            final List<Boolean> occupiedCellsNearby = computeBuildingOrResourcesNearby(state, what);
            this.occupiedCellsNearby = occupiedCellsNearby(occupiedCellsNearby);
            final int distToZeroMultiplier = what == TURRET || what == RANGED_BASE ? (-1) : 1;
            this.distToZero = (where.getX() + where.getY()) * distToZeroMultiplier;
        }

        @Override
        public int compareTo(BuildOption o) {
            if (occupiedCellsNearby != o.occupiedCellsNearby) {
                return Integer.compare(occupiedCellsNearby, o.occupiedCellsNearby);
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
                final Position targetCell = state.map.findLastCellOnPath(pos, currentDist, bfsQueue, false);
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
                    BuildOption buildOption = new BuildOption(state, builder, where, toBuild);
                    buildOptions.add(buildOption);
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
        findPathsToResources(state, needPathToResources);
        state.decidedWhatToWithBuilders = true;
    }

    static long pathDistToWeight(int dist) {
        final int MAX_DIST = 10;
        final int BASE = 10;
        long res = 0;
        for (int i = 0; i < MAX_DIST; i++) {
            res = res * BASE;
            if (dist >= i + 1) {
                res++;
            }
        }
        res = res * 1000 + dist;
        return res;
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
                long weight = pathDistToWeight(suggestion.dist);
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
                    state.move(builder, suggestion.firstCellOnPath);
                    if (state.debugInterface != null) {
                        state.debugTargets.put(builder.getPosition(), targetCell);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (!moveAwayFromAttack(state, builder)) {
                    // I will die, but at least will do something!
                    state.addDebugUnitInBadPosition(builder.getPosition());
                    if (!mineRightNow(state, builder)) {
                        // TODO: do something?
                    }
                }
            }
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


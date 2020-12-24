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
        Collections.shuffle(allPossibleMoves, State.rnd);
        for (Position check : allPossibleMoves) {
            double scoreHere = state.attackedByPos.getOrDefault(check, 0.0);
            if (scoreHere <= currentAttackScore) {
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
        final Position where;
        final int occupiedCellsNearby;
        final int distToZero;
        final int ticksToBuild;
        final int score;
        int maxDistToBuilder;
        Entity builderReadyToBuild;
        final List<BuilderWithDist> builderWithDists;

        @Override
        public String toString() {
            return "BuildOption{" +
                    "where=" + where +
                    ", occupiedCellsNearby=" + occupiedCellsNearby +
                    ", distToZero=" + distToZero +
                    ", ticksToBuild=" + ticksToBuild +
                    ", score=" + score +
                    ", maxDistToBuilder=" + maxDistToBuilder +
                    ", builderReadyToBuild=" + builderReadyToBuild +
                    ", builderWithDists=" + builderWithDists +
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

        BuildOption(final State state, List<BuilderWithDist> builderWithDists, Position where, EntityType what) {
            this.where = where;
            this.builderWithDists = builderWithDists;
            final List<Boolean> occupiedCellsNearby = computeBuildingOrResourcesNearby(state, what);
            this.occupiedCellsNearby = occupiedCellsNearby(occupiedCellsNearby);
            final int distToZeroMultiplier = what == TURRET || what == RANGED_BASE ? (-1) : 1;
            this.distToZero = (where.getX() + where.getY()) * distToZeroMultiplier;
            this.ticksToBuild = computeTicksToBuild(state, builderWithDists, what);
            // TODO: change constant?
            this.score = (ticksToBuild * 4 + occupiedCellsNearby.size()) * 1000 + distToZero;
            builderReadyToBuild = null;
            for (BuilderWithDist builderWithDist : builderWithDists) {
                if (builderWithDist.dist == 1) {
                    builderReadyToBuild = builderWithDist.builder;
                }
                maxDistToBuilder = Math.max(maxDistToBuilder, builderWithDist.dist);
            }
        }

        @Override
        public int compareTo(BuildOption o) {
            return Integer.compare(score, o.score);
        }
    }

    static void markBuilderAsWorking(final State state, final Entity builder) {
        final Position pos = builder.getPosition();
        state.map.updateCellCanGoThrough(pos, MapHelper.CAN_GO_THROUGH.MY_WORKING_BUILDER);
    }

    public static boolean canMineRightNow(final State state, final Entity builder) {
        final Position pos = builder.getPosition();
        int[] dx = Directions.dx;
        int[] dy = Directions.dy;
        for (int it = 0; it < dx.length; it++) {
            int nx = pos.getX() + dx[it];
            int ny = pos.getY() + dy[it];
            if (state.map.canMineThisCell(nx, ny)) {
                return true;
            }
        }
        return false;
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
            return 10;
        }
        if (type == HOUSE) {
            return 3;
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

        WantToBuild wantToBuild = state.globalStrategy.whatNextToBuild();
        List<EntityType> buildingsToBuild = wantToBuild.whichBuildings();
        for (EntityType toBuild : buildingsToBuild) {
            if (!canBuildOrMineResources.isEmpty()) {
                final Entity builder = tryToBuildSomething(state, canBuildOrMineResources, toBuild);
                if (builder != null) {
                    canBuildOrMineResources.remove(builder);
                }
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

    static class BuilderWithDist implements Comparable<BuilderWithDist> {
        final Entity builder;
        final int dist;

        public BuilderWithDist(Entity builder, int dist) {
            this.builder = builder;
            this.dist = dist;
        }

        @Override
        public int compareTo(BuilderWithDist o) {
            return Integer.compare(dist, o.dist);
        }

        @Override
        public String toString() {
            return "BuilderWithDist{" +
                    "builder=" + builder +
                    ", dist=" + dist +
                    '}';
        }
    }

    private static List<BuilderWithDist> closestBuildersFast(final State state, Position pos, EntityType what, List<Entity> builders, int nBuilders) {
        List<BuilderWithDist> options = new ArrayList<>();
        for (Entity builder : builders) {
            final Position builderPos = builder.getPosition();
            int dist = CellsUtils.distBetweenEntityTypeAndPos(state, pos, what, builderPos.getX(), builderPos.getY());
            options.add(new BuilderWithDist(builder, dist));
        }
        Collections.sort(options);
        while (options.size() > nBuilders) {
            options.remove(options.size() - 1);
        }
        return options;
    }

    private static List<BuilderWithDist> closestBuildersSlow(final State state, Position where, EntityType what, List<Entity> builders, int nBuilders, int maxDistToBuilder) {
        final List<Position> initialPositions = allPositionsOfEntityType(state, where, what);
        List<BuilderWithDist> options = new ArrayList<>();
        MapHelper.PathsFromBuilders pathsForBuilders = state.map.findPathsToBuilding(initialPositions,
                maxDistToBuilder * 2, nBuilders);
        for (Map.Entry<Entity, Integer> entry : pathsForBuilders.dists.entrySet()) {
            options.add(new BuilderWithDist(entry.getKey(), entry.getValue()));
        }
        return options;
    }

    private static int computeTicksToBuild(final State state, List<BuilderWithDist> buildersWithDist, EntityType entityType) {
        final int INITIAL_HEALTH = 5;
        int health = state.getEntityTypeProperties(entityType).getMaxHealth() - INITIAL_HEALTH;
        int left = 0, right = 100;
        while (right - left > 1) {
            int mid = (left + right) >> 1;
            int canProduceHealth = 0;
            for (BuilderWithDist builderWithDist : buildersWithDist) {
                canProduceHealth += Math.max(0, mid - builderWithDist.dist);
            }
            if (canProduceHealth >= health) {
                right = mid;
            } else {
                left = mid;
            }
        }
        return right;
    }

    private static Entity tryToBuildSomething(State state, List<Entity> builders, EntityType what) {
        List<BuildOption> buildOptions = new ArrayList<>();
        List<Position> allPossiblePositions = state.findAllPossiblePositionsToBuild(what);
        if (what == HOUSE) {
            allPossiblePositions = filterHousePositions(allPossiblePositions);
        } else if (what == RANGED_BASE) {
            allPossiblePositions = filterRangedBasePosition(state, allPossiblePositions);
        }
        int nBuilders = getNumWorkersToHelpRepair(what);
        for (Position pos : allPossiblePositions) {
            final List<BuilderWithDist> buildersWithDist = closestBuildersFast(state, pos, what, builders, nBuilders);
            if (buildersWithDist.isEmpty()) {
                continue;
            }
            BuildOption buildOption = new BuildOption(state, buildersWithDist, pos, what);
            buildOptions.add(buildOption);
        }
        Collections.sort(buildOptions);
        final int CHECK_BUILD_OPTIONS = 10;
        List<BuildOption> optionsSmarter = new ArrayList<>();
        for (int i = 0; i < CHECK_BUILD_OPTIONS && i < buildOptions.size(); i++) {
            final Position pos = buildOptions.get(i).where;
            final int maxDistToBuilder = buildOptions.get(i).maxDistToBuilder;
            final List<BuilderWithDist> builderWithDist = closestBuildersSlow(state, pos, what, builders, nBuilders, maxDistToBuilder);
            optionsSmarter.add(new BuildOption(state, builderWithDist, pos, what));
        }
        buildOptions = optionsSmarter;
        Collections.sort(buildOptions);
        for (BuildOption option : buildOptions) {
            if (option.builderReadyToBuild != null) {
                state.buildSomething(option.builderReadyToBuild, what, option.where, MovesPicker.PRIORITY_BUILD);
                markBuilderAsWorking(state, option.builderReadyToBuild);
                return option.builderReadyToBuild;
            } else {
                final List<Position> initialPositions = allPositionsOfEntityType(state, option.where, what);
                // TODO: send more than one worker?
                // TODO: optimize position by number of workers nearby
                MapHelper.PathsFromBuilders pathsForBuilders = state.map.findPathsToBuilding(initialPositions,
                        option.maxDistToBuilder * 2, 1);
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
        final int MAX_OPTIONS = 10;
        final int MAX_DIST = 40;
        final Map<Position, Integer> compressedCoords = new HashMap<>();
        List<MapHelper.PathSuggestion>[] suggestionsForBuilders = new List[builders.size()];
        for (int i = 0; i < builders.size(); i++) {
            final Entity builder = builders.get(i);
            final List<MapHelper.PathSuggestion> suggestions = state.map.findPathsToResourcesFromBuilder(builder.getPosition(), MAX_OPTIONS, MAX_DIST);
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


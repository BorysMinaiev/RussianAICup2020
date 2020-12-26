import model.Entity;
import model.EntityType;
import model.Position;

import java.util.ArrayList;
import java.util.List;

public class FreedomPath {
    final State state;
    final MapHelper mapHelper;

    class Handler implements Dijkstra.DijkstraHandler {
        final MapHelper mapHelper;

        Handler(final MapHelper mapHelper) {
            this.mapHelper = mapHelper;
        }

        @Override
        public boolean canGoThrough(MapHelper.CAN_GO_THROUGH type, MapHelper.UNDER_ATTACK underAttack, int x, int y, int dist) {
            return switch (type) {
                case EMPTY_CELL,
                        UNKNOWN,
                        MY_BUILDER,
                        MY_WORKING_BUILDER,
                        MY_ATTACKING_UNIT,
                        MY_EATING_FOOD_RANGED_UNIT -> true;

                case MY_BUILDING,
                        FOOD,
                        ENEMY_BUILDING -> false;
            };
        }

        private int getNumResourcesNearby(int x, int y) {
            int[] dx = Directions.dx;
            int[] dy = Directions.dy;
            int resources = 0;
            for (int it = 0; it < dx.length; it++) {
                int nx = x + dx[it];
                int ny = y + dy[it];
                if (!mapHelper.insideMap(nx, ny)) {
                    continue;
                }
                Entity there = mapHelper.entitiesByPos[nx][ny];
                if (there != null && there.getPlayerId() == null) {
                    resources++;
                }
            }
            return resources;
        }

        @Override
        public int getEdgeCost(MapHelper.CAN_GO_THROUGH can_go_through, int straightDistToTarget, int x, int y) {
            return switch (can_go_through) {
                case EMPTY_CELL, MY_BUILDER, MY_WORKING_BUILDER, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, ENEMY_BUILDING -> 1 + getNumResourcesNearby(x, y);
                case UNKNOWN -> 20;
                case MY_BUILDING, FOOD -> 787788;
            };
        }

        @Override
        public boolean isOkGoNotGoThere() {
            return true;
        }

        @Override
        public boolean isOkGoThroughMyBuilders() {
            return true;
        }

        @Override
        public boolean isOkGoUnderAttack() {
            return false;
        }

        @Override
        public boolean isOkEatFood() {
            return false;
        }

        @Override
        public int getSkipLastNCells() {
            return 0;
        }
    }

    Entity getBuilderBase(State state) {
        List<Entity> allBuildings = state.myEntitiesByType.get(EntityType.BUILDER_BASE);
        if (allBuildings.isEmpty()) {
            return null;
        }
        return allBuildings.get(0);
    }

    List<Position> constructPath(Position startPos, Dijkstra.State handler) {
        List<Position> path = new ArrayList<>();
        path.add(startPos);
        MapHelper.Dir[] dirs = MapHelper.getDirs(0, 0);
        Position curPos = startPos;
        int curDist = handler.getDist(startPos.getX(), startPos.getY());
        while (true) {
            boolean foundNextMove = false;
            for (MapHelper.Dir dir : dirs) {
                final int nx = curPos.getX() + dir.dx;
                final int ny = curPos.getY() + dir.dy;
                if (!mapHelper.insideMap(nx, ny)) {
                    continue;
                }
                final int distFromNext = handler.getDist(nx, ny);
                if (distFromNext >= Integer.MAX_VALUE / 2) {
                    continue;
                }
                if (distFromNext + handler.getEdgeCost(nx, ny) <= curDist) {
                    foundNextMove = true;
                    Position nextPos = new Position(nx, ny);
                    path.add(nextPos);
                    curDist = distFromNext;
                    curPos = nextPos;
                    break;
                }
            }
            if (!foundNextMove) {
                break;
            }
        }
        return path;
    }

    List<Position> path;
    CachedArrays.IntArray onPath;

    public int getNumCellsOnPathFromRect(Position bottomLeft, Position topRight) {
        int cntCellsOnPath = 0;
        for (int x = bottomLeft.getX(); x <= topRight.getX(); x++) {
            for (int y = bottomLeft.getY(); y <= topRight.getY(); y++) {
                if (isOnPath(x, y)) {
                    cntCellsOnPath++;
                }
            }
        }
        return cntCellsOnPath;
    }

    public boolean isOnPath(int x, int y) {
        return onPath.contains(CompressedCoords.compress(x, y));
    }

    public boolean isOnPath(Position pos) {
        return onPath.contains(CompressedCoords.compress(pos));
    }

    FreedomPath(State state, MapHelper mapHelper) {
        this.state = state;
        this.mapHelper = mapHelper;

        final int mapSize = state.playerView.getMapSize();

        this.onPath = CachedArrays.getNewIntArray(mapSize * mapSize);
        final Position targetPos = new Position(mapSize - 1, mapSize - 1);
        Handler handler = new Handler(mapHelper);
        Dijkstra.State dijkstra = new Dijkstra.State(targetPos, handler, mapSize, mapHelper);

        final Entity builderBase = getBuilderBase(state);
        path = new ArrayList<>();
        if (builderBase != null) {
            Position bottomLeft = builderBase.getPosition().shift(-1, -1);
            int size = state.getEntityProperties(builderBase).getSize();
            Position topRight = builderBase.getPosition().shift(size, size);
            List<Position> onBorder = CellsUtils.getPositionsOnRectBorderCCW(bottomLeft, topRight);
            Position bestPos = null;
            int bestDist = Integer.MAX_VALUE;
            for (Position pos : onBorder) {
                if (state.insideMap(pos)) {
                    dijkstra.getFirstPathOnPath(pos, Integer.MAX_VALUE);
                    int dist = dijkstra.getDist(pos.getX(), pos.getY());
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPos = pos;
                    }
                }
            }
            if (bestPos != null) {
                path = constructPath(bestPos, dijkstra);
                for (Position pos : path) {
                    onPath.put(CompressedCoords.compress(pos), 1);
                }
            }
        }
    }
}

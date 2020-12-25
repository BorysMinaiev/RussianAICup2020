import model.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Dijkstra {
    final MapHelper mapHelper;

    interface DijkstraHandler {
        boolean canGoThrough(MapHelper.CAN_GO_THROUGH type, MapHelper.UNDER_ATTACK underAttack, int x, int y, int dist);

        int getEdgeCost(MapHelper.CAN_GO_THROUGH can_go_through, int straightDistToTarget);

        boolean isOkGoNotGoThere();

        boolean isOkGoThroughMyBuilders();

        boolean isOkGoUnderAttack();

        boolean isOkEatFood();

        int getSkipLastNCells();
    }

    class State implements QueueDist {
        final CachedArrays.IntArray seen;
        final VertexPriorityQueue pq;
        final Position targetPos;
        final DijkstraHandler handler;

        State(Position targetPos, DijkstraHandler handler, final int mapSize) {
            final int arraySize = mapSize * mapSize;
            seen = CachedArrays.getNewIntArray(arraySize);
            pq = new VertexPriorityQueue(arraySize);
            this.targetPos = targetPos;
            this.handler = handler;
            addVertexToQueue(targetPos.getX(), targetPos.getY(), 0);
        }

        void addVertexToQueue(int x, int y, int distToPos) {
            int compressedCoord = CompressedCoords.compress(x, y);
            if (pq.dist.contains(compressedCoord) && pq.dist.get(compressedCoord) <= distToPos) {
                return;
            }
            pq.add(compressedCoord, distToPos);
        }

        private void visitNeighbours(int compressedCoord) {
            MapHelper.Dir[] dirs = MapHelper.dirsUp;

            int curPosX = CompressedCoords.extractX(compressedCoord);
            int curPosY = CompressedCoords.extractY(compressedCoord);
            final int nextDist = pq.dist.get(compressedCoord) + getEdgeCost(curPosX, curPosY);
            for (int it = 0; it < dirs.length; it++) {
                int nx = curPosX + dirs[it].dx;
                int ny = curPosY + dirs[it].dy;
                if (!mapHelper.insideMap(nx, ny)) {
                    continue;
                }
                if (!handler.canGoThrough(mapHelper.canGoThrough[nx][ny], mapHelper.underAttack[nx][ny], nx, ny, nextDist)) {
                    continue;
                }
                addVertexToQueue(nx, ny, nextDist);
            }
        }

        void getFirstPathOnPath(final Position startPos, final int maxDist) {
            if (seen.contains(CompressedCoords.compress(startPos))) {
                return;
            }
            while (!pq.isEmpty()) {
                int compressedCoord = pq.poll();
                if (seen.contains(compressedCoord)) {
                    throw new AssertionError("Shouldn't work like this");
                }
                seen.put(compressedCoord, 1);
                visitNeighbours(compressedCoord);
                if (pq.dist.contains(compressedCoord) && pq.dist.get(compressedCoord) > maxDist) {
                    return;
                }
                int posX = CompressedCoords.extractX(compressedCoord);
                int posY = CompressedCoords.extractY(compressedCoord);
                if (startPos.getX() == posX && startPos.getY() == posY) {
                    return;
                }
            }
        }

        @Override
        public int getDist(int x, int y) {
            return pq.dist.getOrDefault(CompressedCoords.compress(x, y), Integer.MAX_VALUE);
        }

        @Override
        public int getEdgeCost(int x, int y) {
            int straightDistToTarget = targetPos.distTo(x, y);
            return handler.getEdgeCost(mapHelper.canGoThrough[x][y], straightDistToTarget);
        }
    }

    static class DijkstraProperties {
        final Position targetPos;
        final boolean okGoNotGoThere;
        final boolean okGoThroughBuilders;
        final boolean okGoUnderAttack;
        final boolean okEatFood;
        final int skipLastNCells;

        public DijkstraProperties(Position targetPos, boolean okGoNotGoThere, boolean okGoThroughBuilders, boolean okGoUnderAttack, final int skipLastNCells, final boolean okEatFood) {
            this.targetPos = targetPos;
            this.okGoNotGoThere = okGoNotGoThere;
            this.okGoThroughBuilders = okGoThroughBuilders;
            this.okGoUnderAttack = okGoUnderAttack;
            this.skipLastNCells = skipLastNCells;
            this.okEatFood = okEatFood;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DijkstraProperties that = (DijkstraProperties) o;
            return okGoNotGoThere == that.okGoNotGoThere &&
                    okGoThroughBuilders == that.okGoThroughBuilders &&
                    okGoUnderAttack == that.okGoUnderAttack &&
                    okEatFood == that.okEatFood &&
                    skipLastNCells == that.skipLastNCells &&
                    Objects.equals(targetPos, that.targetPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetPos, okGoNotGoThere, okGoThroughBuilders, okGoUnderAttack, okEatFood, skipLastNCells);
        }
    }

    final Map<DijkstraProperties, State> statesByProperties;

    public Dijkstra(MapHelper mapHelper) {
        this.mapHelper = mapHelper;
        this.statesByProperties = new HashMap<>();
    }


    QueueDist findFirstCellOnPath(final Position startPos,
                                  final Position targetPos,
                                  final DijkstraHandler handler,
                                  int maxDist,
                                  int mapSize) {
        if (startPos.distTo(targetPos) == 0) {
            return null;
        }
        DijkstraProperties properties = new DijkstraProperties(targetPos,
                handler.isOkGoNotGoThere(),
                handler.isOkGoThroughMyBuilders(),
                handler.isOkGoUnderAttack(),
                handler.getSkipLastNCells(),
                handler.isOkEatFood());
        State state = statesByProperties.get(properties);
        if (state == null) {
            state = new State(targetPos, handler, mapSize);
            statesByProperties.put(properties, state);
        }
        state.getFirstPathOnPath(startPos, maxDist);
        return state;
    }
}

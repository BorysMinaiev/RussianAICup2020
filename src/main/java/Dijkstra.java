import model.Position;

import java.util.*;

public class Dijkstra {
    final MapHelper mapHelper;

    interface DijkstraHandler {
        boolean canGoThrough(MapHelper.CAN_GO_THROUGH type, MapHelper.UNDER_ATTACK underAttack, int x, int y, int dist);

        int getEdgeCost(MapHelper.CAN_GO_THROUGH can_go_through);

        boolean isOkGoNotGoThere();

        boolean isOkGoThroughMyBuilders();

        boolean isOkEatFood();

        int getSkipLastNCells();
    }

    class Vertex implements Comparable<Vertex> {
        final Position pos;
        final int dist;

        public Vertex(Position pos, int dist) {
            this.pos = pos;
            this.dist = dist;
        }

        @Override
        public int compareTo(Vertex o) {
            return Integer.compare(dist, o.dist);
        }
    }

    class State implements QueueDist {
        // TODO: replace map with array?
        final Map<Position, Integer> dist;
        final Set<Position> seen;
        final Map<Position, Position> firstCellOnPath;
        final PriorityQueue<Vertex> pq;
        final Position targetPos;
        final DijkstraHandler handler;

        State(Position targetPos, DijkstraHandler handler) {
            dist = new HashMap<>();
            seen = new HashSet<>();
            firstCellOnPath = new HashMap<>();
            pq = new PriorityQueue<>();
            this.targetPos = targetPos;
            this.handler = handler;
            addVertexToQueue(targetPos, 0, null);
        }

        void addVertexToQueue(Position pos, int distToPos, Position prev) {
            Integer curBestDist = dist.get(pos);
            if (curBestDist != null && curBestDist <= distToPos) {
                return;
            }
            firstCellOnPath.put(pos, prev);
            pq.add(new Vertex(pos, distToPos));
            dist.put(pos, distToPos);
        }

        private void visitNeighbours(Vertex vertex) {
            MapHelper.Dir[] dirs = MapHelper.dirsUp;
            final Position curPos = vertex.pos;

            final int nextDist = vertex.dist + getEdgeCost(curPos.getX(), curPos.getY());
            for (int it = 0; it < dirs.length; it++) {
                int nx = curPos.getX() + dirs[it].dx;
                int ny = curPos.getY() + dirs[it].dy;
                if (!mapHelper.insideMap(nx, ny)) {
                    continue;
                }
                if (!handler.canGoThrough(mapHelper.canGoThrough[nx][ny], mapHelper.underAttack[nx][ny], nx, ny, nextDist)) {
                    continue;
                }
                addVertexToQueue(new Position(nx, ny), nextDist, curPos);
            }
        }

        Position getFirstPathOnPath(final Position startPos, final int maxDist) {
            if (firstCellOnPath.containsKey(startPos)) {
                return firstCellOnPath.get(startPos);
            }
            while (!pq.isEmpty()) {
                Vertex vertex = pq.poll();
                if (seen.contains(vertex.pos)) {
                    continue;
                }
                seen.add(vertex.pos);
                visitNeighbours(vertex);
                if (vertex.dist > maxDist) {
                    return null;
                }
                if (vertex.pos.distTo(startPos) == 0) {
                    return firstCellOnPath.get(startPos);
                }
            }
            return null;
        }

        @Override
        public int getDist(int x, int y) {
            Position pos = new Position(x, y);
            return dist.getOrDefault(pos, Integer.MAX_VALUE);
        }

        @Override
        public int getEdgeCost(int x, int y) {
            return handler.getEdgeCost(mapHelper.canGoThrough[x][y]);
        }
    }

    static class DijkstraProperties {
        final Position targetPos;
        final boolean okGoNotGoThere;
        final boolean okGoThroughBuilders;
        final boolean okEatFood;
        final int skipLastNCells;

        public DijkstraProperties(Position targetPos, boolean okGoNotGoThere, boolean okGoThroughBuilders, final int skipLastNCells, final boolean okEatFood) {
            this.targetPos = targetPos;
            this.okGoNotGoThere = okGoNotGoThere;
            this.okGoThroughBuilders = okGoThroughBuilders;
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
                    okEatFood == that.okEatFood &&
                    skipLastNCells == that.skipLastNCells &&
                    Objects.equals(targetPos, that.targetPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetPos, okGoNotGoThere, okGoThroughBuilders, okEatFood, skipLastNCells);
        }
    }

    final Map<DijkstraProperties, State> statesByProperties;

    public Dijkstra(MapHelper mapHelper) {
        this.mapHelper = mapHelper;
        this.statesByProperties = new HashMap<>();
    }


    QueueDist findFirstCellOnPath(final Position startPos, final Position targetPos, final DijkstraHandler handler, int maxDist) {
        if (startPos.distTo(targetPos) == 0) {
            return null;
        }
        DijkstraProperties properties = new DijkstraProperties(targetPos,
                handler.isOkGoNotGoThere(),
                handler.isOkGoThroughMyBuilders(),
                handler.getSkipLastNCells(),
                handler.isOkEatFood());
        State state = statesByProperties.get(properties);
        if (state == null) {
            state = new State(targetPos, handler);
            statesByProperties.put(properties, state);
        }
        state.getFirstPathOnPath(startPos, maxDist);
        return state;
    }
}

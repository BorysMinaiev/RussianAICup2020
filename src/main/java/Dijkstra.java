import model.Position;

import java.util.*;

public class Dijkstra {
    final MapHelper mapHelper;

    interface DijkstraHandler {
        boolean canGoThrough(MapHelper.CAN_GO_THROUGH type, MapHelper.UNDER_ATTACK underAttack, int dist);

        int getEdgeCost(MapHelper.CAN_GO_THROUGH can_go_through);
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
                if (!handler.canGoThrough(mapHelper.canGoThrough[nx][ny], mapHelper.underAttack[nx][ny], nextDist)) {
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

    final Map<Position, State> statesByTargetPos;

    public Dijkstra(MapHelper mapHelper) {
        this.mapHelper = mapHelper;
        this.statesByTargetPos = new HashMap<>();
    }


    QueueDist findFirstCellOnPath(final Position startPos, final Position targetPos, final DijkstraHandler handler, int maxDist) {
        // TODO: we need to check that handlers are the same to use cache?!
        if (startPos.distTo(targetPos) == 0) {
            return null;
        }
        State state = statesByTargetPos.get(targetPos);
        if (state == null) {
            state = new State(targetPos, handler);
            statesByTargetPos.put(targetPos, state);
        }
        state.getFirstPathOnPath(startPos, maxDist);
        return state;
    }
}

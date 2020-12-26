import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TargetsMatcher {
    public void markImportantEnemy(int enemyId) {
        importantEnemies[enemyId] = true;
    }

    static class Edge implements Comparable<Edge> {
        final int myUnitId;
        final int targetId;
        final int dist;
        boolean used;

        public Edge(int myUnitId, int targetId, int dist) {
            this.myUnitId = myUnitId;
            this.targetId = targetId;
            this.dist = dist;
        }

        @Override
        public int compareTo(Edge o) {
            return Integer.compare(dist, o.dist);
        }
    }

    final List<Edge> edges;
    final int myUnitsNum;
    final int targetsNum;
    final boolean[] importantEnemies;

    TargetsMatcher(int myUnitsNum, int targetsNum) {
        edges = new ArrayList<>();
        this.myUnitsNum = myUnitsNum;
        this.targetsNum = targetsNum;
        importantEnemies = new boolean[targetsNum];
    }

    public Edge addEdge(int myUnitId, int targetId, int dist) {
        Edge edge = new Edge(myUnitId, targetId, dist);
        edges.add(edge);
        return edge;
    }

    private final static int UNITS_PER_TARGET = 2;

    boolean requiresMoreUnits() {
        Collections.sort(edges);
        boolean[] hasTarget = new boolean[myUnitsNum];
        int[] targetsCapacity = new int[targetsNum];
        Arrays.fill(targetsCapacity, UNITS_PER_TARGET);
        for (boolean onlyImportant : new boolean[]{true, false}) {
            for (Edge edge : edges) {
                if (onlyImportant && !importantEnemies[edge.targetId]) {
                    continue;
                }
                if (targetsCapacity[edge.targetId] == 0) {
                    continue;
                }
                if (hasTarget[edge.myUnitId]) {
                    continue;
                }
                edge.used = true;
                targetsCapacity[edge.targetId]--;
                hasTarget[edge.myUnitId] = true;
            }
        }
        for (int moreCapacity : targetsCapacity) {
            if (moreCapacity > 0) {
                return true;
            }
        }
        return false;
    }
}

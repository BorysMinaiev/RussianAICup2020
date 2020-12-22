import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;

class MinCostMaxFlow {
    int n;
    ArrayList<Edge>[] g;

    class Edge {
        int from, to;
        long cap, flow, cost;
        Edge rev;

        public Edge(int from, int to, long cap, long flow, long cost) {
            super();
            this.from = from;
            this.to = to;
            this.cap = cap;
            this.flow = flow;
            this.cost = cost;
        }
    }

    class Vertex implements Comparable<Vertex> {
        final int v;
        final Edge e;
        final long d;

        public Vertex(int v, Edge e, long d) {
            super();
            this.v = v;
            this.e = e;
            this.d = d;
        }

        @Override
        public int compareTo(Vertex o) {
            return d < o.d ? -1 : d > o.d ? 1 : v - o.v;
        }

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

    public MinCostMaxFlow(int n) {
        this.n = n;
        g = new ArrayList[n];
        for (int i = 0; i < n; i++) {
            g[i] = new ArrayList<Edge>();
        }
    }

    public Edge addEdge(int fr, int to, int cap, long cost) {
        Edge e1 = new Edge(fr, to, cap, 0, cost);
        Edge e2 = new Edge(to, fr, 0, 0, -cost);
        e1.rev = e2;
        e2.rev = e1;
        g[fr].add(e1);
        g[to].add(e2);
        return e1;
    }

    public long[] getMinCostMaxFlow(int source, int target) {
        long[] h = new long[n];
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int i = 0; i < n; i++) {
                for (Edge e : g[i]) {
                    if (e.cap > 0 && h[e.to] > h[e.from] + e.cost) {
                        h[e.to] = h[e.from] + e.cost;
                        changed = true;
                    }
                }
            }
        }
        Vertex[] vertices = new Vertex[n];
        long[] d = new long[n];
        boolean[] was = new boolean[n];
        int flow = 0;
        long cost = 0;
        while (true) {
            Arrays.fill(was, false);
            dijkstra(source, vertices, d, h, was);
            if (d[target] == Long.MAX_VALUE) {
                break;
            }
            long addFlow = Long.MAX_VALUE;
            Vertex v = vertices[target];
            while (v != vertices[source]) {
                addFlow = Math.min(addFlow, v.e.cap - v.e.flow);
                v = vertices[v.e.from];
            }
            cost += (d[target] + h[target] - h[source]) * addFlow;
            flow += addFlow;
            v = vertices[target];
            while (v != vertices[source]) {
                v.e.flow += addFlow;
                v.e.rev.flow -= addFlow;
                v = vertices[v.e.from];
            }
            for (int i = 0; i < n; i++) {
                h[i] += d[i] == Long.MAX_VALUE ? 0 : d[i];
            }
        }
        return new long[]{flow, cost};
    }

    void dijkstra(int source, Vertex[] vertices, long[] d, long[] h, boolean[] was) {
        PriorityQueue<Vertex> ts = new PriorityQueue<Vertex>(vertices.length);
        Arrays.fill(d, Long.MAX_VALUE);
        d[source] = 0;
        vertices[source] = new Vertex(source, null, 0);
        ts.add(vertices[source]);
        while (!ts.isEmpty()) {
            Vertex v = ts.poll();
            if (was[v.v]) {
                continue;
            }
            was[v.v] = true;
            for (Edge e : g[v.v]) {
                if (e.flow >= e.cap) {
                    continue;
                }
                if (d[e.to] == Long.MAX_VALUE
                        || d[e.to] > d[e.from] + e.cost + h[e.from]
                        - h[e.to]) {
                    if (e.cost + h[e.from] - h[e.to] < 0) {
                        throw new AssertionError();
                    }
                    d[e.to] = d[e.from] + e.cost + h[e.from] - h[e.to];
                    vertices[e.to] = new Vertex(e.to, e, d[e.to]);
                    ts.add(vertices[e.to]);
                }
            }
        }
    }
}
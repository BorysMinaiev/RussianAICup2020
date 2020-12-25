
public class VertexPriorityQueue {
    CachedArrays.IntArray dist;
    CachedArrays.IntArray posInHeap;
    CachedArrays.IntArray heap;
    int heapSize;

    final int id;

    VertexPriorityQueue(int n) {
        dist = CachedArrays.getNewIntArray(n);
        posInHeap = CachedArrays.getNewIntArray(n);
        heap = CachedArrays.getNewIntArray(n);
        heapSize = 0;

        id = State.rnd.nextInt();
    }

    void clear() {
        while (heapSize > 0) {
            removeFromHeap(heap.get(heapSize - 1));
            heapSize--;
        }
    }

    void removeFromHeap(int vertex) {
        if (!posInHeap.contains(vertex)) {
            return;
        }
        int pos = posInHeap.get(vertex);
        heapSize--;
        if (pos < heapSize) {
            swap(pos, heapSize);
            int lastPos = siftUp(pos);
            siftDown(lastPos);
        }
//        dist.put(vertex, Integer.MAX_VALUE);
        posInHeap.put(vertex, -1);
        if (heapSize < 0) {
            throw new AssertionError();
        }
    }

    void set(int vertex, int pos) {
        posInHeap.put(vertex, pos);
        heap.put(pos, vertex);
    }

    void swap(int p1, int p2) {
        int v1 = heap.get(p1);
        int v2 = heap.get(p2);
        set(v1, p2);
        set(v2, p1);
    }

    void siftDown(int pos) {
        while (pos > 0) {
            int parentPos = (pos - 1) >>> 1;
            if (dist.get(heap.get(parentPos)) <= dist.get(heap.get(pos))) {
                break;
            }
            swap(pos, parentPos);
            pos = parentPos;
        }
    }

    int siftUp(int pos) {
        while (true) {
            int ch1 = pos * 2 + 1, ch2 = pos * 2 + 2;
            if (ch1 >= heapSize) {
                break;
            }
            int minChildPos = ch2 >= heapSize ? ch1 : (dist.get(heap.get(ch1)) < dist.get(heap.get(ch2)) ? ch1 : ch2);
            if (dist.get(heap.get(pos)) < dist.get(heap.get(minChildPos))) {
                break;
            }
            swap(pos, minChildPos);
            pos = minChildPos;
        }
        return pos;
    }

    void add(int vertex, int d) {
        if (d >= dist.getOrDefault(vertex, Integer.MAX_VALUE)) {
            throw new AssertionError();
        }
        removeFromHeap(vertex);
        dist.put(vertex, d);
        set(vertex, heapSize++);
        siftDown(heapSize - 1);
    }

    int poll() {
        if (heapSize == 0) {
            throw new AssertionError();
        }
        int resVertex = heap.get(0);
        removeFromHeap(resVertex);
        return resVertex;
    }

    boolean isEmpty() {
        return heapSize == 0;
    }
}

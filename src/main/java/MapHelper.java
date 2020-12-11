import model.Entity;
import model.EntityType;
import model.PlayerView;
import model.Position;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MapHelper {
    final Entity[][] entitiesByPos;
    final int[][] enemiesPrefSum;

    enum CAN_GO_THROUGH {
        CAN_GO,
        CANT_GO,
        MAYBE_LATER
    }

    final CAN_GO_THROUGH[][] canGoThrough;
    final int myPlayerId;
    final int[] queue;
    final int[] visited;
    final int[] dist;
    int currentVisitedIter;

    private boolean isEntityCouldBeAttacked(final Entity entity) {
        return entity != null && entity.getPlayerId() != null && entity.getPlayerId() != myPlayerId;
    }

    private boolean canGoThrough(final Entity entity) {
        if (entity.getPlayerId() == null) {
            // TODO: potentially I can...
            return false;
        }
        if (entity.getPlayerId() == myPlayerId) {
            return !entity.getEntityType().isBuilding() && entity.getEntityType() != EntityType.BUILDER_UNIT;
        } else {
            return true;
        }
    }

    MapHelper(final PlayerView playerView) {
        this.myPlayerId = playerView.getMyId();
        final int size = playerView.getMapSize();
        entitiesByPos = new Entity[size][size];
        canGoThrough = new CAN_GO_THROUGH[size][size];
        for (CAN_GO_THROUGH[] pass : canGoThrough) {
            Arrays.fill(pass, CAN_GO_THROUGH.CAN_GO);
        }
        enemiesPrefSum = new int[size + 1][size + 1];
        for (Entity entity : playerView.getEntities()) {
            final Position pos = entity.getPosition();
            final int entitySize = playerView.getEntityProperties().get(entity.getEntityType()).getSize();
            for (int dx = 0; dx < entitySize; dx++) {
                for (int dy = 0; dy < entitySize; dy++) {
                    final int x = pos.getX() + dx;
                    final int y = pos.getY() + dy;
                    entitiesByPos[x][y] = entity;
                    if (!canGoThrough(entity)) {
                        canGoThrough[x][y] = CAN_GO_THROUGH.CANT_GO;
                    }
                }
            }
            if (isEntityCouldBeAttacked(entity)) {
                enemiesPrefSum[pos.getX() + 1][pos.getY() + 1]++;
            }
        }
        for (int x = 1; x <= size; x++) {
            for (int y = 1; y <= size; y++) {
                enemiesPrefSum[x][y] += enemiesPrefSum[x - 1][y];
                enemiesPrefSum[x][y] += enemiesPrefSum[x][y - 1];
                enemiesPrefSum[x][y] -= enemiesPrefSum[x - 1][y - 1];
            }
        }
        final int totalCells = size * size;
        this.queue = new int[totalCells];
        this.visited = new int[totalCells];
        this.dist = new int[totalCells];
        this.currentVisitedIter = 0;
    }

    private boolean insideMap(final int x, final int y) {
        return x >= 0 && x < entitiesByPos.length && y >= 0 && y < entitiesByPos[x].length;
    }

    public List<Entity> getEntitiesToAttack(final Position pos, final int maxDist) {
        final int curX = pos.getX();
        final int curY = pos.getY();
        List<Entity> toAttack = new ArrayList<>();
        for (int x = curX - maxDist; x <= curX + maxDist; x++) {
            int dx = Math.abs(x - curX);
            int maxDY = maxDist - dx;
            for (int y = curY - maxDY; y <= curY + maxDY; y++) {
                if (!insideMap(x, y)) {
                    continue;
                }
                Entity entity = entitiesByPos[x][y];
                if (!isEntityCouldBeAttacked(entity)) {
                    continue;
                }
                toAttack.add(entity);
            }
        }
        // TODO: shuffle?
        return toAttack;
    }

    private int fitCoordToMap(int coord) {
        if (coord < 0) {
            return 0;
        }
        if (coord >= entitiesByPos.length) {
            return entitiesByPos.length - 1;
        }
        return coord;
    }

    private int getNumEnemiesInRect(int maxX, int maxY) {
        return enemiesPrefSum[maxX + 1][maxY + 1];
    }

    private int getNumEnemiesInRect(int frX, int toX, int frY, int toY) {
        int res = getNumEnemiesInRect(toX, toY);
        res -= getNumEnemiesInRect(frX - 1, toY);
        res -= getNumEnemiesInRect(toX, frY - 1);
        res += getNumEnemiesInRect(frX - 1, frY - 1);
        return res;
    }

    private boolean anyEnemyInRect(int frX, int toX, int frY, int toY) {
        frX = fitCoordToMap(frX);
        toX = fitCoordToMap(toX);
        frY = fitCoordToMap(frY);
        toY = fitCoordToMap(toY);
        return getNumEnemiesInRect(frX, toX, frY, toY) > 0;
    }

    private List<Entity> enemiesOnRectBorders(final int frX, final int toX, final int frY, final int toY) {
        List<Entity> enemies = new ArrayList<>();
        for (int y = frY; y <= toY; y++) {
            for (int x : new int[]{frX, toX}) {
                if (insideMap(x, y) && isEntityCouldBeAttacked(entitiesByPos[x][y])) {
                    enemies.add(entitiesByPos[x][y]);
                }
            }
        }
        for (int x = frX + 1; x < toX; x++) {
            for (int y : new int[]{frY, toY}) {
                if (insideMap(x, y) && isEntityCouldBeAttacked(entitiesByPos[x][y])) {
                    enemies.add(entitiesByPos[x][y]);
                }
            }
        }
        return enemies;
    }

    // TODO: this function uses wrong metric, because I am lazy
    public Entity findClosestEnemy(final Position pos) {
        final int maxR = entitiesByPos.length + entitiesByPos[0].length + 3;
        int l = 0, r = maxR;
        int x = pos.getX();
        int y = pos.getY();
        while (r - l > 1) {
            int mid = (l + r) >> 1;
            if (anyEnemyInRect(x - mid, x + mid, y - mid, y + mid)) {
                r = mid;
            } else {
                l = mid;
            }
        }
        if (r == maxR) {
            return null;
        }
        final int frX = x - r, toX = x + r;
        final int frY = y - r, toY = y + r;
        List<Entity> enemies = enemiesOnRectBorders(frX, toX, frY, toY);
        if (enemies.isEmpty()) {
            for (int xx = frX; xx <= toX; xx++) {
                for (int yy = frY; yy <= toY; yy++) {
                    if (insideMap(xx, yy)) {
                        if (isEntityCouldBeAttacked(entitiesByPos[xx][yy])) {
                            throw new AssertionError("Found enemy: " + xx + " " + yy + ", " +
                                    entitiesByPos[xx][yy]);
                        }
                    }
                }
            }
            boolean enemiesInRect = anyEnemyInRect(frX, toX, frY, toY);
            if (enemiesInRect) {
                System.err.println(frX + " " + toX + " " + frY + " " + toY);
                throw new AssertionError("Expect to have enemies in rect, but can't see them");
            }
            throw new AssertionError("wrong enemies findings?");
        }
        enemies.sort(new Comparator<Entity>() {
            @Override
            public int compare(Entity o1, Entity o2) {
                int d1 = o1.getPosition().distTo(pos);
                int d2 = o2.getPosition().distTo(pos);
                return Integer.compare(d1, d2);
            }
        });
        return enemies.get(0);
    }

    private int compressCoord(int x, int y) {
        return x + y * entitiesByPos.length;
    }

    private int extractXFromCompressedCoord(int coords) {
        return coords % entitiesByPos.length;
    }

    private int extractYFromCompressedCoord(int coords) {
        return coords / entitiesByPos.length;
    }

    static class Dir {
        final int dx, dy;

        public Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    final static Dir right = new Dir(1, 0);
    final static Dir left = new Dir(-1, 0);
    final static Dir up = new Dir(0, 1);
    final static Dir down = new Dir(0, -1);

    final static Dir[] dirsRight = new Dir[]{right, up, down, left};
    final static Dir[] dirsLeft = new Dir[]{left, down, up, right};
    final static Dir[] dirsUp = new Dir[]{up, left, right, down};
    final static Dir[] dirsDown = new Dir[]{down, right, left, up};

    static Dir[] getDirs(final int dx, final int dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) {
                return dirsRight;
            } else {
                return dirsLeft;
            }
        } else {
            if (dy > 0) {
                return dirsUp;
            } else {
                return dirsDown;
            }
        }
    }

    boolean visit(int compressedCoord) {
        if (visited[compressedCoord] == currentVisitedIter) {
            return false;
        }
        visited[compressedCoord] = currentVisitedIter;
        return true;
    }

    private Position findFirstCellOnPath(final Position startPos, final Position targetPos, final int totalDist) {
        Dir[] dirs = getDirs(targetPos.getX() - startPos.getX(), targetPos.getY() - startPos.getY());
        for (Dir dir : dirs) {
            final int nx = startPos.getX() + dir.dx;
            final int ny = startPos.getY() + dir.dy;
            final int compressedCoord = compressCoord(nx, ny);
            if (insideMap(nx, ny) && visited[compressedCoord] == currentVisitedIter && dist[compressedCoord] == totalDist - 1) {
                if (canGoThrough[nx][ny] == CAN_GO_THROUGH.CAN_GO) {
                    return new Position(nx, ny);
                }
            }
        }
        return null;
    }

    public void updateBlockedCell(final Position pos) {
        canGoThrough[pos.getX()][pos.getY()] = CAN_GO_THROUGH.MAYBE_LATER;
    }

    /**
     * @return first cell in the path
     */
    public Position findBestPathToTarget(final Position startPos, final Position targetPos) {
        if (startPos.distTo(targetPos) == 0) {
            return null;
        }
        currentVisitedIter++;
        int qSz = 0;
        final int endCompressedCoord = compressCoord(targetPos.getX(), targetPos.getY());
        queue[qSz++] = endCompressedCoord;
        dist[endCompressedCoord] = 0;
        int qIt = 0;
        // TODO: restrictions on max qSz to make it faster
        while (qIt < qSz) {
            int compressedCoord = queue[qIt++];
            int x = extractXFromCompressedCoord(compressedCoord);
            int y = extractYFromCompressedCoord(compressedCoord);
            // TODO: this doesn't make much sense now
            Dir[] dirs = getDirs(startPos.getX() - x, startPos.getY() - y);
            final int nextDist = dist[compressedCoord] + 1;
            for (int it = 0; it < dirs.length; it++) {
                int nx = x + dirs[it].dx;
                int ny = y + dirs[it].dy;
                if (!insideMap(nx, ny)) {
                    continue;
                }
                if (nx == startPos.getX() && ny == startPos.getY()) {
                    return findFirstCellOnPath(startPos, targetPos, nextDist);
                }
                if (canGoThrough[nx][ny] == CAN_GO_THROUGH.CANT_GO) {
                    continue;
                }
                int nextCompressedCoord = compressCoord(nx, ny);
                if (!visit(nextCompressedCoord)) {
                    continue;
                }
                queue[qSz++] = nextCompressedCoord;
                dist[nextCompressedCoord] = nextDist;
            }
        }
        return null;
    }
}

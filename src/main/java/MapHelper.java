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
    final State state;

    enum CAN_GO_THROUGH {
        EMPTY_CELL,
        MY_BUILDING_OR_FOOD,
        MY_BUILDER,
        MY_WORKING_BUILDER,
        MY_ATTACKING_UNIT
    }

    enum UNDER_ATTACK {
        SAFE,
        UNDER_ATTACK,
    }

    final CAN_GO_THROUGH[][] canGoThrough;
    final UNDER_ATTACK[][] underAttack;
    final int myPlayerId;
    final BfsQueue bfs;

    private boolean isEntityCouldBeAttacked(final Entity entity) {
        return entity != null && entity.getPlayerId() != null && entity.getPlayerId() != myPlayerId;
    }

    private boolean isEnemyWarUnit(final Entity entity) {
        if (!isEntityCouldBeAttacked(entity)) {
            return false;
        }
        if (entity.getEntityType().isBuilding()) {
            return false;
        }
        if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
            return false;
        }
        return true;
    }

    private CAN_GO_THROUGH computeCanGoThrough(final Entity entity) {
        if (entity.getPlayerId() == null) {
            // TODO: potentially I can...
            return CAN_GO_THROUGH.MY_BUILDING_OR_FOOD;
        }
        if (entity.getPlayerId() == myPlayerId) {
            if (entity.getEntityType().isBuilding()) {
                return CAN_GO_THROUGH.MY_BUILDING_OR_FOOD;
            }
            if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
                return CAN_GO_THROUGH.MY_BUILDER;
            }
            return CAN_GO_THROUGH.EMPTY_CELL;
        } else {
            return CAN_GO_THROUGH.EMPTY_CELL;
        }
    }

    private void markUnderAttack(final Entity entity) {
        int damageRange = state.getEntityProperties(entity).getAttack().getAttackRange();
        if (!entity.getEntityType().isBuilding()) {
            damageRange++;
        }
        final Position bottomLeft = entity.getPosition();
        final int entitySize = state.getEntityProperties(entity).getSize();
        final Position topRight = bottomLeft.shift(entitySize - 1, entitySize - 1);
        for (int x = bottomLeft.getX() - damageRange; x <= topRight.getX() + damageRange; x++) {
            for (int y = bottomLeft.getY() - damageRange; y <= topRight.getY() + damageRange; y++) {
                if (!insideMap(x, y)) {
                    continue;
                }
                if (CellsUtils.distBetweenEntityAndPos(state, entity, x, y) <= damageRange) {
                    underAttack[x][y] = UNDER_ATTACK.UNDER_ATTACK;
                }
            }
        }
    }

    MapHelper(final State state) {
        this.state = state;
        final PlayerView playerView = state.playerView;
        this.myPlayerId = playerView.getMyId();
        final int size = playerView.getMapSize();
        entitiesByPos = new Entity[size][size];
        canGoThrough = new CAN_GO_THROUGH[size][size];
        underAttack = new UNDER_ATTACK[size][size];
        for (CAN_GO_THROUGH[] pass : canGoThrough) {
            Arrays.fill(pass, CAN_GO_THROUGH.EMPTY_CELL);
        }
        for (int i = 0; i < size; i++) {
            Arrays.fill(underAttack[i], UNDER_ATTACK.SAFE);
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
                    canGoThrough[x][y] = computeCanGoThrough(entity);
                }
            }
            if (isEntityCouldBeAttacked(entity)) {
                enemiesPrefSum[pos.getX() + 1][pos.getY() + 1]++;

                if (isEnemyWarUnit(entity) || entity.getEntityType() == EntityType.TURRET) {
                    markUnderAttack(entity);
                }
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
        this.bfs = new BfsQueue(totalCells);
    }

    private boolean insideMap(final int x, final int y) {
        return x >= 0 && x < entitiesByPos.length && y >= 0 && y < entitiesByPos[x].length;
    }

    public boolean canMineThisCell(final int x, final int y) {
        if (!insideMap(x, y)) {
            return false;
        }
        Entity entity = entitiesByPos[x][y];
        return (entity != null && entity.getEntityType() == EntityType.RESOURCE);
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

    interface BfsHandler {
        boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack);

        boolean shouldEnd(int x, int y, int dist);
    }

    static class PathToTargetBfsHandler implements BfsHandler {
        final Position startPos;
        int dist = -1;

        PathToTargetBfsHandler(final Position startPos) {
            this.startPos = startPos;
        }

        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack) {
            return switch (type) {
                case EMPTY_CELL, MY_ATTACKING_UNIT -> true;
                case MY_BUILDING_OR_FOOD, MY_BUILDER, MY_WORKING_BUILDER -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            if (x == startPos.getX() && y == startPos.getY()) {
                this.dist = dist;
                return true;
            }
            return false;
        }

        public boolean foundPath() {
            return dist != -1;
        }
    }

    static class EnemyFinderBfsHandler implements BfsHandler {
        final int maxDist;
        boolean foundEnemy;
        final MapHelper map;

        public EnemyFinderBfsHandler(int maxDist, final MapHelper map) {
            this.maxDist = maxDist;
            this.map = map;
        }


        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack) {
            // TODO: think about it?
            return switch (type) {
                case EMPTY_CELL, MY_ATTACKING_UNIT -> true;
                case MY_BUILDING_OR_FOOD, MY_BUILDER, MY_WORKING_BUILDER -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            Entity whatThere = map.entitiesByPos[x][y];
            if (map.isEnemyWarUnit(whatThere)) {
                foundEnemy = true;
            }
            return foundEnemy || dist > maxDist;
        }
    }

    static class PathToResourcesBfsHandler implements BfsHandler {
        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack) {
            switch (underAttack) {
                case SAFE:
                    break;
                case UNDER_ATTACK:
                    return false;
            }
            return switch (type) {
                case EMPTY_CELL, MY_ATTACKING_UNIT, MY_BUILDER -> true;
                case MY_BUILDING_OR_FOOD, MY_WORKING_BUILDER -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            return false;
        }
    }

    class BfsQueue {
        int qIt, qSz;
        final int[] queue;
        final int[] visited;
        final int[] dist;
        int currentVisitedIter;

        BfsQueue(int totalCells) {
            this.queue = new int[totalCells];
            this.visited = new int[totalCells];
            this.dist = new int[totalCells];
            this.currentVisitedIter = 0;
        }

        void visit(int compressedCoord, int nowDist) {
            if (visited[compressedCoord] == currentVisitedIter) {
                return;
            }
            dist[compressedCoord] = nowDist;
            visited[compressedCoord] = currentVisitedIter;
            queue[qSz++] = compressedCoord;
        }


        void initState(final List<Position> initialPositions) {
            qIt = 0;
            qSz = 0;
            currentVisitedIter++;

            for (Position pos : initialPositions) {
                int compressedPos = compressCoord(pos.getX(), pos.getY());
                visit(compressedPos, 0);
            }
        }

        void run(final List<Position> initialPositions, BfsHandler handler) {
            initState(initialPositions);

            while (qIt < qSz) {
                int compressedCoord = queue[qIt++];
                int x = extractXFromCompressedCoord(compressedCoord);
                int y = extractYFromCompressedCoord(compressedCoord);
                Dir[] dirs = dirsUp;
                final int nextDist = dist[compressedCoord] + 1;
                for (int it = 0; it < dirs.length; it++) {
                    int nx = x + dirs[it].dx;
                    int ny = y + dirs[it].dy;
                    if (!insideMap(nx, ny)) {
                        continue;
                    }
                    if (handler.shouldEnd(nx, ny, nextDist)) {
                        return;
                    }
                    if (!handler.canGoThrough(canGoThrough[nx][ny], underAttack[nx][ny])) {
                        continue;
                    }
                    int nextCompressedCoord = compressCoord(nx, ny);
                    visit(nextCompressedCoord, nextDist);
                }
            }
        }

        int getDist(int x, int y) {
            int compressedCoord = compressCoord(x, y);
            if (visited[compressedCoord] != currentVisitedIter) {
                return Integer.MAX_VALUE;
            }
            return dist[compressedCoord];
        }
    }


    public Position findFirstCellOnPath(final Position startPos, final Position targetPos, final int totalDist, final BfsQueue bfs) {
        Dir[] dirs = getDirs(targetPos.getX() - startPos.getX(), targetPos.getY() - startPos.getY());
        for (Dir dir : dirs) {
            final int nx = startPos.getX() + dir.dx;
            final int ny = startPos.getY() + dir.dy;
            if (insideMap(nx, ny) && bfs.getDist(nx, ny) < totalDist) {
                if (canGoThrough[nx][ny] == CAN_GO_THROUGH.EMPTY_CELL) {
                    return new Position(nx, ny);
                }
            }
        }
        return null;
    }

    public void updateCellCanGoThrough(final Position pos, final CAN_GO_THROUGH type) {
        canGoThrough[pos.getX()][pos.getY()] = type;
    }

    /**
     * @return first cell in the path
     */
    public Position findBestPathToTarget(final Position startPos, final Position targetPos) {
        if (startPos.distTo(targetPos) == 0) {
            return null;
        }
        final List<Position> initialPositions = new ArrayList<>();
        initialPositions.add(targetPos);
        final PathToTargetBfsHandler handler = new PathToTargetBfsHandler(startPos);
        bfs.run(initialPositions, handler);
        if (!handler.foundPath()) {
            return null;
        }
        return findFirstCellOnPath(startPos, targetPos, handler.dist, bfs);
    }


    public boolean existEnemyWarUnitsNearby(Entity fromEntity, int maxDist) {
        int entitySize = state.getEntityProperties(fromEntity).getSize();
        final List<Position> initialPositions = new ArrayList<>();
        final Position entityPos = fromEntity.getPosition();
        for (int dx = 0; dx < entitySize; dx++) {
            for (int dy = 0; dy < entitySize; dy++) {
                initialPositions.add(entityPos.shift(dx, dy));
            }
        }
        final EnemyFinderBfsHandler handler = new EnemyFinderBfsHandler(maxDist, this);
        bfs.run(initialPositions, handler);
        return handler.foundEnemy;
    }

    public BfsQueue findPathsToResources() {
        final List<Entity> resources = state.allResources;
        List<Position> initialPositions = new ArrayList<>();
        for (Entity resource : resources) {
            initialPositions.add(resource.getPosition());
        }
        PathToResourcesBfsHandler handler = new PathToResourcesBfsHandler();
        bfs.run(initialPositions, handler);
        return bfs;
    }
}

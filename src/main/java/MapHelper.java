import model.Entity;
import model.EntityType;
import model.PlayerView;
import model.Position;

import java.util.*;

public class MapHelper {
    private static final int RANGED_UNIT_RANGE_FOR_BUILDERS = 15;
    final Entity[][] entitiesByPos;
    final int[][] enemiesPrefSum;
    final int[][] distToClosestEnemyRangedUnit;
    final State state;
    final List<Position> safePositionsToMine;

    enum CAN_GO_THROUGH {
        EMPTY_CELL,
        MY_BUILDING,
        FOOD,
        UNKNOWN,
        MY_BUILDER,
        MY_WORKING_BUILDER,
        MY_ATTACKING_UNIT,
        MY_EATING_FOOD_RANGED_UNIT,
        ENEMY_BUILDING
    }

    enum UNDER_ATTACK {
        SAFE,
        UNDER_ATTACK,
        UNDER_ATTACK_DO_NOT_GO_THERE;

        public boolean isUnderAttack() {
            return switch (this) {
                case SAFE -> false;
                case UNDER_ATTACK, UNDER_ATTACK_DO_NOT_GO_THERE -> true;
            };
        }
    }

    final CAN_GO_THROUGH[][] canGoThrough;
    final UNDER_ATTACK[][] underAttack;
    final int myPlayerId;
    final BfsQueue bfs;
    final Dijkstra dijkstra;

    private boolean isEntityCouldBeAttacked(final Entity entity, boolean okToAttackTurrets) {
        boolean canAttack = entity != null && entity.getPlayerId() != null && entity.getPlayerId() != myPlayerId;
        if (!canAttack) {
            return false;
        }
        if (entity.getEntityType() == EntityType.TURRET && entity.isActive()) {
            return okToAttackTurrets;
        }
        return true;
    }

    private boolean isEnemyWarUnit(final Entity entity) {
        if (!isEntityCouldBeAttacked(entity, true)) {
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
            return CAN_GO_THROUGH.FOOD;
        }
        if (entity.getPlayerId() == myPlayerId) {
            if (entity.getEntityType().isBuilding()) {
                return CAN_GO_THROUGH.MY_BUILDING;
            }
            if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
                return CAN_GO_THROUGH.MY_BUILDER;
            }
            return CAN_GO_THROUGH.EMPTY_CELL;
        } else {
            if (entity.getEntityType().isBuilding()) {
                return CAN_GO_THROUGH.ENEMY_BUILDING;
            } else {
                return CAN_GO_THROUGH.EMPTY_CELL;
            }
        }
    }

    private void updateUnderAttack(int x, int y, UNDER_ATTACK type) {
        if (type == UNDER_ATTACK.UNDER_ATTACK_DO_NOT_GO_THERE) {
            underAttack[x][y] = type;
            return;
        }
        if (underAttack[x][y] == UNDER_ATTACK.UNDER_ATTACK_DO_NOT_GO_THERE) {
            return;
        }
        if (type == UNDER_ATTACK.UNDER_ATTACK) {
            if (underAttack[x][y] == UNDER_ATTACK.SAFE) {
                underAttack[x][y] = UNDER_ATTACK.UNDER_ATTACK;
            } else {
                // if cell is under attack by more than one unit -> do not go there.
                underAttack[x][y] = UNDER_ATTACK.UNDER_ATTACK_DO_NOT_GO_THERE;
            }
        }
    }

    private void markUnderAttack(final Entity entity, boolean notGoThere) {
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
                    if (notGoThere) {
                        updateUnderAttack(x, y, UNDER_ATTACK.UNDER_ATTACK_DO_NOT_GO_THERE);
                    } else {
                        updateUnderAttack(x, y, UNDER_ATTACK.UNDER_ATTACK);
                    }
                }
            }
        }
    }

    private void markSeeCells(final Entity entity) {
        int sightRange = state.getEntityProperties(entity).getSightRange();
        final Position bottomLeft = entity.getPosition();
        final int entitySize = state.getEntityProperties(entity).getSize();
        final Position topRight = bottomLeft.shift(entitySize - 1, entitySize - 1);
        for (int x = bottomLeft.getX() - sightRange; x <= topRight.getX() + sightRange; x++) {
            for (int y = bottomLeft.getY() - sightRange; y <= topRight.getY() + sightRange; y++) {
                if (!insideMap(x, y)) {
                    continue;
                }
                if (CellsUtils.distBetweenEntityAndPos(state, entity, x, y) <= sightRange) {
                    canGoThrough[x][y] = CAN_GO_THROUGH.EMPTY_CELL;
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
            Arrays.fill(pass, CAN_GO_THROUGH.UNKNOWN);
        }
        for (Entity entity : state.myEntities) {
            markSeeCells(entity);
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
            if (isEntityCouldBeAttacked(entity, true)) {
                if (entity.getEntityType() != EntityType.TURRET || !entity.isActive()) {
                    enemiesPrefSum[pos.getX() + 1][pos.getY() + 1]++;
                }

                if (isEnemyWarUnit(entity) || entity.getEntityType() == EntityType.TURRET) {
                    boolean notGoThere = entity.getEntityType() == EntityType.TURRET && entity.isActive();
                    markUnderAttack(entity, notGoThere);
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
        this.dijkstra = new Dijkstra(this);
        this.distToClosestEnemyRangedUnit = computeDistToClosesEnemyRangedUnit();
        this.safePositionsToMine = computeSafePositionsToMine();
    }

    private int[][] computeDistToClosesEnemyRangedUnit() {
        final int mapSize = state.playerView.getMapSize();
        int[][] dist = new int[mapSize][mapSize];
        BfsQueue queue = findPathsToEnemyRangedUnits();
        for (int x = 0; x < mapSize; x++) {
            for (int y = 0; y < mapSize; y++) {
                dist[x][y] = queue.getDist(x, y);
            }
        }
        return dist;
    }

    public boolean isSafePositionToMine(int x, int y) {
        if (entitiesByPos[x][y] != null) {
            return false;
        }
        if (distToClosestEnemyRangedUnit[x][y] <= RANGED_UNIT_RANGE_FOR_BUILDERS) {
            return false;
        }
        if (canGoThrough[x][y] == CAN_GO_THROUGH.UNKNOWN) {
            return false;
        }
        int[] dx = Directions.dx;
        int[] dy = Directions.dy;
        for (int it = 0; it < dx.length; it++) {
            int nx = x + dx[it];
            int ny = y + dy[it];
            if (!insideMap(nx, ny)) {
                continue;
            }
            if (canMineThisCell(nx, ny)) {
                return true;
            }
        }
        return false;
    }

    private List<Position> computeSafePositionsToMine() {
        List<Position> safePositions = new ArrayList<>();
        final int mapSize = state.playerView.getMapSize();
        for (int x = 0; x < mapSize; x++) {
            for (int y = 0; y < mapSize; y++) {
                if (!isSafePositionToMine(x, y)) {
                    continue;
                }

                safePositions.add(new Position(x, y));
            }
        }
        return safePositions;
    }

    public boolean insideMap(final int x, final int y) {
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
                if (!isEntityCouldBeAttacked(entity, true)) {
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
                if (insideMap(x, y) && isEntityCouldBeAttacked(entitiesByPos[x][y], false)) {
                    enemies.add(entitiesByPos[x][y]);
                }
            }
        }
        for (int x = frX + 1; x < toX; x++) {
            for (int y : new int[]{frY, toY}) {
                if (insideMap(x, y) && isEntityCouldBeAttacked(entitiesByPos[x][y], false)) {
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
                        if (isEntityCouldBeAttacked(entitiesByPos[xx][yy], false)) {
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
        boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int dist);

        boolean shouldEnd(int x, int y, int dist);
    }

    static class PathToTargetBfsHandler implements BfsHandler, Dijkstra.DijkstraHandler {
        final Position startPos;
        int resultDist = -1;
        final int skipLastNCells;
        final boolean okGoNotGoThere;
        final boolean okGoThroughMyBuilders;
        final boolean okGoUnderAttack;
        final boolean okEatFood;

        public boolean isOkGoNotGoThere() {
            return okGoNotGoThere;
        }

        public boolean isOkGoThroughMyBuilders() {
            return okGoThroughMyBuilders;
        }

        @Override
        public boolean isOkGoUnderAttack() {
            return okGoUnderAttack;
        }

        public boolean isOkEatFood() {
            return okEatFood;
        }

        @Override
        public int getSkipLastNCells() {
            return skipLastNCells;
        }

        PathToTargetBfsHandler(final Position startPos,
                               final int skipLastNCells,
                               final boolean okGoNotGoThere,
                               final boolean okGoThroughMyBuilders,
                               final boolean okGoUnderAttack,
                               final boolean okEatFood) {
            this.startPos = startPos;
            this.skipLastNCells = skipLastNCells;
            this.okGoNotGoThere = okGoNotGoThere;
            this.okGoThroughMyBuilders = okGoThroughMyBuilders;
            this.okGoUnderAttack = okGoUnderAttack;
            this.okEatFood = okEatFood;
        }

        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int dist) {
            // < - because otherwise we go under turrets
            if (dist < skipLastNCells) {
                return true;
            }
            switch (underAttack) {
                case UNDER_ATTACK_DO_NOT_GO_THERE:
                    if (okGoNotGoThere) {
                        break;
                    } else {
                        return false;
                    }
                case UNDER_ATTACK:
                    if (okGoUnderAttack) {
                        break;
                    } else {
                        return false;
                    }
                case SAFE:
                    break;
            }
            return switch (type) {
                case EMPTY_CELL, UNKNOWN, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT -> true;
                case FOOD -> okEatFood;
                case MY_BUILDER, MY_WORKING_BUILDER -> okGoThroughMyBuilders;
                case MY_BUILDING, ENEMY_BUILDING -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            if (x == startPos.getX() && y == startPos.getY()) {
                this.resultDist = dist;
                return true;
            }
            return false;
        }

        @Override
        public int getEdgeCost(CAN_GO_THROUGH type) {
            return switch (type) {
                case EMPTY_CELL, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, MY_BUILDING, MY_BUILDER, MY_WORKING_BUILDER -> 1;
                case UNKNOWN, FOOD, ENEMY_BUILDING -> 2;
            };
        }

        public boolean foundPath() {
            return resultDist != -1;
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
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int dist) {
            // TODO: think about it?
            return switch (type) {
                case EMPTY_CELL, UNKNOWN, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, ENEMY_BUILDING -> true;
                case MY_BUILDING, FOOD, MY_BUILDER, MY_WORKING_BUILDER -> false;
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
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int dist) {
            switch (underAttack) {
                case SAFE:
                    break;
                case UNDER_ATTACK, UNDER_ATTACK_DO_NOT_GO_THERE:
                    return false;
            }
            return switch (type) {
                case EMPTY_CELL, UNKNOWN, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, MY_BUILDER -> true;
                case MY_BUILDING, FOOD, MY_WORKING_BUILDER, ENEMY_BUILDING -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            return false;
        }
    }

    static class PathToEnemyUnitsBfsHandler implements BfsHandler {
        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int dist) {
            return true;
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            return false;
        }
    }

    static class PathToBuildersBfsHandler implements BfsHandler {
        final int maxDist;
        final Set<Entity> builders;
        final int needBuilders;
        final State state;


        public PathToBuildersBfsHandler(int maxDist, int needBuilders, State state) {
            this.maxDist = maxDist;
            this.builders = new HashSet<>();
            this.needBuilders = needBuilders;
            this.state = state;
        }

        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int dist) {
            switch (underAttack) {
                case SAFE:
                    break;
                case UNDER_ATTACK, UNDER_ATTACK_DO_NOT_GO_THERE:
                    return false;
            }
            return switch (type) {
                case EMPTY_CELL, UNKNOWN, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, MY_BUILDER -> true;
                case MY_BUILDING, FOOD, MY_WORKING_BUILDER, ENEMY_BUILDING -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            if (dist > maxDist) {
                return true;
            }
            final MapHelper map = state.map;
            if (map.canGoThrough[x][y] != CAN_GO_THROUGH.MY_BUILDER) {
                return false;
            }
            Entity entity = map.entitiesByPos[x][y];
            if (entity.getEntityType() != EntityType.BUILDER_UNIT) {
                throw new AssertionError("Expected builder, found something else");
            }
            builders.add(entity);
            return builders.size() >= needBuilders;
        }
    }

    class BfsQueue implements QueueDist {
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

        boolean isVisited(int compressedCoord) {
            return visited[compressedCoord] == currentVisitedIter;
        }

        void visit(int compressedCoord, int nowDist) {
            if (isVisited(compressedCoord)) {
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
                int compressedPos = CompressedCoords.compress(pos);
                visit(compressedPos, 0);
            }
        }

        void run(final List<Position> initialPositions, BfsHandler handler) {
            initState(initialPositions);

            while (qIt < qSz) {
                int compressedCoord = queue[qIt++];
                int x = CompressedCoords.extractX(compressedCoord);
                int y = CompressedCoords.extractY(compressedCoord);
                Dir[] dirs = dirsUp;
                final int nextDist = dist[compressedCoord] + 1;
                for (int it = 0; it < dirs.length; it++) {
                    int nx = x + dirs[it].dx;
                    int ny = y + dirs[it].dy;
                    if (!insideMap(nx, ny)) {
                        continue;
                    }
                    int nextCompressedCoord = CompressedCoords.compress(nx, ny);
                    if (isVisited(nextCompressedCoord)) {
                        continue;
                    }
                    if (handler.shouldEnd(nx, ny, nextDist)) {
                        return;
                    }
                    if (!handler.canGoThrough(canGoThrough[nx][ny], underAttack[nx][ny], nx, ny, nextDist)) {
                        continue;
                    }
                    visit(nextCompressedCoord, nextDist);
                }
            }
        }

        public int getDist(int x, int y) {
            int compressedCoord = CompressedCoords.compress(x, y);
            if (visited[compressedCoord] != currentVisitedIter) {
                return Integer.MAX_VALUE;
            }
            return dist[compressedCoord];
        }

        @Override
        public int getEdgeCost(int x, int y) {
            return 1;
        }
    }

    public static boolean canGoThereOnCurrentTurn(CAN_GO_THROUGH type, boolean okToAttackFood, boolean okGoThroughMyBuilders) {
        return switch (type) {
            case EMPTY_CELL, UNKNOWN -> true;
            case MY_BUILDING, MY_ATTACKING_UNIT, ENEMY_BUILDING -> false;
            case MY_BUILDER, MY_WORKING_BUILDER -> okGoThroughMyBuilders;
            case FOOD, MY_EATING_FOOD_RANGED_UNIT -> okToAttackFood;
        };
    }

    static class FirstMoveOption implements Comparable<FirstMoveOption> {
        final Position goTo;
        final int dist;

        public FirstMoveOption(Position goTo, int dist) {
            this.goTo = goTo;
            this.dist = dist;
        }

        @Override
        public int compareTo(FirstMoveOption o) {
            return Integer.compare(dist, o.dist);
        }
    }

    public List<Position> findFirstCellOnPath(final Position startPos,
                                              final Position targetPos,
                                              final int totalDist,
                                              final QueueDist bfs,
                                              boolean canAttackResources,
                                              boolean okGoThrougMyBuilders) {
        Dir[] dirs = getDirs(targetPos.getX() - startPos.getX(), targetPos.getY() - startPos.getY());
        List<FirstMoveOption> options = new ArrayList<>();
        for (Dir dir : dirs) {
            final int nx = startPos.getX() + dir.dx;
            final int ny = startPos.getY() + dir.dy;
            if (!insideMap(nx, ny)) {
                continue;
            }
            final int distFromNext = bfs.getDist(nx, ny);
            if (distFromNext >= Integer.MAX_VALUE / 2) {
                continue;
            }
            if (insideMap(nx, ny) && distFromNext + bfs.getEdgeCost(nx, ny) <= totalDist) {
                if (canGoThereOnCurrentTurn(canGoThrough[nx][ny], canAttackResources, okGoThrougMyBuilders)) {
                    options.add(new FirstMoveOption(new Position(nx, ny), distFromNext));
                }
            }
        }
        Collections.sort(options);
        if (options.isEmpty()) {
            return new ArrayList<>();
        }
        while (options.get(options.size() - 1).compareTo(options.get(0)) != 0) {
            options.remove(options.size() - 1);
        }
        List<Position> firstPositions = new ArrayList<>();
        for (FirstMoveOption option : options) {
            firstPositions.add(option.goTo);
        }
        return firstPositions;
    }

    public Position findLastCellOnPath(final Position startPos, final int totalDist, final BfsQueue bfs, boolean minDistIsOne) {
        Dir[] dirs = getDirs(0, 0);
        Position curPos = startPos;
        int curDist = totalDist;
        while (true) {
            if (minDistIsOne && curDist == 1) {
                break;
            }
            boolean found = false;
            for (Dir dir : dirs) {
                final int nx = curPos.getX() + dir.dx;
                final int ny = curPos.getY() + dir.dy;
                if (insideMap(nx, ny) && bfs.getDist(nx, ny) < curDist) {
                    found = true;
                    curDist = bfs.getDist(nx, ny);
                    curPos = new Position(nx, ny);
                    break;
                }
            }
            if (!found) {
                break;
            }
        }
        return curPos;
    }

    public void updateCellCanGoThrough(final Position pos, final CAN_GO_THROUGH type) {
        canGoThrough[pos.getX()][pos.getY()] = type;
    }

    /**
     * @return first cell in the path
     */
    public List<Position> findBestPathToTargetDijkstra(final Position startPos,
                                                       final Position targetPos,
                                                       int skipLastNCells,
                                                       int maxDist,
                                                       boolean okGoToNotGoThere,
                                                       boolean okGoThroughMyBuilders,
                                                       boolean okGoUnderAttack,
                                                       boolean okEatFood) {
        if (startPos.distTo(targetPos) == 0) {
            return new ArrayList<>();
        }
        final PathToTargetBfsHandler handler = new PathToTargetBfsHandler(startPos, skipLastNCells, okGoToNotGoThere, okGoThroughMyBuilders, okGoUnderAttack, okEatFood);
        QueueDist queue = dijkstra.findFirstCellOnPath(startPos, targetPos, handler, maxDist, state.playerView.getMapSize());
        return findFirstCellOnPath(startPos, targetPos, queue.getDist(startPos.getX(), startPos.getY()), queue, true, okGoThroughMyBuilders);
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

    private BfsQueue findPathsToCells(final List<Position> initialPositions, final BfsHandler handler) {
        bfs.run(initialPositions, handler);
        return bfs;
    }

    public BfsQueue findPathsToResources() {
        final List<Entity> resources = state.allResources;
        List<Position> initialPositions = new ArrayList<>();
        for (Entity resource : resources) {
            initialPositions.add(resource.getPosition());
        }
        PathToResourcesBfsHandler handler = new PathToResourcesBfsHandler();
        return findPathsToCells(initialPositions, handler);
    }

    public BfsQueue findPathsToEnemyRangedUnits() {
        List<Position> initialPositions = new ArrayList<>();
        for (Entity entity : state.allEnemiesWarUnits) {
            if (entity.getEntityType() == EntityType.RANGED_UNIT) {
                initialPositions.add(entity.getPosition());
            }
        }
        PathToEnemyUnitsBfsHandler handler = new PathToEnemyUnitsBfsHandler();
        return findPathsToCells(initialPositions, handler);
    }

    class PathsFromBuilders {
        final Map<Entity, List<Position>> firstCellsInPath;
        final Map<Entity, Integer> dists;

        public PathsFromBuilders(final PathToBuildersBfsHandler handler, BfsQueue queue) {
            this.firstCellsInPath = new HashMap<>();
            this.dists = new HashMap<>();
            for (Entity builder : handler.builders) {
                // TODO: fix target cell
                final Position pos = builder.getPosition();
                final int dist = queue.getDist(pos.getX(), pos.getY());
                final List<Position> firstCell = findFirstCellOnPath(pos, builder.getPosition(), dist, queue, false, true);
                if (firstCell.isEmpty()) {
                    continue;
                }
                final Position someFirstCell = firstCell.get(0);
                final int realDist = 1 + queue.getDist(someFirstCell.getX(), someFirstCell.getY());
                firstCellsInPath.put(builder, firstCell);
                dists.put(builder, realDist);
            }
        }
    }

    public PathsFromBuilders findPathsToBuilding(final List<Position> initialPositions, final int maxDist, final int needBuilders) {
        PathToBuildersBfsHandler handler = new PathToBuildersBfsHandler(maxDist, needBuilders, state);
        final BfsQueue queue = findPathsToCells(initialPositions, handler);
        return new PathsFromBuilders(handler, queue);
    }

    class PathSuggestion {
        final Position targetCell;
        final Position firstCellOnPath;
        final int dist;

        public PathSuggestion(Position targetCell, Position firstCellOnPath, final int dist) {
            this.targetCell = targetCell;
            this.firstCellOnPath = firstCellOnPath;
            this.dist = dist;
        }
    }

    class PathsToResourcesFromBuilderBfsHandler implements BfsHandler {
        final List<Position> targetCells;
        final int maxOptions;
        final int maxDist;

        int[] dx = Directions.dx;
        int[] dy = Directions.dy;

        final Position startPos;

        boolean isTargetCell(int x, int y) {
            for (int it = 0; it < dx.length; it++) {
                final int nx = x + dx[it];
                final int ny = y + dy[it];
                if (insideMap(nx, ny)) {
                    final Entity entity = entitiesByPos[nx][ny];
                    if (entity != null && entity.getEntityType() == EntityType.RESOURCE) {
                        return true;
                    }
                }
            }
            return false;
        }

        PathsToResourcesFromBuilderBfsHandler(int maxOptions, final Position startPos, final int maxDist) {
            targetCells = new ArrayList<>();
            this.maxOptions = maxOptions;
            this.startPos = startPos;
            this.maxDist = maxDist;
        }

        @Override
        public boolean canGoThrough(CAN_GO_THROUGH type, UNDER_ATTACK underAttack, int x, int y, int curDist) {
            switch (underAttack) {
                case SAFE:
                    break;
                case UNDER_ATTACK, UNDER_ATTACK_DO_NOT_GO_THERE:
                    return false;
            }
            int dist = Math.abs(x - startPos.getX()) + Math.abs(y - startPos.getY());
            if (dist == 1) {
                return switch (type) {
                    case EMPTY_CELL, UNKNOWN, MY_BUILDER -> true;
                    case MY_BUILDING, FOOD, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, MY_WORKING_BUILDER, ENEMY_BUILDING -> false;
                };
            }
            return switch (type) {
                case EMPTY_CELL, UNKNOWN, MY_ATTACKING_UNIT, MY_EATING_FOOD_RANGED_UNIT, MY_BUILDER -> true;
                case MY_BUILDING, FOOD, MY_WORKING_BUILDER, ENEMY_BUILDING -> false;
            };
        }

        @Override
        public boolean shouldEnd(int x, int y, int dist) {
            if (targetCells.size() >= maxOptions) {
                return true;
            }
            if (isTargetCell(x, y) && canGoThrough(canGoThrough[x][y], underAttack[x][y], x, y, dist)) {
                targetCells.add(new Position(x, y));
            }
            return dist > maxDist;
        }
    }

    public List<PathSuggestion> findPathsToResourcesFromBuilder(final Position startPos, final int maxOptions, final int maxDist) {
        PathsToResourcesFromBuilderBfsHandler handler = new PathsToResourcesFromBuilderBfsHandler(maxOptions, startPos, maxDist);
        final List<Position> initialPositions = new ArrayList<>();
        initialPositions.add(startPos);
        final BfsQueue queue = findPathsToCells(initialPositions, handler);
        List<PathSuggestion> pathSuggestions = new ArrayList<>();
        for (Position targetCell : handler.targetCells) {
            final int dist = queue.getDist(targetCell.getX(), targetCell.getY());
            final Position firstCellOnPath = findLastCellOnPath(targetCell, dist, queue, true);
            if (startPos.distTo(firstCellOnPath) != 1) {
                throw new AssertionError("strange first cell! " + startPos + " " + firstCellOnPath + " " + targetCell + " " + dist);
            }
            pathSuggestions.add(new PathSuggestion(targetCell, firstCellOnPath, dist));
        }
        return pathSuggestions;
    }
}

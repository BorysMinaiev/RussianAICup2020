import model.*;

import java.util.*;

public class State {
    final Action actions;
    final PlayerView playerView;
    final Random rnd;
    final List<Entity> myEntities;
    final List<Entity> allEnemiesWarUnits;
    final Map<EntityType, List<Entity>> myEntitiesByType;
    final int populationUsed;
    final int populationTotal;
    final int populationExpected;
    final GlobalStrategy globalStrategy;
    final Map<Position, Double> attackedByPos;
    final Set<Position> occupiedPositions;
    final Set<Position> occupiedByBuildingsPositions;
    final Map<EntityType, Integer> myEntitiesCount;
    final Map<Integer, Map<EntityType, Integer>> entitiesByPlayer;
    final Map<Position, Position> debugTargets;
    final Set<Position> debugUnitsInBadPostion;
    final List<Entity> allResources;
    final int totalResources;
    final MapHelper map;
    int debugPos = 30;

    private int countTotalPopulation() {
        int population = 0;
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }
            if (entity.isActive()) {
                population += getEntityProperties(entity).getPopulationProvide();
            }
        }
        return population;
    }

    private int countExpectedPopulation() {
        int population = 0;
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }
            population += getEntityProperties(entity).getPopulationProvide();
        }
        return population;
    }

    private int countUsedPopulation() {
        int population = 0;
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }
            population += getEntityProperties(entity).getPopulationUse();
        }
        return population;
    }

    boolean inRectVertices(final Position bottomLeft, final Position topRight, final Position pos) {
        if (pos.distTo(bottomLeft) == 0 || pos.distTo(topRight) == 0) {
            return true;
        }
        final Position bottomRight = new Position(topRight.getX(), bottomLeft.getY());
        final Position topLeft = new Position(bottomLeft.getX(), topRight.getY());
        return pos.distTo(topLeft) == 0 || pos.distTo(bottomRight) == 0;
    }

    boolean insideRect(final Position bottomLeft, final Position topRight, final Position pos) {
        return pos.getX() >= bottomLeft.getX() && pos.getX() <= topRight.getX() && pos.getY() >= bottomLeft.getY() && pos.getY() <= topRight.getY();
    }

    boolean isNearby(final Entity building, final Entity unit) {
        Position bottomLeft = building.getPosition().shift(-1, -1);
        int buildingSize = getEntityProperties(building).getSize();
        Position topRight = building.getPosition().shift(buildingSize, buildingSize);
        return insideRect(bottomLeft, topRight, unit.getPosition()) && !inRectVertices(bottomLeft, topRight, unit.getPosition());
    }

    private int dist(int dx, int dy) {
        return Math.abs(dx) + Math.abs(dy);
    }

    Map<Position, Double> computeAttackedByPos() {
        Map<Position, Double> attackedByPos = new HashMap<>();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null) {
                continue;
            }
            if (entity.getPlayerId() == playerView.getMyId()) {
                continue;
            }
            if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
                continue;
            }
            EntityProperties entityProperties = getEntityProperties(entity);
            AttackProperties attackProperties = entityProperties.getAttack();
            if (attackProperties == null) {
                continue;
            }
            int damage = attackProperties.getDamage();
            if (damage == 0) {
                continue;
            }
            int attackRange = attackProperties.getAttackRange();
            if (!entity.getEntityType().isBuilding()) {
                attackRange++;
            }
            for (int dx = -attackRange; dx <= attackRange; dx++) {
                for (int dy = -attackRange; dy <= attackRange; dy++) {
                    int dist = dist(dx, dy);
                    if (dist > attackRange) {
                        continue;
                    }
                    double ratio = (attackRange - dist + 1) / (1.0 + attackRange);
                    Position pos = entity.getPosition().shift(dx, dy);
                    attackedByPos.put(pos, attackedByPos.getOrDefault(pos, 0.0) + damage * ratio);
                }
            }
        }
        return attackedByPos;
    }

    Set<Position> computeOccupiedPositions() {
        Set<Position> occupied = new HashSet<>();
        for (Entity entity : playerView.getEntities()) {
            EntityProperties entityProperties = getEntityProperties(entity);
            for (int dx = 0; dx < entityProperties.getSize(); dx++) {
                for (int dy = 0; dy < entityProperties.getSize(); dy++) {
                    occupied.add(entity.getPosition().shift(dx, dy));
                }
            }
        }
        return occupied;
    }

    Set<Position> computeOccupiedByBuildingsPositions() {
        Set<Position> occupied = new HashSet<>();
        for (Entity entity : playerView.getEntities()) {
            EntityProperties entityProperties = getEntityProperties(entity);
            if (!entityProperties.isBuilding()) {
                continue;
            }
            for (int dx = 0; dx < entityProperties.getSize(); dx++) {
                for (int dy = 0; dy < entityProperties.getSize(); dy++) {
                    occupied.add(entity.getPosition().shift(dx, dy));
                }
            }
        }
        return occupied;
    }

    Map<EntityType, Integer> computeMyEntitiesCount(List<Entity> entities) {
        Map<EntityType, Integer> count = new HashMap<>();
        for (EntityType type : EntityType.values()) {
            count.put(type, 0);
        }
        for (Entity entity : entities) {
            count.put(entity.getEntityType(), count.get(entity.getEntityType()) + 1);
        }
        return count;
    }

    Map<Integer, Map<EntityType, Integer>> computeEntitiesByPlayer() {
        Map<Integer, List<Entity>> entitiesByPlayer = new HashMap<>();
        for (Entity entity : playerView.getEntities()) {
            Integer playerId = entity.getPlayerId();
            if (playerId == null) {
                continue;
            }
            List<Entity> playerEntities = entitiesByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>());
            playerEntities.add(entity);
        }
        Map<Integer, Map<EntityType, Integer>> result = new HashMap<>();
        for (Map.Entry<Integer, List<Entity>> entry : entitiesByPlayer.entrySet()) {
            result.put(entry.getKey(), computeMyEntitiesCount(entry.getValue()));
        }
        return result;
    }

    Map<EntityType, List<Entity>> computeMyEntitiesByType() {
        Map<EntityType, List<Entity>> count = new HashMap<>();
        for (EntityType type : EntityType.values()) {
            count.put(type, new ArrayList<>());
        }
        for (Entity entity : myEntities) {
            List<Entity> list = count.get(entity.getEntityType());
            list.add(entity);
        }
        return count;
    }

    List<Entity> computeAllEnemiesWarUnits() {
        List<Entity> enemiesUnits = new ArrayList<>();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null) {
                continue;
            }
            if (entity.getPlayerId() == playerView.getMyId()) {
                continue;
            }
            if (entity.getEntityType().isBuilding()) {
                continue;
            }
            if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
                continue;
            }
            enemiesUnits.add(entity);
        }
        return enemiesUnits;
    }

    List<Entity> computeMyEntities() {
        List<Entity> myEntities = new ArrayList<>();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null) {
                continue;
            }
            if (entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }
            myEntities.add(entity);
        }
        return myEntities;
    }

    List<Entity> computeAllResourcesList() {
        List<Entity> resources = new ArrayList<>();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getEntityType() == EntityType.RESOURCE) {
                resources.add(entity);
            }
        }
        return resources;
    }

    State(final PlayerView playerView) {
        this.actions = new Action(new HashMap<>());
        this.playerView = playerView;
        this.myEntities = computeMyEntities();
        this.allEnemiesWarUnits = computeAllEnemiesWarUnits();
        this.myEntitiesByType = computeMyEntitiesByType();
        this.rnd = new Random(123 + playerView.getCurrentTick());
        this.populationUsed = countUsedPopulation();
        this.populationTotal = countTotalPopulation();
        this.populationExpected = countExpectedPopulation();
        this.globalStrategy = new GlobalStrategy(this);
        this.attackedByPos = computeAttackedByPos();
        this.occupiedPositions = computeOccupiedPositions();
        this.occupiedByBuildingsPositions = computeOccupiedByBuildingsPositions();
        this.myEntitiesCount = computeMyEntitiesCount(this.myEntities);
        this.entitiesByPlayer = computeEntitiesByPlayer();
        this.allResources = computeAllResourcesList();
        this.totalResources = computeTotalResources();
        this.debugTargets = new HashMap<>();
        this.debugUnitsInBadPostion = new HashSet<>();
        this.map = new MapHelper(this);
        System.err.println("CURRENT TICK: " + playerView.getCurrentTick() + ", population: " + populationUsed + "/" + populationTotal);
        defaultDoNothing();
    }

    public void addDebugTarget(final Position from, final Position to) {
        debugTargets.put(from, to);
    }

    public void addDebugUnitInBadPosition(final Position pos) {
        debugUnitsInBadPostion.add(pos);
    }

    private int computeTotalResources() {
        int totalResources = 0;
        for (Entity resource : allResources) {
            totalResources += resource.getHealth();
        }
        return totalResources;
    }

    private void defaultDoNothing() {
        for (Entity entity : myEntities) {
            setAction(entity, EntityAction.emptyAction);
        }
    }


    public void printSomeDebug(DebugInterface debugInterface, boolean isBetweenTicks) {
        Debug.printSomeDebug(debugInterface, this, isBetweenTicks);
    }

    public boolean checkCanBuild(EntityType who, EntityType what) {
        EntityProperties properties = playerView.getEntityProperties().get(who);
        EntityType[] canBuild = properties.getCanBuild();
        for (EntityType can : canBuild) {
            if (can == what) {
                return true;
            }
        }
        return false;
    }

    public EntityProperties getEntityTypeProperties(EntityType type) {
        return playerView.getEntityProperties().get(type);
    }

    public EntityProperties getEntityProperties(Entity entity) {
        return getEntityTypeProperties(entity.getEntityType());
    }

    private List<Position> getPositionsOnRectBorder(Position bottomLeft, Position topRight) {
        List<Position> positions = new ArrayList<>();
        for (int x = bottomLeft.getX(); x <= topRight.getX(); x++) {
            for (int y = bottomLeft.getY(); y <= topRight.getY(); y++) {
                if (!(x == bottomLeft.getX() || y == bottomLeft.getY() || x == topRight.getX() || y == topRight.getY())) {
                    continue;
                }
                if (inRectVertices(bottomLeft, topRight, new Position(x, y))) {
                    continue;
                }
                positions.add(new Position(x, y));
            }
        }
        Collections.shuffle(positions, rnd);
        return positions;
    }

    private boolean isSegIntersect(int from1, int to1, int from2, int to2) {
        return Math.max(from1, from2) <= Math.min(to1, to2);
    }

    private boolean willIntersect(final Entity entity, final Position pos, final EntityType type) {
        int x = entity.getPosition().getX(), y = entity.getPosition().getY();
        int entitySize = getEntityProperties(entity).getSize();
        int newObjSize = getEntityTypeProperties(type).getSize();
        return isSegIntersect(x, x + entitySize - 1, pos.getX(), pos.getX() + newObjSize - 1) &&
                isSegIntersect(y, y + entitySize - 1, pos.getY(), pos.getY() + newObjSize - 1);
    }

    private boolean canBuild(final Position pos, final EntityType type) {
        if (pos.getX() < 0 || pos.getY() < 0) {
            return false;
        }
        int objSize = getEntityTypeProperties(type).getSize();
        if (pos.getX() + objSize > playerView.getMapSize() || pos.getY() + objSize > playerView.getMapSize()) {
            return false;
        }
        for (Entity entity : playerView.getEntities()) {
            if (willIntersect(entity, pos, type)) {
                return false;
            }
        }
        return true;
    }

    public List<Position> findPossiblePositionToBuild(final Entity who, final EntityType what) {
        int whoSize = getEntityTypeProperties(who.getEntityType()).getSize();
        int whatSize = getEntityTypeProperties(what).getSize();
        Position bottomLeft = who.getPosition().shift(-whatSize, -whatSize);
        Position topRight = who.getPosition().shift(whoSize, whoSize);
        List<Position> possiblePositions = getPositionsOnRectBorder(bottomLeft, topRight);
        List<Position> positionsWhereCanBuild = new ArrayList<>();
        for (Position pos : possiblePositions) {
            if (canBuild(pos, what)) {
                positionsWhereCanBuild.add(pos);
            }
        }
        return positionsWhereCanBuild;
    }

    public boolean isEnoughResourcesToBuild(final EntityType what) {
        int cost = getEntityTypeProperties(what).getInitialCost();
        int money = playerView.getMyPlayer().getResource();
        return cost <= money;
    }

    public void repairSomething(final Entity who, final int what) {
        actions.getEntityActions().put(who.getId(), new EntityAction(null, null, null, new RepairAction(what)));
    }

    private void setAction(final Entity entity, final EntityAction action) {
        actions.getEntityActions().put(entity.getId(), action);
    }

    public void buildSomething(final Entity who, final EntityType what, final Position where) {
        System.err.println(who + " tries to build " + what + " at " + where);
        if (where == null) {
            throw new AssertionError();
        }
        if (!checkCanBuild(who.getEntityType(), what)) {
            throw new AssertionError(who + " can't build " + what);
        }
        if (!isEnoughResourcesToBuild(what)) {
            throw new AssertionError("Not enough money to build :(");
        }
        setAction(who, EntityAction.createBuildAction(what, where));
    }

    public void attackSomebody(final Entity who) {
        EntityProperties properties = playerView.getEntityProperties().get(who.getEntityType());
        // TODO: use smarter attack?
        setAction(who, EntityAction.createAttackAction(null, new AutoAttack(properties.getSightRange(), new EntityType[]{})));
    }

    public void attack(final Entity who, final Entity what) {
        setAction(who, EntityAction.createAttackAction(what.getId(), null));
    }

    boolean insideMap(Position pos) {
        return pos.getX() >= 0 &&
                pos.getY() >= 0 &&
                pos.getX() < playerView.getMapSize() &&
                pos.getY() < playerView.getMapSize();
    }

    static boolean canGoThere(MapHelper.CAN_GO_THROUGH type) {
        return switch (type) {
            case EMPTY_CELL, MY_BUILDER -> true;
            case MY_BUILDING_OR_FOOD, MY_ATTACKING_UNIT, MY_WORKING_BUILDER -> false;
        };
    }

    public List<Position> getAllPossibleUnitMoves(Entity unit) {
        List<Position> canGo = new ArrayList<>();
        int[] dx = new int[]{-1, 0, 0, 1};
        int[] dy = new int[]{0, -1, 1, 0};
        for (int it = 0; it < dx.length; it++) {
            Position checkPos = unit.getPosition().shift(dx[it], dy[it]);
            if (!insideMap(checkPos)) {
                continue;
            }
            if (!canGoThere(map.canGoThrough[checkPos.getX()][checkPos.getY()])) {
                continue;
            }
            canGo.add(checkPos);
        }
        return canGo;
    }

    public void move(Entity unit, Position where) {
        setAction(unit, EntityAction.createMoveAction(where, true, true));
    }

    public void randomlyMoveAndAttack(final Entity unit) {
        MoveAction moveAction = null;
        moveAction = new MoveAction(
                new Position(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                true,
                true);
        EntityType[] validAutoAttackTargets = new EntityType[0];
        EntityProperties properties = getEntityProperties(unit);
        setAction(unit, new EntityAction(
                moveAction,
                null,
                new AttackAction(
                        null, new AutoAttack(properties.getSightRange() * 5, validAutoAttackTargets)
                ),
                null
        ));
    }

    public boolean isOccupiedByBuilding(Position position) {
        return occupiedByBuildingsPositions.contains(position);
    }
}

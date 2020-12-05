import model.*;

import java.util.*;

public class State {
    final Action actions;
    final PlayerView playerView;
    final Random rnd;
    final List<Entity> myEntities;
    final int populationUsed;
    final int populationTotal;
    final GlobalStrategy globalStrategy;

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

    boolean insideRect(final Position bottomLeft, final Position topRight, final Position pos) {
        return pos.getX() >= bottomLeft.getX() && pos.getX() <= topRight.getX() && pos.getY() >= bottomLeft.getY() && pos.getY() <= topRight.getY();
    }

    boolean isNearby(final Entity building, final Entity unit) {
        Position bottomLeft = building.getPosition().shift(-1, -1);
        int buildingSize = getEntityProperties(building).getSize();
        Position topRight = building.getPosition().shift(buildingSize, buildingSize);
        return insideRect(bottomLeft, topRight, unit.getPosition());
    }

    State(PlayerView playerView) {
        this.actions = new Action(new HashMap<>());
        this.playerView = playerView;
        this.myEntities = new ArrayList<>();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null) {
                continue;
            }
            if (entity.getPlayerId() != playerView.getMyId()) {
                continue;
            }
            myEntities.add(entity);
        }
        this.rnd = new Random(123);
        this.populationUsed = countUsedPopulation();
        this.populationTotal = countTotalPopulation();
        this.globalStrategy = new GlobalStrategy(this);
        System.err.println("CURRENT TICK: " + playerView.getCurrentTick() + ", population: " + populationUsed + "/" + populationTotal);
    }

    private void checkCanBuild(EntityType who, EntityType what) {
        EntityProperties properties = playerView.getEntityProperties().get(who);
        EntityType[] canBuild = properties.getCanBuild();
        for (EntityType can : canBuild) {
            if (can == what) {
                return;
            }
        }
        throw new AssertionError(who + " can't build " + what);
    }

    private EntityProperties getEntityTypeProperties(EntityType type) {
        return playerView.getEntityProperties().get(type);
    }

    private EntityProperties getEntityProperties(Entity entity) {
        return getEntityTypeProperties(entity.getEntityType());
    }

    private List<Position> getPositionsOnRectBorder(Position bottomLeft, Position topRight) {
        List<Position> positions = new ArrayList<>();
        for (int x = bottomLeft.getX(); x <= topRight.getX(); x++) {
            for (int y = bottomLeft.getY(); y <= topRight.getY(); y++) {
                if (x == bottomLeft.getX() || y == bottomLeft.getY() || x == topRight.getX() || y == topRight.getY()) {
                    positions.add(new Position(x, y));
                }
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

    public void buildSomething(final Entity who, final EntityType what, final Position where) {
        System.err.println(who + " tries to build " + what + " at " + where);
        checkCanBuild(who.getEntityType(), what);
        if (!isEnoughResourcesToBuild(what)) {
            throw new AssertionError("Not enough money to build :(");
        }
        actions.getEntityActions().put(who.getId(), new EntityAction(
                null,
                new BuildAction(what, where),
                null,
                null
        ));
    }

    public void attackSomebody(final Entity who) {
        EntityProperties properties = playerView.getEntityProperties().get(who.getEntityType());
        actions.getEntityActions().put(who.getId(), new EntityAction(
                null,
                null,
                new AttackAction(null, new AutoAttack(properties.getSightRange(), new EntityType[]{})),
                null
        ));
    }
}

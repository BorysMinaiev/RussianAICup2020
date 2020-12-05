import model.*;

import java.util.*;

public class State {
    final Action actions;
    final PlayerView playerView;
    final Random rnd;

    State(PlayerView playerView) {
        this.actions = new Action(new HashMap<>());
        this.playerView = playerView;
        int populationProvide = playerView.getEntityProperties().get(EntityType.BUILDER_UNIT).getPopulationProvide();
//        System.err.println("can build: " + populationProvide);
        this.rnd = new Random(123);
        System.err.println("CURRENT TICK: " + playerView.getCurrentTick());
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

    private List<Vec2Int> getPositionsOnRectBorder(Vec2Int bottomLeft, Vec2Int topRight) {
        List<Vec2Int> positions = new ArrayList<>();
        for (int x = bottomLeft.getX(); x <= topRight.getX(); x++) {
            for (int y = bottomLeft.getY(); y <= topRight.getY(); y++) {
                if (x == bottomLeft.getX() || y == bottomLeft.getY() || x == topRight.getX() || y == topRight.getY()) {
                    positions.add(new Vec2Int(x, y));
                }
            }
        }
        Collections.shuffle(positions, rnd);
        return positions;
    }

    private boolean isSegIntersect(int from1, int to1, int from2, int to2) {
        return Math.max(from1, from2) <= Math.min(to1, to2);
    }

    private boolean willIntersect(final Entity entity, final Vec2Int pos, final EntityType type) {
        int x = entity.getPosition().getX(), y = entity.getPosition().getY();
        int entitySize = getEntityProperties(entity).getSize();
        int newObjSize = getEntityTypeProperties(type).getSize();
        return isSegIntersect(x, x + entitySize - 1, pos.getX(), pos.getX() + newObjSize - 1) &&
                isSegIntersect(y, y + entitySize - 1, pos.getY(), pos.getY() + newObjSize - 1);
    }

    private boolean canBuild(final Vec2Int pos, final EntityType type) {
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

    public List<Vec2Int> findPossiblePositionToBuild(final Entity who, final EntityType what) {
        int whoSize = getEntityTypeProperties(who.getEntityType()).getSize();
        int whatSize = getEntityTypeProperties(what).getSize();
        Vec2Int bottomLeft = who.getPosition().shift(-whatSize, -whatSize);
        Vec2Int topRight = who.getPosition().shift(whoSize, whoSize);
        List<Vec2Int> possiblePositions = getPositionsOnRectBorder(bottomLeft, topRight);
        List<Vec2Int> positionsWhereCanBuild = new ArrayList<>();
        for (Vec2Int pos : possiblePositions) {
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

    public void buildSomething(final Entity who, final EntityType what, final Vec2Int where) {
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
}

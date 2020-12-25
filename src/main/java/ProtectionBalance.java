import model.Entity;
import model.EntityType;
import model.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProtectionBalance {
    final State state;
    final MapHelper mapHelper;
    // num builders - num rangers
    final int[][] balance;
    final List<TopBalance> topBalances;

    private void changeBalance(int[][] balance, Entity entity, int balanceDiff, int sightRange) {
        final Position bottomLeft = entity.getPosition();
        final int entitySize = state.getEntityProperties(entity).getSize();
        final Position topRight = bottomLeft.shift(entitySize - 1, entitySize - 1);
        for (int x = bottomLeft.getX() - sightRange; x <= topRight.getX() + sightRange; x++) {
            for (int y = bottomLeft.getY() - sightRange; y <= topRight.getY() + sightRange; y++) {
                if (!mapHelper.insideMap(x, y)) {
                    continue;
                }
                if (CellsUtils.distBetweenEntityAndPos(state, entity, x, y) <= sightRange) {
                    balance[x][y] += balanceDiff;
                }
            }
        }
    }

    private int[][] computebalance() {
        final int mapSize = state.playerView.getMapSize();
        int[][] balance = new int[mapSize][mapSize];
        final int BUILDER_RANGE = 5;
        final int RANGED_RANGE = 10;
        for (Entity builder : state.myEntitiesByType.get(EntityType.BUILDER_UNIT)) {
            changeBalance(balance, builder, +1, BUILDER_RANGE);
        }
        for (Entity rangedUnit : state.myEntitiesByType.get(EntityType.RANGED_UNIT)) {
            changeBalance(balance, rangedUnit, -2, RANGED_RANGE);
        }
        return balance;
    }

    static class TopBalance implements Comparable<TopBalance> {
        int x, y, balance;

        public TopBalance(int x, int y, int balance) {
            this.x = x;
            this.y = y;
            this.balance = balance;
        }

        @Override
        public int compareTo(TopBalance o) {
            return -Integer.compare(balance, o.balance);
        }

        int distTo(TopBalance another) {
            return Math.abs(another.x - x) + Math.abs(another.y - y);
        }
    }

    final int CLOSE_ENOUGH = 20;

    private List<TopBalance> computeTopBalance() {
        List<TopBalance> all = new ArrayList<>(balance.length * balance.length);
        for (int x = 0; x < balance.length; x++) {
            for (int y = 0; y < balance[x].length; y++) {
                if (balance[x][y] > 0) {
                    all.add(new TopBalance(x, y, balance[x][y]));
                }
            }
        }
        Collections.sort(all);
        List<TopBalance> notClosePoints = new ArrayList<>();
        for (TopBalance balance : all) {
            boolean existClose = false;
            for (TopBalance chose : notClosePoints) {
                if (chose.distTo(balance) <= CLOSE_ENOUGH) {
                    existClose = true;
                    break;
                }
            }
            if (!existClose) {
                notClosePoints.add(balance);
            }
        }
        return notClosePoints;
    }


    ProtectionBalance(MapHelper mapHelper) {
        this.mapHelper = mapHelper;
        this.state = mapHelper.state;
        this.balance = computebalance();
        this.topBalances = computeTopBalance();
    }
}

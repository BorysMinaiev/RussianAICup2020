import model.Entity;
import model.EntityType;
import model.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PositionsPicker {
    static Position pickRandomPosition(List<Position> positions) {
        // TODO: smart things?
        if (positions.isEmpty()) {
            return null;
        }
        return positions.get(0);
    }

    static public Position getTarget(final State state, final Entity building, EntityType whatToBuild) {
        List<Entity> possibleTargets;
        if (whatToBuild == EntityType.BUILDER_UNIT) {
            possibleTargets = state.allResources;
        } else {
            possibleTargets = state.allEnemiesWarUnits;
        }
        int bestDist = Integer.MAX_VALUE;
        Position bestTarget = null;
        for (Entity target : possibleTargets) {
            int dist = CellsUtils.distBetweenTwoEntities(state, target, building);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = target.getPosition();
            }
        }
        return bestTarget;
    }

    static class PositionWithScore implements Comparable<PositionWithScore> {
        final Position pos;
        // bigger - better!
        final double score;

        public PositionWithScore(Position pos, double score) {
            this.pos = pos;
            this.score = score;
        }

        @Override
        public int compareTo(PositionWithScore o) {
            return -Double.compare(score, o.score);
        }
    }

    static double calcPositionScore(final Position position, final EntityType whatToBuild, final Position target) {
        int dist = position.distTo(target);
        if (whatToBuild == EntityType.RANGED_UNIT) {
            if (4 <= dist && dist <= 5) {
                return 100 + dist;
            }
            if (dist <= 3) {
                return dist;
            }
            return 50 - dist;
        }
        return -dist;
    }

    static Position pickPositionToBuildUnit(final State state, final Entity who, final EntityType what) {
        List<Position> unitPositions = state.findPossiblePositionToBuild(who, what);
        if (unitPositions.isEmpty()) {
            return null;
        }
        Position target = getTarget(state, who, what);

        MapHelper.BfsQueue bfsQueue = null;
        if (what == EntityType.BUILDER_UNIT) {
            if (!state.decidedWhatToWithBuilders) {
                throw new AssertionError("Wrong order of operations");
            }
            bfsQueue = state.map.findPathsToResources();
        }
        if (target != null) {
            List<PositionWithScore> options = new ArrayList<>();
            for (Position pos : unitPositions) {
                double score = bfsQueue == null ? calcPositionScore(pos, what, target) : -bfsQueue.getDist(pos.getX(), pos.getY());
                options.add(new PositionWithScore(pos, score));
            }
            Collections.sort(options);
            int cntSameScore = 1;
            while (cntSameScore != options.size() && options.get(cntSameScore).compareTo(options.get(0)) == 0) {
                cntSameScore++;
            }
            int useOption = state.rnd.nextInt(cntSameScore);
            return options.get(useOption).pos;
        }
        return pickRandomPosition(unitPositions);
    }
}

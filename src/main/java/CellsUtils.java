import model.Entity;
import model.EntityType;
import model.Position;

import java.util.ArrayList;
import java.util.List;

public class CellsUtils {
    public static boolean isOnRectBorder(Position bottomLeft, Position topRight, int x, int y) {
        return x == bottomLeft.getX() || x == topRight.getX() || y == bottomLeft.getY() || y == topRight.getY();
    }

    private static int distOneCoord(int startFirst, int endFirst, int startSecond, int endSecond) {
        if (endFirst <= startSecond) {
            return startSecond - endFirst;
        }
        if (endSecond <= startFirst) {
            return startFirst - endSecond;
        }
        return 0;
    }

    public static int distBetweenTwoEntities(final State state, final Entity first, final Entity second) {
        final int firstSize = state.getEntityProperties(first).getSize();
        final int secondSize = state.getEntityProperties(second).getSize();
        final Position firstPos = first.getPosition();
        final Position secondPos = second.getPosition();
        return distOneCoord(firstPos.getX(), firstPos.getX() + firstSize - 1, secondPos.getX(), secondPos.getX() + secondSize - 1) +
                distOneCoord(firstPos.getY(), firstPos.getY() + firstSize - 1, secondPos.getY(), secondPos.getY() + secondSize - 1);
    }

    public static int distBetweenEntityAndPos(final State state, final Entity first, int sx, int sy) {
        final int firstSize = state.getEntityProperties(first).getSize();
        final Position firstPos = first.getPosition();
        return distOneCoord(firstPos.getX(), firstPos.getX() + firstSize - 1, sx, sx) +
                distOneCoord(firstPos.getY(), firstPos.getY() + firstSize - 1, sy, sy);
    }

    public static int distBetweenEntityTypeAndPos(final State state, final Position firstPos, final EntityType firstType, int sx, int sy) {
        final int firstSize = state.getEntityTypeProperties(firstType).getSize();
        return distOneCoord(firstPos.getX(), firstPos.getX() + firstSize - 1, sx, sx) +
                distOneCoord(firstPos.getY(), firstPos.getY() + firstSize - 1, sy, sy);
    }

    public static List<Position> getPositionsOnRectBorderCCW(final Position bottomLeft, final Position topRight) {
        List<Position> positions = new ArrayList<>();
        final int width = topRight.getX() - bottomLeft.getX();
        final int height = topRight.getY() - bottomLeft.getY();
        for (int dx = 0; dx < width; dx++) {
            positions.add(bottomLeft.shift(dx, 0));
        }
        for (int dy = height; dy > 0; dy--) {
            positions.add(topRight.shift(0, -dy));
        }
        for (int dx = 0; dx < width; dx++) {
            positions.add(topRight.shift(-dx, 0));
        }
        for (int dy = height; dy > 0; dy--) {
            positions.add(bottomLeft.shift(0, dy));
        }
        return positions;
    }

    private static boolean isInside(int value, int from, int to) {
        return value >= Math.min(from, to) && value <= Math.max(from, to);
    }

    public static boolean isBetween(Position what, Position from, Position to) {
        return isInside(what.getX(), from.getX(), to.getX()) && isInside(what.getY(), from.getY(), to.getY());
    }
}

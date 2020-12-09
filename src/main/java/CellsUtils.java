import model.Entity;
import model.Position;

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
}

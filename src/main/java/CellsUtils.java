import model.Position;

public class CellsUtils {
    public static boolean isOnRectBorder(Position bottomLeft, Position topRight, int x, int y) {
        return x == bottomLeft.getX() || x == topRight.getX() || y == bottomLeft.getY() || y == topRight.getY();
    }
}

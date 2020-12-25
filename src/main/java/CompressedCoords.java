import model.Position;

public class CompressedCoords {
    private final static int MAP_SIZE = 80;

    public static int compress(int x, int y) {
        return x + y * MAP_SIZE;
    }

    public static int extractX(int coords) {
        return coords % MAP_SIZE;
    }

    public static int extractY(int coords) {
        return coords / MAP_SIZE;
    }

    public static int compress(Position pos) {
        return compress(pos.getX(), pos.getY());
    }
}

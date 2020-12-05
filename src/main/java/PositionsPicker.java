import model.Entity;
import model.EntityType;
import model.Position;

import java.util.List;

public class PositionsPicker {
    static Position pickPosition(List<Position> positions) {
        // TODO: smart things?
        if (positions.isEmpty()) {
            return null;
        }
        return positions.get(0);
    }

    static Position pickPositionToBuild(final State state, final Entity who, final EntityType what) {
        List<Position> housePositions = state.findPossiblePositionToBuild(who, what);
        System.err.println("want to build: " + what + ", ways to put: " + housePositions.size());
        return pickPosition(housePositions);
    }
}

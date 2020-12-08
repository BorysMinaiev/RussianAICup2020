import model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Debug {
    public static final boolean ATTACKED_BY = false;
    public static final boolean ENTITIES_STAT = true;
    public static final boolean PRINT_CURRENT_BUILD_TARGET = true;
    public static final boolean PRINT_BUILD_ACTIONS = true;

    private static void printAttackedBy(final State state, final DebugInterface debugInterface) {
        if (!Debug.ATTACKED_BY) {
            return;
        }
        for (Map.Entry<Position, Double> attacked : state.attackedByPos.entrySet()) {
            Position pos = attacked.getKey();
            ColoredVertex v1 = new ColoredVertex(pos.getX(), pos.getY(), Color.RED);
            ColoredVertex v2 = new ColoredVertex(pos.getX() + 1, pos.getY(), Color.RED);
            ColoredVertex v3 = new ColoredVertex(pos.getX() + 1, pos.getY() + 1, Color.RED);
            ColoredVertex v4 = new ColoredVertex(pos.getX(), pos.getY() + 1, Color.RED);
            ColoredVertex[] vertices = new ColoredVertex[]{v1, v3, v2, v4};
            debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));
        }
    }

    private static void printDebugText(final State state, DebugInterface debugInterface, String text, Color color) {
        ColoredVertex location = new ColoredVertex(new Vec2Float(-12.0f, 1.2f * state.debugPos), color);
        state.debugPos++;
        debugInterface.send(new DebugCommand.Add(new DebugData.PlacedText(location, text, 0.0f, 22.0f)));
    }

    private static void printDebugTexts(final State state, DebugInterface debugInterface, String[] texts, Color color) {
        for (int it = texts.length - 1; it >= 0; it--) {
            ColoredVertex location = new ColoredVertex(new Vec2Float(-12.0f, 1.2f * state.debugPos), color);
            debugInterface.send(new DebugCommand.Add(new DebugData.PlacedText(location, texts[it], 0.0f, 22.0f)));
            state.debugPos++;
        }
    }

    private static void printEntitiesStat(final State state, DebugInterface debugInterface) {
        if (!Debug.ENTITIES_STAT) {
            return;
        }
        for (Map.Entry<EntityType, Integer> entry : state.myEntitiesCount.entrySet()) {
            final String text = entry.getKey() + ": " + entry.getValue();
            printDebugText(state, debugInterface, text, Color.WHITE);
        }
        state.debugPos++;
    }

    private static void printCurrentBuildTarget(final State state, DebugInterface debugInterface) {
        if (!Debug.PRINT_CURRENT_BUILD_TARGET) {
            return;
        }
        EntityType currentBuildTarget = state.globalStrategy.whatNextToBuild();
        printDebugText(state, debugInterface, "want build: " + currentBuildTarget, Color.YELLOW);
        state.debugPos++;
    }

    private static List<EntityType> getAllBuildActions(final State state) {
        List<EntityType> buildActions = new ArrayList<>();
        for (Map.Entry<Integer, EntityAction> entry : state.actions.getEntityActions().entrySet()) {
            EntityAction action = entry.getValue();
            BuildAction buildAction = action.getBuildAction();
            if (buildAction == null) {
                continue;
            }
            buildActions.add(buildAction.getEntityType());
        }
        return buildActions;
    }

    private static void printBuildActions(final State state, DebugInterface debugInterface) {
        if (!Debug.PRINT_BUILD_ACTIONS) {
            return;
        }
        List<EntityType> buildActions = getAllBuildActions(state);
        if (buildActions.isEmpty()) {
            return;
        }
        String[] texts = new String[buildActions.size() + 1];
        texts[0] = "build actions:";
        for (int i = 0; i < buildActions.size(); i++) {
            texts[i + 1] = buildActions.get(i).toString();
        }
        printDebugTexts(state, debugInterface, texts, Color.RED);
        state.debugPos++;
    }


    public static void printSomeDebug(final DebugInterface debugInterface, final State state) {
        if (debugInterface == null) {
            return;
        }

        printAttackedBy(state, debugInterface);
        printEntitiesStat(state, debugInterface);
        printCurrentBuildTarget(state, debugInterface);
        printBuildActions(state, debugInterface);
    }
}

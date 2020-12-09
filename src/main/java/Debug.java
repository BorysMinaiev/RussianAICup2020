import model.*;

import java.util.ArrayList;
import java.util.HashMap;
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

    private static class TextLine {
        final Color color;
        final String text;

        public TextLine(Color color, String text) {
            this.color = color;
            this.text = text;
        }
    }

    static void printTextBlock(final DebugInterface debugInterface, final List<TextLine> lines, final Vec2Float offset) {
        for (int i = 0; i < lines.size(); i++) {
            final String text = lines.get(i).text;
            ColoredVertex location = new ColoredVertex(new Vec2Float(0.0f, -1.2f * i).shift(offset.getX(), offset.getY()), lines.get(i).color);
            debugInterface.send(new DebugCommand.Add(new DebugData.PlacedText(location, text, 0.0f, 22.0f)));
        }
    }

    private static void printEntitiesStat(final State state, DebugInterface debugInterface) {
        if (!Debug.ENTITIES_STAT) {
            return;
        }

        Map<Integer, Vec2Float> offsetsByPlayer = new HashMap<>();
        Map<Integer, Color> colorByPlayer = new HashMap<>();
        float xOffset = -10f;
        float yOffset = 15f;
        final int mapSize = state.playerView.getMapSize();
        /*
         *  |4 2|
         *  |1 3|
         */
        offsetsByPlayer.put(1, new Vec2Float(xOffset, yOffset));
        colorByPlayer.put(1, Color.BLUE);
        offsetsByPlayer.put(2, new Vec2Float(mapSize + 1f, mapSize - 1f));
        colorByPlayer.put(2, Color.GREEN);
        offsetsByPlayer.put(3, new Vec2Float(mapSize + 1f, yOffset));
        colorByPlayer.put(3, Color.RED);
        offsetsByPlayer.put(4, new Vec2Float(xOffset, mapSize - 1));
        colorByPlayer.put(4, Color.ORANGE);
        // TODO: for all players?
        for (Map.Entry<Integer, Map<EntityType, Integer>> playerMap : state.entitiesByPlayer.entrySet()) {
            int playerId = playerMap.getKey();
            List<TextLine> lines = new ArrayList<>();
            for (Map.Entry<EntityType, Integer> entry : playerMap.getValue().entrySet()) {
                final String text = entry.getKey() + ": " + entry.getValue();
                lines.add(new TextLine(colorByPlayer.get(playerId), text));
            }
            Vec2Float offset = offsetsByPlayer.get(playerId);
            printTextBlock(debugInterface, lines, offset);
        }
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

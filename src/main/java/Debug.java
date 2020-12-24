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
    public static final boolean PRINT_MOVES_PICKER = false;

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

    static void printTextBlock(final DebugInterface debugInterface, final List<TextLine> lines, final Vec2Float offset, final State state) {
        final int playerId = state.playerView.getMyId();
        for (int i = 0; i < lines.size(); i++) {
            final String text = lines.get(i).text;
            ColoredVertex location = ColoredVertex.fromScreen(new Vec2Float(0.0f, -1.2f * i).shift(offset.getX(), offset.getY()), lines.get(i).color, playerId);
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
            printTextBlock(debugInterface, lines, offset, state);
        }
    }

    private static void printCurrentBuildTarget(final State state, DebugInterface debugInterface) {
        if (!Debug.PRINT_CURRENT_BUILD_TARGET) {
            return;
        }
        WantToBuild currentBuildTarget = state.globalStrategy.whatNextToBuild();
        printDebugText(state, debugInterface, "want build: " + currentBuildTarget, Color.YELLOW);
        state.debugPos++;
    }

    private static List<EntityType> getAllBuildActions(final State state) {
        List<EntityType> buildActions = new ArrayList<>();
        // TODO: fix this
//        for (Map.Entry<Integer, EntityAction> entry : state.actions.getEntityActions().entrySet()) {
//            EntityAction action = entry.getValue();
//            BuildAction buildAction = action.getBuildAction();
//            if (buildAction == null) {
//                continue;
//            }
//            buildActions.add(buildAction.getEntityType());
//        }
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

    private static void printRangedHealth(final State state, DebugInterface debugInterface) {
        int cnt5 = 0, cnt69 = 0, cnt10 = 0;
        for (Entity myEntity : state.myEntities) {
            if (myEntity.getEntityType() == EntityType.RANGED_UNIT) {
                int health = myEntity.getHealth();
                if (health == 5) {
                    cnt5++;
                } else if (health >= 6 && health <= 9) {
                    cnt69++;
                } else if (health == 10) {
                    cnt10++;
                }
            }
        }
        printDebugText(state, debugInterface, "Health: " + cnt5 + "/" + cnt69 + "/" + cnt10, Color.GREEN);
    }

    private static void fillCell(List<Vec2Float> trianglePoints, int x, int y) {
        trianglePoints.add(new Vec2Float(x, y));
        trianglePoints.add(new Vec2Float(x + 1, y));
        trianglePoints.add(new Vec2Float(x, y + 1));

        trianglePoints.add(new Vec2Float(x + 1, y + 1));
        trianglePoints.add(new Vec2Float(x + 1, y));
        trianglePoints.add(new Vec2Float(x, y + 1));
    }

    private static ColoredVertex[] convertVerticesToList(final List<Vec2Float> points, final Color color) {
        ColoredVertex[] vertices = new ColoredVertex[points.size()];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new ColoredVertex(points.get(i), color);
        }
        return vertices;
    }

    private static void showUnderAttackMap(final State state, final DebugInterface debugInterface) {
        final MapHelper map = state.map;
        final int mapSize = map.underAttack.length;
        List<Vec2Float> trianglePoints = new ArrayList<>();
        for (int x = 0; x < mapSize; x++) {
            for (int y = 0; y < mapSize; y++) {
                if (map.underAttack[x][y] == MapHelper.UNDER_ATTACK.SAFE) {
                    continue;
                }
                fillCell(trianglePoints, x, y);
            }
        }
        ColoredVertex[] vertices = convertVerticesToList(trianglePoints, Color.TRANSPARENT_RED);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.TRIANGLES)));
    }

    private static void showSafePlacesToMine(final State state, final DebugInterface debugInterface) {
        final MapHelper map = state.map;
        List<Vec2Float> points = new ArrayList<>();
        for (Position pos : map.safePositionsToMine) {
            addCellBorderToPoint(pos, points);
        }

        ColoredVertex[] vertices = convertVerticesToList(points, Color.YELLOW);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));

        final String dbgMessage = "Safe pos to mine: " + map.safePositionsToMine.size() + "/" + state.myEntitiesByType.get(EntityType.BUILDER_UNIT).size();
        printDebugText(state, debugInterface, dbgMessage, Color.GREEN);
    }

    private static void showNeedsProtection(final State state, final DebugInterface debugInterface) {
        List<Vec2Float> points = new ArrayList<>();
        for (NeedProtection.ToPretect toProtect : state.needProtection.toProtect) {
            final Entity entity = toProtect.entity;
            final Position pos = entity.getPosition();
            final int size = state.getEntityProperties(entity).getSize();
            addRectBorderToPoint(pos, pos.shift(size, size), points);

        }
        for (Entity enemy : state.needProtection.enemiesToAttack) {
            addCellBorderToPoint(enemy.getPosition(), points);
        }

        ColoredVertex[] vertices = convertVerticesToList(points, Color.ORANGE);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));
    }

    private static void showTargets(final State state, final DebugInterface debugInterface) {
        final List<Vec2Float> linePoints = new ArrayList<>();
        for (Map.Entry<Position, Position> entry : state.debugTargets.entrySet()) {
            Position from = entry.getKey(), to = entry.getValue();
            linePoints.add(new Vec2Float(from.getX() + 0.5f, from.getY() + 0.5f));
            linePoints.add(new Vec2Float(to.getX() + 0.5f, to.getY() + 0.5f));
        }
        ColoredVertex[] vertices = convertVerticesToList(linePoints, Color.TRANSPARENT_BLUE);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));
    }

    private static void addRectBorderToPoint(final Position bottomLeft, final Position topRight, final List<Vec2Float> points) {
        final int x1 = bottomLeft.getX();
        final int y1 = bottomLeft.getY();
        final int x2 = topRight.getX();
        final int y2 = topRight.getY();
        points.add(new Vec2Float(x1, y1));
        points.add(new Vec2Float(x1, y2));
        points.add(new Vec2Float(x1, y2));
        points.add(new Vec2Float(x2, y2));
        points.add(new Vec2Float(x2, y2));
        points.add(new Vec2Float(x2, y1));
        points.add(new Vec2Float(x2, y1));
        points.add(new Vec2Float(x1, y1));
    }

    private static void addCellBorderToPoint(final Position pos, final List<Vec2Float> points) {
        addRectBorderToPoint(pos, pos.shift(1, 1), points);
    }

    private static void showBadUnits(final State state, final DebugInterface debugInterface) {
        List<Vec2Float> points = new ArrayList<>();
        for (Position pos : state.debugUnitsInBadPostion) {
            addCellBorderToPoint(pos, points);
        }
        ColoredVertex[] vertices = convertVerticesToList(points, Color.RED);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));
    }

    private static void drawArrow(final List<Vec2Float> points, Vec2Float from, Vec2Float to) {
        points.add(from);
        points.add(to);
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        dist *= 5;
        dx /= dist;
        dy /= dist;
        Vec2Float O = to.shift(-dx, -dy);
        Vec2Float A = O.shift(dy / 2, -dx / 2);
        Vec2Float B = O.shift(-dy / 2, dx / 2);
        points.add(A);
        points.add(to);
        points.add(B);
        points.add(to);
    }

    private static void showActions(final State state, final DebugInterface debugInterface, Action action) {
        if (action == null) {
            return;
        }
        if (!PRINT_MOVES_PICKER) {
            return;
        }
        final List<Vec2Float> linePoints = new ArrayList<>();
        for (Map.Entry<Integer, EntityAction> entry : action.getEntityActions().entrySet()) {
            Entity entity = state.getEntityById(entry.getKey());
            EntityAction entityAction = entry.getValue();
            Position targetPos = null;
            MoveAction moveAction = entityAction.getMoveAction();
            if (moveAction != null) {
                targetPos = moveAction.getTarget();
            }
            AttackAction attackAction = entityAction.getAttackAction();
            if (attackAction != null) {
                Integer targetId = attackAction.getTarget();
                if (targetId != null) {
                    targetPos = state.getEntityById(targetId).getPosition();
                }
            }
            if (targetPos != null) {
                Position from = entity.getPosition();
                drawArrow(linePoints, new Vec2Float(from.getX() + 0.5f, from.getY() + 0.5f),
                        new Vec2Float(targetPos.getX() + 0.5f, targetPos.getY() + 0.5f));
            }
        }
        ColoredVertex[] vertices = convertVerticesToList(linePoints, Color.PURPLE);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));
    }

    private static void showMovesPicker(final State state, final DebugInterface debugInterface) {
        if (!PRINT_MOVES_PICKER) {
            return;
        }
        for (Map.Entry<Entity, MovesPicker.Move> entry : state.movesPicker.movesByEntity.entrySet()) {
            final Position pos = entry.getKey().getPosition();
            int movePriority = entry.getValue().priority;
            ColoredVertex location = new ColoredVertex(new Vec2Float(pos.getX() + 0.2f, pos.getY() + 0.6f), Color.RED);
            debugInterface.send(new DebugCommand.Add(new DebugData.PlacedText(location, "" + movePriority, 0.0f, 22.0f)));
        }
        for (Map.Entry<Entity, List<MovesPicker.Move>> entry : state.movesPicker.possibilities.entrySet()) {
            final Position pos = entry.getKey().getPosition();
            for (MovesPicker.Move move : entry.getValue()) {
                int movePriority = move.priority;
                float alpha = 0.7f;
                float x = 0.5f + pos.getX() * alpha + move.targetPos.getX() * (1 - alpha);
                float y = 0.5f + pos.getY() * alpha + move.targetPos.getY() * (1 - alpha);
                ColoredVertex location = new ColoredVertex(new Vec2Float(x, y), Color.GREEN);
                debugInterface.send(new DebugCommand.Add(new DebugData.PlacedText(location, movePriority + "/" + move.priorityRelativeToTargetCell, 0.5f, 22.0f)));
            }
        }
    }

    private static Vec2Float toV2float(Position pos) {
        return new Vec2Float(pos.getX(), pos.getY());
    }

    // TODO: remove it?
    private static void highlightSpecialAgent(List<Vec2Float> points, Position pos) {
        float x = pos.getX(), y = pos.getY();
        points.add(new Vec2Float(x - 0.5f, y + 0.5f));
        points.add(new Vec2Float(x + 0.5f, y + 1.5f));
        points.add(new Vec2Float(x + 1.5f, y + 0.5f));
        points.add(new Vec2Float(x + 0.5f, y - 0.5f));
    }

    private static void showSpecialAgents(final State state, final DebugInterface debugInterface) {
        List<Vec2Float> linePoints = new ArrayList<>();
        for (Entity entity : state.myEntities) {
            SpecialAgents.Profile profile = SpecialAgents.getSpecialAgentProfile(state, entity);
            if (profile == null) {
                continue;
            }
            final Position targetPos = profile.currentTarget;
            drawArrow(linePoints, toV2float(entity.getPosition()), toV2float(targetPos));
//            highlightSpecialAgent(linePoints, entity.getPosition());
        }

        ColoredVertex[] vertices = convertVerticesToList(linePoints, Color.MAGENTA);
        debugInterface.send(new DebugCommand.Add(new DebugData.Primitives(vertices, PrimitiveType.LINES)));
    }

    public static void printSomeDebug(final DebugInterface debugInterface, final State state, boolean isBetweenTicks, Action action) {
        if (debugInterface == null) {
            return;
        }
        debugInterface.send(new DebugCommand.SetAutoFlush(false));
        debugInterface.send(new DebugCommand.Clear());

        printAttackedBy(state, debugInterface);
        printEntitiesStat(state, debugInterface);
        printCurrentBuildTarget(state, debugInterface);
        printTotalResourcesLeft(state, debugInterface);
        printRangedHealth(state, debugInterface);
        if (isBetweenTicks) {
            showUnderAttackMap(state, debugInterface);
            showSafePlacesToMine(state, debugInterface);
            showNeedsProtection(state, debugInterface);
        } else {
            showMovesPicker(state, debugInterface);
        }
        printBuildActions(state, debugInterface);
        showTargets(state, debugInterface);
        showBadUnits(state, debugInterface);
        showActions(state, debugInterface, action);
        showSpecialAgents(state, debugInterface);

        debugInterface.send(new DebugCommand.Flush());
    }

    private static void printTotalResourcesLeft(State state, DebugInterface debugInterface) {
        printDebugText(state, debugInterface, "RESOURCES TOTAL: " + state.totalResources, Color.ORANGE);
    }
}

import model.*;

import java.util.*;

public class MovesPicker {
    public final static int PRIORITY_MINE_RESOURCES = 10;
    public final static int PRIORITY_NOTHING = 0;
    public final static int PRIORITY_GO_TO_PROTECT = 200;
    public final static int PRIORITY_BUILDER_BLOCKED_GO_AWAY = 15;
    public final static int PRIORITY_GO_FOR_ATTACK = 11;
    public final static int PRIORITY_GO_FOR_ATTACK_THROUGH_BUILDERS = 15;
    public final static int PRIORITY_BUILD = 30;
    public final static int PRIORITY_REPAIR = 35;
    public final static int PRIORITY_ATTACK = 100;
    public final static int PRIORITY_ATTACK_FOOD = 99;
    public final static int PRIORITY_SMALL = 1;
    public final static int PRIORITY_MAX = 1000;
    public final static int PRIORITY_GO_AWAY_FROM_ATTACK = 14;
    public final static int PRIORITY_GO_TO_REPAIR = 40;
    public final static int PRIORITY_GO_TO_BUILD = 5;
    public final static int PRIORITY_GO_TO_MINE = 5;
    public static final int PRIORITY_GO_TO_UNBLOCK = 40;


    public Move getCurrentBestAction(Entity entity) {
        List<Move> moves = possibilities.get(entity);
        Move best = moves.get(0);
        for (Move move : moves) {
            if (move.priority > best.priority) {
                best = move;
            }
        }
        return best;
    }

    public List<MoveAction> getMoveActions(Entity entity) {
        List<Move> moves = possibilities.get(entity);
        List<MoveAction> actions = new ArrayList<>();
        for (Move move : moves) {
            if (move.action.getMoveAction() != null) {
                actions.add(move.action.getMoveAction());
            }
        }
        return actions;
    }

    public List<AttackAction> getAttackActions(Entity entity) {
        List<Move> moves = possibilities.get(entity);
        List<AttackAction> actions = new ArrayList<>();
        for (Move move : moves) {
            if (move.action.getAttackAction() != null) {
                actions.add(move.action.getAttackAction());
            }
        }
        return actions;
    }

    public boolean hasGoodAction(Entity entity) {
        return getCurrentBestAction(entity).priority > PRIORITY_NOTHING;
    }

    public void doNothing(Entity entity) {
        List<Move> moves = possibilities.get(entity);
        List<Move> filteredMoves = new ArrayList<>();
        for (Move move : moves) {
            if (move.priority == PRIORITY_NOTHING) {
                filteredMoves.add(move);
            }
        }
        possibilities.put(entity, filteredMoves);
    }

    public void addMoveAction(Entity unit, Position where, int priority) {
        EntityAction action = EntityAction.createMoveAction(where, true, true);
        add(unit, new Move(unit, where, action, priority));
    }

    public void addAutoMoveAction(Entity unit, Position where) {
        EntityAction action = EntityAction.createMoveAction(where, true, true);
        add(unit, new Move(unit, unit.getPosition(), action, PRIORITY_GO_FOR_ATTACK));
    }

    static class Move implements Comparable<Move> {
        final Entity who;
        final Position targetPos;
        final EntityAction action;
        final int priority;
        int priorityRelativeToTargetCell;

        public Move(Entity who, Position targetPos, EntityAction action, int priority) {
            this.who = who;
            this.targetPos = targetPos;
            this.action = action;
            this.priority = priority;
            if (priority < 0) {
                throw new AssertionError("Negative priority?");
            }
        }

        @Override
        public String toString() {
            return "Move{" +
                    "who=" + who +
                    ", targetPos=" + targetPos +
                    ", action=" + action +
                    ", priority=" + priority +
                    '}';
        }

        @Override
        public int compareTo(Move o) {
            return -Integer.compare(priority, o.priority);
        }
    }

    private void addPossibleMoves(List<Move> moves, Entity unit, State state) {
        final Position pos = unit.getPosition();
        moves.add(new Move(unit, pos, EntityAction.emptyAction, PRIORITY_NOTHING));
        if (unit.getEntityType().isBuilding()) {
            return;
        }
        for (int it = 0; it < Directions.dx.length; it++) {
            Position nextPos = pos.shift(Directions.dx[it], Directions.dy[it]);
            if (!state.insideMap(nextPos)) {
                continue;
            }
            Entity there = state.map.entitiesByPos[nextPos.getX()][nextPos.getY()];
            if (there != null) {
                if (there.getPlayerId() == null) {
                    continue;
                }
                if (state.playerView == null) {
                    throw new AssertionError();
                }
                if (there.getPlayerId() != state.playerView.getMyId()) {
                    continue;
                }
                if (there.getEntityType().isBuilding()) {
                    continue;
                }
            }
            moves.add(new Move(unit, nextPos, EntityAction.createMoveAction(nextPos, false, false), PRIORITY_NOTHING));
        }
    }

    State state;

    MovesPicker(State state) {
        this.state = state;
        possibilities = new HashMap<>();
        for (Entity entity : state.myEntities) {
            ArrayList<Move> moves = new ArrayList<>();
            moves.add(new Move(entity, entity.getPosition(), EntityAction.emptyAction, PRIORITY_NOTHING));
            if (!entity.getEntityType().isBuilding()) {
                addPossibleMoves(moves, entity, state);
            }
            possibilities.put(entity, moves);
        }
    }

    Map<Entity, List<Move>> possibilities;
    Map<Entity, Move> movesByEntity;

    class MatchingFinder {
        final Map<Position, Integer> positionIds;
        final List<Entity> myUnits;
        final List<Move> allMoves;
        final Map<Entity, Integer> posOfUnit;
        final Map<Integer, Integer> whoWasThere;
        final Move[] unitMove;
        final boolean[] usedPositions;
        final int[] seen;
        int seenIter;

        public MatchingFinder(Map<Position, Integer> positionIds, List<Entity> myUnits, List<Move> allMoves, Map<Entity, Integer> posOfUnit) {
            this.positionIds = positionIds;
            this.myUnits = myUnits;
            this.allMoves = allMoves;
            this.posOfUnit = posOfUnit;
            unitMove = new Move[myUnits.size()];
            usedPositions = new boolean[positionIds.size()];
            seen = new int[posOfUnit.size()];
            whoWasThere = new HashMap<>();
            for (Entity unit : myUnits) {
                whoWasThere.put(positionIds.get(unit.getPosition()), posOfUnit.get(unit));
            }
        }

        boolean dfs(Entity unit, boolean firstInPath) {
            int unitPos = posOfUnit.get(unit);
            if (unitMove[unitPos] != null) {
                return false;
            }
            if (firstInPath) {
                seenIter++;
            }
            if (seen[unitPos] == seenIter) {
                return false;
            }
            seen[unitPos] = seenIter;
            int curPosId = positionIds.get(unit.getPosition());
            List<Move> checkMoves = new ArrayList<>(possibilities.get(unit));
            if (firstInPath) {
                Collections.sort(checkMoves);
            } else {
                Collections.sort(checkMoves, new Comparator<Move>() {
                    @Override
                    public int compare(Move o1, Move o2) {
                        int c1 = Integer.compare(o1.priorityRelativeToTargetCell, o2.priorityRelativeToTargetCell);
                        if (c1 != 0) {
                            return -c1;
                        }
                        return o1.compareTo(o2);
                    }
                });
            }
            for (Move move : checkMoves) {
                int targetPosId = positionIds.get(move.targetPos);
                if (targetPosId == curPosId) {
                    if (firstInPath) {
                        unitMove[unitPos] = move;
                        return true;
                    } else {
                        continue;
                    }
                } else {
                    if (usedPositions[targetPosId]) {
                        Integer wasThere = whoWasThere.get(targetPosId);
                        if (wasThere == null || unitMove[wasThere] != null) {
                            continue;
                        }
                        if (dfs(myUnits.get(wasThere), false)) {
                            usedPositions[targetPosId] = true;
                            usedPositions[curPosId] = false;
                            unitMove[unitPos] = move;
                            return true;
                        }
                    } else {
                        usedPositions[targetPosId] = true;
                        usedPositions[curPosId] = false;
                        unitMove[unitPos] = move;
                        return true;
                    }
                }
            }
            return false;
        }

        private Move[] findMatching() {
            for (Map.Entry<Entity, List<Move>> entry : possibilities.entrySet()) {
                usedPositions[positionIds.get(entry.getKey().getPosition())] = true;
            }
            for (Move move : allMoves) {
                dfs(move.who, true);
            }
            return unitMove;
        }
    }

    Action buildActions() {
        Action actions = new Action(new HashMap<>());
        Map<Position, Integer> positionIds = new HashMap();
        List<Entity> myUnits = new ArrayList<>();
        Map<Entity, Integer> posOfUnit = new HashMap<>();
        for (Map.Entry<Entity, List<Move>> entry : possibilities.entrySet()) {
            posOfUnit.put(entry.getKey(), myUnits.size());
            myUnits.add(entry.getKey());
            for (Move move : entry.getValue()) {
                Integer targetPosId = positionIds.get(move.targetPos);
                if (targetPosId == null) {
                    positionIds.put(move.targetPos, positionIds.size());
                }
            }
        }
        List<Move> allMoves = new ArrayList<>();
        for (Map.Entry<Entity, List<Move>> entry : possibilities.entrySet()) {
            List<Move> moves = entry.getValue();
            Collections.sort(moves);
            for (int i = 0; i < moves.size(); i++) {
                for (int j = i + 1; j < moves.size(); j++) {
                    if (moves.get(i).targetPos.distTo(moves.get(j).targetPos) == 0) {
                        moves.remove(j);
                        j--;
                    }
                }
            }
            allMoves.addAll(moves);
        }
        int[] maxScoreForCell = new int[positionIds.size()];
        for (Map.Entry<Entity, List<Move>> entry : possibilities.entrySet()) {
            List<Move> moves = entry.getValue();
            for (Move move : moves) {
                int posId = positionIds.get(move.targetPos);
                maxScoreForCell[posId] = Math.max(maxScoreForCell[posId], move.priority);
            }
        }
        for (Move move : allMoves) {
            move.priorityRelativeToTargetCell = move.priority - maxScoreForCell[positionIds.get(move.targetPos)] / 2;
        }
        Collections.sort(allMoves);
        Move[] unitMove = new MatchingFinder(positionIds, myUnits, allMoves, posOfUnit).findMatching();
        movesByEntity = new HashMap<>();
        Set<Position> usedTargetPos = new HashSet<>();
        for (int i = 0; i < unitMove.length; i++) {
            final Entity unit = myUnits.get(i);
            if (unitMove[i] == null) {
                System.err.println("Can't find move for unit: " + unit);
            } else {
                movesByEntity.put(unit, unitMove[i]);
                Position targetPos = unitMove[i].targetPos;
                if (usedTargetPos.contains(targetPos)) {
                    throw new AssertionError("Wrong moves found");
                }
                usedTargetPos.add(targetPos);
                actions.getEntityActions().put(unit.getId(), unitMove[i].action);
            }
        }

        return actions;
    }

    boolean existBetterMove(List<Move> moves, Move nextMove) {
        for (Move move : moves) {
            if (move.targetPos.distTo(nextMove.targetPos) == 0 && move.priority >= nextMove.priority) {
                return true;
            }
        }
        return false;
    }

    private boolean add(Entity who, Move move) {
        List<Move> moves = possibilities.get(who);
        if (existBetterMove(moves, move)) {
            return false;
        }
        moves.add(move);
        return true;
    }

    void addRepairAction(Entity who, Entity what) {
        EntityAction action = new EntityAction(null, null, null, new RepairAction(what.getId()));
        add(who, new Move(who, who.getPosition(), action, PRIORITY_REPAIR));
    }

    void addBuildAction(Entity who, EntityType what, Position where, int priority) {
        EntityAction action = EntityAction.createBuildAction(what, where);
        // TODO: buildings has size > 1!
        add(who, new Move(who, where, action, Math.max(PRIORITY_SMALL, priority)));
    }

    boolean addAttackAction(Entity who, EntityAction action, int priority) {
        return add(who, new Move(who, who.getPosition(), action, priority));
    }

    void addManualAction(Entity who, EntityAction action, int priority) {
        // TODO: position is probably incorrect!
        add(who, new Move(who, who.getPosition(), action, priority));
    }
}

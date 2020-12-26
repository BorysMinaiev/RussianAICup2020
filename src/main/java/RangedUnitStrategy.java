import model.*;

import java.util.*;

public class RangedUnitStrategy {

    int getExpectedHealth(final Entity enemyUnit) {
        int expectedDamage = expectedDamageByEntityId.getOrDefault(enemyUnit.getId(), 0);
        return enemyUnit.getHealth() - expectedDamage;
    }

    final Map<Integer, Integer> expectedDamageByEntityId;
    final State state;
    boolean needMoreUnitsForSupport;

    RangedUnitStrategy(final State state) {
        this.state = state;
        expectedDamageByEntityId = new HashMap<>();
    }

    void attack(final Entity who, final Entity what) {
        state.map.updateCellCanGoThrough(who.getPosition(), MapHelper.CAN_GO_THROUGH.MY_ATTACKING_UNIT);
        final int damage = state.getEntityProperties(who).getAttack().getDamage();
        expectedDamageByEntityId.put(what.getId(), expectedDamageByEntityId.getOrDefault(what.getId(), 0) + damage);
        state.attack(who, what, MovesPicker.PRIORITY_ATTACK);
    }

    private boolean eat(final Entity unit, final Position pos) {
        if (state.attack(unit, state.map.entitiesByPos[pos.getX()][pos.getY()], MovesPicker.PRIORITY_ATTACK_FOOD)) {
            // TODO: do we need it?
            state.map.updateCellCanGoThrough(unit.getPosition(), MapHelper.CAN_GO_THROUGH.MY_EATING_FOOD_RANGED_UNIT);
            return true;
        }
        return false;
    }

    private void blocked(final Entity unit) {
        state.addDebugUnitInBadPosition(unit.getPosition());
        state.map.updateCellCanGoThrough(unit.getPosition(), MapHelper.CAN_GO_THROUGH.MY_ATTACKING_UNIT);
        state.doNothing(unit);
    }

    private static boolean isCorner(int x, int y, int mapSize) {
        if (x == 0 || x == mapSize - 1) {
            return y == 0 || y == mapSize - 1;
        }
        return false;
    }

    private boolean isPredefinedSetTarget(final Position pos) {
        final int mapSize = state.playerView.getMapSize();
        if (isCorner(pos.getX(), pos.getY(), mapSize)) {
            return true;
        }
        Position[] specialAgents = SpecialAgents.getPredefinedTargets(mapSize);
        for (Position spec : specialAgents) {
            if (spec.distTo(pos) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean goToPosition(final Entity unit,
                                 final Position goToPos,
                                 int maxDist,
                                 boolean okGoToNotGoThere,
                                 boolean okGoThroughMyBuilders,
                                 boolean okGoUnderAttack,
                                 int priority) {
        if (maxDist == Integer.MAX_VALUE) {
            if (!isPredefinedSetTarget(goToPos)) {
                state.autoMove(unit, goToPos);
                return true;
            }
        }
        final int attackRange = state.getEntityProperties(unit).getAttack().getAttackRange();
        List<Position> firstCellsInPath = state.map.findBestPathToTargetDijkstra(unit.getPosition(),
                goToPos,
                attackRange,
                maxDist,
                okGoToNotGoThere,
                okGoThroughMyBuilders,
                okGoUnderAttack,
                true);
        if (firstCellsInPath.isEmpty()) {
            return goAwayFromNotGoThere(unit, priority);
        }
        final Position curPos = unit.getPosition();
        state.addDebugTarget(curPos, goToPos);
        if (state.map.underAttack[curPos.getX()][curPos.getY()] == MapHelper.UNDER_ATTACK.UNDER_ATTACK_DO_NOT_GO_THERE) {
            boolean existGoodMove = false;
            for (Position firstCellInPath : firstCellsInPath) {
                if (state.isOccupiedByResource(firstCellInPath)) {
                    continue;
                }
                state.move(unit, firstCellInPath, priority);
                existGoodMove = true;
            }
            if (!existGoodMove) {
                if (!goAwayFromNotGoThere(unit, priority)) {
                    for (Position firstCellInPath : firstCellsInPath) {
                        if (state.isOccupiedByResource(firstCellInPath)) {
                            eat(unit, firstCellInPath);
                            existGoodMove = true;
                        }
                    }
                    return existGoodMove;
                }
            }
        } else {
            for (Position firstCellInPath : firstCellsInPath) {
                if (state.isOccupiedByResource(firstCellInPath)) {
                    eat(unit, firstCellInPath);
                } else {
                    state.move(unit, firstCellInPath, priority);
                }
            }
        }
        return true;
    }

    private boolean goAwayFromNotGoThere(final Entity unit, final int priority) {
        final Position curPos = unit.getPosition();
        boolean existGoodMove = false;
        for (int it = 0; it < Directions.dx.length; it++) {
            final int nx = curPos.getX() + Directions.dx[it];
            final int ny = curPos.getY() + Directions.dy[it];
            if (!state.map.insideMap(nx, ny)) {
                continue;
            }
            if (state.map.underAttack[nx][ny] == MapHelper.UNDER_ATTACK.UNDER_ATTACK_DO_NOT_GO_THERE) {
                continue;
            }
            if (MapHelper.canGoThereOnCurrentTurn(state.map.canGoThrough[nx][ny], false, true)) {
                state.move(unit, new Position(nx, ny), priority + 1);
                existGoodMove = true;
            }
        }
        return existGoodMove;
    }

    private boolean goToPosition(final Entity unit, final Position goToPos, int maxDist, boolean okGoToNotGoThere, boolean okGoUnderAttack) {
        if (goToPosition(unit, goToPos, maxDist, okGoToNotGoThere, false, okGoUnderAttack, MovesPicker.PRIORITY_GO_FOR_ATTACK)) {
            return true;
        }
        if (goToPosition(unit, goToPos, maxDist, okGoToNotGoThere, true, okGoUnderAttack, MovesPicker.PRIORITY_GO_FOR_ATTACK_THROUGH_BUILDERS)) {
            return true;
        }
        return false;
    }

    void resolveEatingFoodPaths(final List<Entity> units) {
        int resolveIt = 0;
        while (true) {
            if (resolveIt++ > 10) {
                System.err.println("VERY STRANGE: Can't resolve food path fast");
                break;
            }
            boolean changed = false;
            for (Entity unit : units) {
                List<MoveAction> moveActions = state.getUnitMoveActions(unit);
                for (MoveAction moveAction : moveActions) {
                    final Position movePos = moveAction.getTarget();
                    final Entity who = state.map.entitiesByPos[movePos.getX()][movePos.getY()];

                    if (who == null || who.getPlayerId() == null) {
                        continue;
                    }

                    final int myPlayerId = state.playerView.getMyId();

                    if (who.getPlayerId() == myPlayerId && who.getEntityType() == EntityType.RANGED_UNIT) {
                        final List<AttackAction> hisActions = state.getUnitAttackActions(who);
                        for (AttackAction attackAction : hisActions) {
                            final Entity resource = state.getEntityById(attackAction.getTarget());
                            final Position foodPos = resource.getPosition();
                            if (unit.getPosition().distTo(foodPos) <= state.getEntityProperties(unit).getAttack().getAttackRange()) {
                                if (eat(unit, resource.getPosition())) {
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    void updateDefenderSpecialAgentsMissions(List<Entity> allUnits) {
        List<Entity> defenders = new ArrayList<>();
        for (Entity rangedUnit : allUnits) {
            SpecialAgents.Profile profile = SpecialAgents.getSpecialAgentProfile(state, rangedUnit);
            if (profile == null) {
                continue;
            }
            if (profile.defender) {
                defenders.add(rangedUnit);
            }
        }
        List<ProtectionBalance.TopBalance> topBalances = state.map.protectionBalance.topBalances;
        if (topBalances.isEmpty()) {
            return;
        }
        final int unitsPerPoint = 1 + (defenders.size() - 1) / topBalances.size();
        MinCostMaxFlow minCostMaxFlow = new MinCostMaxFlow(1 + defenders.size() + topBalances.size() + 1);
        List<MinCostMaxFlow.Edge>[] edges = new List[defenders.size()];
        List<ProtectionBalance.TopBalance>[] targets = new List[defenders.size()];
        for (int i = 0; i < defenders.size(); i++) {
            minCostMaxFlow.addEdge(0, 1 + i, 1, 0);
            edges[i] = new ArrayList<>();
            targets[i] = new ArrayList<>();
            for (int j = 0; j < topBalances.size(); j++) {
                ProtectionBalance.TopBalance topBalance = topBalances.get(j);
                final Position expectedPosToProtect = new Position(topBalance.x, topBalance.y);
                final int dist = expectedPosToProtect.distTo(defenders.get(i).getPosition());
                long weight = MinCostMaxFlow.pathDistToWeight(dist);
                edges[i].add(minCostMaxFlow.addEdge(1 + i, 1 + defenders.size() + j, 1, weight));
                targets[i].add(topBalances.get(j));
            }
        }
        for (int i = 0; i < topBalances.size(); i++) {
            minCostMaxFlow.addEdge(1 + defenders.size() + i, minCostMaxFlow.n - 1, unitsPerPoint, 0);
        }
        minCostMaxFlow.getMinCostMaxFlow(0, minCostMaxFlow.n - 1);
        for (int i = 0; i < defenders.size(); i++) {
            final Entity myUnit = defenders.get(i);
            for (int j = 0; j < edges[i].size(); j++) {
                final MinCostMaxFlow.Edge edge = edges[i].get(j);
                if (edge.flow > 0) {
                    final ProtectionBalance.TopBalance enemy = targets[i].get(j);
                    final Position targetCell = new Position(enemy.x, enemy.y);
                    SpecialAgents.getSpecialAgentProfile(state, myUnit).setMission(targetCell);
                    break;
                }
            }
        }
    }

    void makeMoveForAll() {
        final List<Entity> allRangedUnits = state.myEntitiesByType.get(EntityType.RANGED_UNIT);
        updateDefenderSpecialAgentsMissions(allRangedUnits);
        final int attackRange = state.getEntityTypeProperties(EntityType.RANGED_UNIT).getAttack().getAttackRange();
        List<Entity> notAttackingOnCurrentTurn = new ArrayList<>();
        for (Entity unit : allRangedUnits) {
            // TODO: attack units closer to my builders
            List<Entity> toAttack = state.map.getEntitiesToAttack(unit.getPosition(), attackRange);
            Entity bestEntityToAttack = null;
            int smallestHealth = Integer.MAX_VALUE;
            SpecialAgents.Profile profile = SpecialAgents.getSpecialAgentProfile(state, unit);
            for (Entity enemyToAttack : toAttack) {
                if (profile != null && !profile.shouldAttack(enemyToAttack.getEntityType())) {
                    continue;
                }
                int expectedHealth = getExpectedHealth(enemyToAttack);
                if (expectedHealth <= 0) {
                    continue;
                }
                if (expectedHealth < smallestHealth) {
                    smallestHealth = expectedHealth;
                    bestEntityToAttack = enemyToAttack;
                }
            }
            if (bestEntityToAttack != null) {
                attack(unit, bestEntityToAttack);
            } else {
                notAttackingOnCurrentTurn.add(unit);
            }
        }
        ProtectionsResult protectionsResult = handleProtections(notAttackingOnCurrentTurn);
        notAttackingOnCurrentTurn = filterProtections(notAttackingOnCurrentTurn, protectionsResult.usedUnits);
        needMoreUnitsForSupport = protectionsResult.needMoreSupport;
        for (Entity unit : notAttackingOnCurrentTurn) {
            SpecialAgents.Profile specialAgentProfile = SpecialAgents.getSpecialAgentProfile(state, unit);
            Entity closestEnemy = state.map.findClosestEnemy(unit.getPosition());
            if (specialAgentProfile != null) {
                if (closestEnemy != null) {
                    boolean shouldAttackClosestEnemy = closestEnemy.getEntityType() == EntityType.BUILDER_UNIT;
                    if (specialAgentProfile.defender) {
                        shouldAttackClosestEnemy = true;
                    }
                    if (closestEnemy.getPosition().distTo(unit.getPosition()) > CLOSE_ENOUGH) {
                        shouldAttackClosestEnemy = false;
                    }
                    if (shouldAttackClosestEnemy) {
                        int maxDist = CLOSE_ENOUGH * 2;
                        if (goToPosition(unit, closestEnemy.getPosition(), maxDist, false, false)) {
                            continue;
                        }
                    }
                }
                if (specialAgentProfile.defender) {
                    goToPosition(unit, specialAgentProfile.currentTarget, Integer.MAX_VALUE - 1, false, false, true, MovesPicker.PRIORITY_SMALL);
                } else {
                    if (specialAgentProfile.shouldUpdateMission(unit)) {
                        specialAgentProfile.updateMission(state);
                    }
                    int maxDist = Integer.MAX_VALUE;
                    if (!goToPosition(unit, specialAgentProfile.currentTarget, maxDist, false, false)) {
                        maybeSpecialAgentCouldAttackSomething(unit);
                        specialAgentProfile.updateMission(state);
                    }
                }
            } else {
//                if (closestEnemy != null) {
//                    boolean inMyRegion = state.inMyRegionOfMap(closestEnemy);
//                    if (closestEnemy.getPosition().distTo(unit.getPosition()) <= CLOSE_ENOUGH || inMyRegion) {
//                        // Integer.MAX_VALUE will try to use auto-attack :(
//                        int maxDist = inMyRegion ? (Integer.MAX_VALUE - 1) : (CLOSE_ENOUGH * 2);
//                        if (goToPosition(unit, closestEnemy.getPosition(), maxDist, false, true)) {
//                            continue;
//                        }
//                    }
//                }
                final Position globalTargetPos = state.globalStrategy.whichPlayerToAttack();
                if (!goToPosition(unit, globalTargetPos, Integer.MAX_VALUE, false, true)) {
                    blocked(unit);
                }
            }
        }
        resolveEatingFoodPaths(allRangedUnits);
    }

    void maybeSpecialAgentCouldAttackSomething(final Entity unit) {
        int attackRange = state.getEntityProperties(unit).getAttack().getAttackRange();
        List<Entity> toAttackOptions = state.map.getEntitiesToAttack(unit.getPosition(), attackRange);
        for (Entity toAttack : toAttackOptions) {
            attack(unit, toAttack);
        }
    }

    static private List<Entity> filterProtections(List<Entity> allUnits, Set<Entity> used) {
        List<Entity> rest = new ArrayList<>();
        for (Entity entity : allUnits) {
            if (used.contains(entity)) {
                continue;
            }
            rest.add(entity);
        }
        return rest;
    }

    static class ProtectionsResult {
        final Set<Entity> usedUnits;
        final boolean needMoreSupport;

        public ProtectionsResult(Set<Entity> usedUnits, boolean needMoreSupport) {
            this.usedUnits = usedUnits;
            this.needMoreSupport = needMoreSupport;
        }
    }

    /**
     * @return used units
     */
    private ProtectionsResult handleProtections(List<Entity> myUnits) {
        Set<Entity> used = new HashSet<>();
        Set<Entity> importantEnemies = state.needProtection.enemiesToAttack.keySet();
        List<Entity> enemyUnits = new ArrayList<>(state.allEnemiesEntities);
        MinCostMaxFlow minCostMaxFlow = new MinCostMaxFlow(1 + myUnits.size() + enemyUnits.size() + 1);
        List<MinCostMaxFlow.Edge>[] edges = new List[myUnits.size()];
        List<Entity>[] targets = new List[myUnits.size()];
        // TODO: optimize speed?
        for (int i = 0; i < myUnits.size(); i++) {
            Entity unit = myUnits.get(i);
            SpecialAgents.Profile profile = SpecialAgents.getSpecialAgentProfile(state, unit);
            if (profile != null && !profile.shouldProtect()) {
                continue;
            }
            minCostMaxFlow.addEdge(0, 1 + i, 1, 0);
            edges[i] = new ArrayList<>();
            targets[i] = new ArrayList<>();
            for (int j = 0; j < enemyUnits.size(); j++) {
                final Position expectedPosToProtect = enemyUnits.get(j).getPosition();
                final int dist = expectedPosToProtect.distTo(myUnits.get(i).getPosition());
                if (dist > CLOSE_ENOUGH && !importantEnemies.contains(enemyUnits.get(j))) {
                    continue;
                }
                long weight = MinCostMaxFlow.pathDistToWeight(dist);
                edges[i].add(minCostMaxFlow.addEdge(1 + i, 1 + myUnits.size() + j, 1, weight));
                targets[i].add(enemyUnits.get(j));
            }
        }
        // TODO: THINK ABOUT CAP = 2!!!
        final int MY_UNITS_PER_ENEMY = 2;
        for (int i = 0; i < enemyUnits.size(); i++) {
            minCostMaxFlow.addEdge(1 + myUnits.size() + i, minCostMaxFlow.n - 1, MY_UNITS_PER_ENEMY, 0);
        }
        long flow = minCostMaxFlow.getMinCostMaxFlow(0, minCostMaxFlow.n - 1)[0];
        for (int i = 0; i < myUnits.size(); i++) {
            final Entity myUnit = myUnits.get(i);
            if (edges[i] == null) {
                continue;
            }
            for (int j = 0; j < edges[i].size(); j++) {
                final MinCostMaxFlow.Edge edge = edges[i].get(j);
                if (edge.flow > 0) {
                    final Entity enemy = targets[i].get(j);
                    final Position targetCell = enemy.getPosition();
                    // TODO: MAX_VALUE, STAY?
                    boolean important = importantEnemies.contains(enemy);
                    int maxDist = important ? Integer.MAX_VALUE - 1 : CLOSE_ENOUGH * 2;
                    if (!goToPosition(myUnit, targetCell, maxDist, important, true, true, MovesPicker.PRIORITY_GO_TO_PROTECT)) {
                        blocked(myUnit);
                    }
                    used.add(myUnit);
                    if (state.debugInterface != null) {
                        state.debugTargets.put(myUnit.getPosition(), targetCell);
                    }
                    break;
                }
            }
        }
        return new ProtectionsResult(used, flow < MY_UNITS_PER_ENEMY * enemyUnits.size());
    }

    final int CLOSE_ENOUGH = 20;
}

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
        state.attack(who, what);
    }

    private void eat(final Entity unit, final Position pos) {
        state.attack(unit, state.map.entitiesByPos[pos.getX()][pos.getY()]);
        state.map.updateCellCanGoThrough(unit.getPosition(), MapHelper.CAN_GO_THROUGH.MY_EATING_FOOD_UNIT);
    }

    private void blocked(final Entity unit) {
        state.addDebugUnitInBadPosition(unit.getPosition());
        state.map.updateCellCanGoThrough(unit.getPosition(), MapHelper.CAN_GO_THROUGH.MY_ATTACKING_UNIT);
        state.doNothing(unit);
    }

    private boolean goToPosition(final Entity unit, final Position goToPos, int maxDist, boolean okGoToNotGoThere) {
        final int attackRange = state.getEntityProperties(unit).getAttack().getAttackRange();
        Position firstCellInPath = state.map.findBestPathToTargetDijkstra(unit.getPosition(), goToPos, attackRange, maxDist, okGoToNotGoThere);
        if (firstCellInPath != null) {
            state.addDebugTarget(unit.getPosition(), goToPos);
            if (state.isOccupiedByResource(firstCellInPath)) {
                eat(unit, firstCellInPath);
            } else {
                state.move(unit, firstCellInPath);
            }
            return true;
        }
        return false;
    }

    void resolveEatingFoodPaths(final List<Entity> units) {
        while (true) {
            boolean changed = false;
            for (Entity unit : units) {
                EntityAction action = state.getUnitAction(unit);
                if (action == null) {
                    continue;
                }
                MoveAction moveAction = action.getMoveAction();
                if (moveAction == null) {
                    continue;
                }
                final Position movePos = moveAction.getTarget();
                if (state.map.canGoThrough[movePos.getX()][movePos.getY()] == MapHelper.CAN_GO_THROUGH.MY_EATING_FOOD_UNIT) {
                    changed = true;
                    final Entity who = state.map.entitiesByPos[movePos.getX()][movePos.getY()];
                    final EntityAction hisAction = state.getUnitAction(who);
                    final AttackAction attackAction = hisAction.getAttackAction();
                    if (attackAction == null) {
                        throw new AssertionError("Eat food, but not attack?");
                    }
                    final Entity resource = state.getEntityById(attackAction.getTarget());
                    final Position foodPos = resource.getPosition();
                    if (unit.getPosition().distTo(foodPos) <= state.getEntityProperties(unit).getAttack().getAttackRange()) {
                        eat(unit, resource.getPosition());
                    } else {
                        blocked(unit);
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    void makeMoveForAll() {
        // TODO: do we need to protect something?
        final List<Entity> allRangedUnits = state.myEntitiesByType.get(EntityType.RANGED_UNIT);
        final int attackRange = state.getEntityTypeProperties(EntityType.RANGED_UNIT).getAttack().getAttackRange();
        List<Entity> notAttackingOnCurrentTurn = new ArrayList<>();
        for (Entity unit : allRangedUnits) {
            List<Entity> toAttack = state.map.getEntitiesToAttack(unit.getPosition(), attackRange);
            Entity bestEntityToAttack = null;
            int smallestHealth = Integer.MAX_VALUE;
            for (Entity enemyToAttack : toAttack) {
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
            Entity closestEnemy = state.map.findClosestEnemy(unit.getPosition());
            if (closestEnemy != null) {
                boolean inMyRegion = state.inMyRegionOfMap(closestEnemy);
                if (closestEnemy.getPosition().distTo(unit.getPosition()) <= CLOSE_ENOUGH || inMyRegion) {
                    int maxDist = inMyRegion ? Integer.MAX_VALUE : (CLOSE_ENOUGH * 2);
                    if (goToPosition(unit, closestEnemy.getPosition(), maxDist, false)) {
                        continue;
                    }
                }
            }
            final Position globalTargetPos = state.globalStrategy.whichPlayerToAttack();
            if (!goToPosition(unit, globalTargetPos, Integer.MAX_VALUE, false)) {
                blocked(unit);
            }
        }
        resolveEatingFoodPaths(allRangedUnits);
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
        List<Entity> enemyUnits = new ArrayList<>(state.needProtection.enemiesToAttack);
        MinCostMaxFlow minCostMaxFlow = new MinCostMaxFlow(1 + myUnits.size() + enemyUnits.size() + 1);
        MinCostMaxFlow.Edge[][] edges = new MinCostMaxFlow.Edge[myUnits.size()][];
        // TODO: optimize speed?
        for (int i = 0; i < myUnits.size(); i++) {
            minCostMaxFlow.addEdge(0, 1 + i, 1, 0);
            edges[i] = new MinCostMaxFlow.Edge[enemyUnits.size()];
            for (int j = 0; j < enemyUnits.size(); j++) {
                final int dist = enemyUnits.get(j).getPosition().distTo(myUnits.get(i).getPosition());
                long weight = MinCostMaxFlow.pathDistToWeight(dist);
                edges[i][j] = minCostMaxFlow.addEdge(1 + i, 1 + myUnits.size() + j, 1, weight);
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
            for (int j = 0; j < edges[i].length; j++) {
                final MinCostMaxFlow.Edge edge = edges[i][j];
                if (edge.flow > 0) {
                    final Position targetCell = enemyUnits.get(j).getPosition();
                    // TODO: MAX_VALUE, STAY?
                    if (!goToPosition(myUnit, targetCell, Integer.MAX_VALUE, true)) {
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

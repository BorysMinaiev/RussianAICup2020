import model.*;

import java.util.*;

public class RangedUnitStrategy {

    int getExpectedHealth(final Entity enemyUnit) {
        int expectedDamage = expectedDamageByEntityId.getOrDefault(enemyUnit.getId(), 0);
        return enemyUnit.getHealth() - expectedDamage;
    }

    final Map<Integer, Integer> expectedDamageByEntityId;
    final State state;

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

    private boolean goToPosition(final Entity unit, final Position goToPos, int maxDist) {
        final int attackRange = state.getEntityProperties(unit).getAttack().getAttackRange();
        Position firstCellInPath = state.map.findBestPathToTargetDijkstra(unit.getPosition(), goToPos, attackRange, maxDist);
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
        GlobalStrategy.ProtectSomething protectSomething = state.globalStrategy.needToProtectSomething();
        if (protectSomething != null) {
            final Position targetPos = protectSomething.whereToGo;
            notAttackingOnCurrentTurn.sort(new Comparator<Entity>() {
                @Override
                public int compare(Entity o1, Entity o2) {
                    final int d1 = o1.getPosition().distTo(targetPos);
                    final int d2 = o2.getPosition().distTo(targetPos);
                    return -Integer.compare(d1, d2);
                }
            });
            final int UNITS_TO_PROTECT = Math.min(10, notAttackingOnCurrentTurn.size());
            final List<Entity> toProtect = new ArrayList<>();
            for (int it = 0; it < UNITS_TO_PROTECT; it++) {
                toProtect.add(notAttackingOnCurrentTurn.get(notAttackingOnCurrentTurn.size() - 1));
                notAttackingOnCurrentTurn.remove(notAttackingOnCurrentTurn.size() - 1);
            }
            for (Entity unit : toProtect) {
                if (!goToPosition(unit, targetPos, Integer.MAX_VALUE)) {
                    state.randomlyMoveAndAttack(unit);
                }
            }
        }
        for (Entity unit : notAttackingOnCurrentTurn) {
            Entity closestEnemy = state.map.findClosestEnemy(unit.getPosition());
            if (closestEnemy != null) {
                boolean inMyRegion = state.inMyRegionOfMap(closestEnemy);
                if (closestEnemy.getPosition().distTo(unit.getPosition()) <= CLOSE_ENOUGH || inMyRegion) {
                    int maxDist = inMyRegion ? Integer.MAX_VALUE : (CLOSE_ENOUGH * 2);
                    if (goToPosition(unit, closestEnemy.getPosition(), maxDist)) {
                        continue;
                    }
                }
            }
            final Position globalTargetPos = state.globalStrategy.whichPlayerToAttack();
            if (!goToPosition(unit, globalTargetPos, Integer.MAX_VALUE)) {
                blocked(unit);
            }
        }
        resolveEatingFoodPaths(allRangedUnits);
    }

    final int CLOSE_ENOUGH = 20;
}

import model.Entity;
import model.EntityType;
import model.Position;

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
        state.map.updateBlockedCell(who.getPosition());
        final int damage = state.getEntityProperties(who).getAttack().getDamage();
        expectedDamageByEntityId.put(what.getId(), expectedDamageByEntityId.getOrDefault(what.getId(), 0) + damage);
        state.attack(who, what);
    }

    private boolean goToPosition(final Entity unit, final Position goToPos) {
        Position firstCellInPath = state.map.findBestPathToTarget(unit.getPosition(), goToPos);
        if (firstCellInPath != null) {
            state.move(unit, firstCellInPath);
            return true;
        }
        return false;
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
                if (!goToPosition(unit, targetPos)) {
                    state.randomlyMoveAndAttack(unit);
                }
            }
        }
        for (Entity unit : notAttackingOnCurrentTurn) {
            Entity closestEnemy = state.map.findClosestEnemy(unit.getPosition());
            if (closestEnemy != null) {
                if (goToPosition(unit, closestEnemy.getPosition())) {
                    continue;
                }
            }
            state.randomlyMoveAndAttack(unit);
        }
    }
}

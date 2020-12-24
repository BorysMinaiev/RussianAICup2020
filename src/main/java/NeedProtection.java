import model.Entity;
import model.EntityType;
import model.Position;

import java.util.*;

public class NeedProtection {
    static boolean requiresProtection(EntityType entityType) {
        return switch (entityType) {
            case HOUSE, BUILDER_BASE, BUILDER_UNIT, RANGED_BASE -> true;
            case MELEE_BASE, MELEE_UNIT, WALL, RANGED_UNIT, RESOURCE, TURRET -> false;
        };
    }

    final static int MAX_DIST_TO_CONSIDER = 30;
    final static int MAX_NEARBY_UNITS_TO_CONSIDER = 10;

    static int updatedDist(final State state, Entity entity, Position pos) {
        boolean myUnit = entity.getPlayerId() == state.playerView.getMyId();
        int dist = entity.getPosition().distTo(pos);
        if (!myUnit) {
            dist -= 5;
        }
        return dist;
    }

    // negative - needs protection!
    static ToPretect computeEntity(final State state, final Entity entityToProtect) {
        final Position pos = entityToProtect.getPosition();
        List<Entity> nearbyUnits = new ArrayList<>();
        for (Entity anotherEntity : state.allEnemiesWarUnits) {
            if (anotherEntity.getPosition().distTo(pos) > MAX_DIST_TO_CONSIDER) {
                continue;
            }
            nearbyUnits.add(anotherEntity);
        }
        for (Entity anotherEntity : state.myEntities) {
            if (anotherEntity.getEntityType().isBuilding()) {
                continue;
            }
            if (anotherEntity.getEntityType() == EntityType.BUILDER_UNIT) {
                continue;
            }
            if (anotherEntity.getPosition().distTo(pos) > MAX_DIST_TO_CONSIDER) {
                continue;
            }
            nearbyUnits.add(anotherEntity);
        }
        Collections.sort(nearbyUnits, new Comparator<Entity>() {
            @Override
            public int compare(Entity o1, Entity o2) {
                int d1 = updatedDist(state, o1, pos);
                int d2 = updatedDist(state, o2, pos);
                if (d1 != d2) {
                    return Integer.compare(d1, d2);
                }
                int myIdDiff1 = Math.abs(o1.getPlayerId() - state.playerView.getMyId());
                int myIdDiff2 = Math.abs(o2.getPlayerId() - state.playerView.getMyId());
                return Integer.compare(myIdDiff1, myIdDiff2);
            }
        });
        while (nearbyUnits.size() > MAX_NEARBY_UNITS_TO_CONSIDER) {
            nearbyUnits.remove(nearbyUnits.size() - 1);
        }
        int balance = 0;
        int minBalance = 0;
        for (Entity anotherEntity : nearbyUnits) {
            if (anotherEntity.getPlayerId() == state.playerView.getMyId()) {
                balance++;
            } else {
                balance--;
            }
            minBalance = Math.min(minBalance, balance);
        }
        if (minBalance < 0) {
            List<Entity> enemies = new ArrayList<>();
            for (Entity entity : nearbyUnits) {
                if (entity.getPlayerId() == state.playerView.getMyId()) {
                    continue;
                }
                enemies.add(entity);
            }
            return new ToPretect(entityToProtect, minBalance, enemies);
        }
        return null;

    }

    static class ToPretect {
        final Entity entity;
        final int balance;
        final List<Entity> enemies;

        public ToPretect(Entity entity, int balance, List<Entity> enemies) {
            this.entity = entity;
            this.balance = balance;
            this.enemies = enemies;
        }
    }

    static class AtackingEnemy {
        double sumXrequiresProtection;
        double sumYrequiresProtection;
        int cntRequiresProtection;

        AtackingEnemy() {

        }

        void addRequiresProtection(Entity myUnit) {
            sumXrequiresProtection += myUnit.getPosition().getX();
            sumYrequiresProtection += myUnit.getPosition().getY();
            cntRequiresProtection++;
        }

        public Position getPosition() {
            return new Position((int) Math.round(sumXrequiresProtection / cntRequiresProtection),
                    (int) (Math.round(sumYrequiresProtection / cntRequiresProtection)));
        }
    }

    final List<ToPretect> toProtect;
    final Map<Entity, AtackingEnemy> enemiesToAttack;

    NeedProtection(final State state) {
        toProtect = new ArrayList<>();
        enemiesToAttack = new HashMap<>();
        for (Entity entity : state.myEntities) {
            if (!requiresProtection(entity.getEntityType())) {
                continue;
            }
            ToPretect entityToProtect = computeEntity(state, entity);
            if (entityToProtect != null) {
                toProtect.add(entityToProtect);
                for (Entity enemy : entityToProtect.enemies) {
                    if (!enemiesToAttack.containsKey(enemy)) {
                        enemiesToAttack.put(enemy, new AtackingEnemy());
                    }
                    AtackingEnemy atackingEnemy = enemiesToAttack.get(enemy);
                    atackingEnemy.addRequiresProtection(entity);
                }
            }
        }
    }
}

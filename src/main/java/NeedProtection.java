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
                int d1 = o1.getPosition().distTo(pos);
                int d2 = o2.getPosition().distTo(pos);
                return Integer.compare(d1, d2);
            }
        });
        while (nearbyUnits.size() > MAX_NEARBY_UNITS_TO_CONSIDER) {
            nearbyUnits.remove(nearbyUnits.size() - 1);
        }
        if (needsProtectionByNearbyUnits(state, pos, nearbyUnits)) {
            List<Entity> enemies = new ArrayList<>();
            for (Entity entity : nearbyUnits) {
                if (entity.getPlayerId() == state.playerView.getMyId()) {
                    continue;
                }
                enemies.add(entity);
            }
            return new ToPretect(entityToProtect, enemies);
        }
        return null;
    }

    private static boolean canProtect(final Position pos, final Entity myUnit, final Entity enemyUnit) {
        return CellsUtils.isBetween(myUnit.getPosition(), pos, enemyUnit.getPosition());
    }

    private static boolean needsProtectionByNearbyUnits(final State state, final Position pos, List<Entity> units) {
        int myId = state.playerView.getMyId();
        for (Entity enemyUnit : units) {
            if (enemyUnit.getPlayerId() == myId) {
                continue;
            }
            boolean existProtector = false;
            for (Entity myUnit : units) {
                if (myUnit.getPlayerId() != myId) {
                    continue;
                }
                if (canProtect(pos, myUnit, enemyUnit)) {
                    existProtector = true;
                    break;
                }
            }
            if (!existProtector) {
                return true;
            }
        }
        return false;
    }

    static class ToPretect {
        final Entity entity;
        final List<Entity> enemies;

        public ToPretect(Entity entity, List<Entity> enemies) {
            this.entity = entity;
            this.enemies = enemies;
        }
    }

    static class AtackingEnemy {
        double sumXrequiresProtection;
        double sumYrequiresProtection;
        int cntRequiresProtection;
        final Entity unit;

        AtackingEnemy(final Entity unit) {
            this.unit = unit;
        }

        void addRequiresProtection(Entity myUnit) {
            sumXrequiresProtection += myUnit.getPosition().getX();
            sumYrequiresProtection += myUnit.getPosition().getY();
            cntRequiresProtection++;
        }

        public Position getPosition() {
            return unit.getPosition();
//            return new Position((int) Math.round(sumXrequiresProtection / cntRequiresProtection),
//                    (int) (Math.round(sumYrequiresProtection / cntRequiresProtection)));
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
                        enemiesToAttack.put(enemy, new AtackingEnemy(enemy));
                    }
                    AtackingEnemy atackingEnemy = enemiesToAttack.get(enemy);
                    atackingEnemy.addRequiresProtection(entity);
                }
            }
        }
    }
}

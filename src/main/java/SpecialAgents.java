import model.Entity;
import model.EntityType;
import model.Position;

import java.util.HashMap;
import java.util.Map;

public class SpecialAgents {
    static class Profile {
        Position currentTarget;
        int currentMissionId;
        final boolean defender;
        final boolean okToProtect;

        public Profile(Position currentTarget, boolean defender, boolean okToProtect) {
            this.currentTarget = currentTarget;
            this.currentMissionId = 0;
            this.defender = defender;
            this.okToProtect = okToProtect;
        }

        public boolean shouldUpdateMission(Entity unit) {
            return unit.getPosition().distTo(currentTarget) < 10 && !defender;
        }

        public void updateMission(State state) {
            final int mapSize = state.playerView.getMapSize();
            if (currentMissionId == 0) {
                currentTarget = new Position(mapSize - 1, mapSize - 1);
            } else {
                currentTarget = new Position(State.rnd.nextInt(mapSize), State.rnd.nextInt(mapSize));
            }
            currentMissionId++;
        }

        public void setMission(Position targetPos) {
            currentTarget = targetPos;
        }

        public boolean shouldAttack(EntityType entityType) {
            return !entityType.isBuilding() || defender || okToProtect;
        }

        public boolean shouldProtect() {
            return currentMissionId > 1 || defender || okToProtect;
        }
    }

    static private final Map<Entity, Profile> agents = new HashMap<>();

    public static Position[] getPredefinedTargets(final int mapSize) {
        return new Position[]{
                new Position(mapSize * 4 / 5, mapSize / 5),
                new Position(mapSize / 5, mapSize * 4 / 5)};
    }

    static Profile createProfileById(final State state, int id) {
        if (id % 15 < 3) {
            final int mapSize = state.playerView.getMapSize();
            Position[] targetPositions = getPredefinedTargets(mapSize);
            boolean defender = State.rnd.nextDouble() < 0.0;
            boolean okToProtect = State.rnd.nextBoolean();
            return new Profile(targetPositions[State.rnd.nextInt(targetPositions.length)], defender, okToProtect);
        } else {
            return null;
        }
    }

    public static Profile getSpecialAgentProfile(final State state, final Entity unit) {
        if (unit.getEntityType() != EntityType.RANGED_UNIT) {
            return null;
        }
        if (!agents.containsKey(unit)) {
            agents.put(unit, createProfileById(state, agents.size()));
        }
        return agents.get(unit);
    }

}

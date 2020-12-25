import model.Player;

import java.util.HashMap;
import java.util.Map;

public class OpponentTracker {
    static Map<Integer, Integer> money = new HashMap<>();

    final static int BIG_CHANGE = 300;

    static public int tickWhenSomebodyBuildARangedBase = Integer.MAX_VALUE / 2;

    static void updateState(final State state) {
        for (Player player : state.playerView.getPlayers()) {
            int resources = player.getResource();
            int wasResources = money.getOrDefault(player.getId(), 0);
            if (resources < wasResources - BIG_CHANGE) {
                tickWhenSomebodyBuildARangedBase = Math.min(tickWhenSomebodyBuildARangedBase, state.playerView.getCurrentTick());
            }
            money.put(player.getId(), resources);
        }
    }
}

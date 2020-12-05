public class GlobalStrategy {
    State state;

    public GlobalStrategy(State state) {
        this.state = state;
    }

    boolean needMoreHouses() {
        // TODO: think about it?
        return state.populationTotal - state.populationUsed < 10;
    }
}

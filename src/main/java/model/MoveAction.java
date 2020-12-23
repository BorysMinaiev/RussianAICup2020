package model;

import util.StreamUtil;

public class MoveAction {
    private Position target;

    @Override
    public String toString() {
        return "MoveAction{" +
                "target=" + target +
                ", findClosestPosition=" + findClosestPosition +
                ", breakThrough=" + breakThrough +
                '}';
    }

    public Position getTarget() {
        return target;
    }

    public void setTarget(Position target) {
        this.target = target;
    }

    private boolean findClosestPosition;

    public boolean isFindClosestPosition() {
        return findClosestPosition;
    }

    public void setFindClosestPosition(boolean findClosestPosition) {
        this.findClosestPosition = findClosestPosition;
    }

    private boolean breakThrough;

    public boolean isBreakThrough() {
        return breakThrough;
    }

    public void setBreakThrough(boolean breakThrough) {
        this.breakThrough = breakThrough;
    }

    public MoveAction() {
    }

    public MoveAction(Position target, boolean findClosestPosition, boolean breakThrough) {
        this.target = target;
        this.findClosestPosition = findClosestPosition;
        this.breakThrough = breakThrough;
    }

    public static MoveAction readFrom(java.io.InputStream stream) throws java.io.IOException {
        MoveAction result = new MoveAction();
        result.target = Position.readFrom(stream);
        result.findClosestPosition = StreamUtil.readBoolean(stream);
        result.breakThrough = StreamUtil.readBoolean(stream);
        return result;
    }

    public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
        target.writeTo(stream);
        StreamUtil.writeBoolean(stream, findClosestPosition);
        StreamUtil.writeBoolean(stream, breakThrough);
    }
}

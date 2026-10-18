package ca.lukegrahamlandry.mimic.goals;

/** Bounded search: no permanent wall-tracking and no endless pathfinding at a ledge. */
public final class PursuitMemory {
    private int unseenTicks;
    private int stuckTicks;

    public void reset() { unseenTicks = 0; stuckTicks = 0; }

    public void tick(boolean visible, boolean madeProgress) {
        unseenTicks = visible ? 0 : unseenTicks + 1;
        stuckTicks = madeProgress ? 0 : stuckTicks + 1;
    }

    public boolean expired(int memoryTicks) {
        return unseenTicks >= memoryTicks || stuckTicks >= 200;
    }
}

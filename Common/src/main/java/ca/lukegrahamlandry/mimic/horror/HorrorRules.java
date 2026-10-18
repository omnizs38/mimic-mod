package ca.lukegrahamlandry.mimic.horror;

/** Pure rules shared by the server director and regression tests. */
public final class HorrorRules {
    private HorrorRules() {}
    public static long calendarDay(long dayTime) { return Math.max(0L, dayTime / 24000L); }
    public static int stage(long highestDay, int cap) { return (int) Math.max(0L, Math.min(highestDay, cap)); }
    public static double multiplier(int stage, double perDay) { return 1.0 + Math.max(0, stage) * Math.max(0.0, perDay); }
    public static boolean inBiteCone(double dx, double dz, double forwardX, double forwardZ) {
        double length = Math.sqrt(dx * dx + dz * dz);
        double forwardLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        return length < 0.001 || (forwardLength > 0.001 && (dx * forwardX + dz * forwardZ) / (length * forwardLength) >= 0.5);
    }
    public static int encounterDelay(int a, int b, int stage, double roll) {
        int low = Math.max(200, Math.min(a, b));
        int high = Math.max(low, Math.max(a, b));
        double random = Math.max(0.0, Math.min(1.0, roll));
        return Math.max(200, (int) ((low + (high - low) * random) / (1.0 + stage * 0.04)));
    }
}

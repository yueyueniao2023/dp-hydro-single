/** 保留旧绘图工具接口，按总泄流查尾水并按实际流量计算发电量。 */
public class PowerCalculator {
    public static final double A = 8.5;
    public static final int TEN_DAY_HOURS = 240;
    public static final double UNIT_CONVERT = 1e-4;
    public static final double MAX_INSTALLED_POWER = 3_600_000;
    public static final double MAX_PERIOD_POWER = 86_400; // 仅适用于10天
    private final ExcelReader excelReader;
    public PowerCalculator(ExcelReader excelReader) { this.excelReader = excelReader; }

    public double[] calculatePowerWithAbandon(double start, double end, double release) {
        return calculatePowerWithAbandon(start, end, release, 10);
    }

    public double[] calculatePowerWithAbandon(double start, double end, double release, double days) {
        if (!Double.isFinite(release) || release < 0 || !Double.isFinite(days) || days <= 0)
            throw new IllegalArgumentException("泄流和时长不合法");
        double head = (start + end) / 2 - excelReader.getTailLevelByFlow(release);
        if (!Double.isFinite(head) || head <= 0) throw new IllegalArgumentException("水头非正或无效");
        double turbine = Math.min(release, Math.min(5000, MAX_INSTALLED_POWER / (A * head)));
        return new double[]{turbine, release - turbine, A * turbine * head * days * 24 * UNIT_CONVERT};
    }
}

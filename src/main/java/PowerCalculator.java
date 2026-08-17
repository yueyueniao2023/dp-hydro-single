public class PowerCalculator {
    public static final double A = 8.5;          // 综合出力系数
    public static final int TEN_DAY_HOURS = 240; // 一旬小时数（10*24）
    public static final double UNIT_CONVERT = 1e-4; // kWh转万kWh
    public static final double MAX_INSTALLED_POWER = 3600000; // 最大出力（kW，3600MW）
    public static final double MAX_PERIOD_POWER = 86400;      // 旬最大发电量（万kWh）

    private ExcelReader excelReader;

    public PowerCalculator(ExcelReader excelReader) {
        this.excelReader = excelReader;
    }

//    // 计算逐旬发电量（未考虑弃水）
//    public double calculatePeriodPower(double startLevel, double endLevel, double powerFlow) {
//        // 1. 平均库水位 = (旬初 + 旬末)/2
//        double avgReservoirLevel = (startLevel + endLevel) / 2;
//        // 2. 平均尾水位（插值查询）
//        double avgTailLevel = excelReader.getTailLevelByFlow(powerFlow);
//        // 3. 平均水头 = 平均库水位 - 平均尾水位
//        double avgHead = avgReservoirLevel - avgTailLevel;
//        // 4. 平均出力（kW）= A * Q * H
//        double avgPower = A * powerFlow * avgHead;
//        // 5. 旬发电量（万kWh）= 出力 * 时间（h）* 单位转换
//        return avgPower * TEN_DAY_HOURS * UNIT_CONVERT;
//    }

    /**
     * 计算考虑装机容量的实际发电流量、弃水流量、实际发电量
     * @param startLevel 旬初水位（m）
     * @param endLevel 旬末水位（m）
     * @param theoreticalFlow 理论发电流量（m³/s，由水量平衡计算）
     * @return 数组：[实际发电流量, 弃水流量, 实际发电量]
     */


    public double[] calculatePowerWithAbandon(double startLevel, double endLevel, double theoreticalFlow) {
        // 1. 计算平均水头（原有逻辑）
        double avgReservoirLevel = (startLevel + endLevel) / 2;
        double avgTailLevel = excelReader.getTailLevelByFlow(theoreticalFlow);
        double avgHead = avgReservoirLevel - avgTailLevel;

        // 2. 计算受装机容量限制的最大允许发电流量
        double maxPowerFlow = MAX_INSTALLED_POWER / (A * avgHead);

        // 计算发电流量最大限值（同时考虑装机容量限制和出库流量限制）
        maxPowerFlow = Math.min(maxPowerFlow, ConstraintChecker.MAX_FLOW);

        // 3. 计算弃水流量与实际发电流量
        double actualFlow;
        double abandonFlow;
        if (theoreticalFlow > maxPowerFlow) {
            actualFlow = maxPowerFlow;
            abandonFlow = theoreticalFlow - maxPowerFlow;
        } else {
            actualFlow = theoreticalFlow;
            abandonFlow = 0;
        }

        // 4. 计算实际发电量
        double actualPower;
        if (abandonFlow > 0) {
            actualPower = MAX_PERIOD_POWER; // 超装机时取上限
        } else {
            double avgPower = A * actualFlow * avgHead;
            actualPower = avgPower * TEN_DAY_HOURS * UNIT_CONVERT;
        }

        return new double[]{actualFlow, abandonFlow, actualPower};
    }

}
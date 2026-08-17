import java.util.Arrays;

public class ConstraintChecker {
    // 基础约束常量
    public static final double MIN_FLOW = 188;       // 最小出库流量（m³/s）
    public static final double MAX_FLOW = 5000;      // 最大出库流量（m³/s）
    public static final double MIN_LEVEL = 790;      // 死水位（m）
    public static final double MAX_LEVEL = 850;      // 正常蓄水位（m）
    public static final double FLOOD_LIMIT_LEVEL = 842; // 主汛期汛限水位（m）
    public static final double MONTH_LEVEL_CHANGE_LIMIT = 30; // 月度水位变幅上限（m）

    // 检查单旬约束（流量+水位+汛限）
    public boolean checkSinglePeriodConstraint(int month, double powerFlow, double startLevel, double endLevel) {
        // 1. 流量约束（允许0，因为弃水逻辑已处理）
        if (powerFlow < MIN_FLOW || powerFlow > MAX_FLOW) {//??????????????????
            return false;
        }

        // 2. 水位范围约束
        if (startLevel < MIN_LEVEL || startLevel > MAX_LEVEL || endLevel < MIN_LEVEL || endLevel > MAX_LEVEL) {
            return false;
        }

        // 3. 主汛期（7、8月）汛限水位约束
        if ((month == 7 || month == 8) && (startLevel > FLOOD_LIMIT_LEVEL || endLevel > FLOOD_LIMIT_LEVEL)) {
            return false;
        }

        // 4. 旬水位变幅约束（单旬变幅≤30m，与月度一致）
        if (Math.abs(endLevel - startLevel) > MONTH_LEVEL_CHANGE_LIMIT) {//检查旬初旬末水位差的绝对值是否小于月水位变幅限制值
            return false;
        }

        //5.（2025/15/09 补充）月水位变幅约束
        //??????????

        return true;
    }

    // 检查月度水位变幅约束（放宽：允许±30m，且容错空数组）
    public boolean checkMonthLevelChange(double[] monthLevels) {

//        //空值检查
//        if (monthLevels == null || monthLevels.length == 0) {
//            return true;
//        }

        double maxLevel = Arrays.stream(monthLevels).max().getAsDouble();           //Arrays什么意思？？？？？？？？
        double minLevel = Arrays.stream(monthLevels).min().getAsDouble();
        if ((maxLevel - minLevel) <= MONTH_LEVEL_CHANGE_LIMIT) {
            return true;
        } else return false;
    }
}
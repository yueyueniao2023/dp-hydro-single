import java.util.ArrayList;
import java.util.List;

public class DynamicProgramming {
    private static final int            PERIOD_COUNT = 36; // 总旬数
    private static final double         MIN_LEVEL = 790; // 最小水位（死水位）
    private static final double         MAX_LEVEL = 850; // 最大水位（正常蓄水位）
    private static final double         LEVEL_STEP = 0.5; // 水位间隔
    private static final int            STATE_COUNT = (int) ((MAX_LEVEL - MIN_LEVEL) / LEVEL_STEP) + 1; // 121个状态

    private ExcelReader              excelReader;
    private ConstraintChecker        constraintChecker;
    private PowerCalculator          powerCalculator;
    private List<PeriodBasicData>    periodBasicDataList;

    // DP数组：dp[旬索引][状态索引] = 累计最大发电量（万kWh）
    private double[][] dp = new double[PERIOD_COUNT + 1][STATE_COUNT];
    // 路径记录：prev[旬索引][状态索引] = 上一旬的最优状态索引
    private int[][] prev = new int[PERIOD_COUNT + 1][STATE_COUNT];
    // 状态对应的水位值（索引0→790，索引1→790.5，...，索引120→850）
    private double[] stateLevels = new double[STATE_COUNT];

    public DynamicProgramming(ExcelReader excelReader) {        //public DynamicProgramming(ExcelReader excelReader){...}是DynamicProgramming类的构造方法（此方法名与类名相同，无返回值，连void都不能写）
                                                                //构造方法的作用：用于创建对象时初始化对象（如初始化成员变量、设置初始状态）；
                                                                // 如果类中没有定义构造方法，编译器会自动生成一个无参构造方法

        this.excelReader = excelReader;                             //这四句是将DynamicProgramming类的属性 excelReader、constraintChecker、powerCalculator和periodBasicDataList进行初始化。
        this.constraintChecker = new ConstraintChecker();
        this.powerCalculator = new PowerCalculator(excelReader);
        this.periodBasicDataList = excelReader.getPeriodBasicDataList();

        // 初始化状态水位数组（790m——>850m，间隔为0.5m）
        for (int i = 0; i < STATE_COUNT; i++) {
            stateLevels[i] = MIN_LEVEL + i * LEVEL_STEP;
        }

        // 初始化DP数组为-∞（不可达状态）
        for (int i = 0; i <= PERIOD_COUNT; i++) {
            for (int j = 0; j < STATE_COUNT; j++) {
                dp[i][j] = -Double.MAX_VALUE;
            }
        }

        // 初始状态（第0旬末=第1旬初）：水位830m，对应状态索引
        int initStateIndex = getStateIndexByLevel(830);
        System.out.println(initStateIndex);
        dp[0][initStateIndex] = 0; // 初始发电量为0
                                    //dp[0][80]=0;其余dp[0][]默认为初始值负无穷；
    }

    // 执行DP求解（核心修改：集成弃水逻辑）
    public List<PeriodResult> solve() {                         //返回值类型List，泛函为PeriodResult。

        for (int period = 1; period <= PERIOD_COUNT; period++) {           //遍历36旬
            PeriodBasicData periodData = periodBasicDataList.get(period - 1);   //定义了一个PeriodBasicData类型的对象（有旬索引、月份、天然来水三个属性），名字叫periodData
            int month = periodData.getMonth();
            double naturalInFlow = periodData.getNaturalInFlow();/*这三行作用是将当前旬索引对应的旬基础数据（包括月份、天然来水）取出来*/

            // 遍历当前旬所有可能状态（旬末水位状态）
            for (int currState = 0; currState < STATE_COUNT; currState++) {     //遍历121个状态。currState是当前状态的索引，STATE_COUNT常量表示总计121个状态数。
                double endLevel = stateLevels[currState];                          //stateLevels[]是状态水位数字，索引0->121对应状态水位790m->850m;
                                                                                    // 变量endLevel表示旬末水位,double endLevel = stateLevels[currState]对旬末水位变量endLevel赋初值790m。
                double endCapacity = excelReader.getCapacityByLevel(endLevel);      //调用插值函数，根据旬末水位查询对应的旬末库容。

                // 遍历上一旬所有可能状态（旬初水位状态）
                for (int prevState = 0; prevState < STATE_COUNT; prevState++) {     //prevState记录dp[k][h_k]的子结构的状态水位索引；//这个for循环可以遍历该dp[k][h_k]的所有前述子结构，对比分析找到最优子结构dp[k-1][h_k-1]
                    if (dp[period - 1][prevState] == -Double.MAX_VALUE) {
                        /*Double.MAX_VALUE作为哨兵值，核心作用是 标记 “不可达的状态”      ;Double.MAX_VALUE约 1.7976931348623157×10³⁰⁸；Double.MAX_VALUE与-Double.MAX_VALUE分别表示正负无穷。*/
                        continue; // 上一旬该状态不可达
                    }

                    double startLevel = stateLevels[prevState];                 //找到了上一旬的可达状态。记录旬初水位startLevel
                    double startCapacity = excelReader.getCapacityByLevel(startLevel);      //插值查询旬初水位startLevel对应的旬初库容

                    // 1. 计算理论发电流量（水量平衡）
                    double theoreticalFlow = naturalInFlow - (endCapacity - startCapacity) * 1e8 / (10 * 24 * 3600);    //(10 * 24 * 3600)表示一旬多少秒，1e8是亿m³

                    // 2. 检查基础约束（约束含：流量、非汛期水位、汛限水位，单旬水位变化，不含发电量约束）
                    if (!constraintChecker.checkSinglePeriodConstraint(month, theoreticalFlow, startLevel, endLevel)) {
                        continue;       //如果不满足单旬的约束条件，就直接跳出这一层for循环，后面的其他约束条件就不必检查了。
                    }

                    // 3. 检查月度水位变幅约束
                    int tenDayOfMonth = (period - 1) % 3 + 1;           //变量tenDayOfMonth：通过取模运算得到当前旬在本月是第 1 旬、第 2 旬还是第 3 旬。
                    boolean monthChangeValid = true;                    //变量monthChangeValid：月度水位变幅是否有效的标志位（布尔值）
                    if (tenDayOfMonth >= 2) {                           //这是月内检查，不是相邻三旬检查；所以如果当前旬是本月第 1 旬的话就不必检查了
                        double[] monthLevels = new double[tenDayOfMonth];      //数组monthLevels存储月内各旬的水位
                        monthLevels[tenDayOfMonth - 1] = endLevel;              //tenDayOfMonth是数组monthLevels的长度，tenDayOfMonth - 1是数组monthLevels的末索引；对数组的最后一位元素初始化为当前旬末水位

                        int tempPeriod = period;                        //temp--临时变量        //tempPeriod初值：当前第*旬
                        int tempState = currState;                                            //tempState初值：当前水位在状态水位数组中的索引；
                        for (int i = tenDayOfMonth - 2; i >= 0; i--) {                          //i可能的初值：0或1——>循环1次或2次
                            tempPeriod--;
                            tempState = prev[tempPeriod][tempState];
                            if (dp[tempPeriod][tempState] == -Double.MAX_VALUE) {
                                monthChangeValid = false;
                                break;
                            }
                            monthLevels[i] = stateLevels[tempState];
                        }

                        if (monthChangeValid && !constraintChecker.checkMonthLevelChange(monthLevels)) {
                            monthChangeValid = false;
                        }
                    }
                    if (!monthChangeValid) {                            //如果确定月度水位变幅约束已经不满足了，
                        continue;                                       //就跳出第三层的for循环，即跳过当前子结构，不必要做后续的检查了。
                    }

                    // ============= 改动点1：调用弃水计算，获取实际发电量 =============
                    double[] powerWithAbandon = powerCalculator.calculatePowerWithAbandon(
                            startLevel, endLevel, theoreticalFlow);
                    double actualFlow = powerWithAbandon[0]; // 实际发电流量（已满足188~5000）
                    double actualPower = powerWithAbandon[2]; // 实际发电量（已限制≤86400万kWh）
                    // ============= 改动点1结束 =============

                    // 4. 状态转移：用实际发电量更新DP数组
                    if (dp[period][currState] < dp[period - 1][prevState] + actualPower) {
                        dp[period][currState] = dp[period - 1][prevState] + actualPower;
                        prev[period][currState] = prevState;
                    }
                }
//                //打印测试prev[][]
//                System.out.println(prev[period][currState]+"--"+"period="+period+"--"+"currState="+currState);
            }

//            // 输出每旬可达状态数（调试用）
//            int reachableCount = 0;
//            for (int s = 0; s < STATE_COUNT; s++) {
//                if (dp[period][s] != -Double.MAX_VALUE) {
//                    reachableCount++;
//                }
//            }
//            System.out.println("第" + period + "旬 可达状态数：" + reachableCount + " | 所属月份：" + month);
        }

        // 回溯最优路径
        return backtrackBestPath();
    }

    // 回溯最优路径（核心修改：补充弃水数据赋值）
    private List<PeriodResult> backtrackBestPath() {
        List<PeriodResult> resultList = new ArrayList<>();

        // 1. 找到第36旬中最接近840m的可行状态（末水位约束）
        int finalState = -1;
        double minDiff = 1.0; // 允许±1m误差（确保找到可行解）
        for (int s = 0; s < STATE_COUNT; s++) {                                         //遍历末旬121个水位状态
            double diff = Math.abs(stateLevels[s] - 840);
            if (diff <= minDiff && dp[36][s] != -Double.MAX_VALUE) {
                minDiff = diff;                                                         //记录末旬最小水位误差
                finalState = s;                                                         //记录末旬最小水位误差对应的状态水位索引
            }
        }
        if (finalState == -1) {
            throw new RuntimeException("未找到满足末水位840m的可行解，请检查约束逻辑");
        }

        // 2. 从第36旬回溯到第1旬
        int currState = finalState;
        for (int period = PERIOD_COUNT; period >= 1; period--) {
            PeriodBasicData periodData = periodBasicDataList.get(period - 1);
            int prevState = prev[period][currState];

            // 基础数据计算
            double endLevel = stateLevels[currState];
            double startLevel = stateLevels[prevState];
            double endCapacity = excelReader.getCapacityByLevel(endLevel);
            double startCapacity = excelReader.getCapacityByLevel(startLevel);
            double naturalInFlow = periodData.getNaturalInFlow();

            // 计算理论发电流量
            double theoreticalFlow = naturalInFlow - (endCapacity - startCapacity) * 1e8 / (10 * 24 * 3600);

            // ============= 改动点2：计算弃水数据，赋值到结果 =============
            double[] powerWithAbandon = powerCalculator.calculatePowerWithAbandon(startLevel, endLevel, theoreticalFlow);
            double actualFlow = powerWithAbandon[0];
            double abandonFlow = powerWithAbandon[1];
            double actualPower = powerWithAbandon[2];
            double avgTailLevel = excelReader.getTailLevelByFlow(actualFlow); // 按实际流量查尾水位
            // ============= 改动点2结束 =============

            // 构建结果对象（包含弃水数据）
            PeriodResult result = new PeriodResult();
            result.setPeriodIndex(period);
            result.setPowerFlow(actualFlow); // 实际发电流量
            result.setAbandonFlow(abandonFlow); // 弃水流量
            result.setStartLevel(startLevel);
            result.setEndLevel(endLevel);
            result.setStartCapacity(startCapacity);
            result.setEndCapacity(endCapacity);
            result.setAvgTailLevel(avgTailLevel); // 实际流量对应的尾水位
            result.setPowerGeneration(actualPower); // 实际发电量（≤86400）
            result.setActualPowerGen(actualPower); // 实际发电量（与上一致，用于Excel输出）

            resultList.add(result);
            currState = prevState;
        }

        // 反转列表，得到1-36旬的顺序
        List<PeriodResult> finalResult = new ArrayList<>();
        for (int i = resultList.size() - 1; i >= 0; i--) {
            finalResult.add(resultList.get(i));
        }

        return finalResult;
    }

    // 根据水位获取状态索引
    private int getStateIndexByLevel(double level) {
        return (int) ((level - MIN_LEVEL) / LEVEL_STEP);
    }
}
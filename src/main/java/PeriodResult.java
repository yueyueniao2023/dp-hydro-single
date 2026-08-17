import java.util.List;

// 水位-库容实体
class WaterLevelCapacity {
    private double waterLevel; // 水位（m）
    private double capacity;   // 库容（亿m³）

    public WaterLevelCapacity(double waterLevel, double capacity) {
        this.waterLevel = waterLevel;
        this.capacity = capacity;
    }

    // getter/setter
    public double getWaterLevel() { return waterLevel; }
    public double getCapacity() { return capacity; }
}

// 尾水位-流量实体
class TailWaterFlow {
    private double flow;       // 发电流量（m³/s）
    private double tailLevel;  // 尾水位（m）

    public TailWaterFlow(double flow, double tailLevel) {
        this.flow = flow;
        this.tailLevel = tailLevel;
    }

    // getter/setter
    public double getFlow() { return flow; }
    public double getTailLevel() { return tailLevel; }
}

// 逐旬基础数据（输入）
class PeriodBasicData {
    private int periodIndex;       // 旬索引（1-36）
    private double naturalInFlow;  // 天然来水流量（m³/s）
    private int month;             // 所属月份（1-12）

    public PeriodBasicData(int periodIndex, double naturalInFlow, int month) {
        this.periodIndex = periodIndex;
        this.naturalInFlow = naturalInFlow;
        this.month = month;
    }

    // getter/setter
    public int getPeriodIndex() { return periodIndex; }
    public double getNaturalInFlow() { return naturalInFlow; }
    public int getMonth() { return month; }
}

// 逐旬调度结果（输出）
public class PeriodResult {
    private int periodIndex;       // 旬索引
    private double powerFlow;      // 发电流量（m³/s）
    private double startLevel;     // 旬初水位（m）
    private double endLevel;       // 旬末水位（m）
    private double startCapacity;  // 旬初库容（亿m³）
    private double endCapacity;    // 旬末库容（亿m³）
    private double powerGeneration; // 旬发电量（万kWh）
    private double avgTailLevel; // 旬平均尾水位（m）
    private double abandonFlow;      // 弃水流量（m³/s）
    private double actualPowerGen;   // 实际旬发电量（考虑弃水，万kWh）

    // getter/setter
    public void setPeriodIndex(int periodIndex) { this.periodIndex = periodIndex; }
    public void setPowerFlow(double powerFlow) { this.powerFlow = powerFlow; }
    public void setStartLevel(double startLevel) { this.startLevel = startLevel; }
    public void setEndLevel(double endLevel) { this.endLevel = endLevel; }
    public void setStartCapacity(double startCapacity) { this.startCapacity = startCapacity; }
    public void setEndCapacity(double endCapacity) { this.endCapacity = endCapacity; }
    public void setPowerGeneration(double powerGeneration) { this.powerGeneration = powerGeneration; }
    public double getAvgTailLevel() { return avgTailLevel; }
    public void setAvgTailLevel(double avgTailLevel) { this.avgTailLevel = avgTailLevel; }
    public double getAbandonFlow() { return abandonFlow; }
    public void setAbandonFlow(double abandonFlow) { this.abandonFlow = abandonFlow; }
    public double getActualPowerGen() { return actualPowerGen; }
    public void setActualPowerGen(double actualPowerGen) { this.actualPowerGen = actualPowerGen; }

    // 用于Excel输出的getter
    public int getPeriodIndex() { return periodIndex; }
    public double getPowerFlow() { return powerFlow; }
    public double getStartLevel() { return startLevel; }
    public double getEndLevel() { return endLevel; }
    public double getStartCapacity() { return startCapacity; }
    public double getEndCapacity() { return endCapacity; }
    public double getPowerGeneration() { return powerGeneration; }
}
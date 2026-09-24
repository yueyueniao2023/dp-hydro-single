package cascade;

/** 不可变水库参数。库容单位：亿 m³；出力系数对应 P(kW)=A*Q*H。 */
public record Reservoir(
        String name,
        double minLevel, double maxLevel, double levelStep,
        double initialLevel, double terminalLevel,
        double minRelease, double maxRelease, double maxTurbineFlow,
        double installedPowerMw, double powerCoefficient, double headLoss,
        double maxLevelChange,
        LinearCurve levelStorage, LinearCurve releaseTailwater) {

    public Reservoir {
        if (name == null || name.isBlank() || levelStorage == null || releaseTailwater == null) {
            throw new IllegalArgumentException("水库名称和两条曲线不能为空");
        }
        double[] parameters = {minLevel, maxLevel, levelStep, initialLevel, terminalLevel,
                minRelease, maxRelease, maxTurbineFlow, installedPowerMw,
                powerCoefficient, headLoss, maxLevelChange};
        for (double value : parameters) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + "：参数必须为有限数");
        }
        if (maxLevel <= minLevel || levelStep <= 0 || minRelease < 0
                || maxRelease <= 0 || maxRelease < minRelease || maxTurbineFlow <= 0
                || installedPowerMw <= 0 || powerCoefficient <= 0 || headLoss < 0
                || maxLevelChange < 0) {
            throw new IllegalArgumentException(name + "：水位范围、步长或水力参数不合法");
        }
        gridIndex(minLevel, maxLevel, levelStep, maxLevel, name);
        gridIndex(minLevel, maxLevel, levelStep, initialLevel, name);
        gridIndex(minLevel, maxLevel, levelStep, terminalLevel, name);
        if (levelStorage.minX() > minLevel || levelStorage.maxX() < maxLevel
                || releaseTailwater.minX() > minRelease || releaseTailwater.maxX() < maxRelease) {
            throw new IllegalArgumentException(name + "：曲线未完整覆盖允许水位或总泄流范围");
        }
        levelStorage.requireIncreasingValues(true, name + "水位—库容曲线");
        releaseTailwater.requireIncreasingValues(false, name + "泄流—尾水位曲线");
        if (levelStorage.at(minLevel) < 0) throw new IllegalArgumentException(name + "：库容不能为负");
    }

    /** 初末边界必须落在网格上，不能强制取整或默许容差水位。 */
    public int indexOf(double level) {
        return gridIndex(minLevel, maxLevel, levelStep, level, name);
    }

    public double[] levels() {
        int count = indexOf(maxLevel) + 1;
        double[] result = new double[count];
        for (int i = 0; i < count; i++) result[i] = minLevel + i * levelStep;
        result[count - 1] = maxLevel; // 消除浮点乘法在端点的微小误差。
        return result;
    }

    private static int gridIndex(double min, double max, double step, double level, String name) {
        double index = (level - min) / step;
        double rounded = Math.rint(index);
        if (!Double.isFinite(level) || level < min || level > max || !Double.isFinite(index)
                || Math.abs(index - rounded) > 1e-8 || rounded > Integer.MAX_VALUE - 1) {
            throw new IllegalArgumentException(name + "：水位 " + level + " 不在离散网格上");
        }
        return (int) rounded;
    }
}

package cascade;

/** 水量平衡、单期约束与发电计算；DP递推和回溯使用同一套物理公式。 */
public final class HydroPhysics {
    private static final double STORAGE_TO_M3 = 1e8;
    private static final double EPS = 1e-8;

    private HydroPhysics() { }

    /**
     * 返回该转移的全部物理量；违反运行约束时返回 null。
     * days可随时段变化；水位上限同时约束期初和期末。
     */
    public static StationOperation evaluate(Reservoir r, double startLevel, double endLevel,
                                            double inflow, double days, double levelLimit) {
        if (r == null || !Double.isFinite(startLevel) || !Double.isFinite(endLevel)
                || !Double.isFinite(inflow) || inflow < 0 || !Double.isFinite(days) || days <= 0
                || !Double.isFinite(days * 86400.0) || !Double.isFinite(levelLimit)) {
            throw new IllegalArgumentException("水力计算输入不合法");
        }
        if (startLevel < r.minLevel() || endLevel < r.minLevel()
                || startLevel > r.maxLevel() || endLevel > r.maxLevel()
                || startLevel > levelLimit + EPS || endLevel > levelLimit + EPS
                || Math.abs(endLevel - startLevel) > r.maxLevelChange() + EPS) return null;

        double startStorage = r.levelStorage().at(startLevel);
        double endStorage = r.levelStorage().at(endLevel);
        double seconds = days * 86400.0;

        // 水量平衡反算的是【总泄流】，并非机组发电流量。
        double release = inflow + (startStorage - endStorage) * STORAGE_TO_M3 / seconds;
        if (!Double.isFinite(release) || release < r.minRelease()
                || release > r.maxRelease()) return null;
        // 不截断反算泄流：否则会改变给定初末库容对应的水量平衡。

        // 尾水位取决于总泄流（假定发电尾水与弃水汇入同一河道）。
        double tailwater = r.releaseTailwater().at(release);
        double grossHead = (startLevel + endLevel) / 2.0 - tailwater;
        // 本模型没有抽水设施，非正毛水头不能维持正向重力泄流。
        if (grossHead <= 0 && release > EPS) return null;
        double head = grossHead - r.headLoss();
        double turbineFlow = 0.0;
        if (head > 0) {
            double capacityFlow = r.installedPowerMw() * 1000.0 / (r.powerCoefficient() * head);
            turbineFlow = Math.min(release, Math.min(r.maxTurbineFlow(), capacityFlow));
        }
        // 净水头非正时无发电，总泄流仍作为弃水传入下游。
        double spill = release - turbineFlow;
        double powerMw = head > 0 ? r.powerCoefficient() * turbineFlow * head / 1000.0 : 0.0;
        double energyMwh = powerMw * days * 24.0;
        if (!Double.isFinite(energyMwh)) throw new IllegalArgumentException("发电量计算溢出，请检查参数量级");
        double residual = (endStorage - startStorage) * STORAGE_TO_M3 - (inflow - release) * seconds;
        return new StationOperation(startLevel, endLevel, startStorage, endStorage,
                inflow, release, turbineFlow, spill, tailwater, head, powerMw, energyMwh, residual);
    }
}

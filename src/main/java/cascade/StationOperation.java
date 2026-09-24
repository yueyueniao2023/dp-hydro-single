package cascade;

/** 单库单期的物理量；库容为亿m³，流量为m³/s，出力MW，电量MWh。 */
public record StationOperation(
        double startLevel, double endLevel, double startStorage, double endStorage,
        double inflow, double release, double turbineFlow, double spill,
        double tailwater, double head, double powerMw, double energyMwh,
        double balanceResidualM3) {
}

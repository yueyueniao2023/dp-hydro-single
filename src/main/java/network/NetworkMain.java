package network;

import java.nio.file.Path;
import java.util.Locale;
import cascade.StationOperation;

public final class NetworkMain {
    private NetworkMain() { }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("用法：network.NetworkMain [输入目录] [输出CSV]；默认 data/network results/network/dispatch.csv");
            return;
        }
        if (args.length > 2 || (args.length > 0 && args[0].startsWith("--")))
            throw new IllegalArgumentException("用法：network.NetworkMain [输入目录] [输出CSV]");
        NetworkProblem problem = NetworkDataReader.read(Path.of(args.length > 0 ? args[0] : "data/network"));
        NetworkDp dp = new NetworkDp(problem);
        System.out.println("教学合成数据，非真实电站资料；拓扑顺序：" + problem.network().topologicalOrder());
        System.out.printf("时段：%d；状态：%d；候选联合转移上界：%d%n",
                problem.periods().size(), dp.stateCount(), dp.candidateUpperBound());
        NetworkDp.Result result = dp.solve();
        Path output = Path.of(args.length > 1 ? args[1] : "results/network/dispatch.csv");
        NetworkCsvWriter.write(output, result);
        double maxResidual = 0;
        for (String id : NetworkProblem.IDS) {
            double energy = 0;
            for (NetworkDp.Stage stage : result.periods()) {
                StationOperation op = stage.operations().get(id);
                energy += op.energyMwh();
                maxResidual = Math.max(maxResidual, Math.abs(op.balanceResidualM3()));
            }
            StationOperation last = result.periods().get(result.periods().size() - 1).operations().get(id);
            System.out.printf(Locale.ROOT, "%s：%.9f MWh；末水位 %.3f m%n", id, energy, last.endLevel());
        }
        System.out.printf(Locale.ROOT, "四库总发电量：%.9f MWh；最大水量平衡残差：%.3e m3%n",
                result.totalEnergyMwh(), maxResidual);
        System.out.println("输出：" + output);
    }
}

package cascade;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** 双库串联调度入口；单库 Main 的代码和输入文件保持独立。 */
public final class CascadeMain {
    private CascadeMain() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            printUsage();
            return;
        }
        if (args.length > 2 || (args.length > 0 && args[0].startsWith("--"))) {
            throw new IllegalArgumentException("用法：cascade.CascadeMain [输入目录] [输出CSV路径]；使用 --help 查看说明");
        }
        Path inputDirectory = Path.of(args.length >= 1 ? args[0] : "data/cascade");
        Path outputFile = Path.of(args.length >= 2 ? args[1] : "results/cascade/dispatch.csv");
        CascadeProblem problem = CascadeDataReader.read(inputDirectory);
        System.out.printf(Locale.ROOT, "串联双库：%s → %s，共 %d 期，联合状态数 %d × %d = %d%n",
                problem.upstream().name(), problem.downstream().name(), problem.periods().size(),
                problem.upstream().levels().length, problem.downstream().levels().length,
                problem.upstream().levels().length * problem.downstream().levels().length);
        DispatchResult result = new CascadeDp(problem).solve();
        CascadeCsvWriter.write(outputFile, result);
        double upstreamEnergy = 0;
        double downstreamEnergy = 0;
        double maxBalanceResidual = 0;
        double maxCouplingResidual = 0;
        for (CascadePeriodResult period : result.periods()) {
            upstreamEnergy += period.upstream().energyMwh();
            downstreamEnergy += period.downstream().energyMwh();
            maxBalanceResidual = Math.max(maxBalanceResidual, Math.abs(period.upstream().balanceResidualM3()));
            maxBalanceResidual = Math.max(maxBalanceResidual, Math.abs(period.downstream().balanceResidualM3()));
            maxCouplingResidual = Math.max(maxCouplingResidual,
                    Math.abs(period.downstream().inflow() - period.upstream().release() - period.input().intervalInflow()));
        }
        CascadePeriodResult last = result.periods().get(result.periods().size() - 1);
        System.out.printf(Locale.ROOT, "上游发电量：%.3f MWh；下游发电量：%.3f MWh%n", upstreamEnergy, downstreamEnergy);
        System.out.printf(Locale.ROOT, "梯级总发电量：%.3f MWh = %.3f 万kWh%n", result.totalEnergyMwh(), result.totalEnergyMwh() / 10.0);
        System.out.printf(Locale.ROOT, "严格期末水位：上游 %.3f m，下游 %.3f m%n", last.upstream().endLevel(), last.downstream().endLevel());
        System.out.printf(Locale.ROOT, "最大水量平衡残差：%.3e m³；最大上下游耦合残差：%.3e m³/s%n", maxBalanceResidual, maxCouplingResidual);
        System.out.println("结果已写入：" + outputFile.toAbsolutePath().normalize());
        System.out.println("示例数据为人工合成，仅用于理解串联双库动态规划。阅读 docs/cascade-tutorial.md 查看公式与代码导读。");
    }

    private static void printUsage() {
        System.out.println("用法：cascade.CascadeMain [输入目录] [输出CSV路径]");
        System.out.println("默认输入：data/cascade；默认输出：results/cascade/dispatch.csv");
        System.out.println("输入需含 reservoirs.properties、四个曲线CSV和 inflows.csv（详见 docs/cascade-tutorial.md）。");
        System.out.println("Maven：mvn compile exec:java \"-Dexec.mainClass=cascade.CascadeMain\"");
        System.out.println("自定义：追加 \"-Dexec.args=data/cascade results/cascade/dispatch.csv\"");
    }
}

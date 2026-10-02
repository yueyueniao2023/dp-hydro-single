import java.util.*;
import java.nio.file.*;

/** 原单库入口：Excel输入 → 精确离散DP → 独立目录中的CSV及Excel。 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) throw new IllegalArgumentException("单库Main无需参数；请在仓库根目录运行");
        ExcelReader reader = new ExcelReader();
        reader.init();
        DynamicProgramming dp = new DynamicProgramming(reader);
        List<PeriodResult> result = dp.solve();
        SingleCsvWriter.write(Path.of("results/single/dispatch.csv"), result);
        new ExcelWriter().writeResult(result);
        System.out.printf(Locale.ROOT, "单库：%d期，%.0f天；总发电量 %.9f MWh（%.6f 万kWh）%n",
                result.size(), result.stream().mapToDouble(PeriodResult::getDays).sum(),
                dp.optimumMwh(), dp.optimumMwh() / 10);
        System.out.printf(Locale.ROOT, "严格初末水位：%.3f → %.3f m%n",
                result.get(0).getStartLevel(), result.get(result.size() - 1).getEndLevel());
    }
}

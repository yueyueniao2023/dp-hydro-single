import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. 读取Excel数据
            ExcelReader excelReader = new ExcelReader();
            excelReader.init();
            System.out.println("数据读取完成");

            // 2. 执行动态规划求解
            DynamicProgramming dp = new DynamicProgramming(excelReader);            //传入excelReader，通过DynamicProgramming（）方法进行初始化
            List<PeriodResult> bestResult = dp.solve();
            System.out.println("DP求解完成");

            // 3. 输出结果到Excel
            ExcelWriter excelWriter = new ExcelWriter();
            excelWriter.writeResult(bestResult);

            // 4. 打印总发电量
            double totalPower = 0;
            for (PeriodResult result : bestResult) {
                totalPower += result.getPowerGeneration();
            }
            System.out.println("年总发电量：" + String.format("%.2f", totalPower) + "万kWh");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}


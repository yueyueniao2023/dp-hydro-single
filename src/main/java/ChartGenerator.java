import org.apache.poi.ss.usermodel.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.Font;  // 新增：导入字体类
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class ChartGenerator {
    private static final String ORIGIN_EXCEL_PATH = "C:\\1.保研人的大四\\入门案例（两周-截止11月16号）\\水位库容关系&尾水位流量关系&来水过程&实际水位过程.xlsx";
    private static final String OPTIMAL_EXCEL_PATH = "水电站最优调度结果.xlsx";
    private static final String CHART_OUTPUT_PATH = "调度结果对比图/";

    private ExcelReader excelReader;
    private PowerCalculator powerCalculator;

    // 存储36旬数据的实体类
    static class PeriodData {
        int periodIndex; // 旬索引
        double optimalLevel; // 最优旬末水位（m）
        double actualLevel; // 实际旬末水位（m）
        double optimalFlow; // 最优发电流量（m³/s）
        double actualFlow; // 实际发电流量（m³/s）
        double optimalPower; // 最优旬发电量（万kWh）
        double actualPower; // 实际旬发电量（万kWh）
        double powerIncrease; // 发电量提升量（万kWh）

        public PeriodData(int periodIndex) {
            this.periodIndex = periodIndex;
        }
    }

    public ChartGenerator() throws Exception {
        // 初始化现有工具类（复用数据读取和发电量计算逻辑）
        excelReader = new ExcelReader();
        excelReader.init();
        powerCalculator = new PowerCalculator(excelReader);

        // 创建图片输出目录（不存在则自动创建）
        java.io.File dir = new java.io.File(CHART_OUTPUT_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // 核心方法：生成所有4类对比图
    public void generateAllCharts() throws Exception {
        // 1. 读取所有数据（最优+实际）
        List<PeriodData> allData = loadAllPeriodData();

        // 2. 绘制4类图表
        drawWaterLevelChart(allData);
        drawFlowChart(allData);
        drawPowerChart(allData);
        drawPowerIncreaseChart(allData);

        System.out.println("所有对比图已生成至：" + CHART_OUTPUT_PATH);
    }

    // 读取最优调度结果和实际数据，计算对比所需指标
    private List<PeriodData> loadAllPeriodData() throws Exception {
        List<PeriodData> dataList = new ArrayList<>();

        // 第一步：读取最优调度结果（Excel）
        List<OptimalPeriodData> optimalDataList = loadOptimalData();

        // 第二步：读取实际数据（原始Excel的sheet1来水、sheet4实际水位）
        List<ActualPeriodData> actualDataList = loadActualData();

        // 第三步：数据对齐与计算（36旬一一对应）
        for (int i = 0; i < 36; i++) {
            int periodIndex = i + 1;
            PeriodData periodData = new PeriodData(periodIndex);

            // 最优数据赋值
            OptimalPeriodData optimal = optimalDataList.get(i);
            periodData.optimalLevel = optimal.endLevel;
            periodData.optimalFlow = optimal.powerFlow;
            periodData.optimalPower = optimal.powerGeneration;

            // 实际数据计算与赋值
            ActualPeriodData actual = actualDataList.get(i);
            periodData.actualLevel = actual.endLevel;
            periodData.actualFlow = actual.calcActualFlow(); // 按水量平衡反推实际出流
            periodData.actualPower = calcActualPower(actual, periodData.actualFlow); // 计算实际发电量
            periodData.powerIncrease = periodData.optimalPower - periodData.actualPower; // 提升量

            dataList.add(periodData);
        }

        return dataList;
    }

    // 读取最优调度结果Excel
// 读取最优调度结果Excel（修正列索引+增加数值校验）
    private List<OptimalPeriodData> loadOptimalData() throws Exception {
        List<OptimalPeriodData> list = new ArrayList<>();
        Workbook workbook = WorkbookFactory.create(new FileInputStream(OPTIMAL_EXCEL_PATH));
        Sheet sheet = workbook.getSheetAt(0); // 第一个sheet是最优结果
        // 跳过表头（第0行），读取1-36行（36旬）
        for (int rowNum = 1; rowNum <= 36; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;
            OptimalPeriodData data = new OptimalPeriodData();

            // 1. 旬索引（第0列，正确）
            Cell indexCell = row.getCell(0);
            checkNumericCell(indexCell, rowNum, 0, "旬索引");
            data.periodIndex = (int) indexCell.getNumericCellValue();

            // 2. 发电流量（第1列，正确，用户出流图无问题）
            Cell flowCell = row.getCell(1);
            checkNumericCell(flowCell, rowNum, 1, "发电流量");
            data.powerFlow = flowCell.getNumericCellValue();

            // 3. 最优旬末水位（修正为第4列，原错误读第3列）
            Cell endLevelCell = row.getCell(4);
            checkNumericCell(endLevelCell, rowNum, 4, "旬末水位");
            data.endLevel = endLevelCell.getNumericCellValue();

            // 4. 最优发电量（修正为第8列，原错误读第7列）
            // 注：ExcelWriter中第8列是"理论发电量"，第9列是"实际发电量"，两者数值一致，取任一均可
            Cell powerCell = row.getCell(8);
            checkNumericCell(powerCell, rowNum, 8, "理论发电量");
            data.powerGeneration = powerCell.getNumericCellValue();

            list.add(data);
        }
        workbook.close();
        return list;
    }

    // 辅助方法：校验单元格是否为数值类型（避免读取异常）
    private void checkNumericCell(Cell cell, int rowNum, int colIndex, String colName) {
        if (cell == null) {
            throw new RuntimeException("第" + (rowNum + 1) + "行，第" + (colIndex + 1) + "列（" + colName + "）：单元格不存在！");
        }
        if (cell.getCellType() != CellType.NUMERIC) {
            throw new RuntimeException("第" + (rowNum + 1) + "行，第" + (colIndex + 1) + "列（" + colName + "）：单元格不是数值类型！");
        }
    }

    // 读取原始Excel的实际数据（sheet1来水 + sheet4实际水位）
    private List<ActualPeriodData> loadActualData() throws Exception {
        List<ActualPeriodData> list = new ArrayList<>();
        Workbook workbook = WorkbookFactory.create(new FileInputStream(ORIGIN_EXCEL_PATH));

        // 1. 读取sheet1（来水过程，36旬）
        Sheet inflowSheet = workbook.getSheetAt(0);
        List<Double> inflowList = new ArrayList<>();
        for (int rowNum = 1; rowNum <= 36; rowNum++) { // C2-C37对应行1-36
            Row row = inflowSheet.getRow(rowNum);
            double inflow = row.getCell(2).getNumericCellValue();
            inflowList.add(inflow);
        }

        // 2. 读取sheet4（实际水位过程，36旬）
        Sheet actualLevelSheet = workbook.getSheetAt(3);
        int periodIndex = 1;
        for (int rowNum = 1; rowNum <= 36; rowNum++) { // 从第1行开始（跳过表头行）
            Row endLevelRow = actualLevelSheet.getRow(rowNum);
            if (endLevelRow == null) continue;

            // 实际旬初水位（第一旬初为830m，后续为上一旬末水位）
            double startLevel = (periodIndex == 1) ? 830.0 : list.get(periodIndex - 2).endLevel;

            // 读取旬末水位（D列，索引3），并处理单元格类型
            Cell endLevelCell = endLevelRow.getCell(3);
            if (endLevelCell == null) {
                throw new RuntimeException("第" + rowNum + "行，列3：未找到实际旬末水位单元格！");
            }
            // 先判断单元格类型，确保是数值类型
            if (endLevelCell.getCellType() != CellType.NUMERIC) {
                throw new RuntimeException("第" + rowNum + "行，列3：单元格类型不是数值，无法读取水位！");
            }
            double endLevel = endLevelCell.getNumericCellValue();

            ActualPeriodData data = new ActualPeriodData();
            data.periodIndex = periodIndex;
            data.startLevel = startLevel;
            data.endLevel = endLevel;
            data.naturalInFlow = inflowList.get(periodIndex - 1); // 对应旬来水
            list.add(data);

            periodIndex++;
        }

        workbook.close();
        return list;
    }

    // 计算实际旬发电量（复用PowerCalculator逻辑）
// 计算实际旬发电量（复用PowerCalculator逻辑，新增发电量上限约束）
    private double calcActualPower(ActualPeriodData actualData, double actualFlow) {
        double avgReservoirLevel = (actualData.startLevel + actualData.endLevel) / 2;
        double avgTailLevel = excelReader.getTailLevelByFlow(actualFlow);
        double avgHead = avgReservoirLevel - avgTailLevel;
        double avgPower = PowerCalculator.A * actualFlow * avgHead;
        // 计算原始发电量（未截断）
        double actualPower = avgPower * PowerCalculator.TEN_DAY_HOURS * PowerCalculator.UNIT_CONVERT;
        // 新增：应用发电量上限约束（不超过86400万kWh）
        return Math.min(actualPower, PowerCalculator.MAX_PERIOD_POWER);
    }

    // ========== 1. 水位过程对比图（解决中文乱码+完善要素） ==========
    private void drawWaterLevelChart(List<PeriodData> dataList) throws Exception {
        // 1. 定义曲线含义（明确标注）
        XYSeries optimalSeries = new XYSeries("DP最优调度系列");
        XYSeries actualSeries = new XYSeries("基准调度系列");

        // 2. 计算水位实际范围（消除空白）
        double minLevel = Double.MAX_VALUE;
        double maxLevel = Double.MIN_VALUE;
        for (PeriodData data : dataList) {
            optimalSeries.add(data.periodIndex, data.optimalLevel);
            actualSeries.add(data.periodIndex, data.actualLevel);
            minLevel = Math.min(minLevel, Math.min(data.optimalLevel, data.actualLevel));
            maxLevel = Math.max(maxLevel, Math.max(data.optimalLevel, data.actualLevel));
        }

        // 3. 构建数据集
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(optimalSeries);
        dataset.addSeries(actualSeries);

        // 4. 创建图表（明确图名+轴物理量）
        JFreeChart chart = ChartFactory.createXYLineChart(
                "逐旬库水位变化对比图",
                "旬索引（1-36旬）",
                "库水位（m）",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        // 5. 设置中文字体（解决方块乱码）
        Font titleFont = new Font("SimHei", Font.BOLD, 14); // 标题：黑体+加粗
        Font labelFont = new Font("SimHei", Font.PLAIN, 12); // 标签：黑体
        chart.getTitle().setFont(titleFont);
        chart.getLegend().setItemFont(labelFont);

        // 6. 美化曲线+轴设置
        XYPlot plot = chart.getXYPlot();
        // 轴标签字体
        plot.getDomainAxis().setLabelFont(labelFont);
        plot.getRangeAxis().setLabelFont(labelFont);
        // 曲线样式（红色=最优，蓝色=基准）
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, java.awt.Color.RED);
        renderer.setSeriesPaint(1, java.awt.Color.BLUE);
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new java.awt.BasicStroke(2.0f));
        plot.setRenderer(renderer);
        // 纵轴范围（贴合实际水位）
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(minLevel - 1, maxLevel + 1);
        yAxis.setAutoRange(false);

        // 7. 保存图片
        ChartUtils.saveChartAsPNG(
                new java.io.File(CHART_OUTPUT_PATH + "水位过程对比图.png"),
                chart, 800, 500
        );
    }

    // ========== 2. 出流过程对比图 ==========
    private void drawFlowChart(List<PeriodData> dataList) throws Exception {
        XYSeries optimalSeries = new XYSeries("DP最优调度系列");
        XYSeries actualSeries = new XYSeries("基准调度系列");

        double minFlow = Double.MAX_VALUE;
        double maxFlow = Double.MIN_VALUE;
        for (PeriodData data : dataList) {
            optimalSeries.add(data.periodIndex, data.optimalFlow);
            actualSeries.add(data.periodIndex, data.actualFlow);
            minFlow = Math.min(minFlow, Math.min(data.optimalFlow, data.actualFlow));
            maxFlow = Math.max(maxFlow, Math.max(data.optimalFlow, data.actualFlow));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(optimalSeries);
        dataset.addSeries(actualSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "逐旬发电流量对比图",
                "旬索引（1-36旬）",
                "发电流量（m³/s）",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        // 中文字体设置
        Font titleFont = new Font("SimHei", Font.BOLD, 14);
        Font labelFont = new Font("SimHei", Font.PLAIN, 12);
        chart.getTitle().setFont(titleFont);
        chart.getLegend().setItemFont(labelFont);

        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setLabelFont(labelFont);
        plot.getRangeAxis().setLabelFont(labelFont);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, java.awt.Color.RED);
        renderer.setSeriesPaint(1, java.awt.Color.BLUE);
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new java.awt.BasicStroke(2.0f));
        plot.setRenderer(renderer);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(Math.max(0, minFlow - 10), maxFlow + 10);
        yAxis.setAutoRange(false);

        ChartUtils.saveChartAsPNG(
                new java.io.File(CHART_OUTPUT_PATH + "出流过程对比图.png"),
                chart, 800, 500
        );
    }

    // ========== 3. 发电量对比图 ==========
    private void drawPowerChart(List<PeriodData> dataList) throws Exception {
        XYSeries optimalSeries = new XYSeries("DP最优调度系列");
        XYSeries actualSeries = new XYSeries("基准调度系列");

        double minPower = Double.MAX_VALUE;
        double maxPower = Double.MIN_VALUE;
        for (PeriodData data : dataList) {
            optimalSeries.add(data.periodIndex, data.optimalPower);
            actualSeries.add(data.periodIndex, data.actualPower);
            minPower = Math.min(minPower, Math.min(data.optimalPower, data.actualPower));
            maxPower = Math.max(maxPower, Math.max(data.optimalPower, data.actualPower));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(optimalSeries);
        dataset.addSeries(actualSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "逐旬发电量对比图",
                "旬索引（1-36旬）",
                "旬发电量（万kWh）",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        Font titleFont = new Font("SimHei", Font.BOLD, 14);
        Font labelFont = new Font("SimHei", Font.PLAIN, 12);
        chart.getTitle().setFont(titleFont);
        chart.getLegend().setItemFont(labelFont);

        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setLabelFont(labelFont);
        plot.getRangeAxis().setLabelFont(labelFont);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, java.awt.Color.RED);
        renderer.setSeriesPaint(1, java.awt.Color.BLUE);
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new java.awt.BasicStroke(2.0f));
        plot.setRenderer(renderer);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(Math.max(0, minPower - 10), maxPower + 10);
        yAxis.setAutoRange(false);

        ChartUtils.saveChartAsPNG(
                new java.io.File(CHART_OUTPUT_PATH + "发电量对比图.png"),
                chart, 800, 500
        );
    }

    // ========== 4. 发电量提升图 ==========
    private void drawPowerIncreaseChart(List<PeriodData> dataList) throws Exception {
        XYSeries perPeriodIncrease = new XYSeries("每旬提升量");
        XYSeries totalIncrease = new XYSeries("累计提升量");

        double total = 0;
        double minIncrease = Double.MAX_VALUE;
        double maxIncrease = Double.MIN_VALUE;
        for (PeriodData data : dataList) {
            total += data.powerIncrease;
            perPeriodIncrease.add(data.periodIndex, data.powerIncrease);
            totalIncrease.add(data.periodIndex, total);
            minIncrease = Math.min(minIncrease, Math.min(data.powerIncrease, total));
            maxIncrease = Math.max(maxIncrease, Math.max(data.powerIncrease, total));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(perPeriodIncrease);
        dataset.addSeries(totalIncrease);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "发电量提升对比图",
                "旬索引（1-36旬）",
                "发电量（万kWh）",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        Font titleFont = new Font("SimHei", Font.BOLD, 14);
        Font labelFont = new Font("SimHei", Font.PLAIN, 12);
        chart.getTitle().setFont(titleFont);
        chart.getLegend().setItemFont(labelFont);

        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setLabelFont(labelFont);
        plot.getRangeAxis().setLabelFont(labelFont);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, java.awt.Color.ORANGE);
        renderer.setSeriesPaint(1, java.awt.Color.GREEN);
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new java.awt.BasicStroke(2.0f));
        plot.setRenderer(renderer);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(minIncrease - 5, maxIncrease + 5);
        yAxis.setAutoRange(false);

        ChartUtils.saveChartAsPNG(
                new java.io.File(CHART_OUTPUT_PATH + "发电量提升图.png"),
                chart, 800, 500
        );
    }

    // 辅助类：最优调度结果数据封装
    static class OptimalPeriodData {
        int periodIndex;
        double powerFlow;
        double endLevel;
        double powerGeneration;
    }

    // 辅助类：实际数据封装（含水量平衡计算）
    class ActualPeriodData {
        int periodIndex;
        double startLevel; // 实际旬初水位
        double endLevel; // 实际旬末水位
        double naturalInFlow; // 实际天然来水

        // 按水量平衡反推实际发电流量（出流）
// 按水量平衡反推实际发电流量（出流），新增流量约束
        public double calcActualFlow() {
            double startCapacity = excelReader.getCapacityByLevel(startLevel);
            double endCapacity = excelReader.getCapacityByLevel(endLevel);
            // 水量平衡公式：Q实际 = 来水 - (旬末库容 - 旬初库容)*1e8/(10*24*3600)
            double flow = naturalInFlow - (endCapacity - startCapacity) * 1e8 / (10 * 24 * 3600);
            // 新增：应用流量约束（最小188，最大5000m³/s）
            flow = Math.max(ConstraintChecker.MIN_FLOW, Math.min(flow, ConstraintChecker.MAX_FLOW));
            return flow;
        }
    }

    // 测试方法：直接运行生成图表
    public static void main(String[] args) {
        try {
            ChartGenerator generator = new ChartGenerator();
            generator.generateAllCharts();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
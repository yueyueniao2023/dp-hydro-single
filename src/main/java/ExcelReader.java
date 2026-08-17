import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ExcelReader {
    private static final String FILE_PATH = "C:\\1.保研人的大四\\入门案例（两周-截止11月16号）\\水位库容关系&尾水位流量关系&来水过程&实际水位过程.xlsx";
    private List<WaterLevelCapacity> levelCapacityList = new ArrayList<>();
    private List<TailWaterFlow> tailWaterFlowList = new ArrayList<>();
    private List<PeriodBasicData> periodBasicDataList = new ArrayList<>();

    // 初始化：读取所有sheet数据
    public void init() throws Exception {
        readWaterLevelCapacity();
        readTailWaterFlow();
        readNaturalInFlow();
    }

    // 读取水位-库容关系（sheet2）
    private void readWaterLevelCapacity() throws Exception {
        Workbook workbook = WorkbookFactory.create(new FileInputStream(FILE_PATH));
        Sheet sheet = workbook.getSheetAt(1); // sheet2：水位库容关系

        // 读取整数水位（A列）和小数位（第一行）
        Row decimalRow = sheet.getRow(0); // 第一行：小数位0-0.9;decimal指小数
        for (int i = 4; i <= sheet.getLastRowNum(); i++) { // A5开始是整数水位；sheet.getLastRowNum()：获取 Sheet 的最后一行索引（即 Excel 中数据的最后一行行号 - 1）
            Row row = sheet.getRow(i);
//            if (row == null) continue;//空值判断，若为空，当前循环的后续语句跳过，可节省资源；
            Cell integerCell = row.getCell(0); // 整数水位（785,786,...）；integer指整数
//            if (integerCell == null) continue;

            double integerLevel = integerCell.getNumericCellValue();//变量integerLevel存储水位的整数部分
            for (int j = 1; j <= 10; j++) { // B-K列：对应小数位0-0.9
                Cell decimalCell = decimalRow.getCell(j);
                Cell capacityCell = row.getCell(j);
                if (decimalCell == null || capacityCell == null) continue;

                double decimal = decimalCell.getNumericCellValue();
                double waterLevel = integerLevel + decimal;
                double capacity = capacityCell.getNumericCellValue();
                levelCapacityList.add(new WaterLevelCapacity(waterLevel, capacity));
            }
        }
        workbook.close();
    }

    // 读取尾水位-流量关系（sheet3）
    private void readTailWaterFlow() throws Exception {
        Workbook workbook = WorkbookFactory.create(new FileInputStream(FILE_PATH));
        Sheet sheet = workbook.getSheetAt(2); // sheet3：尾水位流量关系

        Row decimalRow = sheet.getRow(0); // 第一行：小数位0-0.9
        for (int i = 4; i <= sheet.getLastRowNum(); i++) { // A5开始是整数水位
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell integerCell = row.getCell(0); // 整数尾水位（667,668,...）
            if (integerCell == null) continue;

            double integerLevel = integerCell.getNumericCellValue();
            for (int j = 1; j <= 10; j++) { // B-K列：对应流量值
                Cell decimalCell = decimalRow.getCell(j);
                Cell flowCell = row.getCell(j);
                if (decimalCell == null || flowCell == null) continue;

                double decimal = decimalCell.getNumericCellValue();
                double tailLevel = integerLevel + decimal;
                double flow = flowCell.getNumericCellValue();
                if (flow > 0) { // 过滤无效数据
                    tailWaterFlowList.add(new TailWaterFlow(flow, tailLevel));
                    /*
                    * new TailWaterFlow(flow, tailLevel) 执行完后，会返回一个 “已经装好了数据的 TailWaterFlow 对象”（就是那个 “打包好的小盒子”）。
后续的 tailWaterFlowList.add(...) ，就是把这个 “装了数据的小盒子” 放进 tailWaterFlowList 集合中。这样一来：
集合中存储的不是零散的 flow 和 tailLevel 两个值，而是 “一组关联的数据对象”（不会出现 “流量和尾水位对应错乱” 的问题）；
后续要使用这组数据时，只要从集合中取出这个对象，通过 getFlow() 和 getTailLevel() 就能精准拿到对应的流量和尾水位（比如根据流量查尾水位、遍历所有有效数据等）。
                    * */
                }
            }
        }
        workbook.close();
    }
    // 读取逐旬天然来水（sheet1）
    private void readNaturalInFlow() throws Exception {
        Workbook workbook = WorkbookFactory.create(new FileInputStream(FILE_PATH));
        Sheet sheet = workbook.getSheetAt(0); // sheet1：来水过程

        int periodIndex = 1; // 第1旬到第36旬
        // C2至C37对应行索引1至36（Excel行从0开始计数）
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row currentRow = sheet.getRow(rowIndex);
//            if (currentRow == null) {
//                throw new Exception("来水过程表中第" + (rowIndex + 1) + "行数据不存在");
//            }
            // C列对应索引2（Excel列从0开始计数：A=0,B=1,C=2）
            Cell flowCell = currentRow.getCell(2);
//            if (flowCell == null) {
//                throw new Exception("来水过程表中C" + (rowIndex + 1) + "单元格数据不存在");
//            }
            double naturalInFlow = flowCell.getNumericCellValue();
            // 计算当前旬对应的月份（1-3旬→1月，4-6旬→2月...34-36旬→12月）
            int month = (periodIndex - 1) / 3 + 1;
            periodBasicDataList.add(new PeriodBasicData(periodIndex++, naturalInFlow, month));
        }
        workbook.close();//关闭 Excel 文件，释放资源。
    }

    // 插值查询：根据发电流量查尾水位（修改）
    public double getTailLevelByFlow(double flow) {
        // 专门处理TailWaterFlow的插值，传入标识区分类型
        return linearInterpolation(tailWaterFlowList, flow, true, false);
    }

    // 插值查询：根据水位查库容（修改）
    public double getCapacityByLevel(double waterLevel) {
        return linearInterpolation(levelCapacityList, waterLevel, true, true);
    }

    // 插值查询：根据库容查水位（修改）
    public double getLevelByCapacity(double capacity) {
        return linearInterpolation(levelCapacityList, capacity, false, true);
    }

    // 通用线性插值方法（完全重构，适配两种实体类）
    private double linearInterpolation(List<?> dataList, double target, boolean isTargetX, boolean isWaterLevelCapacity) {
        // 1. 先将列表转换为有序的(x,y)对
        List<Double[]> xyList = new ArrayList<>();
        for (Object obj : dataList) {//遍历传入集合（水位-库容、或者尾水位-流量）全部元素
            double x, y;
            if (isWaterLevelCapacity) {
                // 处理WaterLevelCapacity：x=水位/y=库容，或x=库容/y=水位
                WaterLevelCapacity wlc = (WaterLevelCapacity) obj;
                if (isTargetX) {
                    x = wlc.getWaterLevel();
                    y = wlc.getCapacity();
                } else {
                    x = wlc.getCapacity();
                    y = wlc.getWaterLevel();
                }
            } else {
                // 处理TailWaterFlow：x=流量，y=尾水位
                TailWaterFlow twf = (TailWaterFlow) obj;
                x = twf.getFlow();
                y = twf.getTailLevel();
            }
            xyList.add(new Double[]{x, y});//确定好x、y后传入xyList，这一步实际上就是将集合（水位-库容、或者尾水位-流量）拆解为有序对(x,y)
        }

        // 2. 对xyList按x值（自变量）升序排序。线性插值的前提是 “自变量 x 必须有序”，否则无法找到目标值所在的区间；
        xyList.sort(Comparator.comparingDouble(arr -> arr[0]));
        //Comparator.comparingDouble(arr -> arr[0])的作用：定义了排序变量————数组的第 0 位（x 值）；
        //sort（）是以升序排列

        // 3. 线性插值
        for (int i = 0; i < xyList.size() - 1; i++) {
            Double[] curr = xyList.get(i);
            Double[] next = xyList.get(i + 1);
            double x1 = curr[0], y1 = curr[1];
            double x2 = next[0], y2 = next[1];

            if (target >= x1 && target <= x2) {
                return y1 + (target - x1) * (y2 - y1) / (x2 - x1);
            }
        }

        // 若目标值超出范围，取边界值（避免抛出异常导致程序终止）
        if (xyList.size() == 0) {
            throw new IllegalArgumentException("插值数据列表为空");
        }
        Double[] first = xyList.get(0);
        Double[] last = xyList.get(xyList.size() - 1);
        return target < first[0] ? first[1] : last[1];
    }
    // getter：供其他模块获取数据
    public List<PeriodBasicData> getPeriodBasicDataList() {
        return periodBasicDataList;
    }
}
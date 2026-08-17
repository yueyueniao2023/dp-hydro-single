import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.util.List;

public class ExcelWriter {
    private static final String OUTPUT_PATH = "水电站最优调度结果.xlsx";

    // 只保留一个writeResult方法（合并所有逻辑）
    public void writeResult(List<PeriodResult> resultList) throws Exception {
        // 1. 创建Excel工作簿和工作表
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("最优调度结果");

        // 2. 定义表头（包含弃水、实际发电量）
        String[] headers = {"旬索引", "发电流量（m³/s）", "弃水流量（m³/s）", "旬初水位（m）", "旬末水位（m）",
                "旬初库容（亿m³）", "旬末库容（亿m³）", "旬平均尾水位（m）", "理论发电量（万kWh）", "实际发电量（万kWh）"};

        // 3. 写入表头
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // 4. 遍历结果列表，写入数据
        for (int i = 0; i < resultList.size(); i++) {
            PeriodResult result = resultList.get(i);
            Row row = sheet.createRow(i + 1); // 数据行从第1行开始（表头是第0行）

            // 按表头顺序赋值
            row.createCell(0).setCellValue(result.getPeriodIndex());
            row.createCell(1).setCellValue(result.getPowerFlow());
            row.createCell(2).setCellValue(result.getAbandonFlow()); // 弃水流量
            row.createCell(3).setCellValue(result.getStartLevel());
            row.createCell(4).setCellValue(result.getEndLevel());
            row.createCell(5).setCellValue(result.getStartCapacity());
            row.createCell(6).setCellValue(result.getEndCapacity());
            row.createCell(7).setCellValue(result.getAvgTailLevel());
            row.createCell(8).setCellValue(result.getPowerGeneration()); // 理论发电量
            row.createCell(9).setCellValue(result.getActualPowerGen());   // 实际发电量
        }

        // 5. 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // 6. 写入文件并关闭资源
        FileOutputStream outputStream = new FileOutputStream(OUTPUT_PATH);
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
        System.out.println("结果已写入：" + OUTPUT_PATH);
    }
}
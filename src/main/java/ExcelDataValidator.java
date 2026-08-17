import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExcelDataValidator {

    // 校验结果封装
    public static class ValidateResult {
        private boolean valid;
        private List<String> errors = new ArrayList<>();

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getErrors() { return errors; }
    }

    // 校验规则定义
    public static class ValidationRule {
        private int sheetIndex; // sheet索引（0开始）
        private int columnIndex; // 列索引（0开始）
        private String columnName; // 列名（用于错误提示）
        private boolean required; // 是否必填
        private Class<?> dataType; // 数据类型
        private Double minValue; // 数值最小值
        private Double maxValue; // 数值最大值

        public ValidationRule(int sheetIndex, int columnIndex, String columnName) {
            this.sheetIndex = sheetIndex;
            this.columnIndex = columnIndex;
            this.columnName = columnName;
        }

        public void setRequired(boolean required) { this.required = required; }
        public void setDataType(Class<?> dataType) { this.dataType = dataType; }
        public void setMinValue(Double minValue) { this.minValue = minValue; }
        public void setMaxValue(Double maxValue) { this.maxValue = maxValue; }
    }

    /**
     * 执行Excel数据校验（支持多sheet）
     */
    public static ValidateResult validate(String filePath, List<ValidationRule> rules, int headerRowIndex) {
        ValidateResult result = new ValidateResult();
        Workbook workbook = null;

        try (InputStream inputStream = new FileInputStream(filePath)) {
            workbook = WorkbookFactory.create(inputStream);

            // 按规则校验每个sheet
            for (ValidationRule rule : rules) {
                Sheet sheet = workbook.getSheetAt(rule.sheetIndex);
                if (sheet == null) {
                    result.getErrors().add(String.format("sheet%d不存在", rule.sheetIndex + 1));
                    continue;
                }

                int lastRowNum = sheet.getLastRowNum();
                // 遍历数据行（跳过表头）
                for (int rowNum = headerRowIndex + 1; rowNum <= lastRowNum; rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null) continue;

                    Cell cell = row.getCell(rule.columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String cellValue = getCellValue(cell);
                    int excelRowNum = rowNum + 1; // Excel行号从1开始

                    // 1. 校验必填项
                    if (rule.required && (cellValue == null || cellValue.trim().isEmpty())) {
                        result.getErrors().add(String.format(
                                "sheet%d，第%d行，%s：不能为空",
                                rule.sheetIndex + 1, excelRowNum, rule.columnName
                        ));
                        continue;
                    }

                    // 非必填项为空时跳过后续校验
                    if (!rule.required && (cellValue == null || cellValue.trim().isEmpty())) {
                        continue;
                    }

                    // 2. 校验数据类型（仅数字，适配你的Excel需求）
                    if (rule.dataType == Double.class) {
                        try {
                            Double.parseDouble(cellValue);
                        } catch (NumberFormatException e) {
                            result.getErrors().add(String.format(
                                    "sheet%d，第%d行，%s：必须为数字（当前值：%s）",
                                    rule.sheetIndex + 1, excelRowNum, rule.columnName, cellValue
                            ));
                            continue;
                        }
                    }

                    // 3. 校验数值范围
                    if (rule.dataType == Double.class) {
                        double value = Double.parseDouble(cellValue);
                        if (rule.minValue != null && value < rule.minValue) {
                            result.getErrors().add(String.format(
                                    "sheet%d，第%d行，%s：不能小于%.2f（当前值：%.2f）",
                                    rule.sheetIndex + 1, excelRowNum, rule.columnName, rule.minValue, value
                            ));
                        }
                        if (rule.maxValue != null && value > rule.maxValue) {
                            result.getErrors().add(String.format(
                                    "sheet%d，第%d行，%s：不能大于%.2f（当前值：%.2f）",
                                    rule.sheetIndex + 1, excelRowNum, rule.columnName, rule.maxValue, value
                            ));
                        }
                    }
                }
            }

            result.setValid(result.getErrors().isEmpty());
        } catch (Exception e) {
            result.getErrors().add("校验失败：" + e.getMessage());
            result.setValid(false);
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (Exception e) { /* 忽略关闭异常 */ }
            }
        }

        return result;
    }

    // 获取单元格值（转为字符串）
    private static String getCellValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default: return "";
        }
    }

    // 主方法：校验你的Excel文件
    public static void main(String[] args) {
        // 1. 你的Excel文件路径（替换为实际路径）
        String filePath = "C:\\1.保研人的大四\\入门案例（两周-截止11月16号）\\水位库容关系&尾水位流量关系&来水过程&实际水位过程.xlsx";

        // 2. 定义校验规则（严格匹配ExcelReader的读取逻辑）
        List<ValidationRule> rules = new ArrayList<>();

        // 规则1：校验sheet1（来水过程，索引0）的C列（索引2）：36旬来水流量
        // （保持不变，之前的sheet1校验逻辑正确）
        ValidationRule sheet1Rule = new ValidationRule(0, 2, "来水流量（C列）");
        sheet1Rule.setRequired(true);
        sheet1Rule.setDataType(Double.class);
        sheet1Rule.setMinValue(0.0);
        rules.add(sheet1Rule);

        // 规则2：校验sheet2（水位库容，索引1）的整数位列（A列，索引0）
        // 仅校验数据行（row=4到row=73，对应Excel第5-74行）
        ValidationRule sheet2LevelRule = new ValidationRule(1, 0, "库水位整数位（A列）");
        sheet2LevelRule.setRequired(true); // 数据行必须有值
        sheet2LevelRule.setDataType(Double.class); // 必须是数字（允许790以下或850以上，后续插值用）
        rules.add(sheet2LevelRule);

        // 规则3：校验sheet2的库容值列（B-K列，索引1-10）
        // 仅校验数据行（row=4到row=73）
        for (int col = 1; col <= 10; col++) {
            ValidationRule sheet2CapacityRule = new ValidationRule(1, col,
                    "库容值（" + (char) ('A' + col) + "列）");
            sheet2CapacityRule.setRequired(true); // 数据行必须有值
            sheet2CapacityRule.setDataType(Double.class); // 必须是数字
            sheet2CapacityRule.setMinValue(0.0); // 库容不能为负
            rules.add(sheet2CapacityRule);
        }

        // 3. 执行校验：表头行索引设为3（数据从第4行索引开始，对应Excel第5行）
        ValidateResult result = validate(filePath, rules, 3);

        // 4. 输出校验结果
        if (result.isValid()) {
            System.out.println("Excel数据格式校验通过！");
        } else {
            System.out.println("Excel数据格式存在以下问题：");
            for (String error : result.getErrors()) {
                System.out.println("- " + error);
            }
        }
    }
}
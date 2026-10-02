import cascade.LinearCurve;
import cascade.Reservoir;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 保留原Excel格式；曲线读取一次并缓存，DP内不再反复排序。 */
public class ExcelReader {
    private static final Path FILE = Path.of("data/hydro_basic_data.xlsx");
    private LinearCurve levelStorage;
    private LinearCurve storageLevel;
    private LinearCurve releaseTailwater;
    private List<PeriodBasicData> periods;

    public void init() throws Exception {
        try (InputStream input = Files.newInputStream(FILE); Workbook workbook = WorkbookFactory.create(input)) {
            double[][] storage = readMatrix(workbook.getSheetAt(1), false);
            levelStorage = new LinearCurve(storage[0], storage[1]);
            storageLevel = new LinearCurve(storage[1], storage[0]);
            double[][] tailwater = readMatrix(workbook.getSheetAt(2), true);
            // 原表最低为350 m3/s，不能直接支持188 m3/s下泄下限。
            // 显式读取单独标注的教学补点；不再把越界流量悄悄夹到曲线端点。
            String[] lowFlow = Files.readAllLines(Path.of("data/single/tailwater-low-flow.csv"))
                    .stream().filter(line -> !line.startsWith("#") && !line.startsWith("release_") && !line.isBlank())
                    .findFirst().orElseThrow().split(",");
            double[][] complete = new double[2][tailwater[0].length + 1];
            complete[0][0] = Double.parseDouble(lowFlow[0]);
            complete[1][0] = Double.parseDouble(lowFlow[1]);
            for (int axis = 0; axis < 2; axis++) System.arraycopy(tailwater[axis], 0, complete[axis], 1, tailwater[axis].length);
            releaseTailwater = new LinearCurve(complete[0], complete[1]);
            List<PeriodBasicData> inputs = new ArrayList<>();
            for (int t = 1; t <= 36; t++) {
                double flow = workbook.getSheetAt(0).getRow(t).getCell(2).getNumericCellValue();
                inputs.add(new PeriodBasicData(t, flow, (t - 1) / 3 + 1));
            }
            periods = List.copyOf(inputs);
        }
    }

    /** 表头B:K是小数位，A5起为整数位；尾水矩阵需交换横纵坐标。 */
    private static double[][] readMatrix(Sheet sheet, boolean tailwater) {
        List<double[]> pairs = new ArrayList<>();
        for (int i = tailwater ? 3 : 4; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null) continue;
            for (int j = 1; j <= 10; j++) {
                Cell value = row.getCell(j);
                if (value == null || value.getCellType() == CellType.BLANK) continue;
                double level = row.getCell(0).getNumericCellValue() + sheet.getRow(0).getCell(j).getNumericCellValue();
                double quantity = value.getNumericCellValue();
                if (tailwater && quantity <= 0) continue;
                pairs.add(tailwater ? new double[]{quantity, level} : new double[]{level, quantity});
            }
        }
        pairs.sort(Comparator.comparingDouble(pair -> pair[0]));
        double[][] curve = new double[2][pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            curve[0][i] = pairs.get(i)[0];
            curve[1][i] = pairs.get(i)[1];
        }
        return curve;
    }

    public double getTailLevelByFlow(double flow) { return releaseTailwater.at(flow); }
    public double getCapacityByLevel(double level) { return levelStorage.at(level); }
    public double getLevelByCapacity(double volume) { return storageLevel.at(volume); }
    public List<PeriodBasicData> getPeriodBasicDataList() { return periods; }

    public Reservoir reservoir() {
        return new Reservoir("single", 790, 850, 0.5, 830, 840,
                188, 5000, 5000, 3600, 8.5, 0, 30, levelStorage, releaseTailwater);
    }
}

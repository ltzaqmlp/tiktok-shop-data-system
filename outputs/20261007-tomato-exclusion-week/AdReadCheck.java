import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.poi.ss.usermodel.*;

public class AdReadCheck {
    static final DataFormatter FORMAT = new DataFormatter(Locale.ROOT);
    static BigDecimal value(Row row, int column) {
        return new BigDecimal(FORMAT.formatCellValue(row.getCell(column)));
    }
    public static void main(String[] paths) throws Exception {
        BigDecimal spend=BigDecimal.ZERO, revenue=BigDecimal.ZERO, orders=BigDecimal.ZERO;
        for (String path : paths) try (Workbook book=WorkbookFactory.create(Path.of(path).toFile())) {
            Sheet sheet=book.getSheetAt(0);
            if (Path.of(path).getFileName().toString().startsWith("Product data")) {
                Row row=sheet.getRow(1);
                if (!FORMAT.formatCellValue(row.getCell(1)).equals("1736479479514957476")
                    || value(row,3).compareTo(new BigDecimal("36.59"))!=0
                    || value(row,6).compareTo(new BigDecimal("43.35"))!=0) throw new AssertionError("Product calibration mismatch");
            } else {
                BigDecimal daySpend=BigDecimal.ZERO, dayRevenue=BigDecimal.ZERO, dayOrders=BigDecimal.ZERO;
                Row total=null;
                for (Row row : sheet) {
                    String date=FORMAT.formatCellValue(row.getCell(0));
                    if (date.equals("-")) total=row;
                    if (!date.startsWith("2026-10-")) continue;
                    daySpend=daySpend.add(value(row,1)); dayOrders=dayOrders.add(value(row,2)); dayRevenue=dayRevenue.add(value(row,4));
                }
                if (total==null || daySpend.compareTo(value(total,1))!=0 || dayOrders.compareTo(value(total,2))!=0
                    || dayRevenue.compareTo(value(total,4))!=0) throw new AssertionError("Hourly totals mismatch");
                spend=spend.add(daySpend); orders=orders.add(dayOrders); revenue=revenue.add(dayRevenue);
            }
        }
        if (spend.compareTo(new BigDecimal("218.11"))!=0 || orders.compareTo(new BigDecimal("20"))!=0
            || revenue.compareTo(new BigDecimal("329.73"))!=0) throw new AssertionError("Period totals mismatch");
        System.out.println("POI PASS: 7 workbooks readable; exact hourly and daily totals: USD "+spend+", "+orders+" orders, USD "+revenue);
    }
}

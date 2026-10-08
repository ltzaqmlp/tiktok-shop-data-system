import java.nio.file.Path;
import java.util.Locale;
import org.apache.poi.ss.usermodel.*;

public class OrderReadCheck {
    public static void main(String[] paths) throws Exception {
        for (String path : paths) {
            try (Workbook book = WorkbookFactory.create(Path.of(path).toFile())) {
                Sheet sheet = book.getSheetAt(0);
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                Row header = sheet.getRow(0);
                int[] columns = {0, 1, 5, 9};
                String[] expected = {"Order ID", "Order Status", "SKU ID", "Quantity"};
                for (int i = 0; i < columns.length; i++) {
                    String actual = formatter.formatCellValue(header.getCell(columns[i]));
                    if (!actual.equals(expected[i])) throw new AssertionError(actual);
                }
                for (Row row : sheet) if (row.getRowNum() >= 2) {
                    String id = formatter.formatCellValue(row.getCell(0));
                    if (!id.matches("[0-9]{18}")) throw new AssertionError("Order ID not intact");
                    String name = formatter.formatCellValue(row.getCell(7));
                    if (name.toLowerCase(Locale.ROOT).contains("tomato facial mask")) throw new AssertionError("Fake order remains");
                }
                System.out.println("PASS " + Path.of(path).getFileName() + " header and " + (sheet.getPhysicalNumberOfRows()-2) + " order rows");
            }
        }
    }
}

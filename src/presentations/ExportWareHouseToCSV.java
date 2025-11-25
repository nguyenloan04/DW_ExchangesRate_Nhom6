package presentations;

import common.DBConnector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class ExportWareHouseToCSV {

    private static final String FILE_NAME = "agg_exchange_monthly_export.csv";

    public static void main(String[] args) {
        String csvPath = args.length > 0 ? args[0] : FILE_NAME;

        // Bỏ LogUtils.log để tránh lỗi liên quan endTime
        System.out.println("[RUNNING] Exporting Warehouse → CSV");

        try (
                Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, year, month, currencyId, avgRate, minRate, maxRate FROM agg_exchange_monthly"
                );
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(csvPath), StandardCharsets.UTF_8)
                )
        ) {
            // HEADER
            writer.write("id,year,month,currencyId,avgRate,minRate,maxRate");
            writer.newLine();

            int rowCount = 0;

            while (rs.next()) {
                String line = String.format("%s,%s,%s,%s,%s,%s,%s",
                        rs.getString("id"),
                        rs.getString("year"),
                        rs.getString("month"),
                        rs.getString("currencyId"),
                        rs.getString("avgRate"),
                        rs.getString("minRate"),
                        rs.getString("maxRate")
                );

                writer.write(line);
                writer.newLine();
                rowCount++;
            }

            System.out.println("[SUCCESS] Exported " + rowCount + " rows to CSV");
            System.out.println("File CSV: " + new File(csvPath).getAbsolutePath());

        } catch (Exception e) {
            System.err.println("[ERROR] Export WH → CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package presentations;

import common.DBConnector;
import common.LogUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class DataMartLoader {

    public static void main(String[] args) {
        String csvPath = args.length > 0 ? args[0] : "agg_exchange_monthly_export.csv";


        String sql = """
            INSERT INTO fact_monthly_rate 
            (id, year, month, currencyId, avgRate, minRate, maxRate)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (
                Connection conn = DBConnector.getConnection(DBConnector.DB_MART);
                PreparedStatement ps = conn.prepareStatement(sql);
                BufferedReader reader = new BufferedReader(new FileReader(csvPath))
        ) {

            conn.setAutoCommit(false);

            // TRUNCATE
            conn.createStatement().execute("TRUNCATE TABLE fact_monthly_rate");

            String line;
            int count = 0;

            reader.readLine(); // skip header

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1); // giữ nguyên cột

                ps.setInt(1, Integer.parseInt(parts[0]));
                ps.setInt(2, Integer.parseInt(parts[1]));
                ps.setInt(3, Integer.parseInt(parts[2]));
                ps.setInt(4, Integer.parseInt(parts[3]));
                ps.setDouble(5, Double.parseDouble(parts[4]));
                ps.setDouble(6, Double.parseDouble(parts[5]));
                ps.setDouble(7, Double.parseDouble(parts[6]));

                ps.addBatch();

                if (++count % 500 == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
            conn.commit();

            System.out.println("Load thành công " + count + " dòng!");

            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

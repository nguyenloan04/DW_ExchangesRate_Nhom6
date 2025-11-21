package staging;
import common.DBConnector;
import common.LogUtils;
import java.io.*;
import java.sql.*;
public class StagingLoader {
    public static void main(String[] args) {
        String csvPath = args[0];
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {
            conn.createStatement().execute("TRUNCATE TABLE stg_exchange_rate");

            String sql = "INSERT INTO stg_exchange_rate (sourceDateUTC, sourceDateVN, baseCurrency, currency, rate) VALUES (?,?,?,?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            conn.setAutoCommit(false);

            try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] d = line.split(",");
                    ps.setString(1, d[0]); ps.setString(2, d[1]); ps.setString(3, d[2]); ps.setString(4, d[3]);
                    ps.setDouble(5, Double.parseDouble(d[4]));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }

            LogUtils.log("RUNNING", "Staging Loaded");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", "Staging: " + e.getMessage());
            System.exit(1);
        }
    }
}

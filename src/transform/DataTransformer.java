package transform;

import common.*;
import org.json.JSONObject;

import java.nio.file.*;
import java.sql.*;

public class DataTransformer {
    public static void main(String[] args) {
        String dDir = args[0];
        String tDate = (args.length > 1) ? args[1] : null;
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {
            String mapC = new String(Files.readAllBytes(Paths.get(dDir + "/currencies.json")));
            JSONObject cmap = new JSONObject(mapC);
            String w = (tDate != null) ? "WHERE DATE(sourceDateVN)='" + tDate + "'" : "WHERE DATE(sourceDateVN)=CURDATE()";
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM stg_exchange_rate " + w);

            PreparedStatement psC = conn.prepareStatement("INSERT INTO tmp_dim_currency (currencyCode,currencyName) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM tmp_dim_currency WHERE currencyCode=?)");
            PreparedStatement psD = conn.prepareStatement("INSERT INTO tmp_dim_date (dateValue,day,month,year,dayOfWeek) SELECT ?,DAY(?),MONTH(?),YEAR(?),DAYNAME(?) WHERE NOT EXISTS (SELECT 1 FROM tmp_dim_date WHERE dateValue=?)");
            PreparedStatement psF = conn.prepareStatement("INSERT INTO tmp_fact_exchange_rate (dateValue,baseCurrencyCode,currencyCode,rate) VALUES (?,?,?,?)");

            conn.setAutoCommit(false);
            while (rs.next()) {
                String dv = rs.getString("sourceDateVN").split(" ")[0], b = rs.getString("baseCurrency"), c = rs.getString("currency");
                // Curr
                psC.setString(1, c);
                psC.setString(2, cmap.optString(c, c));
                psC.setString(3, c);
                psC.addBatch();
                psC.setString(1, b);
                psC.setString(2, cmap.optString(b, b));
                psC.setString(3, b);
                psC.addBatch();
                // Date
                psD.setString(1, dv);
                psD.setString(2, dv);
                psD.setString(3, dv);
                psD.setString(4, dv);
                psD.setString(5, dv);
                psD.setString(6, dv);
                psD.addBatch();
                // Fact
                psF.setString(1, dv);
                psF.setString(2, b);
                psF.setString(3, c);
                psF.setDouble(4, rs.getDouble("rate"));
                psF.addBatch();
            }
            psC.executeBatch();
            psD.executeBatch();
            psF.executeBatch();
            conn.commit();
            LogUtils.log("RUNNING", "Transformed to Temp Tables");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
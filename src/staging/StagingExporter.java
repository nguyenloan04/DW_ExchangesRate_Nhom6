package staging;
import common.*; import org.json.JSONArray; import org.json.JSONObject; import java.io.*; import java.sql.*;

public class StagingExporter {
    public static void main(String[] args) {
        String dataDir = args[0];
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {

            // 1. Export Currency
            export(conn, "SELECT * FROM tmp_dim_currency", dataDir + "/export_curr.json");

            // 2. Export Date
            export(conn, "SELECT * FROM tmp_dim_date", dataDir + "/export_date.json");

            // 3. Export Fact
            export(conn, "SELECT * FROM tmp_fact_exchange_rate", dataDir + "/export_fact.json");

            LogUtils.log("RUNNING", "Exported 3 JSONs from Staging Temp Tables");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", "Exporter: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void export(Connection conn, String sql, String filePath) throws Exception {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        ResultSetMetaData md = rs.getMetaData();
        JSONArray arr = new JSONArray();

        while (rs.next()) {
            JSONObject obj = new JSONObject();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                // Tự động map tên cột trong DB thành key JSON
                obj.put(md.getColumnLabel(i), rs.getObject(i));
            }
            arr.put(obj);
        }
        try (FileWriter fw = new FileWriter(filePath)) { fw.write(arr.toString()); }
    }
}
package staging;

import common.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.sql.*;

public class StagingExporter {
    public static void main(String[] args) {
        String dDir = args[0];
        String tDate = (args.length > 1) ? args[1] : null;
        String dw = (tDate != null) ? "WHERE dateValue='" + tDate + "'" : "WHERE dateValue=CURDATE()";

        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {
            // 1. Curr
            export(conn, "SELECT DISTINCT currencyCode, currencyName FROM tmp_dim_currency", dDir + "/export_curr.json");
            // 2. Date
            export(conn, "SELECT * FROM tmp_dim_date " + dw, dDir + "/export_date.json");
            // 3. Fact
            export(conn, "SELECT * FROM tmp_fact_exchange_rate " + dw, dDir + "/export_fact.json");
            LogUtils.log("RUNNING", "Exported 3 JSONs");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }

    static void export(Connection c, String q, String f) throws Exception {
        ResultSet rs = c.createStatement().executeQuery(q);
        ResultSetMetaData md = rs.getMetaData();
        JSONArray a = new JSONArray();
        while (rs.next()) {
            JSONObject o = new JSONObject();
            for (int i = 1; i <= md.getColumnCount(); i++) o.put(md.getColumnLabel(i), rs.getObject(i));
            a.put(o);
        }
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(a.toString());
        }
    }
}
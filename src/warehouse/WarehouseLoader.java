package warehouse;

import common.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.*;
import java.sql.*;

public class WarehouseLoader {
    public static void main(String[] args) {
        String d = args[0];
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {
            conn.setAutoCommit(false);
            // Curr
            JSONArray ac = new JSONArray(new String(Files.readAllBytes(Paths.get(d + "/export_curr.json"))));
            CallableStatement cc = conn.prepareCall("{call Load_Dim_Currency(?,?)}");
            for (int i = 0; i < ac.length(); i++) {
                JSONObject o = ac.getJSONObject(i);
                cc.setString(1, o.getString("currencyCode"));
                cc.setString(2, o.getString("currencyName"));
                cc.addBatch();
            }
            cc.executeBatch();
            // Date
            JSONArray ad = new JSONArray(new String(Files.readAllBytes(Paths.get(d + "/export_date.json"))));
            CallableStatement cd = conn.prepareCall("{call Load_Dim_Date(?,?,?,?,?)}");
            for (int i = 0; i < ad.length(); i++) {
                JSONObject o = ad.getJSONObject(i);
                cd.setString(1, o.getString("dateValue"));
                cd.setInt(2, o.getInt("day"));
                cd.setInt(3, o.getInt("month"));
                cd.setInt(4, o.getInt("year"));
                cd.setString(5, o.getString("dayOfWeek"));
                cd.addBatch();
            }
            cd.executeBatch();
            // Fact
            JSONArray af = new JSONArray(new String(Files.readAllBytes(Paths.get(d + "/export_fact.json"))));
            CallableStatement cf = conn.prepareCall("{call Load_Fact(?,?,?,?)}");
            for (int i = 0; i < af.length(); i++) {
                JSONObject o = af.getJSONObject(i);
                cf.setString(1, o.getString("dateValue"));
                cf.setString(2, o.getString("baseCurrencyCode"));
                cf.setString(3, o.getString("currencyCode"));
                cf.setDouble(4, o.getDouble("rate"));
                cf.addBatch();
                if (i % 500 == 0) cf.executeBatch();
            }
            cf.executeBatch();
            conn.commit();
            LogUtils.log("RUNNING", "Warehouse Loaded");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
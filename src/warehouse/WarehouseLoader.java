package warehouse;
import common.*; import org.json.JSONArray; import org.json.JSONObject; import java.nio.file.*; import java.sql.*;

public class WarehouseLoader {
    public static void main(String[] args) {
        String dataDir = args[0];
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {
            conn.setAutoCommit(false);

            // 1. Dim Currency
            JSONArray arrCurr = new JSONArray(new String(Files.readAllBytes(Paths.get(dataDir + "/export_curr.json"))));
            CallableStatement csC = conn.prepareCall("{call Load_Dim_Currency(?,?)}");
            for(int i=0; i<arrCurr.length(); i++){
                JSONObject o = arrCurr.getJSONObject(i);
                // Key JSON phải khớp tên cột trong bảng tmp_dim_currency
                csC.setString(1, o.getString("currencyCode"));
                csC.setString(2, o.getString("currencyName"));
                csC.addBatch();
            }
            csC.executeBatch();

            // 2. Dim Date
            JSONArray arrDate = new JSONArray(new String(Files.readAllBytes(Paths.get(dataDir + "/export_date.json"))));
            CallableStatement csD = conn.prepareCall("{call Load_Dim_Date(?,?,?,?,?)}");
            for(int i=0; i<arrDate.length(); i++){
                JSONObject o = arrDate.getJSONObject(i);
                // Key JSON khớp bảng tmp_dim_date
                csD.setString(1, o.getString("dateValue"));
                csD.setInt(2, o.getInt("day"));
                csD.setInt(3, o.getInt("month"));
                csD.setInt(4, o.getInt("year"));
                csD.setString(5, o.getString("dayOfWeek"));
                csD.addBatch();
            }
            csD.executeBatch();

            // 3. Fact
            JSONArray arrFact = new JSONArray(new String(Files.readAllBytes(Paths.get(dataDir + "/export_fact.json"))));
            CallableStatement csF = conn.prepareCall("{call Load_Fact(?,?,?,?)}");
            for(int i=0; i<arrFact.length(); i++){
                JSONObject o = arrFact.getJSONObject(i);
                // Key JSON khớp bảng tmp_fact_exchange_rate
                csF.setString(1, o.getString("dateValue"));
                csF.setString(2, o.getString("baseCurrencyCode"));
                csF.setString(3, o.getString("currencyCode"));
                csF.setDouble(4, o.getDouble("rate"));
                csF.addBatch();
                if(i%500==0) csF.executeBatch();
            }
            csF.executeBatch();

            conn.commit();
            LogUtils.log("RUNNING", "Warehouse Loaded via Procedures");
            System.exit(0);
        } catch (Exception e) { LogUtils.log("ERROR", "WH Load: " + e.getMessage()); System.exit(1); }
    }
}
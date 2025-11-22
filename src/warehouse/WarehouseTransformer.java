package warehouse;

import common.*;

import java.sql.*;

public class WarehouseTransformer {
    public static void main(String[] args) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {
            conn.prepareCall("{call Aggregate_Monthly()}").execute();
            LogUtils.log("SUCCESS", "ETL Finished");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
package warehouse;

import common.DBConnector;
import common.LogUtils;

import java.sql.Connection;

public class WarehouseTransformer {
    public static void main(String[] args) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {
            conn.prepareCall("{call Aggregate_Monthly()}").execute();
            LogUtils.log("SUCCESS", "ETL Finished Successfully");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", "Aggregator: " + e.getMessage());
            System.exit(1);
        }
    }
}

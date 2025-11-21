package warehouse;
import common.DBConnector;
import common.LogUtils;
import java.sql.Connection;
public class WarehouseLoader {
    public static void main(String[] args) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {
            conn.prepareCall("{call Load_Fact()}").execute();
            LogUtils.log("RUNNING", "Fact Loaded");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", "WH Loader: " + e.getMessage());
            System.exit(1);
        }
    }
}

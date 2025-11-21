package transform;
import common.DBConnector;
import common.LogUtils;
import java.sql.Connection;
public class DataTransformer {
    public static void main(String[] args) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {
            conn.prepareCall("{call Transform_Dimensions()}").execute();
            LogUtils.log("RUNNING", "Dimensions Transformed");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", "Transformer: " + e.getMessage());
            System.exit(1);
        }
    }
}

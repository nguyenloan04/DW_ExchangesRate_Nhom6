package config;
import common.DBConnector;
import java.sql.Connection;
public class ConfigLoader {
    public static void main(String[] args) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // Tạo dòng log RUNNING mới
            String sql = "INSERT INTO etl_log (processId, startedAt, status, message, createdAt) VALUES (1, NOW(), 'RUNNING', 'Starting ETL Pipeline...', CURDATE())";
            conn.createStatement().executeUpdate(sql);
            System.out.println("ConfigLoader: Log initialized.");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

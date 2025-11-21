package common;
import java.sql.Connection;
import java.sql.PreparedStatement;
public class LogUtils {
    // Ghi log vào bảng etl_log trong DB Control
    public static void log(String status, String message) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // Update dòng log đang chạy (status='RUNNING') của processId=1
            String sql = "UPDATE etl_log SET status = ?, message = ?, endTime = CASE WHEN ? IN ('SUCCESS', 'ERROR') THEN NOW() ELSE NULL END WHERE processId = 1 AND status = 'RUNNING' ORDER BY id DESC LIMIT 1";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status);
            ps.setString(2, message);
            ps.setString(3, status);
            ps.executeUpdate();

            // In ra console để debug trên VPS nếu cần
            System.out.println("[" + status + "] " + message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

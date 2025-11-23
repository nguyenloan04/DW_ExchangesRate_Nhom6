package transform;

import common.*;

import java.nio.file.*;
import java.sql.*;

public class DataTransformer {
    public static void main(String[] args) {
        // Tham số: Thư mục data, Ngày cần transform (Optional)
        String targetDate = (args.length > 1) ? args[1] : new java.sql.Date(System.currentTimeMillis()).toString();

        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {
            System.out.println("Calling SP: Transform_Staging for date " + targetDate);

            // Gọi SP đã tạo ở trên
            CallableStatement cs = conn.prepareCall("{call Transform_Staging(?)}");
            cs.setString(1, targetDate);
            cs.execute();

            LogUtils.log("RUNNING", "Staging Raw Transformed to Temp Tables");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", "Transformer: " + e.getMessage());
            System.exit(1);
        }
    }
}

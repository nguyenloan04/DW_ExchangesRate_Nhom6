package common;
import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.io.File;

public class DBConnector {
    // Đường dẫn tuyệt đối tới file .env trên VPS
    private static final String ENV_PATH = "/dw_t5c1n6/.env";

    // Biến cấu hình mặc định
    private static String DB_HOST = "localhost";
    private static String DB_USER = "root";
    private static String DB_PASS =""; // Fallback password

    // Tên các Database (để so sánh)
    public static String DB_CONTROL = "db_control"; // Giá trị mặc định, sẽ bị ghi đè bởi .env
    public static String DB_STAGING = "db_exchange_staging";
    public static String DB_WAREHOUSE = "db_exchange_warehouse";

    // Khối static: Load cấu hình 1 lần duy nhất
    static {
        loadEnv();
    }

    private static void loadEnv() {
        try {
            File envFile = new File(ENV_PATH);
            if (envFile.exists()) {
                List<String> lines = Files.readAllLines(Paths.get(ENV_PATH));
                for (String line : lines) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim();
                        String val = parts[1].trim();

                        switch (key) {
                            case "MYSQL_HOST": DB_HOST = val; break;
                            case "MYSQL_ROOT_PASSWORD": DB_PASS = val; break;
                            case "MYSQL_CONTROL": DB_CONTROL = val; break;
                            case "MYSQL_STAGING": DB_STAGING = val; break;
                            case "MYSQL_WAREHOUSE": DB_WAREHOUSE = val; break;
                        }
                    }
                }
            } else {
                System.out.println("Warning: .env file not found at " + ENV_PATH);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection(String dbName) throws Exception {
        // LOGIC QUAN TRỌNG: CHỌN PORT DỰA TRÊN TÊN DB
        String port = "3306"; // Mặc định

        // So sánh chuỗi (dùng equals)
        if (dbName.equals(DB_CONTROL)) {
            port = "3309"; // Cổng của Control Container
        } else if (dbName.equals(DB_STAGING)) {
            port = "3310"; // Cổng của Staging Container
        } else if (dbName.equals(DB_WAREHOUSE)) {
            port = "3311"; // Cổng của Warehouse Container
        }

        String url = "jdbc:mysql://" + DB_HOST + ":" + port + "/" + dbName +
                "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";

        return DriverManager.getConnection(url, DB_USER, DB_PASS);
    }
}

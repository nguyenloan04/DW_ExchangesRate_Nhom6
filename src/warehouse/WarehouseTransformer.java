package warehouse;

import common.*;
import java.sql.*;

public class WarehouseTransformer {

    public static void main(String[] args) {

        // (1) BẮT ĐẦU CHƯƠNG TRÌNH
        // - Thực hiện ETL tại kho dữ liệu (Warehouse)
        // - Sử dụng try-with-resources để auto close connection
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_WAREHOUSE)) {

            // (2) TẠO KẾT NỐI THÀNH CÔNG
            // - Nếu getConnection() lỗi → nhảy xuống catch (ERROR branch)
            // - Nếu thành công → tiếp tục bước 3

            // (3) GỌI STORED PROCEDURE
            // - Gọi thủ tục Aggregate_Monthly() trong DB Warehouse
            // - Thủ tục chịu trách nhiệm tổng hợp dữ liệu theo tháng
            conn.prepareCall("{call Aggregate_Monthly()}").execute();

            // (4) GHI LOG THÀNH CÔNG
            // - Thực thi stored procedure OK
            LogUtils.log("SUCCESS", "ETL Finished");

            // (5) KẾT THÚC CHƯƠNG TRÌNH (SUCCESS PATH)
            System.exit(0);


        } catch (Exception e) {

            // (6) LOG LỖI
            LogUtils.log("ERROR", e.getMessage());

            // (7) KẾT THÚC CHƯƠNG TRÌNH (ERROR PATH)
            System.exit(1);
        }
    }
}

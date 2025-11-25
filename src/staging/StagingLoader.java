package staging;

import common.*;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class StagingLoader {
    public static void main(String[] args) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 2. Kết nối đến db.exchange_staging trên VPS
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {
            conn.setAutoCommit(false);
            // 3. Lấy input đường dẫn file csv trong hệ thống từ lệnh (java -jar stagingLoader.jar /dw_t5c1n6/staging/data/exchange_rates_yyyyMMdd.csv)
            if (args.length < 1) {
                System.err.println("Missing path to .csv");
                LogUtils.log("ERROR", "StagingLoader: Missing CSV path argument");
                System.exit(1);
            }
            // 4. Kiểm tra có đọc trước được nội dung trog file csv hay không
            try (BufferedReader brCheck = new BufferedReader(new FileReader(args[0]))) {
                String firstLine = brCheck.readLine();
                if (firstLine != null && !firstLine.isEmpty()) { //5. Kiểm tra xem file csv có rỗng hay không?
                    String[] columns = firstLine.split(",");
                    if (columns.length > 1) { //6. Kiểm tra xem số cột của file csv có đủ không
                        // Tách ngày
                        //7. Lấy dữ liệu ngày (date) trong dòng đầu tiên của file csv
                        String dateStr = columns[1].split(" ")[0]; // Lấy YYYY-MM-DD
                        System.out.println("Cleaning data for date: " + dateStr);

                        // gọi procedure Clean_staging_by_date để xóa dữ liệu ngày hôm đó trong 2 bảng stg_exchange_rate và tmp_fact_exchange_rate
                        //input: date (được lấy từ dòng đầu tiên của file csv)

                        try {
                            conn.prepareCall("{call Clean_Staging_By_Date('" + dateStr + "')}").execute(); //Xóa dữ liệu trong 2 bảng stg_exchange_rate và tmp_fact_exchange_rate
                        } catch (SQLException ex) {
                            throw new Exception("SP Clean error: " + ex.getMessage());
                        }
                    } else {
                        throw new Exception("File CSV not in right format");
                    }
                } else {
                    System.out.println("Empty CSV.");
                    LogUtils.log("ERROR", "No data for loading");
                    System.exit(1);
                }
            } catch (Exception e) {
                LogUtils.log("ERROR", "Staging Pre-check Failed: " + e.getMessage());
                System.exit(1);
            }
            //
            int totalLines = 0;
            int successLines = 0;
            int errorLines = 0;

            String sql = "INSERT INTO stg_exchange_rate (sourceDateUTC, sourceDateVN, baseCurrency, currency, rate, source_link, loadedAt) VALUES (?,?,?,?,?,?,NOW())";
            try (BufferedReader br = new BufferedReader(new FileReader(args[0]));
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                String line;
                while ((line = br.readLine()) != null) {
                    // 8. Đọc từng dòng trong file csv để thêm vào stg_exchange_staging
                    totalLines++;
                    try {
                        String[] d = line.split(",");

                        // Kiểm tra có đủ cột theo quy định hay không (số cột =6)
                        if (d.length < 6) {
                            throw new Exception("Missing columns");
                        }

                        // Parse từng dữ liệu từ string về kiểu dữ liệu trong stg_exchange_rate
                        LocalDateTime dtUTC = LocalDateTime.parse(d[0], formatter);
                        LocalDateTime dtVN = LocalDateTime.parse(d[1], formatter);
                        double rate = Double.parseDouble(d[4]);

                        // Set params
                        ps.setTimestamp(1, Timestamp.valueOf(dtUTC));
                        ps.setTimestamp(2, Timestamp.valueOf(dtVN));
                        ps.setString(3, d[2]); // Base
                        ps.setString(4, d[3]); // Currency
                        ps.setDouble(5, rate);
                        ps.setString(6, d[5]); // Source Link

                        ps.addBatch();
                        successLines++;

                        // Batch size 1000 để tối ưu
                        if (successLines % 1000 == 0) {
                            ps.executeBatch(); //Gọi query insert 1000 dòng dữ liệu trong 1 batch vào trong stg_exchange_staging
                            conn.commit(); // Commit từng phần để tránh rollback tất cả nếu lỗi
                        }

                    } catch (DateTimeParseException dtpe) {
                        errorLines++;
                        System.err.println("Lỗi định dạng ngày dòng " + totalLines + ": " + line);
                    } catch (NumberFormatException nfe) {
                        errorLines++;
                        System.err.println("Lỗi định dạng số dòng " + totalLines + ": " + line);
                    } catch (Exception ex) {
                        errorLines++;
                        System.err.println("Lỗi xử lý dòng " + totalLines + ": " + ex.getMessage());
                        // KHÔNG THOÁT, chỉ ghi nhận lỗi và tiếp tục dòng sau
                    }
                }

                // Execute phần còn lại (nếu totalLine >1000)
                ps.executeBatch();
                conn.commit();
            }

            // 9. Kiểm tra loading có thành công không.
            String msg = "Staging Finished. Total: " + totalLines + ", Success: " + successLines + ", Errors: " + errorLines;
            System.out.println(msg);

            if (successLines == 0 && totalLines > 0) {
                // Nếu có dòng mà không thành công dòng nào -> Coi như thất bại toàn tập
                LogUtils.log("ERROR", "Staging Failed: No rows inserted. Check logs.");
                System.exit(1);
            } else if (errorLines > 0) {
                // Thành công một phần (Warning)
                LogUtils.log("RUNNING", "Staging Loaded with " + errorLines + " errors. " + msg);
            } else {
                // Thành công hoàn hảo
                LogUtils.log("RUNNING", "Raw Staging Loaded Successfully");
            }

            System.exit(0);

        } catch (Exception e) {
            // Lỗi kết nối DB hoặc lỗi hệ thống nghiêm trọng
            e.printStackTrace();
            LogUtils.log("ERROR", "Critical Staging Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
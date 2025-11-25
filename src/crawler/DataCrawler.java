package crawler;

import common.*;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class DataCrawler {
    public static void main(String[] args) {


        // Bước 1: Đọc tham số đầu vào
        // - dataDir: Thư mục lưu trữ dữ liệu (bắt buộc)
        // - tDate: Ngày cụ thể để lấy dữ liệu lịch sử (tùy chọn)
        if (args.length == 0) {
            LogUtils.log("ERROR", "Thiếu tham số: Cần truyền dataDir");
            System.exit(1);
        }

        String dataDir = args[0];
        String tDate = (args.length > 1) ? args[1] : null;

        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // Bước 2: Kết nối cơ sở dữ liệu
            // Sử dụng DBConnector để tạo connection đến DB_CONTROL

            // Bước 3: Truy vấn cấu hình từ bảng file_config trong DB
            // Lấy apiKey, sourceUrl, historyUrl, currenciesUrl từ DB
            ResultSet rs = null;
            try {
                rs = conn.createStatement().executeQuery("SELECT * FROM file_config LIMIT 1");
                if (!rs.next()) {
                    LogUtils.log("ERROR", "Không có bản ghi nào trong file_config");
                    System.exit(1);
                }
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không lấy được file_config: " + ex.getMessage());
                System.exit(1);
            }

            String apiKey = rs.getString("apiKey");
            String reqUrl = "";
            String safeUrl = "";

            // Bước 4: Kiểm tra tDate có null không
            // Phân nhánh logic: tDate != null (lịch sử) vs tDate == null (hiện tại)
            try {
                // Bước 5a: Kiem tra tDate
                if (tDate != null) {
                    safeUrl = rs.getString("historyUrl") + tDate + ".json";
                    reqUrl = safeUrl + "?app_id=" + apiKey;
                } else {
                    // Buoc 5b: tDate == null
                    safeUrl = rs.getString("sourceUrl");
                    reqUrl = safeUrl + "?app_id=" + apiKey;
                }
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không xây được URL: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 6: Gọi API Exchange Rate
            // Mở kết nối HTTP đến API và đọc response
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(reqUrl).openStream()))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            } catch (FileNotFoundException nf) {
                LogUtils.log("ERROR", "API trả về 404 Not Found: " + reqUrl);
                System.exit(1);
            } catch (UnknownHostException uh) {
                LogUtils.log("ERROR", "Không thể kết nối API (mất mạng hoặc sai URL): " + uh.getMessage());
                System.exit(1);
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Lỗi khi gọi API ExchangeRate: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 7: Parse JSON response
            // Chuyển đổi chuỗi JSON thành đối tượng JSONObject
            // Lấy ra: rates (tỷ giá), timestamp (thời gian), base (tiền tệ gốc)
            JSONObject json = null, rates = null;
            try {
                json = new JSONObject(sb.toString());
                rates = json.getJSONObject("rates");
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Lỗi parse JSON: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 8: Format ngày giờ từ timestamp
            // Chuyển Unix timestamp (giây) thành định dạng "yyyy-MM-dd HH:mm:ss"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dSQL = "";
            try {
                dSQL = sdf.format(new Date(json.getLong("timestamp") * 1000L));
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Lỗi xử lý timestamp: " + ex.getMessage());
                System.exit(1);
            }

            // Tạo tên file CSV: exchange_[ngày].csv
            String fName = "";
            String fPath = "";
            try {
                fName = "exchange_" + (tDate != null ? tDate : sdf.format(new Date())) + ".csv";
                fPath = dataDir + "/" + fName;
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không tạo được tên file CSV: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 9: Tải dữ liệu currencies.json
            // Gọi API currenciesUrl để lấy danh sách và mô tả các loại tiền tệ
            String currResp = "";
            try {
                currResp = new Scanner(new URL(rs.getString("currenciesUrl")).openStream())
                        .useDelimiter("\\A")
                        .next();
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không tải được currencies.json: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 10: Lưu currencies.json vào dataDir
            // Ghi toàn bộ response vào file currencies.json
            try (FileWriter fw = new FileWriter(dataDir + "/currencies.json")) {
                fw.write(currResp);
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không ghi currencies.json: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 11: Ghi file CSV với dữ liệu rates
            // Format mỗi dòng: timestamp,timestamp,base,currency,rate,sourceUrl
            // Ví dụ: 2024-01-15 10:30:00,2024-01-15 10:30:00,USD,EUR,0.85,https://...
            try (PrintWriter pw = new PrintWriter(new FileWriter(dataDir + "/" + fName))) {
                for (String k : rates.keySet()) {
                    try {
                        pw.println(dSQL + "," + dSQL + "," + json.getString("base") + "," + k + "," + rates.getDouble(k) + "," + safeUrl);
                    } catch (Exception lineEx) {
                        LogUtils.log("ERROR", "Lỗi ghi dòng CSV: " + lineEx.getMessage());
                    }
                }
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không ghi được file CSV: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 12: Ghi latest_file.txt
            // Lưu đường dẫn của file CSV mới nhất để các module khác dễ dàng truy cập
            try (PrintWriter p = new PrintWriter(new FileWriter(dataDir + "/latest_file.txt"))) {
                p.print(fPath);
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không ghi latest_file.txt: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 13: Insert log vào bảng file_log
            // Ghi thông tin: fileConfigId, fileName, filePath, status='FINISHED',
            // totalRecords (số lượng tỷ giá), extractedAt (thời gian crawl)
            try {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO file_log (fileConfigId, fileName, filePath, status, totalRecords, extractedAt) VALUES (?, ?, ?, 'FINISHED', ?, NOW())"
                );
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, fName);
                ps.setString(3, fPath);
                ps.setInt(4, rates.length());
                ps.executeUpdate();
            } catch (Exception ex) {
                LogUtils.log("ERROR", "Không ghi log vào file_log: " + ex.getMessage());
                System.exit(1);
            }

            // Bước 14: Ghi log RUNNING và exit(0)
            // Thông báo crawl thành công và thoát chương trình với mã 0 (success)
            LogUtils.log("RUNNING", "Crawled from " + safeUrl);
            System.exit(0);

        } catch (Exception e) {
            // Bước 15: Xử lý Exception - Ghi log ERROR và exit(1)
            // Nếu có bất kỳ lỗi nào xảy ra trong quá trình crawl:
            // Ghi log với level ERROR và message của exception
            // Thoát chương trình với mã 1 (failure)
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}

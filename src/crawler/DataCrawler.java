package crawler;

import common.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class DataCrawler {
    public static void main(String[] args) {
        String dataDir = args[0];
        String targetDate = (args.length > 1) ? args[1] : null;

        int currentLogId = -1;
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // 1. Lấy Config
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM file_config LIMIT 1");
            if (!rs.next()) throw new Exception("Config not found");
            int configId = rs.getInt("id");
            String apiKey = rs.getString("apiKey");
            String reqUrl;

            // Lấy đường dẫn mẫu từ DB (VD: .../exchange_rates_{yyyymmdd}.csv)
            String templatePath = rs.getString("filePath");
            String dbDateFormat = rs.getString("dateFormat"); // VD: yyyyMMdd
            String initSql = "INSERT INTO file_log (fileConfigId, status, extractedAt) VALUES (?, 'EXTRACTING', NOW())";

            // Quan trọng: Statement.RETURN_GENERATED_KEYS để lấy lại ID vừa insert
            PreparedStatement psInit = conn.prepareStatement(initSql, Statement.RETURN_GENERATED_KEYS);
            psInit.setInt(1, configId);
            psInit.executeUpdate();

            ResultSet keys = psInit.getGeneratedKeys();
            if (keys.next()) {
                currentLogId = keys.getInt(1); // Lưu lại ID: Ví dụ log_id = 50
            }

            LogUtils.log("RUNNING", "Crawler started. Log ID: " + currentLogId);
            // 2. Xác định URL gọi API
            String safeUrl;
            if (targetDate != null) {
                safeUrl = "https://openexchangerates.org/api/historical/" + targetDate + ".json";
                reqUrl = safeUrl + "?app_id=" + apiKey;
            } else {
                safeUrl = rs.getString("sourceUrl");
                reqUrl = safeUrl + "?app_id=" + apiKey;
            }

            // 3. Tải Map Currency
            String currUrl = rs.getString("currenciesUrl");
            if(currUrl != null) {
                String currResp = new Scanner(new URL(currUrl).openStream()).useDelimiter("\\A").next();
                try(FileWriter fw = new FileWriter(dataDir + "/currencies.json")) { fw.write(currResp); }
            }

            // 4. Tải Rates
            BufferedReader br = new BufferedReader(new InputStreamReader(new URL(reqUrl).openStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while((line=br.readLine())!=null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            JSONObject rates = json.getJSONObject("rates");
            long ts = json.getLong("timestamp");
            String base = json.getString("base");

            // A. Xác định chuỗi ngày thực tế
            SimpleDateFormat sdf = new SimpleDateFormat(dbDateFormat); // Dùng format từ DB (yyyyMMdd)
            String realDateStr;

            if (targetDate != null) {
                // Nếu chạy lịch sử, targetDate đang là YYYY-MM-DD, cần bỏ dấu - đi nếu format là yyyyMMdd
                realDateStr = targetDate.replace("-", "");
            } else {
                // Nếu chạy daily, lấy ngày từ timestamp của API
                realDateStr = sdf.format(new Date(ts * 1000L));
            }

            // B. Thay thế placeholder {yyyymmdd} bằng ngày thật
            // templatePath: /dw_t5c1n6/staging/data/exchange_rates_{yyyymmdd}.csv
            String finalPath = templatePath.replace("{yyyymmdd}", realDateStr);
            String fileName = new File(finalPath).getName();

            // Format ngày giờ cho nội dung CSV
            SimpleDateFormat sdfSQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateSQL = sdfSQL.format(new Date(ts * 1000L));

            // 5. Ghi CSV
            try(PrintWriter pw = new PrintWriter(new FileWriter(finalPath))) {
                for(String k : rates.keySet())
                    pw.println(dateSQL+","+dateSQL+","+base+","+k+","+rates.getDouble(k)+","+safeUrl);
            }

            // Ghi file pointer để các bước sau biết đường dẫn
            try(PrintWriter p = new PrintWriter(new FileWriter(dataDir + "/latest_file.txt"))){ p.print(finalPath); }

            // 6. Log DB
            if (currentLogId != -1) {
                String updateSql = "UPDATE file_log SET fileName=?, filePath=?, source_link=?, status='FINISHED', totalRecords=? WHERE id=?";
                PreparedStatement psUpdate = conn.prepareStatement(updateSql);
                psUpdate.setString(1, fileName);
                psUpdate.setString(2, finalPath);
                psUpdate.setString(3, safeUrl);
                psUpdate.setInt(4, rates.length());
                psUpdate.setInt(5, currentLogId); // Update đúng dòng log ban đầu
                psUpdate.executeUpdate();
            }
            LogUtils.log("RUNNING", "Crawled to file: " + fileName);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                // Mở kết nối mới để ghi log lỗi (phòng trường hợp kết nối cũ bị đóng)
                if (currentLogId != -1) {
                    Connection errConn = DBConnector.getConnection(DBConnector.DB_CONTROL);
                    String errSql = "UPDATE file_log SET status='ERROR' WHERE id=?";
                    PreparedStatement psErr = errConn.prepareStatement(errSql);
                    psErr.setInt(1, currentLogId);
                    psErr.executeUpdate();
                    errConn.close();
                }
            } catch (Exception ex) {
                System.err.println("Fatal: Could not update error log.");
            }

            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
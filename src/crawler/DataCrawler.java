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

        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // 1. Lấy Config
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM file_config LIMIT 1");
            rs.next();
            String apiKey = rs.getString("apiKey");
            String reqUrl;

            // Lấy đường dẫn mẫu từ DB (VD: .../exchange_rates_{yyyymmdd}.csv)
            String templatePath = rs.getString("filePath");
            String dbDateFormat = rs.getString("dateFormat"); // VD: yyyyMMdd

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
            PreparedStatement ps = conn.prepareStatement("INSERT INTO file_log (fileConfigId, fileName, filePath, source_link, status, totalRecords, extractedAt) VALUES (?, ?, ?, ?, 'FINISHED', ?, NOW())");
            ps.setInt(1, rs.getInt("id"));
            ps.setString(2, fileName);
            ps.setString(3, finalPath);
            ps.setString(4, safeUrl);
            ps.setInt(5, rates.length());
            ps.executeUpdate();

            LogUtils.log("RUNNING", "Crawled to file: " + fileName);
            System.exit(0);
        } catch (Exception e) { LogUtils.log("ERROR", e.getMessage()); System.exit(1); }
    }
}
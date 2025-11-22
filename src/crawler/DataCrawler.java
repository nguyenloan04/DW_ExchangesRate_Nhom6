package crawler;

import common.DBConnector;
import common.LogUtils;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class DataCrawler {

    public static void main(String[] args) {
        String outputDir = args[0]; // Nhận thư mục output từ script .sh

        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // 1. Lấy Config từ DB
            // Cần lấy thêm trường 'currenciesUrl'
            String sql = "SELECT id, apiKey, sourceUrl, currenciesUrl, dateFormat, baseCurrency FROM file_config WHERE sourceName='OpenExchangeRates' LIMIT 1";
            ResultSet rs = conn.createStatement().executeQuery(sql);

            if (!rs.next()) throw new Exception("Config not found");

            String apiKey = rs.getString("apiKey");
            String sourceUrl = rs.getString("sourceUrl");     // .../latest.json
            String currenciesUrl = rs.getString("currenciesUrl"); // .../currencies.json
            String dateFormatPattern = rs.getString("dateFormat");
            int configId = rs.getInt("id");

            if (dateFormatPattern == null || dateFormatPattern.isEmpty()) dateFormatPattern = "yyyyMMdd";

            // --- BƯỚC 1: LẤY DANH SÁCH CURRENCY HỢP LỆ ---
            System.out.println("Fetching Valid Currencies from: " + currenciesUrl);
            Set<String> validCurrencies = new HashSet<>();

            try {
                // API currencies.json thường public, nhưng thêm app_id cho chắc nếu cần
                String responseCurr = callApi(currenciesUrl);
                JSONObject jsonCurr = new JSONObject(responseCurr);

                // Lưu toàn bộ Key (Mã tiền tệ) vào HashSet để tra cứu cho nhanh
                validCurrencies = jsonCurr.keySet();
                System.out.println("Found " + validCurrencies.size() + " valid currencies.");

            } catch (Exception e) {
                LogUtils.log("ERROR", "Failed to fetch currencies list: " + e.getMessage());
                throw e;
            }

            // --- BƯỚC 2: LẤY TỶ GIÁ (LATEST) ---
            System.out.println("Fetching Rates from: " + sourceUrl);
            String responseRates = callApi(sourceUrl + "?app_id=" + apiKey);
            JSONObject jsonRates = new JSONObject(responseRates);
            JSONObject rates = jsonRates.getJSONObject("rates");
            long ts = jsonRates.getLong("timestamp");
            String base = jsonRates.getString("base");

            // --- BƯỚC 3: LỌC & GHI CSV ---
            // Tạo tên file
            SimpleDateFormat sdfFile = new SimpleDateFormat(dateFormatPattern);
            String fileName = "exchange_rates_" + sdfFile.format(new Date()) + ".csv";
            String fullPath = outputDir + File.separator + fileName;

            SimpleDateFormat sdfSQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateSQL = sdfSQL.format(new Date(ts * 1000L));

            int count = 0;
            try (PrintWriter pw = new PrintWriter(new FileWriter(fullPath))) {
                for (String key : rates.keySet()) {
                    // KIỂM TRA HỢP LỆ: Chỉ ghi nếu key có trong validCurrencies
                    if (validCurrencies.contains(key)) {
                        // Format: dateUTC, dateVN, base, currency, rate
                        pw.println(dateSQL + "," + dateSQL + "," + base + "," + key + "," + rates.getDouble(key));
                        count++;
                    } else {
                        // (Optional) Log warning những mã rác bị loại bỏ
                        // System.out.println("Ignored invalid currency: " + key);
                    }
                }
            }

            // Ghi file pointer cho script sh đọc
            try (PrintWriter pwPointer = new PrintWriter(new FileWriter(outputDir + File.separator + "latest_file.txt"))) {
                pwPointer.print(fullPath);
            }

            // Log DB
            PreparedStatement ps = conn.prepareStatement("INSERT INTO file_log (fileConfigId, fileName, filePath, status, recordCount, extractedAt) VALUES (?, ?, ?, 'FINISHED', ?, NOW())");
            ps.setInt(1, configId);
            ps.setString(2, fileName);
            ps.setString(3, fullPath);
            ps.setInt(4, count);
            ps.executeUpdate();

            LogUtils.log("RUNNING", "Crawled " + count + " valid rates (Filtered from " + rates.length() + ")");
            System.exit(0);

        } catch (Exception e) {
            LogUtils.log("ERROR", "Crawler: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Hàm helper để gọi API cho gọn code
    private static String callApi(String urlStr) throws Exception {
        StringBuilder sb = new StringBuilder();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000); // Timeout 10s
        conn.setReadTimeout(10000);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
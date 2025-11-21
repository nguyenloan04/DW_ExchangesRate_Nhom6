package crawler;
import common.DBConnector;
import common.LogUtils;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
public class DataCrawler {
    public static void main(String[] args) {
        String csvPath = args[0]; // Nhận từ script .sh
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            // 1. Lấy Config
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM file_config WHERE sourceName='OpenExchangeRates' LIMIT 1");
            if(!rs.next()) throw new Exception("Config not found");

            String urlStr = rs.getString("sourceUrl") + "?app_id=" + rs.getString("apiKey");
            String baseConf = rs.getString("baseCurrency");

            // 2. Gọi API
            HttpURLConnection http = (HttpURLConnection) new URL(urlStr).openConnection();
            BufferedReader br = new BufferedReader(new InputStreamReader(http.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);

            // 3. Parse & Write CSV
            JSONObject json = new JSONObject(sb.toString());
            JSONObject rates = json.getJSONObject("rates");
            long ts = json.getLong("timestamp");
            String base = json.getString("base");

            // Format ngày cho MySQL
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateStr = sdf.format(new Date(ts * 1000L));

            try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath))) {
                for (String key : rates.keySet()) {
                    // CSV: dateUTC, dateVN, base, currency, rate
                    pw.println(dateStr + "," + dateStr + "," + base + "," + key + "," + rates.getDouble(key));
                }
            }

            // 4. Log File
            PreparedStatement ps = conn.prepareStatement("INSERT INTO file_log (fileName, filePath, status, recordCount) VALUES (?, ?, 'FINISHED', ?)");
            ps.setString(1, new File(csvPath).getName());
            ps.setString(2, csvPath);
            ps.setInt(3, rates.length());
            ps.executeUpdate();

            LogUtils.log("RUNNING", "Crawled " + rates.length() + " records");
            System.exit(0);

        } catch (Exception e) {
            LogUtils.log("ERROR", "Crawler: " + e.getMessage());
            System.exit(1);
        }
    }
}

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
        String dataDir = args[0];
        String tDate = (args.length > 1) ? args[1] : null;
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_CONTROL)) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM file_config LIMIT 1");
            rs.next();
            String apiKey = rs.getString("apiKey"), reqUrl, safeUrl;

            if (tDate != null) {
                safeUrl = rs.getString("historyUrl") + tDate + ".json";
                reqUrl = safeUrl + "?app_id=" + apiKey;
            } else {
                safeUrl = rs.getString("sourceUrl");
                reqUrl = safeUrl + "?app_id=" + apiKey;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(new URL(reqUrl).openStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            JSONObject json = new JSONObject(sb.toString());
            JSONObject rates = json.getJSONObject("rates");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dSQL = sdf.format(new Date(json.getLong("timestamp") * 1000L));
            String fName = "exchange_" + (tDate != null ? tDate : sdf.format(new Date())) + ".csv";

            // Save Map Currency
            String currResp = new Scanner(new URL(rs.getString("currenciesUrl")).openStream()).useDelimiter("\\A").next();
            try (FileWriter fw = new FileWriter(dataDir + "/currencies.json")) {
                fw.write(currResp);
            }

            try (PrintWriter pw = new PrintWriter(new FileWriter(dataDir + "/" + fName))) {
                for (String k : rates.keySet())
                    pw.println(dSQL + "," + dSQL + "," + json.getString("base") + "," + k + "," + rates.getDouble(k) + "," + safeUrl);
            }
            try (PrintWriter p = new PrintWriter(new FileWriter(dataDir + "/latest_file.txt"))) {
                p.print(dataDir + "/" + fName);
            }

            PreparedStatement ps = conn.prepareStatement("INSERT INTO file_log (fileName,source_link,status,recordCount) VALUES (?,?,'FINISHED',?)");
            ps.setString(1, fName);
            ps.setString(2, safeUrl);
            ps.setInt(3, rates.length());
            ps.executeUpdate();
            LogUtils.log("RUNNING", "Crawled from " + safeUrl);
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
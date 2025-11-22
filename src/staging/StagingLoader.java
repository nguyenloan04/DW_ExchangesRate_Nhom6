package staging;

import common.*;

import java.io.*;
import java.sql.*;

public class StagingLoader {
    public static void main(String[] args) {
        try (Connection conn = DBConnector.getConnection(DBConnector.DB_STAGING)) {
            BufferedReader br = new BufferedReader(new FileReader(args[0]));
            br.mark(1024);
            String f = br.readLine();
            br.reset();
            if (f != null)
                conn.prepareCall("{call Clean_Staging_By_Date('" + f.split(",")[1].split(" ")[0] + "')}").execute();

            PreparedStatement ps = conn.prepareStatement("INSERT INTO stg_exchange_rate (sourceDateUTC,sourceDateVN,baseCurrency,currency,rate,source_link) VALUES (?,?,?,?,?,?)");
            conn.setAutoCommit(false);
            String l;
            while ((l = br.readLine()) != null) {
                String[] d = l.split(",");
                ps.setString(1, d[0]);
                ps.setString(2, d[1]);
                ps.setString(3, d[2]);
                ps.setString(4, d[3]);
                ps.setDouble(5, Double.parseDouble(d[4]));
                ps.setString(6, d[5]);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            LogUtils.log("RUNNING", "Raw Staging Loaded");
            System.exit(0);
        } catch (Exception e) {
            LogUtils.log("ERROR", e.getMessage());
            System.exit(1);
        }
    }
}
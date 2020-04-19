package id.ac.itb.students.pppmbkpdb;

import com.mashape.unirest.http.Unirest;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.UUID;

/**
 * Created by didithilmy on 4/2/18.
 */
public class RecordManager {
    private static RecordManager instance;

    Connection connection = null;

    public static RecordManager getInstance() {
        if(instance == null) instance = new RecordManager();
        return instance;
    }

    private RecordManager() {
        try {
            //Path path = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            // create a database connection
            connection = DriverManager.getConnection("jdbc:sqlite:pppmbkpdb.db");
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.

            statement.executeUpdate("create table if not exists record (id string, nim string, time long, room string)");

            System.out.println("Database initialized");
        } catch(SQLException e) {
            // if the error message is "out of memory",
            // it probably means no database file is found
            System.err.println(e.getMessage());
        }/* finally {
            try {
                if(connection != null)
                    connection.close();
            } catch(SQLException e) {
                // connection close failed.
                e.printStackTrace();
            }
        }*/
    }

    public boolean appendRecord(String nim, String room, long timestamp) {
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate("insert into `record` (`id`, `nim`, `time`, `room`) values('" + UUID.randomUUID().toString() + "', '" + nim + "', '" + timestamp + "', '" + room + "')");
            System.out.println("Record added for NIM " + nim);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public JSONArray getRecords() {
        try {
            JSONArray jsonArray = new JSONArray();
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("select * from record");
            while (rs.next()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("student_id", rs.getString("nim"));
                jsonObject.put("id", rs.getString("id"));
                jsonObject.put("recorded_at", rs.getLong("time"));
                jsonObject.put("ruangan", rs.getString("room"));
                jsonArray.put(jsonObject);

                // read the result set
                //System.out.println(jsonObject.toString());
            }

            return jsonArray;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteRecords(JSONArray ids) {
        try {
            Statement statement = connection.createStatement();
            for(int i = 0; i < ids.length(); i++) {
                statement.executeUpdate("delete from `record` where `id`='" + ids.getString(i) + "'");
                System.out.println("Removed record " + ids.getString(i));
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean closeConnection() {
        try {
            if (connection != null)
                connection.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}

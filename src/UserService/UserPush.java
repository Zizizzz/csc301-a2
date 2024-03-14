package UserService;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;

public class UserPush {
    private static Connection connection;
    private static byte[] salt;
//  private static HashMap<String, String[]> newTable;
    private static JSONObject newTable= new JSONObject();

    public static void main(String[] args) throws Exception {
        final int[] i = {0};
        String addr = "142.1.46.112";
        int port = 6768;
        HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
        // Set up context for /user POST request
        server.createContext("/userpush", new UserPushHandler());

        connection = DriverManager.getConnection("jdbc:sqlite:./UserDB.sqlite");
        initializeDatabase(connection);

        server.setExecutor(null); // creates a default executor

        // Start the scheduled task to push newTable to database every 5 seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println("Server Running" + i[0]);
                i[0] = i[0] +1;
                sql_print("users", "jdbc:sqlite:./UserDB.sqlite");
                if (!newTable.isEmpty()) {
                    System.out.println("Called helper to push to DB");
                    pushNewTableToDB();
                }
            }
        }, 0, 2, TimeUnit.SECONDS);

        server.start();

        System.out.println("UserPushServer started on port " + port);
    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        if (!checkTableExists(conn, "users")) {
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE users (" +
                        "id INTEGER PRIMARY KEY," +
                        "username TEXT NOT NULL," +
                        "email TEXT NOT NULL," +
                        "password TEXT NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'users' created.");
            }
        }
    }

    private static void clearTableData(Connection conn, String tableName) throws SQLException {
        String sql = "DELETE FROM " + tableName;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Data cleared from table '" + tableName + "'.");
        }
    }

    private static boolean checkTableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData dbm = conn.getMetaData();
        try (ResultSet tables = dbm.getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }

    private static void pushNewTableToDB() {
        try {
            if (!newTable.isEmpty()) {
                System.out.println("Table is not empty I will call clean DB table helper");
                clearTableData(connection, "users");
                for (Object key: newTable.keySet()){
                    int id = Integer.parseInt(key.toString());
                    Object value = newTable.get(key);
                    String[] values = value.toString().split(",");

                    if (values.length >= 3) {
                        String name = values[0];
                        String email = values[1];
                        String password = values[2];

                        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setInt(1, id);
                            pstmt.setString(2, name);
                            pstmt.setString(3, email);
                            pstmt.setString(4, password);
                            pstmt.executeUpdate();
                            System.out.println("Inserted: Id = " + id +", Name = " + name + ", Email = " + email + ", Password = " + password);
                            sql_print("users","jdbc:sqlite:./UserDB.sqlite");
                        }
                    } else {
                        System.out.println("Invalid array length for key: " + key);
                    }
                }
                // Clear the newTable only after all entries have been processed
                newTable.clear();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void sql_print(String table, String JDBC_URL) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            String selectSql = "SELECT * FROM " + table;

            try (Statement selectStatement = connection.createStatement();
                 ResultSet resultSet = selectStatement.executeQuery(selectSql)) {

                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Print column headers
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(metaData.getColumnName(i) + "\t");
                }
                System.out.println();

                // Print data
                while (resultSet.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        System.out.print(resultSet.getString(i) + "\t");
                    }
                    System.out.println();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    static class UserPushHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            String failedJSON = "{}";
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser parser = new JSONParser();
                    JSONObject requestData = (JSONObject) parser.parse(new String(requestBody.readAllBytes()));
                    newTable = requestData;
                    int responseCode = 200;
                    sendResponse(exchange, failedJSON, responseCode);
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace();
                sendResponse(exchange, failedJSON, 400);
            }finally {
                exchange.close();
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, String response, int code) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

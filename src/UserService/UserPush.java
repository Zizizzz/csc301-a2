package UserService;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.sql.*;

public class UserPush {
    private static Connection connection;
    private static byte[] salt;
    private static HashMap<String, String[]> newTable;

    public static void main(String[] args) throws Exception {
        String addr = "196.144.23.190";
        int port = 6768;
        HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
        // Set up context for /user POST request
        server.createContext("/userpush", new UserPushHandler());

        connection = DriverManager.getConnection("jdbc:sqlite:./../../src/UserService/UserDB.sqlite");
        initializeDatabase(connection);

        server.setExecutor(null); // creates a default executor

        // Start the scheduled task to push newTable to database every 5 seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (newTable != null && !newTable.isEmpty()) {
                    pushNewTableToDB();
                }
            }
        }, 0, 5, TimeUnit.SECONDS);

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
            if (newTable != null && !newTable.isEmpty()) {
                clearTableData(connection, "users");
                for (Map.Entry<String, String[]> entry : newTable.entrySet()) {
                    String key = entry.getKey();
                    int id = Integer.parseInt(key);
                    String[] value = entry.getValue();

                    if (value.length >= 3) {
                        String name = value[0];
                        String email = value[1];
                        String password = value[2];

                        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setInt(1, id);
                            pstmt.setString(1, name);
                            pstmt.setString(2, email);
                            pstmt.setString(3, password);
                            pstmt.executeUpdate();
                            System.out.println("Inserted: Id = " + id +", Name = " + name + ", Email = " + email + ", Password = " + password);
                        }
                    } else {
                        System.err.println("Invalid array length for key: " + key);
                    }
                }
                // Clear the newTable only after all entries have been processed
                newTable.clear();
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
                    JSONObject json = new JSONObject(new JSONTokener(requestBody));
                    newTable = new HashMap<>(json.toMap());
                    int responseCode = 200;
                    sendResponse(exchange, failedJSON, responseCode);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, failedJSON, 400);
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

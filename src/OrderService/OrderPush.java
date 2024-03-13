package OrderService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OrderPush {
    private static Connection connection;
    private static byte[] salt;
    private static HashMap<String, String[]> newTable;

    public static void main(String[] args) throws Exception {
        String addr = "196.144.23.190";
        int port = 6770;
        HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
        // Set up context for /user POST request
        server.createContext("/orderpush", new OrderPushHandler());

        connection = DriverManager.getConnection("jdbc:sqlite:./../../src/OrderService/OrderDB.sqlite");
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

        System.out.println("OrderPushServer started on port " + port);
    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        if (!checkTableExists(conn, "orders")) {
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE orders (" +
                        "id INTEGER PRIMARY KEY," +
                        "product_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "quantity INTEGER NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'orders' created.");
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
                clearTableData(connection, "orders");
                for (Map.Entry<String, String[]> entry : newTable.entrySet()) {
                    String key = entry.getKey();
                    int orderid = Integer.parseInt(key);
                    String[] value = entry.getValue();

                    if (value.length >= 3) {
                        int userid = Integer.parseInt(value[0]);
                        int productid = Integer.parseInt(value[1]);
                        int quantity = Integer.parseInt(value[2]);

                        String sql = "INSERT INTO orders (id, user_id, product_id, quantity) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setInt(1, orderid);
                            pstmt.setInt(1, userid);
                            pstmt.setInt(2, productid);
                            pstmt.setInt(3, quantity);
                            pstmt.executeUpdate();
                            System.out.println("Inserted: Id = " + orderid +", userid = " + userid + ", productid = " + productid + ", quantity = " + quantity);
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

    static class OrderPushHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            String failedJSON = "{}";
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser parser = new JSONParser();
                    Object obj = parser.parse(new InputStreamReader(requestBody, StandardCharsets.UTF_8));
                    if (obj instanceof JSONObject) {
                        JSONObject json = (JSONObject) obj;
                        newTable = new HashMap<>(json);

                        int responseCode = 200;
                        sendResponse(exchange, failedJSON, responseCode);
                    } else {
                        System.err.println("Invalid JSON format in request body");
                        sendResponse(exchange, failedJSON, 400);
                    }
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
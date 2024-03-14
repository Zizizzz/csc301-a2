package ProductService;

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

public class ProductPush {
    private static Connection connection;
    private static byte[] salt;
    private static HashMap<String, String[]> newTable;

    public static void main(String[] args) throws Exception {
        String addr = "127.0.0.1";
        int port = 6769;
        HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
        // Set up context for /user POST request
        server.createContext("/productpush", new ProductPushHandler());

        connection = DriverManager.getConnection("jdbc:sqlite:./ProductDB.sqlite");
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

        System.out.println("ProductPushServer started on port " + port);
    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        if (!checkTableExists(conn, "products")) {
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE products (" +
                        "id INTEGER PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "description TEXT," +
                        "price DECIMAL(10, 2) NOT NULL," +
                        "quantity INTEGER NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'products' created.");
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
                clearTableData(connection, "products");
                for (Map.Entry<String, String[]> entry : newTable.entrySet()) {
                    String key = entry.getKey();
                    int id = Integer.parseInt(key);
                    String[] value = entry.getValue();

                    if (value.length >= 4) {
                        String name = value[0];
                        String description = value[1];
                        float price = Float.parseFloat(value[2]);
                        int quantity = Integer.parseInt(value[3]);

                        String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setInt(1, id);
                            pstmt.setString(1, name);
                            pstmt.setString(2, description);
                            pstmt.setFloat(3, price);
                            pstmt.setInt(4, quantity);
                            System.out.println("Inserted: Id = " + id +", name = " + name + ", description = " + description + ", price = " + price + ", quantity = " + quantity);
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

    static class ProductPushHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            String failedJSON = "{}";
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    System.out.println(requestBody);
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

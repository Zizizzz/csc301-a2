package UserService;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.sql.*;

public class UserPush {
    private static Connection connection;
    private static byte[] salt;
    private static HashMap<String, Object> newTable;


    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.out.println("Command: java UserPush <config file>");
        }
        else{
            JSONObject config = readConfig(args[0]);
            String addr = config.get("ip").toString();
            int port = Integer.parseInt(config.get("port").toString());

            HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
            // Example: Set a custom executor with a fixed-size thread pool
            server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
            // Set up context for /user POST request
            server.createContext("/user", new UserHandler());

            connection = DriverManager.getConnection("jdbc:sqlite:./../../src/UserService/UserDB.sqlite");
            initializeDatabase(connection);

            server.setExecutor(null); // creates a default executor

            server.start();

            System.out.println("UserPushServer started on port " + port);

        }
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

    public static JSONObject readConfig(String path) throws Exception{
        Object ob = new JSONParser().parse(new FileReader(path));

        JSONObject js = (JSONObject) ob;

        return (JSONObject) js.get("UserService");
    }

    static class UserPushHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange){
            String failedJSON = "{}";
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    StringBuilder sb = new StringBuilder();
                    int nextByte;
                    while ((nextByte = requestBody.read()) != -1) {
                        sb.append((char) nextByte);
                    }

                    // Parse the JSON data into a JSONObject
                    JSONObject json = new JSONObject(sb.toString());

                    newTable = new HashMap<>(json.toMap());
                    String[] keyNames = {"id", "username", "email", "password"};

                    int responseCode = 200;
                    sendResponse(exchange, failedJSON, responseCode);
                    exchange.close();
                }
            }catch(Exception e){
                e.printStackTrace();
                sendResponse(exchange, failedJSON, 400);
                exchange.close();
            }
        }
    }

    private static void sendResponse(HttpExchange exchange, String response, int code){
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

//    public static boolean checkIdExist(int userId) throws SQLException {
//        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";
//        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
//            pstmt.setInt(1, userId);
//            try (ResultSet rs = pstmt.executeQuery()) {
//                if (rs.next()) {
//                    return rs.getInt(1) > 0;
//                }
//            }
//        }
//        return false;
//    }
}

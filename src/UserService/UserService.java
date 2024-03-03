import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.Base64;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.io.FileReader;
import java.sql.*;

public class UserService {
    private static Connection connection;
    private static byte[] salt;
    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.out.println("Command: java UserService <config file>");
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

            System.out.println("UserServer started on port " + port);

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
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE users1 (" +
                        "id INTEGER PRIMARY KEY," +
                        "username TEXT NOT NULL," +
                        "email TEXT NOT NULL," +
                        "password TEXT NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'users1' created.");
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
    private static boolean move_table(Connection conn, String old_table, String new_table) throws SQLException {
        if (isTableEmpty(connection, old_table)) {
            // If old_table is empty, return "success" since there's nothing to move
            return true;
        }
        String moveSql = "INSERT INTO " + new_table + " SELECT * FROM " + old_table;

        try (PreparedStatement moveStatement = connection.prepareStatement(moveSql)) {
            // Execute the SQL statement to move entities
            int rowsAffected = moveStatement.executeUpdate();
            return rowsAffected > 0;
        }
    }
    private static boolean isTableEmpty(Connection connection, String table) throws SQLException {
        // Construct SQL statement to count rows in the table
        String countSql = "SELECT COUNT(*) FROM " + table;

        try (PreparedStatement countStatement = connection.prepareStatement(countSql);
             ResultSet resultSet = countStatement.executeQuery()) {
            // Check if the count is 0 (table is empty)
            return resultSet.next() && resultSet.getInt(1) == 0;
        }
        
    }


    private static boolean checkTableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData dbm = conn.getMetaData();
        try (ResultSet tables = dbm.getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }

    public static JSONObject readConfig(String path) throws Exception{
        Object ob = new JSONParser().parse(new FileReader(path));

        JSONObject js = (JSONObject) ob;

        return (JSONObject) js.get("UserService");
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange){
            try {
                // Handle POST request for /user
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));
                    JSONObject responseData = new JSONObject();
                    responseData.put("id", requestData.get("id"));
                    responseData.put("username", requestData.get("username"));
                    responseData.put("email", requestData.get("email"));
                    responseData.put("password", requestData.get("password"));
                    String[] keyNames = {"command", "id", "username", "email", "password"};
                    int responseCode = 200;
                    switch (requestData.get("command").toString()) {
                        case "create":
                            String badCreateRequest = "Bad request: ";
                            for (String keyName: keyNames){
                                if (requestData.get(keyName) == null){
                                    responseCode = 400;
                                    badCreateRequest = badCreateRequest + "\"" + keyName + "\", ";
                                }
                            }
                            if (responseCode == 400){
                                badCreateRequest = badCreateRequest.substring(0, badCreateRequest.length() - 2) + " missing. ";
                                sendResponse(exchange, badCreateRequest + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleCreateUser(requestData);
                            if (responseCode == 409){
                                String duplicateUser = "Duplicate user already exist. ";
                                sendResponse(exchange, duplicateUser + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            } else if (responseCode == 400){
                                sendResponse(exchange, "Bad Request: Exception appear. " + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            } else{
                                sendResponse(exchange, "Successfully create new user: " + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            }
                        case "update":
                            if (requestData.get("id") == null){
                                responseCode = 400;
                                String badUpdateRequest = "Bad request: Missing user id. ";
                                System.out.println(badUpdateRequest);
                                sendResponse(exchange, badUpdateRequest + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleUpdateUser(requestData);
                            if (responseCode == 400){
                                sendResponse(exchange, "Bad Request: Exception appear. " + responseData.toString(), responseCode);
                                exchange.close();
                            } else if (responseCode == 404){
                                sendResponse(exchange, "User not Found. " + responseData.toString(), responseCode);
                                exchange.close();
                            } else{
                                sendResponse(exchange, "Successfully update new user: " + responseData.toString(), responseCode);
                                exchange.close();
                            }
                            break;
                        case "delete":
                            for (String keyName: keyNames){

                                if (requestData.get(keyName) == null){
                                    responseCode = 400;
                                }
                            }
                            if (responseCode == 400){
                                sendResponse(exchange, "", responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleDeleteUser(requestData);
                            sendResponse(exchange, "", responseCode);
                            exchange.close();
                            break;
                        case "start":
                            clearTableData(connection,"users");
                            move_table(connection,"users1","users");
                            JSONObject responseData1 = new JSONObject();
                            responseData1.put("command", "restart");
                            sendResponse(exchange, responseData1.toString(), 200);
                            exchange.close();
                            break;
                        case "shutdown":
                            clearTableData(connection,"users1");
                            move_table(connection,"users","users1");
                            clearTableData(connection,"users");
                            JSONObject responseData1 = new JSONObject();
                            responseData1.put("command", "shutdown");
                            sendResponse(exchange, responseData1.toString(), 200);
                            exchange.close();
                            System.exit(1);
                            break;
                        default:
                            // Handle unknown operation
                            sendResponse(exchange, "Unknown operation: " + requestData.toString(), 400);
                            exchange.close();
                            break;
                    }
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    if (pathSegments.length < 3){
                        sendResponse(exchange, "Incorrect Get request", 400);
                        exchange.close();
                    } else{
                        int userId = Integer.parseInt(pathSegments[2]);
                        Map<String, String> response = handleGetUser(userId);
                        sendResponse(exchange, response.get("message"), Integer.parseInt(response.get("code")));
                        exchange.close();
                    }
                } else {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                }
            } catch(Exception e){
                e.printStackTrace();
                sendResponse(exchange, "Bad request: Exception. " + exchange.getRequestBody().toString(), 400);
                exchange.close();
            }
        }



    }

    private static void sendResponse(HttpExchange exchange, String response, int code){
        try {
            exchange.sendResponseHeaders(code, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private static int handleCreateUser(JSONObject requestData) throws SQLException {
        // Implement user creation logic
        int responseCode = 200;
        int userId = Integer.parseInt(requestData.get("id").toString());
        String username = requestData.get("username").toString();
        String email = requestData.get("email").toString();
        String password = requestData.get("password").toString();

        if (checkIdExist(userId)){
            System.out.println("Duplicate user already exist. " + requestData.toString());
            responseCode = 409;
        } else {
            String insertQuery = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setInt(1, userId);
                preparedStatement.setString(2, username);
                preparedStatement.setString(3, email);
                preparedStatement.setString(4, hashPassword(password));

                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected == 1){
                    System.out.println("Successfully create new user: " + requestData.toString());
                } else{
                    responseCode = 409;
                    System.out.println("Duplicate user already exist. " + requestData.toString());
                }
            } catch (Exception e){
                responseCode = 400;
                e.printStackTrace();
                System.out.println("Failed to create user: " + requestData.toString());
            }
        }
        return responseCode;
    }

    private static int handleUpdateUser(JSONObject requestData) throws SQLException {
        // Implement user update logic
        int responseCode = 200;
        int userId = Integer.parseInt(requestData.get("id").toString());
        String username = "";
        String email = "";
        String password = "";

        if (!checkIdExist(userId)){
            responseCode = 404;
        } else {
            String updateQuery = "UPDATE users SET ";
            boolean toUpdate = false;
            if (requestData.get("username") != null){
                username = requestData.get("username").toString();
                updateQuery = updateQuery +  "username = ?, ";
                toUpdate = true;
            }if (requestData.get("email") != null) {
                email = requestData.get("email").toString();
                updateQuery = updateQuery +  "email = ?, ";
                toUpdate = true;
            }if (requestData.get("password") != null){
                password = hashPassword(requestData.get("password").toString());
                updateQuery = updateQuery +  "password = ?, ";
                toUpdate = true;
            }if (toUpdate){
                updateQuery = updateQuery.substring(0, updateQuery.length() - 2)+ " WHERE id = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(updateQuery)) {
                     int parameterIndex = 1;
                     if (!Objects.equals(username, "")) {
                         preparedStatement.setString(parameterIndex++, username);
                     }
                     if (!Objects.equals(email, "")) {
                         preparedStatement.setString(parameterIndex++, email);
                     }
                     if (!Objects.equals(password, "")) {
                         preparedStatement.setString(parameterIndex++, password);
                     }
                     preparedStatement.setInt(parameterIndex, userId);
                     int rowsAffected = preparedStatement.executeUpdate();
                     if (rowsAffected == 1){
                         System.out.println("Successfully update user: " + requestData.toString());
                     } else{
                         responseCode = 400;
                         System.out.println("Failed to update user: " + requestData.toString());
                     }
                } catch (Exception e){
                     responseCode = 400;
                     e.printStackTrace();
                     System.out.println("Failed to update user: " + requestData.toString());
                }
            } else{
                System.out.println("Successfully update user: " + requestData.toString());
            }
        }
        return responseCode;
    }

    private static int handleDeleteUser(JSONObject requestData) throws IOException, SQLException {
        int userId = Integer.parseInt(requestData.get("id").toString());
        String username = requestData.get("username").toString();
        String email = requestData.get("email").toString();
        String password = hashPassword(requestData.get("password").toString());

        if (userCheck(userId, username, email, password)) {
            String deleteQuery = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(deleteQuery)) {
                preparedStatement.setInt(1, userId);
                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected > 0) {
                    return 200;
                } else {
                    System.out.println("User not found. " + requestData.toString());
                    return 404;
                }
            } catch (SQLException e) {
                System.out.println("Internal Server Error. " + requestData.toString());
                e.printStackTrace();
                return 500;
            }
        } else {
            System.out.println("User details do not match. " + requestData.toString());
            return 400;
        }
    }

    private static Map<String, String> handleGetUser(int userId) {
        // Implement user retrieval logic
        Map<String, String> response = new HashMap<>();
        String message = "";
        String responseCode = "";
        // Prepare the SQL query
        String sql = "SELECT id, username, email, password FROM users WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            // Set the user ID parameter
            preparedStatement.setInt(1, userId);

            // Execute the query
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                // Check if a user was found
                if (resultSet.next()) {
                    // Retrieve user details
                    int retrievedUserId = resultSet.getInt("id");
                    String username = resultSet.getString("username");
                    String email = resultSet.getString("email");
                    String password = resultSet.getString("password");

                    // Use the retrieved user data as needed
                    message = "Get user: User ID: " + retrievedUserId + ", Username: " + username + ", Email: " + email + ", Password: " + password;
                    responseCode = "200";
                } else {
                    message = "User not found with ID: " + userId;
                    responseCode = "404";
                }

            }
        } catch (SQLException e) {
            responseCode = "400";
            e.printStackTrace();
            message = "Failed to get user";
        }
        System.out.println(message);
        response.put("message", message);
        response.put("code", responseCode);
        return response;
    }

    public static boolean userCheck(int userId, String username, String email, String password) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ? AND username = ? AND email = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public static boolean checkIdExist(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private static String hashPassword(String password) {
        try {
            byte[] salt = {-16, -119, 5, 100, -101, -43, -87, -107, -54, -92, -112, -2, -107, 33, 99, -32};
            // Combine salt and password, then hash using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hashedPassword = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Combine salt and hashed password, then encode to Base64
            byte[] combined = new byte[salt.length + hashedPassword.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hashedPassword, 0, combined, salt.length, hashedPassword.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            // Handle hashing algorithm exception
            return null;
        }
    }


}

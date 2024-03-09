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
            String failedJSON = "{}";
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
                                badCreateRequest = badCreateRequest.substring(0, badCreateRequest.length() - 2) +
                                        " missing. " + requestData.toString();
                                System.out.println(badCreateRequest);
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleCreateUser(requestData);
                            if (responseCode == 409){
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            } else if (responseCode == 400){
                                System.out.println("Bad Request: Exception appear. " + requestData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            } else{
                                Map<String, String> fullUserInfo = handleGetUser(Integer.parseInt(requestData.get("id").toString()));
                                sendResponse(exchange, fullUserInfo.get("data"), Integer.parseInt(fullUserInfo.get("code")));
                                exchange.close();
                                break;
                            }
                        case "update":
                            if (requestData.get("id") == null){
                                responseCode = 400;
                                String badUpdateRequest = "Bad request: Missing user id.";
                                System.out.println(badUpdateRequest + requestData);
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleUpdateUser(requestData);
                            if (responseCode == 400){
                                System.out.println("Bad Request: Exception appear. " + responseData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                            } else if (responseCode == 404){
                                System.out.println("User not Found. " + responseData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                            } else{
                                Map<String, String> fullUserInfo = handleGetUser(Integer.parseInt(requestData.get("id").toString()));
                                sendResponse(exchange, fullUserInfo.get("data"), Integer.parseInt(fullUserInfo.get("code")));
                                exchange.close();
                                break;
                            }
                            break;
                        case "delete":
                            for (String keyName: keyNames){
                                if (requestData.get(keyName) == null){
                                    responseCode = 400;
                                }
                            }
                            if (responseCode == 400){
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleDeleteUser(requestData);
                            sendResponse(exchange, failedJSON, responseCode);
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
                            JSONObject responseData2 = new JSONObject();
                            responseData2.put("command", "shutdown");
                            sendResponse(exchange, responseData2.toString(), 200);
                            exchange.close();
                            System.exit(1);
                            break;
                        default:
                            // Handle unknown operation
                            System.out.println("Unknown operation.");
                            sendResponse(exchange, failedJSON, 400);
                            exchange.close();
                            break;
                    }
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    if (pathSegments.length != 3){
                        System.out.println("Incorrect Get request");
                        sendResponse(exchange, failedJSON, 400);
                        exchange.close();
                    } else{
                        int userId = Integer.parseInt(pathSegments[2]);
                        Map<String, String> response = handleGetUser(userId);
                        sendResponse(exchange, response.get("data"), Integer.parseInt(response.get("code")));
                        exchange.close();
                    }
                } else {
                    System.out.println("Only accept POST or GET request.");
                    sendResponse(exchange, failedJSON, 405);
                    exchange.close();
                }
            } catch(Exception e){
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


    private static int handleCreateUser(JSONObject requestData) throws SQLException {
        // Implement user creation logic
        int responseCode = 200;
        int userId = Integer.parseInt(requestData.get("id").toString());
        String username = requestData.get("username").toString();
        String email = requestData.get("email").toString();
        String password = requestData.get("password").toString();

        // Validate the user data
        if (username.isEmpty() || email.isEmpty() ||
            password.isEmpty() || userId < 0) {
            // Bad Request due to missing, empty, or invalid fields
            return 400; 
        }

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

                if (username.isEmpty()){
                    return 400;
                }

                updateQuery = updateQuery +  "username = ?, ";
                toUpdate = true;
            }if (requestData.get("email") != null) {
                email = requestData.get("email").toString();

                if (email.isEmpty()){
                    return 400;
                }

                updateQuery = updateQuery +  "email = ?, ";
                toUpdate = true;
            }if (requestData.get("password") != null){
                password = hashPassword(requestData.get("password").toString());

                if (password.isEmpty()){
                    return 400;
                }

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
        JSONObject responseData = new JSONObject();
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
                    responseData.put("id", resultSet.getInt("id"));
                    responseData.put("username", resultSet.getString("username"));
                    responseData.put("email", resultSet.getString("email"));
                    responseData.put("password", resultSet.getString("password"));

                    // Use the retrieved user data as needed

                    responseCode = "200";
                } else {
                    responseCode = "404";
                }

            }
        } catch (SQLException e) {
            responseCode = "400";
        }
        response.put("data", responseData.toString());
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

    /**
     * hash the password by SHA-256 message digest
     * @param password
     * @return
     */
    private static String hashPassword(String password) {
        try {
            // Create a SHA-256 message digest
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Convert the password string to bytes and hash it
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Convert the hashed bytes to a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Handle the exception (e.g., log it or throw a runtime exception)
            e.printStackTrace();
            return null;
        }
    }


}

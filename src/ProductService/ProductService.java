
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.io.FileReader;

public class ProductService {
    private static Connection connection;
    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.out.println("Command: java ProductService <config file>");
        }
        else{
            JSONObject config = readConfig(args[0]);
            String addr = config.get("ip").toString();
            int port = Integer.parseInt(config.get("port").toString());

            HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
            // Example: Set a custom executor with a fixed-size thread pool
            server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
            // Set up context for /product request
            server.createContext("/product", new ProductHandler());

            connection = DriverManager.getConnection("jdbc:sqlite:./../../src/ProductService/ProductDB.sqlite");
            initializeDatabase(connection);
            server.setExecutor(null); // creates a default executor

            server.start();

            System.out.println("ProductServer started on port " + port);

        }


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
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE products1 (" +
                        "id INTEGER PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "description TEXT," +
                        "price DECIMAL(10, 2) NOT NULL," +
                        "quantity INTEGER NOT NULL)";
                stmt.execute(sql);
                System.out.println("Table 'products1' created.");
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

        return (JSONObject) js.get("ProductService");
    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                // Handle POST request for /product
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));
                    JSONObject responseData = new JSONObject();
                    responseData.put("id", requestData.get("id"));
                    responseData.put("name", requestData.get("name"));
                    responseData.put("price", requestData.get("price"));
                    responseData.put("quantity", requestData.get("quantity"));
                    responseData.put("description", requestData.get("description"));
                    String[] keyNames = {"command", "id", "name", "price", "quantity"};
                    int responseCode = 200;
                    switch (requestData.get("command").toString()) {
                        case "create":
                            String badCreateRequest = "Bad request: ";
                            for (String keyName : keyNames) {
                                if (requestData.get(keyName) == null) {
                                    responseCode = 400;
                                    badCreateRequest = badCreateRequest + "\"" + keyName + "\", ";
                                }
                            }
                            if (requestData.get("description") == null) {
                                responseCode = 400;
                                badCreateRequest = badCreateRequest + "\"" + "description" + "\", ";
                            }
                            if (responseCode == 400) {
                                badCreateRequest = badCreateRequest.substring(0, badCreateRequest.length() - 2) + " missing. ";
                                sendResponse(exchange, badCreateRequest + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleCreateProduct(requestData);
                            if (responseCode == 409) {
                                String duplicateProduct = "Duplicate product already exist. ";
                                sendResponse(exchange, duplicateProduct + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            } else if (responseCode == 400) {
                                sendResponse(exchange, "Bad Request: Exception appear. " + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            } else {
                                sendResponse(exchange, "Successfully create new product: " + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            }
                        case "update":
                            if (requestData.get("id") == null) {
                                responseCode = 400;
                                String badUpdateRequest = "Bad request: Missing product id. ";
                                System.out.println(badUpdateRequest);
                                sendResponse(exchange, badUpdateRequest + responseData.toString(), responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleUpdateProduct(requestData);
                            if (responseCode == 400) {
                                sendResponse(exchange, "Bad Request: Exception appear. " + responseData.toString(), responseCode);
                                exchange.close();
                            } else if (responseCode == 404) {
                                sendResponse(exchange, "Product not Found. " + responseData.toString(), responseCode);
                                exchange.close();
                            } else {
                                sendResponse(exchange, "Successfully update product: " + responseData.toString(), responseCode);
                                exchange.close();
                            }
                            break;
                        case "delete":
                            for (String keyName : keyNames) {

                                if (requestData.get(keyName) == null) {
                                    responseCode = 400;
                                }
                            }
                            if (responseCode == 400) {
                                sendResponse(exchange, "", responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleDeleteProduct(requestData);
                            sendResponse(exchange, "", responseCode);
                            exchange.close();
                            break;
                        case "start":
                            clearTableData(connection,"products");
                            move_table(connection,"products1","products");
                            JSONObject responseData1 = new JSONObject();
                            responseData1.put("command", "restart");
                            sendResponse(exchange, responseData1.toString(), 200);
                            exchange.close();
                            break;
                        case "shutdown":
                            clearTableData(connection,"products1");
                            move_table(connection,"products","products1");
                            clearTableData(connection,"products");
                            JSONObject responseData2 = new JSONObject();
                            responseData2.put("command", "shutdown");
                            sendResponse(exchange, responseData2.toString(), 200);
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
                    if (pathSegments.length < 3) {
                        sendResponse(exchange, "Incorrect Get request", 400);
                        exchange.close();
                    } else {
                        int productId = Integer.parseInt(pathSegments[2]);
                        Map<String, String> response = handleGetProduct(productId);
                        sendResponse(exchange, response.get("message"), Integer.parseInt(response.get("code")));
                        exchange.close();
                    }
                } else {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, "Bad request: Exception. " + exchange.getRequestBody().toString(), 400);
                exchange.close();
            }
        }



    }
    private static int handleCreateProduct(JSONObject requestData) throws SQLException {
        // Implement user creation logic
        int responseCode = 200;
        int productId = Integer.parseInt(requestData.get("id").toString());
        String name = requestData.get("name").toString();
        String description = requestData.get("description").toString();
        float price = Float.parseFloat(requestData.get("price").toString());
        int quantity = Integer.parseInt(requestData.get("quantity").toString());
        if (checkIdExist(productId)){
            System.out.println("Duplicate product already exist. " + requestData.toString());
            responseCode = 409;
        } else {
            String insertQuery = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setInt(1, productId);
                preparedStatement.setString(2, name);
                preparedStatement.setString(3, description);
                preparedStatement.setFloat(4, price);
                preparedStatement.setInt(5, quantity);

                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected == 1){
                    System.out.println("Successfully create new product: " + requestData.toString());
                } else{
                    responseCode = 409;
                    System.out.println("Duplicate product already exist. " + requestData.toString());
                }
            } catch (Exception e){
                responseCode = 400;
                e.printStackTrace();
                System.out.println("Failed to create product: " + requestData.toString());
            }
        }
        return responseCode;
    }

    private static int handleUpdateProduct(JSONObject requestData) throws SQLException {
        // Implement user update logic
        int responseCode = 200;
        int productId = Integer.parseInt(requestData.get("id").toString());
        String name = "";
        String description = "";
        String price = "";
        String quantity = "";

        if (!checkIdExist(productId)){
            System.out.println("Product doesn't exist.");
            responseCode = 404;
        } else {
            String updateQuery = "UPDATE products SET ";
            boolean toUpdate = false;
            if (requestData.get("name") != null){
                name = requestData.get("name").toString();
                updateQuery = updateQuery +  "name = ?, ";
                toUpdate = true;
            }if (requestData.get("description") != null) {
                description = requestData.get("description").toString();
                updateQuery = updateQuery +  "description = ?, ";
                toUpdate = true;
            }if (requestData.get("price") != null){
                price = requestData.get("price").toString();
                updateQuery = updateQuery +  "price = ?, ";
                toUpdate = true;
            }if (requestData.get("quantity") != null){
                quantity = requestData.get("quantity").toString();
                updateQuery = updateQuery +  "quantity = ?, ";
                toUpdate = true;
            }if (toUpdate){
                updateQuery = updateQuery.substring(0, updateQuery.length() - 2)+ " WHERE id = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(updateQuery)) {
                    int parameterIndex = 1;
                    if (!Objects.equals(name, "")) {
                        preparedStatement.setString(parameterIndex++, name);
                    }
                    if (!Objects.equals(description, "")) {
                        preparedStatement.setString(parameterIndex++, description);
                    }
                    if (!Objects.equals(price, "")) {
                        preparedStatement.setFloat(parameterIndex++, Float.parseFloat(price));
                    }
                    if (!Objects.equals(quantity, "")) {
                        preparedStatement.setInt(parameterIndex++, Integer.parseInt(quantity));
                    }
                    preparedStatement.setInt(parameterIndex, productId);
                    int rowsAffected = preparedStatement.executeUpdate();
                    if (rowsAffected == 1){
                        System.out.println("Successfully update product: " + requestData.toString());
                    } else{
                        responseCode = 400;
                        System.out.println("Failed to update product: " + requestData.toString());
                    }
                } catch (Exception e){
                    responseCode = 400;
                    e.printStackTrace();
                    System.out.println("Failed to update product: " + requestData.toString());
                }
            } else{
                System.out.println("Successfully update product: " + requestData.toString());
            }
        }
        return responseCode;
    }

    private static int handleDeleteProduct(JSONObject requestData) throws IOException, SQLException {
        int productId = Integer.parseInt(requestData.get("id").toString());
        String productName = requestData.get("name").toString();
        float price = Float.parseFloat(requestData.get("price").toString());
        int quantity = Integer.parseInt(requestData.get("quantity").toString());

        if (productCheck(productId, productName, price, quantity)) {
            String deleteQuery = "DELETE FROM products WHERE id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(deleteQuery)) {
                preparedStatement.setInt(1, productId);
                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Successfully delete product: " + requestData.toString());
                    return 200;
                } else {
                    System.out.println("product not found. " + requestData.toString());
                    return 404;
                }
            } catch (SQLException e) {
                System.out.println("Internal Server Error. " + requestData.toString());
                e.printStackTrace();
                return 500;
            }
        } else {
            System.out.println("product details do not match. " + requestData.toString());
            return 404;
        }
    }

    private static Map<String, String> handleGetProduct(int userId) {
        // Implement user retrieval logic
        Map<String, String> response = new HashMap<>();
        String message = "";
        String responseCode = "";
        // Prepare the SQL query
        String sql = "SELECT id, name, description, price, quantity FROM products WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            // Set the user ID parameter
            preparedStatement.setInt(1, userId);

            // Execute the query
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                // Check if a user was found
                if (resultSet.next()) {
                    // Retrieve user details
                    int retrievedProductId = resultSet.getInt("id");
                    String name = resultSet.getString("name");
                    String description = resultSet.getString("description");
                    float price = resultSet.getFloat("price");
                    int quantity = resultSet.getInt("quantity");

                    // Use the retrieved user data as needed
                    message = "Get product: Product ID: " + retrievedProductId + ", Product name: " + name +
                            ", Description: " + description + ", Price: " + price + ", Quantity: " + quantity;
                    responseCode = "200";
                } else {
                    message = "Product not found with ID: " + userId;
                    responseCode = "404";
                }

            }
        } catch (SQLException e) {
            responseCode = "400";
            e.printStackTrace();
            message = "Failed to get product";
        }
        System.out.println(message);
        response.put("message", message);
        response.put("code", responseCode);
        return response;
    }

    public static boolean productCheck(int productId, String productName, float price, int quantity) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE id = ? AND name = ? AND price = CAST(? AS DECIMAL) AND quantity = CAST(? AS INTEGER)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Set the values for the prepared statement
            pstmt.setInt(1, productId);
            pstmt.setString(2, productName);
            pstmt.setFloat(3, price);
            pstmt.setInt(4, quantity);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }


    public static boolean checkIdExist(int productId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // If the count is greater than 0, the user exists
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
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
}


import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.io.FileReader;
import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderService {
    private static Connection connection;
    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.out.println("Command: java OrderService <config file>");
        }
        else{
            JSONObject config = readConfig(args[0]);
            String addr = config.get("ip").toString();
            int port = Integer.parseInt(config.get("port").toString());

            HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
            // Example: Set a custom executor with a fixed-size thread pool
            server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
            server.createContext("/order", new PostHandler(config.get("us").toString(), config.get("ps").toString()));

            server.createContext("/user", new UserHandler(config.get("us").toString()));
            server.createContext("/product", new ProductHandler(config.get("ps").toString()));

            connection = DriverManager.getConnection("jdbc:sqlite:./../../src/OrderService/OrderDB.sqlite");
            initializeDatabase(connection);


            server.setExecutor(null); // creates a default executor

            server.start();

            System.out.println("OrderServer started on port " + port);
        }


    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        if (!checkTableExists(conn, "orders")) {
            try (Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE orders (" +
                        "id INTEGER PRIMARY KEY," +
                        "product_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "quantity INTEGER NOT NULL," +
                        "status TEXT NOT NULL)";
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
        JSONObject orderService = (JSONObject)js.get("OrderService");
        JSONObject us = (JSONObject)js.get("UserService");
        String uString = "http://" + us.get("ip").toString() + ":" + us.get("port").toString() + "/user";
        JSONObject ps = (JSONObject) js.get("ProductService");
        String pString = "http://" + ps.get("ip").toString() + ":" + ps.get("port").toString() + "/product";
        JSONObject iscs = (JSONObject) js.get("InterServiceCommunication");
        String iString = "http://" + iscs.get("ip").toString() + ":" + iscs.get("port").toString();
        orderService.put("us", uString);
        orderService.put("ps", pString);
        orderService.put("iscs", iString);

        return orderService;
    }
    private static int getOrderAmount(){
        int orderCount = 0;
        // Prepare the SQL query to count orders
        String sql = "SELECT MAX(id) AS max_order_id FROM orders";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            // Check if any result is returned
            if (resultSet.next()) {
                // Retrieve the max order ID
                orderCount = resultSet.getInt("max_order_id");

            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Handle database-related exceptions
        }
        return orderCount;
    }

    static class PostHandler implements HttpHandler {
        private static String upath;
        private static String ppath;

        // Constructor that takes a string <UserService location> during initialization
        public PostHandler(String upath, String ppath) {
            this.upath = upath;
            this.ppath = ppath;
        }
        public void handle(HttpExchange exchange){
            try {
                // Handle POST request for /order
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));
                    String[] keyNames = {"product_id", "user_id", "quantity"};
                    int responseCode = 200;
                    if (requestData.get("command") != null){
                        String command = requestData.get("command").toString();
                        switch (command){
                            case "place":
                                for (String keyName: keyNames){
                                    if (requestData.get(keyName) == null){
                                        responseCode = 400;
                                        sendResponse(exchange, "Bad request " + requestData.toString(), 400);
                                        exchange.close();
                                    }
                                }
                                if (responseCode != 400){
                                    String productId = requestData.get("product_id").toString();

                                    String userId = requestData.get("user_id").toString();

                                    int quantity = Integer.parseInt(requestData.get("quantity").toString());

                                    int orderId = getOrderAmount() + 1;
                                    String response = "Order id: " + orderId + ", User id: " + userId + ", Product id: " + productId + ", Status: ";
                                    if (userExist(userId)){
                                        Map<String, String> productInfo = getProductInfo(productId);
                                        if (productInfo.get("code").equals("200")){
                                            int productQuantity = Integer.parseInt(productInfo.get("quantity"));
                                            if (productQuantity == -1){
                                                System.out.println("Product quantity does not exist");
                                                response = response + "InvalidRequest.";
                                                sendResponse(exchange, response + " Product quantity does not exist.", 400);
                                                exchange.close();
                                            } else{
                                                if (quantity > productQuantity){
                                                    response = response + "Exceeded quantity limit";
                                                    sendResponse(exchange, response, 409);
                                                    exchange.close();
                                                } else{
                                                    response = response + "Success.";
                                                    sendResponse(exchange, response, 200);
                                                    exchange.close();
                                                }
                                            }
                                        } else{
                                            System.out.println("Product does not exist");
                                            response = response + "InvalidRequest.";
                                            sendResponse(exchange, response + " Product does not exist.", 400);
                                            exchange.close();
                                        }
                                    } else{
                                        System.out.println("User does not exist");
                                        response = response + "InvalidRequest.";
                                        sendResponse(exchange, response + "User does not exist.", 400);
                                        exchange.close();
                                    }
                                }
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
                                JSONObject responseData1 = new JSONObject();
                                responseData1.put("command", "shutdown");
                                sendResponse(exchange, responseData1.toString(), 200);
                                exchange.close();
                                System.exit(1);
                                break;
                            default:
                                sendResponse(exchange, "Bad request", 400);
                                exchange.close();
                        }
                    }


                } else {
                    // Send a 405 Method Not Allowed response for non-POST requests
                    sendResponse(exchange, "OrderService only accept POST request", 405);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, "Bad request: Exception. " + exchange.getRequestBody().toString(), 400);
                exchange.close();
            }
        }



        private static boolean userExist(String userId){
            boolean exist = false;
            try {
                URL url = new URL(upath + "/" + userId);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200){
                    exist = true;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            return exist;
        }
        private static Map<String, String> getProductInfo(String productId){
            Map<String, String> result = new HashMap<>();
            String responseCode = "";
            String quantity = "";
            try {
                URL url = new URL(ppath + "/" + productId);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");

                int responseCodeInt = connection.getResponseCode();
                if (responseCodeInt == HttpURLConnection.HTTP_OK) {
                    responseCode = "200";
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    quantity = getAmount(response.toString());
                    connection.disconnect();
                } else{
                    connection.disconnect();
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            result.put("quantity", quantity);
            result.put("code", responseCode);
            return result;
        }
        private static String getAmount(String productInfo){
            String quantity = "-1";
            // Define the pattern for extracting the user ID
            Pattern pattern = Pattern.compile("Quantity: (\\d+)");

            // Create a Matcher object and apply the pattern to the input string
            Matcher matcher = pattern.matcher(productInfo);

            // Check if the pattern is found
            if (matcher.find()) {
                // Extract the user ID from the matched group
                String uantityStr = matcher.group(1);

                // Convert the user ID to an integer
                quantity = uantityStr;
            }
            return quantity;
        }

    }

    static class UserHandler implements HttpHandler {
        private String upath;

        // Constructor that takes a string <UserService location> during initialization
        public UserHandler(String upath) {
            this.upath = upath;
        }
        public void handle(HttpExchange exchange){
            // Handle post request for /user
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    URL url = new URL(upath);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");

                    connection.setDoOutput(true);

                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));

                    OutputStream outputStream = connection.getOutputStream();
                    byte[] input = requestData.toString().getBytes("utf-8");
                    outputStream.write(input, 0, input.length);

                    int responseCode = connection.getResponseCode();
                    System.out.println("Response Code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                        System.out.println("conflict");
                    } try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder responseText = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseText.append(line);
                        }
                        System.out.println("Response Text: " + responseText.toString());
                        sendResponse(exchange, responseText.toString(), responseCode);
                        connection.disconnect();
                        exchange.close();
                    } catch(IOException e){
                        sendResponse(exchange, "Cannot read response text", responseCode);
                        connection.disconnect();
                        exchange.close();
                    }
                }
                // Handle get request for /user
                else if ("GET".equals(exchange.getRequestMethod())){
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    String userId = pathSegments[2];
                    URL url = new URL(upath + "/" + userId);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    System.out.println("Response Code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String inputLine;
                        StringBuilder response = new StringBuilder();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        System.out.println("Response Text: " + response.toString());
                        sendResponse(exchange, response.toString(), responseCode);
                        connection.disconnect();
                        exchange.close();
                    } else{
                        System.out.println("GET request fail.");
                        sendResponse(exchange, "GET request fail.", 400);
                        connection.disconnect();
                        exchange.close();
                    }
                } else {
                    System.out.println("User only accept POST or GET.");
                    sendResponse(exchange, "User only accept POST or GET.", 405);
                    exchange.close();
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    static class ProductHandler implements HttpHandler {
        private String ppath;

        // Constructor that takes a string input during initialization
        public ProductHandler(String ppath) {
            this.ppath = ppath;
        }
        public void handle(HttpExchange exchange){
            // Handle post request for /product
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    URL url = new URL(ppath);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");

                    connection.setDoOutput(true);

                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));

                    OutputStream outputStream = connection.getOutputStream();
                    byte[] input = requestData.toString().getBytes("utf-8");
                    outputStream.write(input, 0, input.length);

                    int responseCode = connection.getResponseCode();
                    System.out.println("Response Code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                        System.out.println("conflict");
                    } try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder responseText = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseText.append(line);
                        }
                        System.out.println("Response Text: " + responseText.toString());
                        sendResponse(exchange, responseText.toString(), responseCode);
                        connection.disconnect();
                        exchange.close();
                    } catch(IOException e){
                        sendResponse(exchange, "Cannot read response text", responseCode);
                        connection.disconnect();
                        exchange.close();
                    }
                }
                // Handle get request for /user
                else if ("GET".equals(exchange.getRequestMethod())){
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    String productId = pathSegments[2];
                    URL url = new URL(ppath + "/" + productId);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    System.out.println("Response Code: " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String inputLine;
                        StringBuilder response = new StringBuilder();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        System.out.println("Response Text" + response.toString());
                        sendResponse(exchange, response.toString(), responseCode);
                        connection.disconnect();
                        exchange.close();
                    } else{
                        System.out.println("GET request fail.");
                        sendResponse(exchange, "GET request fail.", 400);
                        connection.disconnect();
                        exchange.close();
                    }
                } else {
                    System.out.println("Product only accept POST or GET.");
                    sendResponse(exchange, "Product only accept POST or GET.", 405);
                    exchange.close();
                }
            }catch (Exception e) {
                e.printStackTrace();
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
}

// import com.sun.net.httpserver.HttpExchange;
// import com.sun.net.httpserver.HttpHandler;
// import com.sun.net.httpserver.HttpServer;
// import java.io.IOException;
// import java.io.OutputStream;
// import java.net.InetSocketAddress;
// import java.util.HashMap;
// import java.util.Map;
// import java.io.*;
// import java.nio.charset.StandardCharsets;
// import java.util.regex.Pattern;
// import java.util.regex.Matcher;
// import java.security.MessageDigest;
// import java.security.NoSuchAlgorithmException;
// import java.security.SecureRandom;
// import java.util.List;
// import java.util.Objects;
// import java.util.concurrent.Executors;
// import java.util.Base64;
// import org.json.simple.JSONObject;
// import org.json.simple.parser.*;

// public class CacheService {

//     // Define hash maps for caching requests for different services
//     private static Map<String, Map<String, String>> userServiceCache = new HashMap<>();
//     private static Map<String, Map<String, String>> productServiceCache = new HashMap<>();
//     private static Map<String, Map<String, String>> orderServiceCache = new HashMap<>();

//     public static void main(String[] args) throws IOException {
//         HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 10021), 0);
//         server.createContext("/api/user", new ServiceHandler(userServiceCache));
//         server.createContext("/api/product", new ServiceHandler(productServiceCache));
//         server.createContext("/api/order", new ServiceHandler(orderServiceCache));
//         server.start();
//         System.out.println("Server started on port 10021");
//     }

//     static class ServiceHandler implements HttpHandler {

//         private Map<String, Map<String, String>> cache;

//         public ServiceHandler(Map<String, Map<String, String>> cache) {
//             this.cache = cache;
//         }

//         @Override
//         public void handle(HttpExchange exchange) throws IOException {
//             if ("POST".equals(exchange.getRequestMethod())) {
//                 // Process the request body and store in cache
//                 String body = new String(exchange.getRequestBody().readAllBytes());
//                 String id = extractIdFromBody(body); // Implement this method based on your request structure
//                 Map<String, String> requestDetails = new HashMap<>();
//                 // Populate requestDetails map with other details from request body

//                 cache.put(id, requestDetails);

//                 String response = "Request stored successfully";
//                 exchange.sendResponseHeaders(200, response.getBytes().length);
//                 OutputStream os = exchange.getResponseBody();
//                 os.write(response.getBytes());
//                 os.close();
//             } else {
//                 String response = "Method Not Allowed";
//                 exchange.sendResponseHeaders(405, response.getBytes().length);
//                 OutputStream os = exchange.getResponseBody();
//                 os.write(response.getBytes());
//                 os.close();
//             }
//         }

//         private String extractIdFromBody(String body) {
//             // Dummy implementation, extract ID from the request body here
//             return "someId";
//         }
//     }
// }

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CacheService {
    private static Map<String, String[]> userServiceCache = new HashMap<>();
    private static Map<String, String[]> productServiceCache = new HashMap<>();
    private static Map<String, String[]> orderServiceCache = new HashMap<>();
    public static void main(String[] args) throws Exception {
        String addr = args[0];
        int port = Integer.parseInt(args[1]);

        HttpServer server = HttpServer.create(new InetSocketAddress(addr, port), 0);
        // Example: Set a custom executor with a fixed-size thread pool
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
        server.createContext("/order", new PostHandler());
        server.createContext("/user", new UserHandler());
        server.createContext("/product", new ProductHandler());


        server.setExecutor(null); // creates a default executor

        server.start();

        System.out.println("CacheServer started on port " + port);

//        往改寫器發東西
    }

    private static int getOrderAmount(){
        return orderServiceCache.size();
    }

    static class PostHandler implements HttpHandler {

        // Constructor that takes a string <UserService location> during initialization
        @Override
        public void handle(HttpExchange exchange){
            JSONObject failedResponseData = new JSONObject();
            try {
                // Handle POST request for /order
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream requestBody = exchange.getRequestBody();
                    JSONParser jsonParser = new JSONParser();
                    JSONObject requestData = (JSONObject) jsonParser.parse(new String(requestBody.readAllBytes()));
                    String[] keyNames = {"product_id", "user_id", "quantity"};
                    JSONObject responseData = new JSONObject();

                    int responseCode = 200;
                    if (requestData.get("command") != null){
                        String command = requestData.get("command").toString();
                        switch (command){
                            case "place order":
                                String badCreateRequest = "Bad request: ";
                                for (String keyName: keyNames){
                                    Object value = requestData.get(keyName);
                                    if (value == null){
                                        responseCode = 400;
                                        badCreateRequest = badCreateRequest + "\"" + keyName + "\", ";
                                    } else {
                                        if (value instanceof Number) {
                                            responseData.put(keyName, Integer.parseInt(value.toString()));
                                        } else {
                                            responseCode = 400;
                                            badCreateRequest = badCreateRequest + "\"" + keyName + "\", ";
                                        }
                                    }
                                }
                                if (responseCode == 400){
                                    badCreateRequest = badCreateRequest.substring(0, badCreateRequest.length() - 2) +
                                            " missing. " + requestData.toString();
                                    System.out.println(badCreateRequest);
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 400);
                                    exchange.close();
                                    break;
                                }
                                int quantity = Integer.parseInt(responseData.get("quantity").toString());
                                if (quantity <= 0){
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 400);
                                    exchange.close();
                                    break;
                                }
                                if (!userExist(responseData.get("user_id").toString())) {
                                    System.out.println("User does not exist");
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 404);
                                    exchange.close();
                                    break;
                                }
                                String[] productInfo = getProductInfo(responseData.get("product_id").toString());
                                if (productInfo[3] == null){
                                    System.out.println("Product does not exist");
                                    failedResponseData.put("status", "Invalid Request");
                                    sendResponse(exchange, failedResponseData.toString(), 404);
                                    exchange.close();
                                    break;
                                }
                                int productQuantity = Integer.parseInt(productInfo[3]);
                                if (quantity > productQuantity){
                                    failedResponseData.put("status", "Exceeded quantity limit");
                                    sendResponse(exchange, failedResponseData.toString(), 409);
                                    exchange.close();
                                    break;
                                } else{
                                    responseCode = handleCreateOrder(requestData);
                                    if (responseCode == 200){
                                        responseData.put("status", "Success");
                                        sendResponse(exchange, responseData.toString(), responseCode);
                                        exchange.close();
                                        break;
                                    } else {
                                        failedResponseData.put("status", "Invalid Request");
                                        sendResponse(exchange, failedResponseData.toString(), 400);
                                        exchange.close();
                                    }
                                }
                            default:
                                failedResponseData.put("status", "Invalid Request");
                                sendResponse(exchange, failedResponseData.toString(), 400);
                                exchange.close();
                        }
                    }


                } else {
                    // Send a 405 Method Not Allowed response for non-POST requests
                    System.out.println("OrderService only accept POST request");
                    failedResponseData.put("status", "Invalid Request");
                    sendResponse(exchange, failedResponseData.toString(), 405);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                failedResponseData.put("status", "Invalid Request");
                sendResponse(exchange, failedResponseData.toString(), 400);
                exchange.close();
            }
        }



        private static boolean userExist(String userId){
            return userServiceCache.containsKey(userId);
        }
        private static String[] getProductInfo(String productId){
            if (productServiceCache.containsKey(productId)){
                return productServiceCache.get(productId);
            }
            return new String[0];
        }


        private static int handleCreateOrder(JSONObject requestData){
            // Implement user creation logic
            int responseCode = 200;
            int orderId = getOrderAmount() + 1;
            String userId = requestData.get("user_id").toString();
            String productId = requestData.get("product_id").toString();
            int quantity = Integer.parseInt(requestData.get("quantity").toString());

            String[] productInfo = productServiceCache.get(requestData.get("product_id").toString());

            int productQuantity = Integer.parseInt(productInfo[3]) - quantity;
            productInfo[3] = Integer.toString(productQuantity);

            String[] newOrder = {userId,productId,requestData.get("quantity").toString()};
            orderServiceCache.put(Integer.toString(orderId), newOrder);

            return responseCode;
        }

    }

    static class UserHandler implements HttpHandler {

        // Constructor that takes a string <UserService location> during initialization
        private static final String EMAIL_PATTERN =
                "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        public static boolean isValidEmail(String email) {
            Pattern pattern = Pattern.compile(EMAIL_PATTERN);
            if (email == null) {
                return false;
            }
            Matcher matcher = pattern.matcher(email);
            return matcher.matches();
        }
        @Override
        public void handle(HttpExchange exchange){
            String failedJSON = "{}";
            // Handle post request for /user
            try {
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
                                Map<String, String> fullUserInfo = handleGetUser(requestData.get("id").toString());
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
                                Map<String, String> fullUserInfo = handleGetUser(requestData.get("id").toString());
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
                        default:
                            // Handle unknown operation
                            System.out.println("Unknown operation.");
                            sendResponse(exchange, failedJSON, 400);
                            exchange.close();
                            break;
                    }
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String[]    pathSegments = path.split("/");
                    if (pathSegments.length >= 3){
                        if (Objects.equals(pathSegments[2], "purchase")){
                            if (pathSegments.length != 4){
                                System.out.println("Wrong purchase url: user id missing.");
                                sendResponse(exchange, failedJSON, 400);
                                exchange.close();
                            } else{
                                if (userServiceCache.containsKey(pathSegments[3])){
                                    Map<String, String> response = handleGetOrder(pathSegments[3]);
                                    if (Objects.equals(response.get("code"), "200")){
                                        System.out.println("Successfully get user's order history.");
                                        sendResponse(exchange, response.get("data"), 200);
                                        exchange.close();
                                    } else {
                                        System.out.println("Failed to get user's order history.");
                                        sendResponse(exchange, failedJSON, Integer.parseInt(response.get("code")));
                                        exchange.close();
                                    }
                                } else {
                                    System.out.println("User does not exist.");
                                    sendResponse(exchange, failedJSON, 404);
                                    exchange.close();
                                }
                            }
                        } else{
                            int userId = Integer.parseInt(pathSegments[2]);
                            Map<String, String> response = handleGetUser(pathSegments[2]);
                            sendResponse(exchange, response.get("data"), Integer.parseInt(response.get("code")));
                            exchange.close();
                        }
                    } else {
                        System.out.println("Get request missing information.");
                        sendResponse(exchange, failedJSON, 400);
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
        private static Map<String, String> handleGetOrder(String userId) {
            Map<String, String> response = new HashMap<>();
            JSONObject responseData = new JSONObject();
            boolean userExists = false;
            boolean userHasPurchases = false;

            for (Map.Entry<String, String[]> entry : orderServiceCache.entrySet()) {
                String[] orderDetails = entry.getValue();
                // Assuming orderDetails[0] is userId, orderDetails[1] is productId, and orderDetails[2] is quantity
                if ((userId).equals(orderDetails[0])){
                    userExists = true;
                    String productId = orderDetails[1];
                    int quantity = Integer.parseInt(orderDetails[2]);
                    // Update the quantity for the productId, if already exists; else add it with the current quantity
                    responseData.put(productId, Integer.parseInt(responseData.getOrDefault(productId,
                            0).toString()) + quantity);
                    userHasPurchases = true;
                }
            }

            // Set response code based on user existence and purchases
            String responseCode = "200"; // Default response code
            if (!userExists) {
                responseCode = "404"; // User ID does not exist
                responseData = new JSONObject(); // Ensure responseData is empty
            } else if (!userHasPurchases) {
                // User exists but has no purchases, return empty JSON {}
                responseData = new JSONObject(); // Ensure responseData is empty
            }

            // Prepare the response
            response.put("data", responseData.toJSONString());
            response.put("code", responseCode);
            return response;
        }

        private static int handleCreateUser(JSONObject requestData){
            // Implement user creation logic
            int responseCode = 200;
            String userId = requestData.get("id").toString();
            String username = requestData.get("username").toString();
            String email = requestData.get("email").toString();
            String password = hashPassword(requestData.get("password").toString());

            // Validate the user data
            if (username.isEmpty() || !isValidEmail(email) ||
                    password.isEmpty() || Integer.parseInt(userId) < 0) {
                // Bad Request due to missing, empty, or invalid fields
                return 400;
            }

            if (userServiceCache.containsKey(userId)){
                System.out.println("Duplicate user already exist. " + requestData.toString());
                responseCode = 409;
            } else {
                String[] newUser = {username,email,password};
                userServiceCache.put(userId, newUser);
            }
            return responseCode;
        }

        private static int handleUpdateUser(JSONObject requestData){
//            username, email, password
            // Implement user update logic
            int responseCode = 200;
            String userId = requestData.get("id").toString();
            if (!userServiceCache.containsKey(userId)){
                System.out.println("User haven't been create. " + requestData.toString());
                responseCode = 404;
            } else {
                String[] newUser = userServiceCache.get(userId);
                if (requestData.get("username") != null){
                    String username = requestData.get("username").toString();
                    if (username.isEmpty()){
                        return 400;
                    }
                    newUser[0] = username;
                } if (requestData.get("email") != null) {
                    String email = requestData.get("email").toString();

                    if (!isValidEmail(email)){
                        return 400;
                    }
                    newUser[1] = email;
                } if (requestData.get("password") != null){
                    String password = hashPassword(requestData.get("password").toString());

                    if (password.isEmpty()){
                        return 400;
                    }
                    newUser[2] = password;
                }
                userServiceCache.put(userId, newUser);
            }
            return responseCode;
        }

        //已改
        private static int handleDeleteUser(JSONObject requestData) {
            String userId = requestData.get("id").toString();
            String username = requestData.get("username").toString();
            String email = requestData.get("email").toString();
            String password = hashPassword(requestData.get("password").toString());
            int exist = userCheck(userId, username, email, password);

            if (exist == 0) {
                userServiceCache.remove(userId);
                return 200;
            }else if (exist == 1) {
                System.out.println("User data didn't match");
                return 404;
            }else{
                System.out.println("User not exist or Missing arguments");
                return 404;
            }
        }

        private static Map<String, String> handleGetUser(String userId) {
            Map<String, String> response = new HashMap<>();
            JSONObject responseData = new JSONObject();
            String responseCode = "";
            if (!userServiceCache.containsKey(userId)){
                responseCode = "404";
            } else {
                responseCode = "200";
                String[] userInfo = userServiceCache.get(userId);
                responseData.put("id", Integer.parseInt(userId));
                responseData.put("username", userInfo[0]);
                responseData.put("email", userInfo[1]);
                responseData.put("password", userInfo[2]);
            }
            response.put("data", responseData.toString());
            response.put("code", responseCode);
            return response;
        }

    //改完了
        public static int userCheck(String userId, String username, String email, String password){
            String[] userInfo = userServiceCache.get(userId);
            if (userInfo != null && userInfo.length == 3) {
                if(userInfo[0].equals(username) && userInfo[1].equals(email) && userInfo[2].equals(password)){
                    return 0;
                }else{
                    return 1;
                }
            }
            return 2;
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
    static class ProductHandler implements HttpHandler {

        // Constructor that takes a string input during initialization
        @Override
        public void handle(HttpExchange exchange) {
            String failedJSON = "{}";
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
                                System.out.println(badCreateRequest + responseData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleCreateProduct(requestData);
                            if (responseCode == 409) {
                                System.out.println("Duplicate product already exist. "+ requestData.toString());
                                sendResponse(exchange, failedJSON , responseCode);
                                exchange.close();
                                break;
                            } else if (responseCode == 400) {
                                System.out.println("Bad Request: Exception appear. " + requestData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            } else {
                                Map<String, String> fullProductInfo = handleGetProduct(requestData.get("id").toString());
                                sendResponse(exchange, fullProductInfo.get("data"), Integer.parseInt(fullProductInfo.get("code")));
                                exchange.close();
                                break;
                            }
                        case "update":
                            if (requestData.get("id") == null) {
                                responseCode = 400;
                                String badUpdateRequest = "Bad request: Missing product id. ";
                                System.out.println(badUpdateRequest + responseData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleUpdateProduct(requestData);
                            if (responseCode == 400) {
                                System.out.println("Bad Request: Exception appear. " + responseData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            } else if (responseCode == 404) {
                                System.out.println("Product not Found. " + responseData.toString());
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            } else {
                                Map<String, String> fullProductInfo = handleGetProduct(requestData.get("id").toString());
                                sendResponse(exchange, fullProductInfo.get("data"), Integer.parseInt(fullProductInfo.get("code")));
                                exchange.close();
                                break;
                            }

                        case "delete":
                            for (String keyName : keyNames) {
                                if (requestData.get(keyName) == null) {
                                    responseCode = 400;
                                }
                            }
                            if (responseCode == 400) {
                                sendResponse(exchange, failedJSON, responseCode);
                                exchange.close();
                                break;
                            }
                            responseCode = handleDeleteProduct(requestData);
                            sendResponse(exchange, failedJSON, responseCode);
                            exchange.close();
                            break;

                        default:
                            // Handle unknown operation
                            System.out.println("Unknown operation: " + requestData.toString());
                            sendResponse(exchange, failedJSON, 400);
                            exchange.close();
                            break;
                    }
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String[] pathSegments = path.split("/");
                    if (pathSegments.length != 3) {
                        System.out.println("Incorrect Get request");
                        sendResponse(exchange, failedJSON, 400);
                        exchange.close();
                    } else {
                        String productId = pathSegments[2];
                        Map<String, String> response = handleGetProduct(productId);
                        sendResponse(exchange, response.get("data"), Integer.parseInt(response.get("code")));
                        exchange.close();
                    }
                } else {
                    System.out.println("Only accept POST or GET request.");
                    sendResponse(exchange, failedJSON, 405);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, failedJSON, 400);
                exchange.close();
            }
        }



    }
        private static int handleCreateProduct(JSONObject requestData){
            int responseCode = 200;
            try {
                String productId = requestData.get("id").toString();
                String name = requestData.get("name").toString();
                String description = requestData.get("description").toString();
                float price = Float.parseFloat(requestData.get("price").toString());
                int quantity = Integer.parseInt(requestData.get("quantity").toString());

                // Validate the product data
                if (name.isEmpty() || description.isEmpty() || price < 0 || quantity < 0 || Integer.parseInt(productId) < 0) {
                    return 400;
                }

                if (productServiceCache.containsKey(productId)) {
                    System.out.println("Duplicate product already exist. " + requestData.toString());
                    responseCode = 409;
                } else {
                    String[] newProduct = {name, description, Float.toString(price), Integer.toString(quantity)};
                    productServiceCache.put(productId, newProduct);
                }
            }catch (Exception e){
                responseCode = 400;
                e.printStackTrace();
                System.out.println("Failed to create product: " + requestData.toString());
            }
            return responseCode;
        }

        private static int handleUpdateProduct(JSONObject requestData){
            int responseCode = 200;
            String productId = requestData.get("id").toString();
            if (!productServiceCache.containsKey(productId)){
                System.out.println("Product doesn't exist.");
                responseCode = 404;
            } else {
                String[] newProduct = productServiceCache.get(productId);
                if (requestData.get("name") != null) {
                    String name = requestData.get("name").toString();
                    if (name.isEmpty()) {
                        return 400;
                    }
                    newProduct[0] = name;
                }
                if (requestData.get("description") != null) {
                    String description = requestData.get("description").toString();
                    if (description.isEmpty()) {
                        return 400;
                    }
                    newProduct[1] = description;
                }
                if (requestData.get("price") != null) {
                    if (!(requestData.get("price") instanceof Float)) {
                        return 400;
                    }
                    String price = requestData.get("price").toString();
                    if (price.isEmpty()) {
                        return 400;
                    }
                    newProduct[2] = price;
                }
                if (requestData.get("quantity") != null) {
                    if (!(requestData.get("quantity") instanceof Integer)) {
                        return 400;
                    }
                    String quantity = requestData.get("quantity").toString();
                    if (quantity.isEmpty()) {
                        return 400;
                    }
                    newProduct[3] = quantity;
                }
            }
            return responseCode;
        }

        private static int handleDeleteProduct(JSONObject requestData) throws IOException, SQLException {
            String productId = requestData.get("id").toString();
            String productName = requestData.get("name").toString();
            String price = requestData.get("price").toString();
            String quantity = requestData.get("quantity").toString();
            int status = productCheck(productId, productName, price, quantity);
            if (status == 2){
                System.out.println("product not found: " + requestData.toString());
                return 404;
            }
            if (status == 1){
                System.out.println("product details do not match: " + requestData.toString());
                return 404;
            }
            System.out.println("Successfully delete product: " + requestData.toString());
            productServiceCache.remove(productId);
            return 200;
        }

        private static Map<String, String> handleGetProduct(String productId) {
            Map<String, String> response = new HashMap<>();
            JSONObject responseData = new JSONObject();
            String responseCode = "";
            if (!productServiceCache. containsKey(productId)){
                System.out.println("Product doesn't exist.");
                responseCode = "404";
            } else {
                String[] productInfo = productServiceCache.get(productId);
                if (productInfo.length != 4){
                    System.out.println("Wrong format of product information.");
                    responseCode = "400";
                } else {
                    responseData.put("name", productInfo[0]);
                    responseData.put("description", productInfo[1]);
                    responseData.put("price", Float.parseFloat(productInfo[2]));
                    responseData.put("quantity", Integer.parseInt(productInfo[3]));
                    responseCode = "200";
                }
            }
            response.put("data", responseData.toString());
            response.put("code", responseCode);
            return response;
        }

        public static Integer productCheck(String productId, String productName, String price, String quantity){
            String[] productInfo = productServiceCache.get(productId);
            if (productInfo != null && productInfo.length == 3) {
                if(productInfo[0].equals(productName) && productInfo[1].equals(price) && productInfo[2].equals(quantity)){
                    return 0;
                }else{
                    return 1;
                }
            }
            return 2;
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
}

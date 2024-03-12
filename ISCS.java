import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

// Represents a service instance with IP and port
class ServiceInstance {
    private String ip;
    private int port;

    public ServiceInstance(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}

// Simple Round-Robin Load Balancer
class LoadBalancer {
    private Map<String, ServiceInstance> services;
    private int currentInstanceIndex;

    public LoadBalancer() {
        services = new HashMap<>();
        currentInstanceIndex = 0;
    }

    // Add a new service instance to the load balancer
    public void addService(String path, ServiceInstance instance) {
        services.put(path, instance);
    }

    // Get the service instance for a given path
    public ServiceInstance getInstance(String path) {
        return services.get(path);
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        // Create a new load balancer
        LoadBalancer loadBalancer = new LoadBalancer();

        // Adding instances of your services
        loadBalancer.addService("/user", new ServiceInstance("127.0.0.1", 14001)); // UserService
        loadBalancer.addService("/order", new ServiceInstance("127.0.0.1", 14000)); // OrderService
        loadBalancer.addService("/product", new ServiceInstance("127.0.0.1", 15000)); // ProductService

        // Create HTTP server listening on port 14002
        HttpServer server = HttpServer.create(new InetSocketAddress(14002), 0);
        server.createContext("/", new RequestHandler(loadBalancer));
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Load Balancer Server started on port 14002");
    }

    static class RequestHandler implements HttpHandler {
        private final LoadBalancer loadBalancer;

        public RequestHandler(LoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            ServiceInstance instance = loadBalancer.getInstance(path);

            if (instance == null) {
                String response = "No service found for path: " + path;
                exchange.sendResponseHeaders(404, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            String url = "http://" + instance.getIp() + ":" + instance.getPort() + path;

            try {
                // Send HTTP request to the selected service
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setRequestMethod(exchange.getRequestMethod());

                // Forward headers
                for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
                    for (String value : header.getValue()) {
                        con.addRequestProperty(header.getKey(), value);
                    }
                }

                // Read response from the service
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Send the received response back to the client
                exchange.sendResponseHeaders(con.getResponseCode(), response.toString().getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.toString().getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
                String errorResponse = "Error processing request: " + e.getMessage();
                exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(errorResponse.getBytes());
                os.close();
            }
        }
    }
}

//docker run --name my-redis -p 6379:6379 -d redis  (use this to setup redis container)

//(Another version)
// import com.sun.net.httpserver.HttpExchange;
// import com.sun.net.httpserver.HttpHandler;
// import com.sun.net.httpserver.HttpServer;
// import redis.clients.jedis.Jedis;

// import java.io.IOException;
// import java.io.OutputStream;
// import java.net.InetSocketAddress;
// import java.util.HashMap;
// import java.util.Map;

// // Represents a service instance with IP and port
// class ServiceInstance {
//     private String ip;
//     private int port;

//     public ServiceInstance(String ip, int port) {
//         this.ip = ip;
//         this.port = port;
//     }

//     public String getIp() {
//         return ip;
//     }

//     public int getPort() {
//         return port;
//     }
// }

// // Simple Round-Robin Load Balancer
// class LoadBalancer {
//     private Map<String, ServiceInstance[]> services;
//     private Map<String, Integer> serviceIndex;

//     public LoadBalancer() {
//         services = new HashMap<>();
//         serviceIndex = new HashMap<>();
//     }

//     // Add instances of a service to the load balancer
//     public void addServiceInstances(String path, ServiceInstance[] instances) {
//         services.put(path, instances);
//         serviceIndex.put(path, 0);
//     }

//     // Get the next service instance for a given path
//     public synchronized ServiceInstance getNextInstance(String path) {
//         if (!services.containsKey(path)) {
//             throw new IllegalArgumentException("Service path not found: " + path);
//         }

//         ServiceInstance[] instances = services.get(path);
//         int currentIndex = serviceIndex.get(path);
//         serviceIndex.put(path, (currentIndex + 1) % instances.length);
//         return instances[currentIndex];
//     }
// }

// public class Main {
//     public static void main(String[] args) throws IOException {
//         // Create a new load balancer
//         LoadBalancer loadBalancer = new LoadBalancer();

//         // Adding instances of your services
//         loadBalancer.addServiceInstances("/user", new ServiceInstance[]{
//                 new ServiceInstance("127.0.0.1", 14001),
//                 new ServiceInstance("127.0.0.1", 14002),
//                 new ServiceInstance("127.0.0.1", 14003)
//         });

//         loadBalancer.addServiceInstances("/order", new ServiceInstance[]{
//                 new ServiceInstance("127.0.0.1", 14000),
//                 new ServiceInstance("127.0.0.1", 14001)
//         });

//         loadBalancer.addServiceInstances("/product", new ServiceInstance[]{
//                 new ServiceInstance("127.0.0.1", 15000)
//         });

//         // Create HTTP server listening on port 14002
//         HttpServer server = HttpServer.create(new InetSocketAddress(14002), 0);
//         server.createContext("/", new RequestHandler(loadBalancer));
//         server.setExecutor(null); // creates a default executor
//         server.start();
//         System.out.println("Load Balancer Server started on port 14002");
//     }

//     static class RequestHandler implements HttpHandler {
//         private final LoadBalancer loadBalancer;
//         private final Jedis jedis;

//         public RequestHandler(LoadBalancer loadBalancer) {
//             this.loadBalancer = loadBalancer;
//             this.jedis = new Jedis("localhost", 6379);
//         }

//         @Override
//         public void handle(HttpExchange exchange) throws IOException {
//             String path = exchange.getRequestURI().getPath();

//             // Get service instance from load balancer
//             ServiceInstance instance = loadBalancer.getNextInstance(path);

//             // Construct the URL for the selected service instance
//             String url = "http://" + instance.getIp() + ":" + instance.getPort() + path;

//             try {
//                 // Check if the response is cached
//                 String cachedResponse = jedis.get(path);
//                 if (cachedResponse != null) {
//                     sendResponse(exchange, cachedResponse, 200);
//                     return;
//                 }

//                 // Send HTTP request to the selected service
//                 URL obj = new URL(url);
//                 HttpURLConnection con = (HttpURLConnection) obj.openConnection();
//                 con.setRequestMethod(exchange.getRequestMethod());

//                 // Forward headers
//                 for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
//                     for (String value : header.getValue()) {
//                         con.addRequestProperty(header.getKey(), value);
//                     }
//                 }

//                 // Read response from the service
//                 BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
//                 StringBuilder response = new StringBuilder();
//                 String inputLine;
//                 while ((inputLine = in.readLine()) != null) {
//                     response.append(inputLine);
//                 }
//                 in.close();

//                 // Cache the response for future requests
//                 jedis.set(path, response.toString());

//                 // Send the received response back to the client
//                 sendResponse(exchange, response.toString(), con.getResponseCode());
//             } catch (IOException e) {
//                 e.printStackTrace();
//                 String errorResponse = "Error processing request: " + e.getMessage();
//                 sendResponse(exchange, errorResponse, 500);
//             }
//         }

//         private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
//             exchange.sendResponseHeaders(statusCode, response.getBytes().length);
//             OutputStream os = exchange.getResponseBody();
//             os.write(response.getBytes());
//             os.close();
//         }
//     }
// }


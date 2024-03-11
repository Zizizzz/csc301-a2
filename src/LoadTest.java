import java.net.http.*;
import java.net.*;

import java.util.Map;
import java.util.Random;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.util.*;

// https://openjdk.org/groups/net/httpclient/intro.html
// https://openjdk.org/groups/net/httpclient/recipes.html
// https://www.appsdeveloperblog.com/execute-an-http-put-request-in-java/







public class LoadTest {
	static char [] ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
	static Random rand;
  static int maxRequestsPerSecond = 100;

	private static JSONObject readUserJSON(String[] dataElements){
		String[] keyName = {"command", "id", "username", "email", "password"};
		Map<String, String> dataMap = new HashMap<>();
		for (int i = 0; i < dataElements.length - 1; i++){
			if (i >= keyName.length){
				break;
			}
				dataMap.put(keyName[i], dataElements[i]);
		}
		return new JSONObject(dataMap);
	}

	private static JSONObject readProductJSON(String[] dataElements){
		String[] keyName = {"command", "id", "name", "description", "price", "quantity"};
		String[] deleteName = {"command", "id", "name", "price", "quantity"};
		Map<String, String> dataMap = new HashMap<>();

		for (int i = 0; i < dataElements.length; i++){
			if (i >= keyName.length){
				break;
			}
			if(i == 3 && dataElements[0].equals("delete")){
				continue;
			}

				dataMap.put(keyName[i], dataElements[i]);
		}
		return new JSONObject(dataMap);
	}
	private static JSONObject readOrderJSON(String[] dataElements){
		String[] keyName = {"command", "product_id", "user_id", "quantity"};
		Map<String, String> dataMap = new HashMap<>();
		for (String key: keyName){
			dataMap.put(key, null);
		}
		for (int i = 0; i < dataElements.length - 2; i++){
			if (i >= keyName.length){
				break;
			}
			dataMap.put(keyName[i], dataElements[i]);
		}
		return new JSONObject(dataMap);
	}
  // Change this to create random user/product/etc ids and then build up the JSON object.
	public static String randomString(int length){
		StringBuilder r = new StringBuilder();
		for(int i=0;i<length;i++){
			int index = rand.nextInt(ALPHANUMERIC.length);
			r.append(ALPHANUMERIC[index]);
		}
		return r.toString();
	}
	public static void main(String [] args){
//		if(args.length!=5){
//			System.out.println("usage: java LoadTest HOST PORT SEED [PUT|GET] NUM_REQUESTS");
//			System.exit(1);
//		}
		String host = "127.0.0.1";
		int port;
		//int numRequests = Integer.parseInt(args[2]);
		int numRequests = 1000;
		String[] body;
		String command;
		JSONObject response;
		String servers;

		rand = new Random();
		long startTime = System.currentTimeMillis(); // Start timer
		try {


			for(int i=0;i<numRequests;i++){
        // Modify to print helpful information if you'd like
        if (i % 100 == 0) {
          System.out.println(i);
        }
		//不知道有啥用，先comment了
        // You probably want to add timers to take into account the time that has passed already.
        //Thread.sleep(1000/maxRequestsPerSecond);
				//只会生成0-9的id
				int randomDigit = rand.nextInt(10);
				String id = Integer.toString(randomDigit);
				String name = randomString(10);
				int randomcommand = rand.nextInt(3);
				command = switch (randomcommand) {
					case 0 -> "create";
					case 1 -> "update";
					default -> "delete";
				};
				body = new String[6];
				body[0] = command;
				body[1] = id;
				body[2] = name;
				body[3] = randomString(10);

				int server = rand.nextInt(3);
				port = 14000;
				switch (server) {
					case 0 -> {
						//user: 14001
						servers = "/user";
						body[4] = randomString(10);
						//System.out.println(Arrays.toString(body));
						response = readUserJSON(body);
						//System.out.println(response.toString());
					}
					case 1 -> {
						// product: 15000
						servers = "/product";
						body[4] = Integer.toString(rand.nextInt(100));
						body[5] = Integer.toString(rand.nextInt(100));
						//System.out.println(Arrays.toString(body));

						response = readProductJSON(body);
						//System.out.println(response.toString());
					}
					default -> {
						//OrderService 14000
						servers = "/order";
						body[0] = "place order";
						body[1] = id;
						body[2] = Integer.toString(rand.nextInt(10));
						body[3] = Integer.toString(rand.nextInt(100));
						//System.out.println(Arrays.toString(body));

						response = readOrderJSON(body);
						//System.out.println(response.toString());
					}
				}
				int randomInt = rand.nextInt(2); // Generates a random integer from 0 (inclusive) to 100 (exclusive)
				//put
				//System.out.println("get or post: " + randomInt);
				if(randomInt == 0){
					post("http://"+host+":"+port+servers, response.toString());
				}else{
					get("http://"+host+":"+port+servers+"/"+id);
					//System.out.println("http://"+host+":"+port+servers+"/"+id);
				}
				// get("http://mcs.utm.utoronto.ca");
				// get("http://localhost:8080/89M6VVVP7369R1VEPSP0");
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		long endTime = System.currentTimeMillis(); // End timer
		long duration = endTime - startTime;
		System.out.println("Request Duration: " + duration + " ms");
	}

	public static void post(String uri, String requestBody) throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(uri))
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
	
		HttpResponse<String> response =
				client.send(request, HttpResponse.BodyHandlers.ofString());
	
		System.out.println("HTTP Status Code: " + response.statusCode());
		System.out.println("Response Body: " + response.body());
	}
	public static void get(String uri) throws Exception {
	    HttpClient client = HttpClient.newHttpClient();
	    HttpRequest request = HttpRequest.newBuilder()
	          .uri(URI.create(uri))
		  .GET()
	          .build();
	
	    HttpResponse<String> response =
	          client.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("HTTP Status Code: " + response.statusCode());
		System.out.println("Response Body: " + response.body());


		// System.out.println(response.body());
	}
}

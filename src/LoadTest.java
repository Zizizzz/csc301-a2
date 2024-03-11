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
		List<String> updateKey = Arrays.asList("username", "email", "password");
		Map<String, String> dataMap = new HashMap<>();
		int flag = 0;
		for (String key: keyName){
			dataMap.put(key, null);
		}
		for (int i = 0; i < dataElements.length - 2; i++){
			if (i >= keyName.length){
				break;
			}
			if (i==0 && dataElements[i + 1].equals("update")){
				flag = 1;
			}
			if (i != 0 && i != 1 && flag == 1){
				String[] currData = dataElements[i + 1].split(":");
				if (updateKey.contains(currData[0])){
					dataMap.put(currData[0], currData[1]);
				}
			} else {
				dataMap.put(keyName[i], dataElements[i + 1]);
			}
		}
		return new JSONObject(dataMap);
	}

	private static JSONObject readProductJSON(String[] dataElements){
		String[] keyName = {"command", "id", "name", "description", "price", "quantity"};
		List<String> updateKey = Arrays.asList("name", "description", "price", "quantity");
		String[] deleteName = {"command", "id", "name", "price", "quantity"};
		Map<String, String> dataMap = new HashMap<>();
		int updateFlag = 0;
		int deleteFlag = 0;
		for (String key: keyName){
			dataMap.put(key, null);
		}
		for (int i = 0; i < dataElements.length - 1; i++){
			if (i >= keyName.length){
				break;
			}
			if (i==0 && dataElements[i + 1].equals("update")){
				updateFlag = 1;
				dataMap.put("command", "update");
			} else if (i==0 && dataElements[i + 1].equals("delete")){
				deleteFlag = 1;
				dataMap.put("command", "delete");
			} else if (i != 0 && i != 1 && updateFlag == 1){
				String[] currData = dataElements[i + 1].split(":");
				if (updateKey.contains(currData[0])){
					dataMap.put(currData[0], currData[1]);
				}
			} else if (i != 0 && deleteFlag == 1){
				dataMap.put(deleteName[i], dataElements[i + 1]);
			} else {
				dataMap.put(keyName[i], dataElements[i + 1]);
			}
		}
		return new JSONObject(dataMap);
	}
	private static JSONObject readOrderJSON(String[] dataElements){
		String[] keyName = {"command", "product_id", "user_id", "quantity"};
		Map<String, String> dataMap = new HashMap<>();
		for (String key: keyName){
			dataMap.put(key, null);
		}
		for (int i = 0; i < dataElements.length - 3; i++){
			if (i >= keyName.length){
				break;
			}
			dataMap.put(keyName[i], dataElements[i + 1]);
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

		rand = new Random();

		try {
      
     
			for(int i=0;i<numRequests;i++){
        // Modify to print helpful information if you'd like
        if (i % 100 == 0) {
          System.out.println(i);
        } 
        // You probably want to add timers to take into account the time that has passed already.
        Thread.sleep(1000/maxRequestsPerSecond);
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
				switch (server) {
					case 0:
						//user: 14001
						port =14001;
						body[4] = randomString(10);
						response = readUserJSON(body);
						break;
					case 1:
						// product: 15000
						port =15000;
						body[4] = Integer.toString(rand.nextInt(100));
						body[5] = Integer.toString(rand.nextInt(100));
						response = readProductJSON(body);
						break;
					default:
						//OrderService 14000
						port =14000;
						body[0] = "place order";
						body[1] = id;
						body[2] =Integer.toString(rand.nextInt(10));
						body[3] = Integer.toString(rand.nextInt(100));
						response = readOrderJSON(body);
						break;
				}
				int randomInt = rand.nextInt(2); // Generates a random integer from 0 (inclusive) to 100 (exclusive)
				//put
				if(randomInt == 0){
					put("http://"+host+":"+port, response.toString());
				}else{
					get("http://"+host+":"+port+"/"+id);
				}
				// get("http://mcs.utm.utoronto.ca");
				// get("http://localhost:8080/89M6VVVP7369R1VEPSP0");
			}
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public static void put(String uri, String requestBody) throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(uri))
				.PUT(HttpRequest.BodyPublishers.ofString(requestBody))
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
	
	    // System.out.println(response.body());
	}
}
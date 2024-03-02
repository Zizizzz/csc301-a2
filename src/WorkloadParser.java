import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class WorkloadParser {
    public static void main(String[] args) throws Exception {
        String url = readConfig(args[0]);
        readWorkload(args[1], url);
    }
    public static String readConfig(String configFile) throws Exception{
        Object ob = new JSONParser().parse(new FileReader(configFile));

        JSONObject js = (JSONObject) ob;
        JSONObject os = (JSONObject)js.get("OrderService");
        return "http://" + os.get("ip").toString() + ":" + os.get("port").toString();
    }

    private static void readWorkload(String workloadFile, String url) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(workloadFile))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                String[] dataElements = line.split("\\s+");
                if (firstLine){
                    firstLine = false;
                    String[] serverNames = {"USER", "PRODUCT", "ORDER"};
                    if (dataElements[0].equals("shutdown")){
                        for (String serverName: serverNames){
                            ArrayList<String> listData = new ArrayList<>();
                            listData.add(serverName);
                            listData.add("shutdown");
                            listData.add("0");

                            handlePOST(listData.toArray(new String[0]), url);
                        }
                    }else if (!dataElements[0].equals("restart") && !dataElements[0].isEmpty()) {
                        // Sent restart POST request
                        for (String serverName : serverNames) {
                            ArrayList<String> listData = new ArrayList<>();
                            listData.add(serverName);
                            listData.add("start");
                            listData.add("0");

                            handlePOST(listData.toArray(new String[0]), url);
                        }
                    }
                }if (dataElements.length > 1){
                    if ((dataElements[0].equals("USER") && !dataElements[1].equals("get")) ||
                            (dataElements[0].equals("PRODUCT") && !dataElements[1].equals("info")) ||
                            (dataElements[0].equals("ORDER") && dataElements[1].equals("place"))){
                        handlePOST(dataElements, url);
                    } else if (dataElements[0].equals("USER") || dataElements[0].equals("PRODUCT")) {
                        handleGET(dataElements, url);
                    }else{
                        System.out.println("Wrong Command");
                    }
                }else if (dataElements.length == 1){
                    String[] serverNames = {"USER", "PRODUCT", "ORDER"};
                    if (dataElements[0].equals("shutdown")){
                        for (String serverName: serverNames){
                            ArrayList<String> listData = new ArrayList<>();
                            listData.add(serverName);
                            listData.add("shutdown");
                            listData.add("0");

                            handlePOST(listData.toArray(new String[0]), url);
                        }
                    }else if (!dataElements[0].isEmpty()){
                        System.out.println("Wrong command: too short.");
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handlePOST(String[] dataElements, String urlString){
        try {
            URL url = null;
            JSONObject requestData = null;
            switch (dataElements[0]) {
                case "USER":
                    url = new URL(urlString + "/user");
                    requestData = readUserJSON(dataElements);
                    break;
                case "PRODUCT":
                    url = new URL(urlString + "/product");
                    requestData = readProductJSON(dataElements);
                    break;
                case "ORDER":
                    url = new URL(urlString + "/order");
                    requestData = readOrderJSON(dataElements);
                    break;
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            connection.setDoOutput(true);


            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = requestData.toString().getBytes("utf-8");
                outputStream.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder responseText = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseText.append(line);
                }
                System.out.println("Response Text: " + responseText.toString());
                connection.disconnect();
            } catch(IOException e){
                connection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static JSONObject readUserJSON(String[] dataElements){
        String[] keyName = {"command", "id", "username", "email", "password"};
        List<String> updateKey = Arrays.asList("username", "email", "password");
        Map<String, String> dataMap = new HashMap<>();
        int flag = 0;
        for (String key: keyName){
            dataMap.put(key, null);
        }
        for (int i = 0; i < dataElements.length - 1; i++){
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
        for (int i = 0; i < dataElements.length - 1; i++){
            if (i >= keyName.length){
                break;
            }
            dataMap.put(keyName[i], dataElements[i + 1]);
        }
        return new JSONObject(dataMap);
    }

    private static void handleGET(String[] dataElements, String urlString){
        try {
            URL url = null;
            JSONObject requestData = null;
            switch (dataElements[0]) {
                case "USER":
                    url = new URL(urlString + "/user/" + dataElements[2]);
                    break;
                case "PRODUCT":
                    url = new URL(urlString + "/product/" + dataElements[2]);
                    break;
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                System.out.println("Response: " + response.toString());
            } else {
                System.out.println("GET request failed. Response Code: " + responseCode);
            }
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

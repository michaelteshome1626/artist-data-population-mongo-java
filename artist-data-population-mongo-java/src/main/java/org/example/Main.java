package org.example;

import static com.mongodb.client.model.Filters.eq;
import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.json.JsonObject;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;

public class Main {

    private static Properties properties;
    public static void main(String[] args) {
        System.out.println("Hello world!");
        String spAuthUri = "https://accounts.spotify.com/api/token";
        String client_id, client_secret;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request;
        HttpResponse<String> response = null;

        try{
            String  propFilePath = "src/main/resources/prop_dev.properties";
            File propFile = new File(propFilePath);
            FileInputStream propFileReader = new FileInputStream(propFile);
            properties = new Properties();
            properties.load(propFileReader);
            propFileReader.close();

        } catch (Exception e){
            System.out.println(e.getMessage());
        }

        client_id = properties.getProperty("client_id");
        client_secret = properties.getProperty("client_secret");

        Map<String, String> formData = new HashMap<>();

        formData.put("client_id", client_id);
        formData.put("client_secret", client_secret);
        formData.put("grant_type", "client_credentials");

        request = HttpRequest.newBuilder().uri(URI.create(spAuthUri))
                .header("Content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                .build();

        try{
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());

        } catch (Exception e){
            System.out.println(e.getMessage());
        }

        if (response == null){
            System.out.println("No response from authentication token request");
            System.exit(0);
        }
        else if (response.statusCode() != 200){
            System.out.println("Problem making authentication request. Status code: " + response.statusCode());
            System.exit(0);
        }

        JSONObject authObj = new JSONObject(response);
        String token = authObj.getString("access_token");
        String tokenType = authObj.getString("token_type");

        



//        String uri = "mongodb://localhost:27017";
//
//        try (MongoClient mongoClient = MongoClients.create(uri)) {
//            MongoDatabase database = mongoClient.getDatabase("bookstore");
//            MongoCollection<Document> collection = database.getCollection("books");
//            Document doc = collection.find(eq("title", "The Way of Kings")).first();
//            if (doc != null) {
//                System.out.println(doc.toJson());
//            } else {
//                System.out.println("No matching documents found.");
//            }
//        }
    }
    private static String getFormDataAsString(Map<String, String> formData) {
        StringBuilder formBodyBuilder = new StringBuilder();
        for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
            if (!formBodyBuilder.isEmpty()) {
                formBodyBuilder.append("&");
            }
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
            formBodyBuilder.append("=");
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
        }
        return formBodyBuilder.toString();
    }

}
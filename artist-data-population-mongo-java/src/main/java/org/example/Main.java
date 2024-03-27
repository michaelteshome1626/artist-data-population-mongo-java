package org.example;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.DocumentToDBRefTransformer;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertManyResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.bson.Document;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;

public class Main {

    private static Properties properties;
    public static void main(String[] args) {
        String client_id, client_secret;
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = null;
        List<Document> vinyls = new ArrayList<Document>();

        getPropertiesFile("src/main/resources/prop_dev.properties");
        client_id = properties.getProperty("client_id");
        client_secret = properties.getProperty("client_secret");
        response = getSpotifyToken(client_id, client_secret);

        if (response == null){
            System.out.println("No response from authentication token request");
            System.exit(0);
        }
        else if (response.statusCode() != 200){
            System.out.println("Problem making authentication request. Status code: " + response.statusCode());
            System.exit(0);
        }

        JSONObject authObj = new JSONObject(response.body());
        String token = authObj.getString("access_token");


        try{
            File ids = new File("src/main/resources/artistIds.txt");
            Scanner scanner = new Scanner(ids);

            try (MongoClient mc = MongoClients.create("mongodb://localhost:27017")){
                MongoDatabase database = mc.getDatabase("vinylstore");
                MongoCollection<Document> collection = database.getCollection("vinyls");
                while (scanner.hasNext()){
                    String id  = scanner.nextLine();
                    response = makeAlbumsRequest(id, token);

                    JSONObject albumsResponse = new JSONObject(response.body());
                    JSONArray albums = new JSONArray(albumsResponse.get("items").toString());
                    for (int i = 0; i < albums.length(); i ++){
                        JSONObject album = albums.getJSONObject(i);
                        List<String> tracks = getTrackList(album.getString("id"), token);

                        JSONArray artistArray = new JSONArray(album.getJSONArray("artists"));
                        List<String> artists = new ArrayList<String>();
                        for (int j = 0; j< artistArray.length(); j ++){
                            artists.add(artistArray.getJSONObject(j).get("name").toString());
                        }

                        Document d = new Document();
                        int stock = (int) Math.floor(Math.random() * (50 - 10 + 1) + 10);
                        d.append("name", album.getString("name"));
                        d.append("artists", artists);
                        d.append("release_date", album.getString("release_date"));
                        d.append("track_list", tracks);
                        d.append("cover_art", album.getJSONArray("images").getJSONObject(0).get("url").toString());
                        d.append("stock", stock);
                        vinyls.add(d);
                    }
                }

                if (vinyls.isEmpty()){
                    System.out.println("Error pulling albums from Spotify");
                }
                else{
                    InsertManyResult manyResult = collection.insertMany(vinyls);
                }
            } catch (MongoException me){
                System.out.println("Mongo connection failed: " + me.getMessage());
            }

        } catch (Exception e){
            System.out.println(e.getMessage());
        }

    }

    private static void getPropertiesFile(String filePath){
        try{
            File propFile = new File(filePath);
            FileInputStream propFileReader = new FileInputStream(propFile);
            properties = new Properties();
            properties.load(propFileReader);
            propFileReader.close();

        } catch (Exception e){
            System.out.println(e.getMessage());
        }
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

    private static HttpResponse<String> getSpotifyToken(String client_id, String client_secret){
        String spAuthUri = "https://accounts.spotify.com/api/token";
        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = null;
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

        } catch (Exception e){
            System.out.println(e.getMessage());
        }
        return response;
    }

    private static HttpResponse<String> makeAlbumsRequest(String id, String token){
        String albumsUri = "https://api.spotify.com/v1/artists";
        String queryParams = "?include_groups=album";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request;
        HttpResponse<String> response = null;


        request = HttpRequest.newBuilder()
                .uri(URI.create(albumsUri + "/" + id + "/albums" + queryParams))
                .GET()
                .header("Authorization", "Bearer " + token)
                .build();

        try{
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e){
            System.out.println(e.getMessage());

        }

        return response;
    }

    private static List<String> getTrackList(String albumId, String token){
        String tracksUri = "https://api.spotify.com/v1/albums";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request;
        HttpResponse<String> response = null;
        List<String> results = new ArrayList<String>();

        request = HttpRequest.newBuilder().uri(URI.create(tracksUri + "/" + albumId + "/tracks?limit=50"))
                .GET()
                .header("Authorization", "Bearer " + token)
                .build();

        try{
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e){
            System.out.println(e.getMessage());
        }

        if (response.statusCode() != 200){
            return null;
        }
        else{
            JSONObject tracksResponse = new JSONObject(response.body());
            JSONArray items = tracksResponse.getJSONArray("items");

            for (int i = 0; i < items.length(); i ++){
                JSONObject track = items.getJSONObject(i);
                results.add(track.getString("name"));
            }

            return results;
        }

    }
    private static boolean AddAlbumToMongo (String name, String [] artists, String releaseDate, String[] trackList ){
        return false;
    }

}
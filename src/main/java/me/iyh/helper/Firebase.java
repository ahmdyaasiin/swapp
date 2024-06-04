package me.iyh.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Firebase {

    public static final String API_URL = "API_URL";
    private static final String DATABASE_URL = "DATABASE_URL";
    private static final String API_KEY = "API_KEY";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Map<String, DataChangeListener> listenersMap = new HashMap<>();
    private static String idToken;
    private static String refreshToken;
    private static final ConcurrentHashMap<String, HttpURLConnection> connectionMap = new ConcurrentHashMap<>();

    public static void clearAllListeners() {
        synchronized (connectionMap) {
            for (Map.Entry<String, HttpURLConnection> entry : connectionMap.entrySet()) {
                entry.getValue().disconnect();
            }
            connectionMap.clear();
            listenersMap.clear();
        }
    }

    public static void signInWithCustomToken(String customToken) throws IOException {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=" + API_KEY;
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("token", customToken);
        jsonBody.addProperty("returnSecureToken", true);

        JsonObject response =  sendPostRequest(url, jsonBody.toString());

        idToken = response.get("idToken").getAsString();
        refreshToken = response.get("refreshToken").getAsString();
    }

    public static void refreshToken() throws IOException {
        String url = "https://securetoken.googleapis.com/v1/token?key=" + API_KEY;
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("grant_type", "refresh_token");
        jsonBody.addProperty("refresh_token", refreshToken);

        JsonObject response =  sendPostRequest(url, jsonBody.toString());

        idToken = response.get("id_token").getAsString();
        refreshToken = response.get("refreshToken").getAsString();
    }

    public static void listenForChanges(String path, DataChangeListener listener) {
        synchronized (listenersMap) {
            listenersMap.put(path, listener);
        }

        executorService.submit(() -> {
            HttpURLConnection con = null;
            try {
                String urlString = DATABASE_URL + "/" + path + ".json?auth=" + idToken;
                URL url = new URL(urlString);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Accept", "text/event-stream");
                con.setReadTimeout(0);

                synchronized (connectionMap) {
                    connectionMap.put(path, con);
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String json = line.substring("data: ".length());
                            if (!json.equals("null")) {
                                String data = getOneTimeData(path);
                                synchronized (listenersMap) {
                                    DataChangeListener pathListener = listenersMap.get(path);
                                    if (pathListener != null) {
                                        new DataUpdateWorker(pathListener, data).execute();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                System.err.println("Socket closed for path: " + path);
                e.printStackTrace(System.err);
            } catch (IOException e) {
                System.err.println("Failed to connect to URL (" + DATABASE_URL + "/" + path + ".json?auth=" + idToken + "): " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                if (con != null) {
                    con.disconnect();
                }
                synchronized (connectionMap) {
                    connectionMap.remove(path);
                }
            }
        });
    }

    private static class DataUpdateWorker extends SwingWorker<Void, Void> {
        private final DataChangeListener listener;
        private final String data;

        public DataUpdateWorker(DataChangeListener listener, String data) {
            this.listener = listener;
            this.data = data;
        }

        @Override
        protected Void doInBackground() {

            return null;
        }

        @Override
        protected void done() {

            try {
                listener.onDataChange(data);
            } catch (IOException e) {
                System.err.println("Failed to update data: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }


    public static String getOneTimeData(String path) throws IOException {
        String urlString = DATABASE_URL + "/" + path + ".json?auth=" + idToken;
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    }

    public interface DataChangeListener {
        void onDataChange(String jsonData) throws IOException;
    }

    private static JsonObject sendPostRequest(String urlString, String jsonInputString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return JsonParser.parseString(response.toString()).getAsJsonObject();
        }
    }

    public static ImageIcon getPhotoProfile(String NIM) {
        String urlPath = String.format("http://file-filkom.ub.ac.id/fileupload/assets/foto/20%s/%s.png", NIM.substring(0, 2), NIM);

        try {
            URL url = new URL(urlPath);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);

            InputStream is = connection.getInputStream();
            BufferedImage bufferedImage = ImageIO.read(is);
            if (bufferedImage != null) {
                return new ImageIcon(bufferedImage);
            }
        } catch (IOException e) {
            System.err.println("Failed to download image from URL (" + urlPath + "): " + e.getMessage());
            e.printStackTrace(System.err);
        }
        return new ImageIcon(Firebase.base64ToImage("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"));
    }

    public static BufferedImage base64ToImage(String imageString) {
        BufferedImage image = null;
        try {
            byte[] imageByte = Base64.getDecoder().decode(imageString);

            ByteArrayInputStream bis = new ByteArrayInputStream(imageByte);

            image = ImageIO.read(bis);

            bis.close();
        } catch (Exception e) {
            System.err.println("Failed to decode base64: " + e.getMessage());
            e.printStackTrace(System.err);
        }
        return image;
    }

    public static void setIdToken(String idToken) {
        Firebase.idToken = idToken;
    }

    public static void setRefreshToken(String refreshToken) {
        Firebase.refreshToken = refreshToken;
    }

    public static String getIdToken() {
        return idToken;
    }

    public static String getRefreshToken() {
        return refreshToken;
    }

}

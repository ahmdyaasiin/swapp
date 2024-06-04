package me.iyh.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.iyh.helper.Conversation;
import me.iyh.helper.Firebase;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Login {

    private final Frame frame;
    private final JTextField nimField;
    private final JPasswordField passwordField;

    public Login() {

        // setup frame
        frame = new Frame("Login", 200, 190);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);

        // setup panel
        JPanel loginPanel = new JPanel();
        loginPanel.setLayout(null);

        // setup label for nim
        JLabel nimLabel = new JLabel();
        nimLabel.setText("NIM");
        nimLabel.setBounds(5, 5, 50, 20);

        // setup input field for nim
        nimField = new JTextField();
        nimField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY),
                new EmptyBorder(0, 3, 0, 0)
        ));
        nimField.setBounds(5, 30, 190, 20);

        // setup label for password
        JLabel passwordLabel = new JLabel();
        passwordLabel.setText("Password");
        passwordLabel.setBounds(5, 60, 70, 20);

        // setup input field for password
        passwordField = new JPasswordField();
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY),
                new EmptyBorder(0, 3, 0, 0)
        ));
        passwordField.setBounds(5, 85, 190, 20);

        // setup button for login
        JButton loginButton = new JButton();
        loginButton.setText("Login");
        loginButton.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        loginButton.setBounds(5, 125, 190, 20);

        // add components to panel
        loginPanel.add(nimLabel);
        loginPanel.add(nimField);
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(loginButton);

        // add panel to frame
        frame.add(loginPanel);

        // ========== add listener start ========== //
        loginButton.addActionListener(e -> {
            try {
                login();
            } catch (IOException | InterruptedException ex) {
                System.err.println("Failed to login: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        });

        KeyAdapter enterKeyListener = new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    try {
                        login();
                    } catch (IOException | InterruptedException ex) {
                        System.err.println("Failed to login: " + ex.getMessage());
                        ex.printStackTrace(System.err);
                    }
                }
            }
        };

        nimField.addKeyListener(enterKeyListener);
        passwordField.addKeyListener(enterKeyListener);
        // ========== add listener end ========== //

        // set frame visibility
        frame.setVisible(true);
    }

    private void login() throws IOException, InterruptedException {
        String nim = nimField.getText();
        String password = new String(passwordField.getPassword());

        // @TODO: do curl here to check the credential
        Map<String, String> res = checkCredential(nim, password);
        if (res.get("message") == null) {
            Firebase.signInWithCustomToken(res.get("token"));

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    Firebase.refreshToken();
                } catch (IOException e) {
                    System.err.println("Failed to refresh token: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }, 50, 50, TimeUnit.MINUTES);

            new Lobby(new Conversation(res.get("nim"), res.get("full_name")), frame);

            frame.setVisible(false);
        } else {
            JOptionPane.showMessageDialog(frame, res.get("message"), "Failed Login", JOptionPane.ERROR_MESSAGE, null);
        }
    }

    private Map<String, String> checkCredential(String username, String password) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Firebase.API_URL + "/_auth"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"username\": \"%s\", \"password\": \"%s\"}", username, password)))
                .build();

        Map<String, String> detailsCredential = new HashMap<>();
        detailsCredential.put("nim", null);
        detailsCredential.put("full_name", null);
        detailsCredential.put("token", null);
        detailsCredential.put("message", null);

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();

            if (jsonObject.has("error") && !jsonObject.get("error").isJsonNull()) {
                JsonObject errorObject = jsonObject.getAsJsonObject("error");
                detailsCredential.put("message", errorObject.get("message").getAsString());

                return detailsCredential;
            }

            JsonObject details = jsonObject.getAsJsonObject("details");
            detailsCredential.put("nim", details.get("nim").getAsString());
            detailsCredential.put("full_name", details.get("full_name").getAsString());
            detailsCredential.put("token", details.get("token").getAsString());

        } catch (Exception e) {
            System.err.println("Failed to send HTTP request or process JSON response: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        return detailsCredential;
    }

}

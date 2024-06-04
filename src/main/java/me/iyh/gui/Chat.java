package me.iyh.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.iyh.helper.Conversation;
import me.iyh.helper.Firebase;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Chat {

    private final Frame frame;
    private final Conversation conversationGlobal;
    private final JTextPane contentPane;
    private final StyledDocument styleDocument;
    private static JTextField inputMessage;
    private int first = 0;
    private boolean isSetup = false;

    public Chat(Conversation conversation, int origin, Frame previousFrame) throws InterruptedException {
        // passing the value so it can be accessed globally
        conversationGlobal = conversation;

        // setup frame
        frame = new Frame("Chat App", 500, 500);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(previousFrame);

        // clear all listeners
        Firebase.clearAllListeners();

        // ========== setup topPanel start ========== //
        JPanel topPanel = new JPanel();
        topPanel.setLayout(null);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        topPanel.setPreferredSize(new Dimension(500, 40));

        JButton buttonBack = new JButton();
        buttonBack.setText("Back");
        buttonBack.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        buttonBack.setBounds(5, 5, 80, 30);

        JLabel recipientName = new JLabel();
        recipientName.setText(conversationGlobal.getRecentRecipientName());
        recipientName.setHorizontalAlignment(SwingConstants.CENTER);
        recipientName.setBounds(100, 8, 300, 24);
        recipientName.setHorizontalTextPosition(SwingConstants.CENTER);

        topPanel.add(buttonBack);
        topPanel.add(recipientName);
        // ========== setup topPanel end ========== //

        // ========== setup botPanel start ========== //
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.BLACK));
        bottomPanel.setPreferredSize(new Dimension(500, 40));

        inputMessage = new JTextField();
        inputMessage.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY),
                new EmptyBorder(0, 3, 0, 0)
        ));
        inputMessage.setHorizontalAlignment(JTextField.LEFT);
        inputMessage.setPreferredSize(new Dimension(400, 24));

        JButton sendMessage = new JButton();
        sendMessage.setText("Send");
        sendMessage.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        sendMessage.setPreferredSize(new Dimension(50, 24));
        sendMessage.requestFocus();

        bottomPanel.add(inputMessage);
        bottomPanel.add(sendMessage);
        // =========== setup botPanel end =========== //

        // ========== setup chatPanel start ========== //
        JPanel chatPanel = new JPanel();
        chatPanel.setLayout(new BorderLayout(5, 5));
        chatPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        contentPane = new JTextPane();
        contentPane.setEditable(false);
        styleDocument = contentPane.getStyledDocument();
        JScrollPane scrollPane = new JScrollPane(contentPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(bottomPanel, BorderLayout.SOUTH);
        // =========== setup chatPanel end =========== //

        // add panel to frame
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(chatPanel);

        // @TODO: add listeners
        buttonBack.addActionListener(e -> {
            try {
                goBack(origin);
            } catch (IOException | InterruptedException ex) {
                System.err.println("Failed to go back: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        });

        sendMessage.addActionListener(e -> {
            try {
                sendMessage(conversationGlobal, inputMessage.getText());
            } catch (InterruptedException | IOException ex) {
                System.err.println("Failed to send message: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        });

        inputMessage.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    try {
                        sendMessage(conversationGlobal, inputMessage.getText());
                    } catch (InterruptedException | IOException ex) {
                        System.err.println("Failed to send message: " + ex.getMessage());
                        ex.printStackTrace(System.err);
                    }
                }
            }
        });

        // set frame visibility
        frame.setVisible(true);

        // setup history and setup vertical scrollbar to bottom
        setupHistory();
        scrollToBottom();
    }

    private void goBack(int origin) throws IOException, InterruptedException {
        if (origin == 0) {
            new Lobby(conversationGlobal, frame);
        } else if (origin == 1) {
            new NewMessage(conversationGlobal, frame);
        }
        frame.setVisible(false);
    }

    private void setupHistory() {
        if (conversationGlobal.getRecentRoomId() == null) {
            conversationGlobal.setRecentRoomId(UUID.randomUUID().toString());
            return;
        }

        isSetup = true;
        Firebase.listenForChanges("conversations/" + conversationGlobal.getRecentRoomId() + "/messages", jsonData -> {
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();

            SwingUtilities.invokeLater(() -> {
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(jsonObject.entrySet());
                entries.sort((entry1, entry2) -> {
                    long createdAt1 = entry1.getValue().getAsJsonObject().get("createdAt").getAsLong();
                    long createdAt2 = entry2.getValue().getAsJsonObject().get("createdAt").getAsLong();
                    return Long.compare(createdAt1, createdAt2);
                });

                if (first == 0) {
                    for (Map.Entry<String, JsonElement> entry : entries) {
                        addMessage(entry.getValue().getAsJsonObject().get("senderId").getAsString(), entry.getValue().getAsJsonObject().get("message").getAsString());
                    }
                    first = 1;
                } else {
                    Map.Entry<String, JsonElement> latestEntry = entries.get(entries.size() - 1);
                    addMessage(latestEntry.getValue().getAsJsonObject().get("senderId").getAsString(), latestEntry.getValue().getAsJsonObject().get("message").getAsString());
                }
            });
        });
    }

    private void addMessage(String NIM, String message) {
        try {
            Style style = contentPane.addStyle("Style" + NIM, null);
            if (NIM.equals(conversationGlobal.getRecentRecipientId())) {
                StyleConstants.setAlignment(style, StyleConstants.ALIGN_LEFT);
                StyleConstants.setBackground(style, Color.LIGHT_GRAY);
            } else {
                StyleConstants.setAlignment(style, StyleConstants.ALIGN_RIGHT);
                StyleConstants.setBackground(style, new Color(144, 238, 144));
            }

            String fullMessage = "\n" + message + "\n";
            styleDocument.insertString(styleDocument.getLength(), fullMessage, style);
            styleDocument.setParagraphAttributes(styleDocument.getLength() - fullMessage.length(), fullMessage.length(), style, false);

            scrollToBottom();
        } catch (BadLocationException ex) {
            System.err.println("Failed to insert text into document: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            contentPane.setCaretPosition(styleDocument.getLength());
        });
    }

    private void sendMessage(Conversation conversationDetails, String message) throws InterruptedException, IOException {
        if (message.isEmpty()) {
            return;
        }

        URL url = new URL(Firebase.API_URL + "/_chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        JsonObject json = new JsonObject();
        json.addProperty("token", Firebase.getIdToken());
        json.addProperty("recipient_id", conversationDetails.getRecentRecipientId());
        json.addProperty("room_id", conversationDetails.getRecentRoomId());
        json.addProperty("message", message);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

        } catch (IOException e) {
            System.err.println("Failed to read response from server: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            conn.disconnect();
        }

        inputMessage.setText("");

        if (!isSetup) {
            Firebase.listenForChanges("conversations/" + conversationGlobal.getRecentRoomId() + "/messages", jsonData -> {
                JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();

                SwingUtilities.invokeLater(() -> {
                    List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(jsonObject.entrySet());
                    entries.sort((entry1, entry2) -> {
                        long createdAt1 = entry1.getValue().getAsJsonObject().get("createdAt").getAsLong();
                        long createdAt2 = entry2.getValue().getAsJsonObject().get("createdAt").getAsLong();
                        return Long.compare(createdAt1, createdAt2);
                    });

                    Map.Entry<String, JsonElement> latestEntry = entries.get(entries.size() - 1);
                    addMessage(latestEntry.getValue().getAsJsonObject().get("senderId").getAsString(), latestEntry.getValue().getAsJsonObject().get("message").getAsString());
                });
            });

            isSetup = true;
        }
    }
}

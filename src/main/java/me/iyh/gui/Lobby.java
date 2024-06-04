package me.iyh.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.iyh.helper.Conversation;
import me.iyh.helper.Firebase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;

public class Lobby {

    private final Frame frame;
    private final Conversation conversationGlobal;
    private final JPanel listConversationsPanel;
    private static final Map<String, JsonObject> conversationList = new LinkedHashMap<>();

    public Lobby(Conversation conversation, Frame previousFrame) {
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
        topPanel.setPreferredSize(new Dimension(500, 40));

        JLabel userName = new JLabel();
        userName.setText("Logged in as");
        userName.setBounds(12, 2, 300, 20);
        userName.setFont(new Font(userName.getFont().getName(), Font.PLAIN, 14));

        JLabel loginAs = new JLabel();
        loginAs.setText(conversationGlobal.getMyName());
        loginAs.setBounds(12, 18, 300, 20);
        loginAs.setFont(new Font(userName.getFont().getName(), Font.BOLD, 14));

        JButton newMessage = new JButton();
        newMessage.setText("New Message");
        newMessage.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        newMessage.setBounds(335, 5, 150, 30);

        topPanel.add(loginAs);
        topPanel.add(userName);
        topPanel.add(newMessage, BorderLayout.CENTER);
        // ========== setup topPanel end ========== //

        // initialize listConversationsPanel and scrollPane
        listConversationsPanel = new JPanel();
        listConversationsPanel.setLayout(null);

        JScrollPane scrollPane = new JScrollPane(listConversationsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // add panel to frame
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane);

        // @TODO: add listener
        newMessage.addActionListener(e -> {
            try {
                newMessage();
            } catch (IOException | InterruptedException ex) {
                System.err.println("Failed to send new message: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        });

        Firebase.listenForChanges("info/" + conversationGlobal.getMyId(), jsonData -> {
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
            JsonObject roomIdObject = jsonObject.getAsJsonObject("roomId");

            SwingUtilities.invokeLater(() -> {
                updateConversationList(roomIdObject);
            });
        });

        // set frame visibility
        frame.setVisible(true);
    }

    private void updateConversationList(JsonObject roomIdObject) {
        conversationList.clear();
        for (Map.Entry<String, JsonElement> entry : roomIdObject.entrySet()) {
            String key = entry.getKey();

            try {
                String jsonDataConversations = Firebase.getOneTimeData("conversations/" + key);
                JsonObject conversationObject = JsonParser.parseString(jsonDataConversations).getAsJsonObject();

                // add conversation to map
                conversationList.put(key, conversationObject);
            } catch (IOException e) {
                System.err.println("Failed to retrieve conversation data for key: " + key + " - " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        // update UI
        listConversationsPanel.removeAll();
        listConversationsPanel.setPreferredSize(new Dimension(500, conversationList.size() * 80));

        java.util.List<Map.Entry<String, JsonObject>> entries = new ArrayList<>(conversationList.entrySet());
        entries.sort((o1, o2) -> Long.compare(o2.getValue().get("lastSent").getAsLong(), o1.getValue().get("lastSent").getAsLong()));

        int i = 0;
        for (Map.Entry<String, JsonObject> entry : entries) {
            JsonObject obj = entry.getValue();
            String anotherUserNIM = null;

            for (Map.Entry<String, JsonElement> subEntry : obj.entrySet()) {
                String key = subEntry.getKey();
                JsonElement value = subEntry.getValue();

                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().getAsBoolean() && !key.equals(conversationGlobal.getMyId())) {
                    anotherUserNIM = key;
                    break;
                }
            }

            String userDetails;
            JsonObject userDetailsObject;

            try {
                userDetails = Firebase.getOneTimeData("users/" + anotherUserNIM);
                userDetailsObject = JsonParser.parseString(userDetails).getAsJsonObject();
            } catch (IOException e) {
                System.err.println("Failed to retrieve user details (" + anotherUserNIM + "): " + e.getMessage());
                e.printStackTrace(System.err);

                continue;
            }

            // setup label for conversation details
            JLabel conversationDetailsLabel = new JLabel();
            conversationDetailsLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
            conversationDetailsLabel.setText(userDetailsObject.get("fullName").getAsString());
            conversationDetailsLabel.setBounds(0, i * 80, 500, 80);
            conversationDetailsLabel.setFont(new Font(conversationDetailsLabel.getFont().getName(), Font.BOLD, 14));
            conversationDetailsLabel.setBorder(BorderFactory.createCompoundBorder(
                    conversationDetailsLabel.getBorder(),
                    BorderFactory.createEmptyBorder(0, 10, 0, 0)));
            conversationDetailsLabel.setIconTextGap(10);
            conversationDetailsLabel.setHorizontalTextPosition(JLabel.RIGHT);
            conversationDetailsLabel.setVerticalTextPosition(JLabel.TOP);

            // setup label for lastMessage
            String contentLastMessage = obj.get("lastMessage").getAsString();
            if (contentLastMessage.length() > 60) {
                contentLastMessage = contentLastMessage.substring(0, 57) + "...";
            }

            JLabel lastMessageLabel = new JLabel();
            lastMessageLabel.setText(contentLastMessage);
            lastMessageLabel.setBounds(70, i * 80, 500, 80);

            // add listener to conversationDetails
            String finalAnotherUserNIM = anotherUserNIM;
            conversationDetailsLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        conversationGlobal.setRecentRecipientId(finalAnotherUserNIM);
                        conversationGlobal.setRecentRecipientName(userDetailsObject.get("fullName").getAsString());
                        conversationGlobal.setRecentRoomId(entry.getKey());

                        new Chat(conversationGlobal, 0, frame);

                        frame.setVisible(false);
                    } catch (InterruptedException ex) {
                        System.err.println("Failed to open new conversation for user (" + finalAnotherUserNIM + "): " + ex.getMessage());
                        ex.printStackTrace(System.err);
                    }
                }
            });

            // trying to get photo profile from file filkom
            ImageIcon originalIcon = Firebase.getPhotoProfile(anotherUserNIM);

            // resize photo profile
            Image scaledImage = originalIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
            ImageIcon resizedIcon = new ImageIcon(scaledImage);

            // set icon for conversationDetailsLabel
            conversationDetailsLabel.setIcon(resizedIcon);

            // add components to panel
            listConversationsPanel.add(lastMessageLabel);
            listConversationsPanel.add(conversationDetailsLabel);

            i++;
        }

        listConversationsPanel.revalidate();
        listConversationsPanel.repaint();
    }

    private void newMessage() throws IOException, InterruptedException {

        // @TODO: setup locationRelativeTo
        new NewMessage(conversationGlobal, frame);
        frame.setVisible(false);
    }

}

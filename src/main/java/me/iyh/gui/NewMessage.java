package me.iyh.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.iyh.helper.Conversation;
import me.iyh.helper.Firebase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;

public class NewMessage {

    private final Frame frame;
    private final Conversation conversationGlobal;
    private final JPanel newConversationsPanel;

    public NewMessage(Conversation conversation, Frame previousFrame) {
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

        JButton buttonBack = new JButton();
        buttonBack.setText("Back");
        buttonBack.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        buttonBack.setBounds(5, 5, 80, 30); // Posisi dan ukuran absolut

        topPanel.add(buttonBack, BorderLayout.CENTER);
        // ========== setup topPanel end ========== //

        // initialize newConversationsPanel and scrollPane
        newConversationsPanel = new JPanel();
        newConversationsPanel.setLayout(null);

        JScrollPane scrollPane = new JScrollPane(newConversationsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // add panel to frame
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane);

        // @TODO: add listener
        buttonBack.addActionListener(e -> {
            try {
                goBack();
            } catch (IOException | InterruptedException ex) {
                System.err.println("Failed to go back: " + ex.getMessage());
                ex.printStackTrace(System.err);
            }
        });

        Firebase.listenForChanges("info/" + conversationGlobal.getMyId(), jsonData -> {
            System.err.println("listen new message");

            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
            JsonObject talkToObject = jsonObject.getAsJsonObject("talkTo");

            String listUser = Firebase.getOneTimeData("users");
            JsonObject UO = JsonParser.parseString(listUser).getAsJsonObject();

            UO.remove(conversationGlobal.getMyId());
            for (Map.Entry<String, JsonElement> entry : talkToObject.entrySet()) {
                UO.remove(entry.getKey());
            }

            SwingUtilities.invokeLater(() -> updateNewMessageList(UO));
        });

        // set frame visibility
        frame.setVisible(true);
    }

    private void updateNewMessageList(JsonObject UO) {
        newConversationsPanel.removeAll();
        newConversationsPanel.setPreferredSize(new Dimension(500, UO.size() * 80));

        int i = 0;
        for (Map.Entry<String, JsonElement> entry : UO.entrySet()) {
            JsonObject userObject = entry.getValue().getAsJsonObject();
            String key = entry.getKey();

            // setup label for new conversations
            JLabel newConversationsLabel = new JLabel();
            newConversationsLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
            newConversationsLabel.setText(userObject.get("fullName").getAsString());
            newConversationsLabel.setBounds(0, i * 80, 500, 80);
            newConversationsLabel.setFont(new Font(newConversationsLabel.getFont().getName(), Font.BOLD, 14));
            newConversationsLabel.setBorder(BorderFactory.createCompoundBorder(
                    newConversationsLabel.getBorder(),
                    BorderFactory.createEmptyBorder(0, 10, 0, 0)));
            newConversationsLabel.setIconTextGap(10);
            newConversationsLabel.setHorizontalTextPosition(JLabel.RIGHT);
            newConversationsLabel.setVerticalTextPosition(JLabel.CENTER);

            // add listener to newConversationsLabel
            newConversationsLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        conversationGlobal.setRecentRecipientId(key);
                        conversationGlobal.setRecentRecipientName(userObject.get("fullName").getAsString());
                        conversationGlobal.setRecentRoomId(null);

                        new Chat(conversationGlobal, 1, frame);

                        frame.setVisible(false);
                    } catch (InterruptedException ex) {
                        System.err.println("Failed to start new chat: " + ex.getMessage());
                        ex.printStackTrace(System.err);
                    }
                }
            });

            // trying to get photo profile from file filkom
            ImageIcon originalIcon = Firebase.getPhotoProfile(key);

            // resize photo profile
            Image scaledImage = originalIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
            ImageIcon resizedIcon = new ImageIcon(scaledImage);

            // set icon for newConversationsLabel
            newConversationsLabel.setIcon(resizedIcon);

            // add components to panel
            newConversationsPanel.add(newConversationsLabel);

            i++;
        }

        newConversationsPanel.revalidate();
        newConversationsPanel.repaint();
    }

    private void goBack() throws IOException, InterruptedException {
        new Lobby(conversationGlobal, frame);
        frame.setVisible(false);
    }
}

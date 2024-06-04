package me.iyh.helper;

public class Conversation {

    private String myId;
    private String myName;
    private String recentRecipientId;
    private String recentRecipientName;
    private String recentRoomId;

    public Conversation() {
        this(null, null);
    }

    public Conversation(String myId, String myName) {
        this.myId = myId;
        this.myName = myName;
    }

    public void setMyId(String myId) {
        this.myId = myId;
    }

    public void setMyName(String myName) {
        this.myName = myName;
    }

    public void setRecentRecipientId(String recentRecipientId) {
        this.recentRecipientId = recentRecipientId;
    }

    public void setRecentRecipientName(String recentRecipientName) {
        this.recentRecipientName = recentRecipientName;
    }

    public void setRecentRoomId(String recentRoomId) {
        this.recentRoomId = recentRoomId;
    }

    public String getMyId() {
        return myId;
    }

    public String getMyName() {
        return myName;
    }

    public String getRecentRecipientId() {
        return recentRecipientId;
    }

    public String getRecentRecipientName() {
        return recentRecipientName;
    }

    public String getRecentRoomId() {
        return recentRoomId;
    }

    @Override
    public String toString() {
        return String.format("myId: %s\nrecentRecipientId: %s\nrecentRecipientName:%s\nrecentRoomId: %s", getMyId(), getRecentRecipientId(), getRecentRecipientName(), getRecentRoomId());
    }
}

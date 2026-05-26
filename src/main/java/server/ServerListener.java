package server;

import java.util.List;

public interface ServerListener {
    void onLog(String text);

    void onUserListChanged(List<String> usernames);
    
    void onConversationsChanged(List<String> conversations);

    void onConversationMessage(String conversation, String text);

    void onServerStarted(int port);

    void onServerStopped();
}

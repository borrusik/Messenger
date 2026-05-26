package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationRegistry {
    private final Set<String> conversations = ConcurrentHashMap.newKeySet();

    public boolean register(String conversation) {
        if (conversation == null || conversation.isBlank()) {
            return false;
        }

        return conversations.add(conversation.trim());
    }

    public boolean register(String firstUsername, String secondUsername) {
        if (firstUsername == null || firstUsername.isBlank()
                || secondUsername == null || secondUsername.isBlank()) {
            return false;
        }

        return conversations.add(createConversationId(firstUsername, secondUsername));
    }

    public String createConversationId(String firstUsername, String secondUsername) {
        String first = firstUsername.trim();
        String second = secondUsername.trim();

        if (first.compareToIgnoreCase(second) <= 0) {
            return first + " <---> " + second;
        }

        return second + " <---> " + first;
    }

    public List<String> list() {
        List<String> result = new ArrayList<>(conversations);
        result.sort(String::compareToIgnoreCase);
        return Collections.unmodifiableList(result);
    }

    public void clear() {
        conversations.clear();
    }

}

package consensus;

import model.Message;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class EquivocationDetector {
    private final Map<String, Message> seen = new ConcurrentHashMap<>();

    public Message check(Message m) {
        String key = m.type + ":" + m.viewNumber + ":" + m.senderPort;
        Message existing = seen.putIfAbsent(key, m);
        if (existing == null) return null;
        if (!existing.blockHash.equals(m.blockHash)) return existing;
        return null;
    }

    public void clear(int maxView) {
        seen.entrySet().removeIf(e -> {
            String[] parts = e.getKey().split(":");
            try {
                return Integer.parseInt(parts[1]) < maxView;
            } catch (NumberFormatException ignored) {
                return false;
            }
        });
    }
}

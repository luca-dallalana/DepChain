package network;
public interface DeliveryListener {
    void onDeliver(int senderPort, String message);
}

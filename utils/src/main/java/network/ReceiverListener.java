package network;
import java.net.DatagramPacket;

public interface ReceiverListener {
    void onReceive(DatagramPacket packet);
}

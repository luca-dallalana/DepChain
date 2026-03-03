import java.net.DatagramSocket;

import config.MemberConfig;
import member.DepChainMember;

public class Main {
    public static void main(String[] args) {
        int N = 4;
        int thisID = 0; 
        MemberConfig config = new MemberConfig(N, thisID, null);

        int port = 3000 + thisID; //FIXME is this good?
        try {
            DatagramSocket socket = new DatagramSocket(port);
            DepChainMember member = new DepChainMember(config, socket); //FIXME port hardcoded for now
            member.start();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
}

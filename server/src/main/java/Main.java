import config.MemberConfig;
import member.DepChainMember;

public class Main {
    public static void main(String[] args) {
        int N = 4;
        int thisID = 0; 
        MemberConfig config = new MemberConfig(N, thisID, null);

        DepChainMember member = new DepChainMember(config, 4000 + thisID); //FIXME port hardcoded for now
        member.start();
    }
}

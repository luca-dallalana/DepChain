package util;
import model.Message;
import model.Node;
import model.QC;

public class DepChainUtil {
    public static Message Msg(String type, Node node, QC qc ,int viewNumber) {
        Message m = new Message();
        m.type = type;
        m.viewNumber = viewNumber;
        m.node = node;
        m.justify = qc;
        return m;
    }

    public static Message voteMsg(String type, Node node, QC qc, int viewNumber) {
        Message m = Msg(type, node, qc, viewNumber);
        //m.partialSig = Crypto.tsign(id, Crypto.hash(type, m.viewNumber, node.hash()));
        return m;
    }

    public boolean matchingMsg(Message m, String type, int viewNumber){
        return m.type.equals(type) && m.viewNumber == viewNumber;
    }

    public boolean matchingQC(QC qc, String type, int viewNumber) {
        return qc.type.equals(type) && qc.viewNumber == viewNumber;
    }

}

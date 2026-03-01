package util;
import model.Message;
import model.Node;
import model.QC;

public class HotStuffUtil {
    /*
    Node createLeaf(Node parent, String cmd) {
        Node b = new Node();
        b.parentHash = hash(parent);
        b.cmd = cmd;
        return b;
    }
    */
    public Message Msg(String type, int viewNumber, Node node, QC justify) {
        Message m = new Message();
        m.type = type;
        m.viewNumber = viewNumber;
        m.node = node;
        m.justify = justify;
        return m;
    }

    public Message voteMsg(String type, int viewNumber, Node node) {
        Message m = Msg(type, viewNumber, node, null);
        //m.partialSig = Crypto.tsign(id, Crypto.hash(type, m.viewNumber, node.hash()));
        return m;
    }

    public boolean matchingMsg(Message m, String type, int viewNumer){
        return m.type.equals(type) && m.viewNumber == viewNumer;
    }

    public boolean matchingQC(QC qc, String type, int viewNumber) {
        return qc.type.equals(type) && qc.viewNumber == viewNumber;
    }

}

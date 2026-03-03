package util;
import consensus.QCManager;
import model.Message;
import model.Node;
import model.QC;

public class DepChainUtil {
    private final QCManager qcManager;

    public DepChainUtil(QCManager qcManager) {
        this.qcManager = qcManager;
    }

    public static Message Msg(String type, Node node, QC qc ,int viewNumber) {
        Message m = new Message();
        m.type = type;
        m.viewNumber = viewNumber;
        m.node = node;
        m.justify = qc;
        return m;
    }

    public Message voteMsg(String type, Node node, QC qc, int viewNumber) throws Exception {
        Message m = Msg(type, node, qc, viewNumber);
        m.partialSig = qcManager.createPartialSignature(type, viewNumber, node);
        return m;
    }

    public boolean matchingMsg(Message m, String type, int viewNumber){
        return m.type.equals(type) && m.viewNumber == viewNumber;
    }

    public boolean matchingQC(QC qc, String type, int viewNumber) {
        return qc.type.equals(type) && qc.viewNumber == viewNumber;
    }

}

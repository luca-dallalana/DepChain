package util;
import consensus.QCManager;
import model.Message;
import model.Node;
import model.QC;
import blockchain.Block;

public class DepChainUtil {
    private final QCManager qcManager;

    public DepChainUtil(QCManager qcManager) {
        this.qcManager = qcManager;
    }

    public static Message Msg(String type, Block block, String blockHash, QC qc ,int viewNumber) {
        Message m = new Message();
        m.type = type;
        m.viewNumber = viewNumber;
        m.block = block;
        m.blockHash = blockHash;
        m.justify = qc;
        return m;
    }

    public Message voteMsg(String type, Block block, String blockHash, QC qc, int viewNumber) throws Exception {
        Message m = Msg(type, block, blockHash, qc, viewNumber);
        m.partialSig = qcManager.createPartialSignature(type, viewNumber, blockHash);
        return m;
    }

    public int matchingMsg(Message m, int viewNumber){ // 0 lower view, 1 exact match, 2 same type higher view
        if (m.viewNumber == viewNumber) {
            return 1;
        }
        if (m.viewNumber > viewNumber) {
            return 2;
        }
        return 0;
    }

    public int matchingQC(QC qc, String type, int viewNumber) { // 0 no match, 1 exact match, 2 same type higher view
        if (qc.type.equals(type) && qc.viewNumber == viewNumber) {
            return 1;
        }
        if (qc.type.equals(type) && qc.viewNumber > viewNumber) {
            return 2;
        }
        return 0;
    }

}

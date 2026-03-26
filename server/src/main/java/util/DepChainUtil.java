package util;
import consensus.QCManager;
import model.Message;
import model.QC;
import blockchain.Transaction;
import java.util.List;

public class DepChainUtil {
    private final QCManager qcManager;

    public static class MaxQCInfo {
        private final QC qc;
        private final int senderPort;

        public MaxQCInfo(QC qc, int senderPort) {
            this.qc = qc;
            this.senderPort = senderPort;
        }

        public QC getQC() {
            return qc;
        }

        public int getSenderPort() {
            return senderPort;
        }
    }

    public DepChainUtil(QCManager qcManager) {
        this.qcManager = qcManager;
    }

    public static Message Msg(String type, List<Transaction> transactions, String blockHash, QC qc ,int viewNumber) {
        Message m = new Message();
        m.type = type;
        m.viewNumber = viewNumber;
        m.transactions = transactions;
        m.blockHash = blockHash;
        m.justify = qc;
        return m;
    }

    public Message voteMsg(String type, List<Transaction> transactions, String blockHash, QC qc, int viewNumber) throws Exception {
        Message m = Msg(type, transactions, blockHash, qc, viewNumber);
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

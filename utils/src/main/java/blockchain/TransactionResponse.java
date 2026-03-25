package blockchain;

public class TransactionResponse {
    private boolean executionSuccess;
    private int sequenceNumber;

    public TransactionResponse(boolean executionSuccess, int sequenceNumber) {
        this.executionSuccess = executionSuccess;
        this.sequenceNumber = sequenceNumber;
    }

    public boolean getExecutionSuccess() {
        return executionSuccess;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }
}

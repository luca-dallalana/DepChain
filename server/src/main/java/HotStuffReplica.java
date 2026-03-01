import model.QC;

public class HotStuffReplica{
    private int     curView;          // current view number
    private QC      lockedQC;         // the highest QC for which this replica voted "commit"
    private QC      prepareQC;        // the highest QC for which this replica voted "pre-commit"


    
    /* 
            Message m = receiveMessage();
        if (matchingMsg(m, "new-view", curView)) {
            handleNewView(m);
        } else if (matchingMsg(m, "prepare", curView)) {
            handlePrepare(m);
        } else if (matchingMsg(m, "pre-commit", curView)) {
            handlePreCommit(m);
        } else if (matchingMsg(m, "commit", curView)) {
            handleCommit(m);
        } else if (matchingMsg(m, "decide", curView)) {
            handleDecide(m);
        }
            */
}

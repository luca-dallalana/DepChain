package crypto;

import threshsig.Dealer;
import threshsig.GroupKey;
import threshsig.KeyShare;
import threshsig.ThresholdSigException;

public class KeySetup {

    public static KeyGenerationResult generateKeys(int n, int f, int keySize) throws ThresholdSigException {
        int k = 2 * f + 1; // Quorum size
        int l = n;         // Total number of shares 

        Dealer dealer = new Dealer(keySize);
        dealer.generateKeys(k, l);

        return new KeyGenerationResult(dealer.getGroupKey(), dealer.getShares());
    }

    public static class KeyGenerationResult {
        public final GroupKey groupKey;
        public final KeyShare[] shares;

        public KeyGenerationResult(GroupKey groupKey, KeyShare[] shares) {
            this.groupKey = groupKey;
            this.shares = shares;
        }
    }
}

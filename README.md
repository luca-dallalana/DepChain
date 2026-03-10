# DepChain - Byzantine Fault-Tolerant Blockchain
## Group 18

- 106147 Diogo Rodrigues
- 106378 Luca Dallalana
- 107157 Inês Alves

## Running the System
```bash
# 1. Generate keys
# N = Number of Members C = Number of clients
./pki.sh N C 

cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="N"

# 2. Start 4 replicas (separate terminals)
cd server && mvn exec:java -Dexec.args="id N"  # switch id and repeat for IDs 0,1,2,3... N

# 3. Start client
cd app && mvn exec:java -Dexec.args="id N" # switch id and repeat for IDs 0,1,2,3... N
```

## Running Tests
```bash
mvn test  # Runs all 24 tests
```

## Example Run
```bash
# Generate RSA keys
./pki.sh 4 2 

# Generate BLS keys
cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="4"

# Open 4 different terminals
cd server && mvn exec:java -Dexec.args="0 4"  
cd server && mvn exec:java -Dexec.args="1 4"  
cd server && mvn exec:java -Dexec.args="2 4"  
cd server && mvn exec:java -Dexec.args="3 4"  

# Open 2 other terminals
cd app && mvn exec:java -Dexec.args="0 4"
cd app && mvn exec:java -Dexec.args="1 4"
```
## Test Descriptions

**QCManagerTest:**
- `testAddVoteRejectsDuplicateSender` - Prevents double voting from same replica
- `testAddVoteRejectsInvalidPartialSignature` - Rejects votes with invalid BLS signatures
- `testAddVoteRejectsInvalidJustifyQC` - Rejects new-view votes with invalid prepareQC
- `testQCWithNoSignature` - Rejects QCs without aggregated signature
- `testQCWithWrongNumberOfSigners` - Rejects QCs with insufficient quorum (< 2f+1)
- `testQCWithWrongSignature` - Rejects QCs with forged aggregated signature

**PhasesTest:** (Prepare/PreCommit/Commit phase validation)
- `testHandlePrepareReplicaRejects for WrongTypeQC, InvalidSender, InvalidJustifyQC, NullJustify, InvalidSignature` - Rejects invalid prepare messages
- `testHandlePreCommitReplicaRejects for WrongTypeQC, NullJustify, InvalidSender, InvalidJustifyQC` - Rejects invalid pre-commit messages
- `testHandleCommitReplicaRejects for WrongTypeQC, NullJustify, InvalidSender, InvalidJustifyQC` - Rejects invalid commit messages

**SafeNodeTest:**
- `testSafeNode_LockedQCNull_ExtendsFrom` - Allows voting when no lockedQC exists and node extends from justify
- `testSafeNode_LockedQCNotNull_ExtendsFrom` - Allows voting when node extends from lockedQC node (safety)
- `testSafeNode_LockedQCNotNull_HigherView` - Allows voting when justify view > lockedQC view (liveness)
- `testSafeNode_LockedQCNotNull_FalseCase` - Rejects voting when node doesn't extend from lockedQC and view isn't higher

**CryptoLibTest:**
- `testHmacIntegrityFail` - Detects message tampering via HMAC verification failure

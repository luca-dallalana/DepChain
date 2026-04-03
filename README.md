# DepChain - Byzantine Fault-Tolerant Blockchain
## Group 18

- 106147 Diogo Rodrigues
- 106378 Luca Dallalana
- 107157 Inês Alves

## Running the System
```bash
# 1. Generate RSA keys (N = Number of replicas, C = Number of clients)
./pki.sh N C

# 2. Generate BLS threshold signature keys
cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="N"

# 3. Start N replicas (separate terminals)
cd server && mvn exec:java -Dexec.args="id N"  # Run for each replica: id = 0,1,2,...,N-1

# 4. Start C clients (separate terminals)
cd app && mvn exec:java -Dexec.args="id N"     # Run for each client: id = 0,1,2,...,C-1
```

## Example: Running with 4 Replicas and 2 Clients

```bash
# Generate keys
./pki.sh 4 3
cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="4"

# Start 4 replicas (4 separate terminals)
cd server && mvn exec:java -Dexec.args="0 4"
cd server && mvn exec:java -Dexec.args="1 4"
cd server && mvn exec:java -Dexec.args="2 4"
cd server && mvn exec:java -Dexec.args="3 4"

# Start 3 clients (3 separate terminals)
cd app && mvn exec:java -Dexec.args="0 4"
cd app && mvn exec:java -Dexec.args="1 4"
cd app && mvn exec:java -Dexec.args="2 4"

```

## Client Commands

Once connected, clients can execute the following operations:

```
0 - DepCoin Transfer (native)       # Transfer native currency between accounts
1 - ISTCoin Transfer (contract)     # Transfer ERC-20 tokens via smart contract
2 - Set Allowance                   # Approve spender to transfer tokens on your behalf
3 - TransferFrom                    # Transfer tokens using an allowance
4 - Get DepCoin Balance             # Query native currency balance
5 - Get ISTCoin Balance             # Query ERC-20 token balance
6 - Get Allowance                   # Query approved spending allowance
7 - Exit                            # Disconnect client
```

## Running Tests

```bash
mvn test  # Runs all 47 tests
```

### Consensus Tests

**QCManagerTest** (6 tests)
- `testAddVoteRejectsDuplicateSender` - Prevents double voting from same replica
- `testAddVoteRejectsInvalidPartialSignature` - Rejects votes with invalid BLS signatures
- `testAddVoteRejectsInvalidJustifyQC` - Rejects new-view votes with invalid prepareQC
- `testQCWithNoSignature` - Rejects QCs without aggregated signature
- `testQCWithWrongNumberOfSigners` - Rejects QCs with insufficient quorum (< 2f+1)
- `testQCWithWrongSignature` - Rejects QCs with forged aggregated signature

**PhasesTest** (12 tests)
- `testHandlePrepareReplicaRejects*` - Validates prepare phase message rejection (WrongTypeQC, InvalidSender, InvalidJustifyQC, NullJustify, InvalidSignature)
- `testHandlePreCommitReplicaRejects*` - Validates pre-commit phase message rejection (WrongTypeQC, NullJustify, InvalidSender, InvalidJustifyQC)
- `testHandleCommitReplicaRejects*` - Validates commit phase message rejection (WrongTypeQC, NullJustify, InvalidSender, InvalidJustifyQC)

**SafeNodeTest** (4 tests)
- `testSafeNode_LockedQCNull_ExtendsFrom` - Allows voting when no lockedQC exists and node extends from justify
- `testSafeNode_LockedQCNotNull_ExtendsFrom` - Allows voting when node extends from lockedQC (safety)
- `testSafeNode_LockedQCNotNull_HigherView` - Allows voting when justify view > lockedQC view (liveness)
- `testSafeNode_LockedQCNotNull_FalseCase` - Rejects voting when conditions not met

**CatchUpTest** (1 test)
- `testCatchUpEndToEndBetweenTwoNodes` - Validates catch-up mechanism for syncing lagging replicas

### Blockchain Tests

**ISTCoinTest** (6 tests)
- `testDeploymentAndInitialAllocation` - Verifies total supply and initial balance distribution (50/50 split)
- `testTransfer` - Tests successful ERC-20 token transfer between accounts
- `testTransferInsufficientBalance` - Rejects transfer attempts exceeding sender's balance
- `testApproveAndTransferFrom` - Tests approve and transferFrom workflow with allowance tracking
- `testTransferFromInsufficientAllowance` - Rejects transferFrom when allowance is insufficient
- `testFrontrunningProtection` - Validates expectedCurrentValue protection against approve/transferFrom race conditions

**TransactionExecutionTest** (5 tests)
- `testNativeDepCoinTransfer` - Verifies native currency transfers with gas deduction
- `testInvalidNonce` - Rejects transactions with invalid nonce
- `testInsufficientBalance` - Rejects transactions exceeding balance (including gas)
- `testContractCallWithGas` - Verifies contract calls with gas deduction
- `testMultipleTransactionsWithNonceSequence` - Tests sequential nonce enforcement

**ReplayAttackTest** (2 tests)
- `testSameNonceReplayBlocked` - Prevents replay of transactions with same nonce
- `testOldTransactionReplayBlocked` - Rejects transactions with old nonces

**DoubleSpendTest** (1 test)
- `testSameBlockDoubleSpend` - Prevents double-spending when multiple transactions from same sender are in same block

**InvalidGasTest** (2 tests)
- `testGasLimitExceeded` - Rejects transactions with insufficient gas
- `testGasParametersInBlockOrdering` - Validates gas-price-based transaction ordering

**NegativeBalanceTest** (1 test)
- `testContractCallCausesNegativeBalance` - Prevents negative balances when contract calls exceed available balance

**TransactionSignatureTest** (3 tests)
- `testValidSignatureAccepted` - Accepts valid RSA signatures
- `testInvalidSignatureRejected` - Rejects forged signatures
- `testMalformedSignatureRejected` - Rejects malformed signature data

**GenesisBlockTest** (1 test)
- `testGenesisBlockCreation` - Validates genesis block structure and initial account setup

**BlockStorePruneTest** (2 tests)
- `testPruneKeepsRecentBlocks` - Retains blocks within pruning window
- `testPruneRemovesOldBlocks` - Removes blocks beyond pruning threshold

### Cryptography Tests

**CryptoLibTest** (1 test)
- `testHmacIntegrityFail` - Detects message tampering via HMAC verification


# DepChain — Byzantine Fault-Tolerant Blockchain

DepChain is a permissioned blockchain built from scratch in Java that tolerates up to *f* Byzantine (arbitrarily malicious) replicas among a network of *N = 3f + 1* nodes. It combines a HotStuff-inspired four-phase BFT consensus protocol with a fully functional EVM execution layer, supporting both native coin transfers and ERC-20 smart contracts.

## Tech Stack

**Language & Build**
- Java 11, Maven (multi-module: `server`, `app`, `utils`, `tests`)

**Cryptography**
- RSA 2048-bit — transaction signing and Diffie-Hellman key authentication
- BLS threshold signatures — Teku (`tech.pegasys.teku`) for per-vote partial signatures and QC aggregation
- HMAC-SHA256 — per-message authentication after DH key exchange
- Diffie-Hellman 2048-bit — pairwise shared-secret establishment over UDP
- SHA-256 — block hashing

**Blockchain / EVM**
- Hyperledger Besu EVM v25.2.1 (`org.hyperledger.besu:evm`)
- Apache Tuweni — byte handling
- Web3j — ABI encoding for Solidity function calls
- Solidity (`solc`, Cancun EVM version) — ISTCoin smart contract

**Serialization & Transport**
- Gson — JSON serialisation of messages and blocks
- Raw UDP — custom reliable authenticated channel layered on top

**Testing**
- JUnit 5, Mockito

## Features

- BFT consensus with safety and liveness guarantees up to *f* Byzantine faults
- EVM smart contract execution — deploy and call Solidity contracts inside each block
- Native coin (DepCoin) transfers with gas-based fee deduction
- ERC-20 token (ISTCoin) with front-running-resistant `approve` (requires `expectedCurrentValue`)
- Gas-price-prioritised transaction ordering within a block gas cap (210,000 gas)
- Replay attack prevention via per-sender nonces tracked across consensus rounds
- Authenticated Perfect Links — DH-derived HMAC + sequence numbers + stubborn retransmission
- Catch-up protocol — lagging replicas request missing blocks from peers and re-execute state
- Block store pruning — alternative fork branches are discarded when a QC is locked

## Architecture / How It Works

The project is split into four Maven modules:

```
server/   — replica: consensus engine + EVM execution
app/      — client CLI
utils/    — shared library: crypto, networking, transaction model
tests/    — unit and integration tests
```

### Consensus (HotStuff variant)

Each consensus round is a **view**. The leader for view *v* is `v % N` (round-robin). A round proceeds through four phases:

1. **Prepare** — leader collects `new-view` messages (each carrying the sender's highest `prepareQC`), picks the highest, builds a block, and broadcasts a signed `prepare` message. Replicas validate every transaction in the proposal before voting.
2. **Pre-Commit** — leader aggregates 2*f*+1 `prepare` votes into a `prepareQC` (BLS-aggregated) and broadcasts it. Replicas update their `prepareQC`.
3. **Commit** — leader forms a `pre-commitQC`, replicas update their `lockedQC`. The block tree is pruned to the locked subtree at this point.
4. **Decide** — leader forms a `commitQC` and broadcasts `decide`. Replicas execute the block and persist it to `blockchain_data/`.

A replica votes in Prepare only if `safeBlock` holds: the block extends from `lockedQC` (safety) **or** the justify QC has a higher view than `lockedQC` (liveness). Timeouts use exponential backoff (doubling up to 64×) to prevent livelock under Byzantine leaders.

### Network Layer (Authenticated Perfect Links)

Before sending the first application message to a peer, a node performs an **RSA-authenticated Diffie-Hellman handshake** to derive a pairwise HMAC-SHA256 key. Every subsequent UDP packet is tagged with a sequence number and HMAC. The receiving side buffers out-of-order packets and delivers them in order, sending ACKs; the sender retransmits until ACKed (Stubborn Links → Authenticated Perfect Links).

### Blockchain & EVM

Each block stores a list of transactions and the resulting `WorldState` (a map of address → account with balance, nonce, bytecode, and storage). On proposal, the leader calls `BlockchainMember.buildBlockForProposal`, which waits up to 5 s for the mempool to fill the gas cap. Replicas rebuild the block deterministically using `buildBlock` and compare hashes before voting.

Transaction execution runs inside a Hyperledger Besu `SimpleWorld` EVM instance: nonce checked, balance checked (including max gas cost), then either a native ETH-style transfer or an EVM call. Failed transactions still consume gas (nonce is incremented) to prevent replay.

The **genesis block** is created on every replica startup. It deploys the `ISTCoin` Solidity contract at a fixed address and funds two client accounts (derived from their RSA public keys) with DepCoin and an equal split of the 100M ISTCoin supply.

## Getting Started

**Prerequisites:** Java 11+, Maven 3.6+, OpenSSL, `solc` (Solidity compiler, Cancun-compatible)

```bash
# 1. Clone the repository
git clone https://github.com/luca-dallalana/DepChain.git
cd DepChain

# 2. Generate RSA key pairs for N replicas and C clients
./pki.sh N C

# 3. Generate BLS threshold signature keys for N replicas
cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="N"
cd ..

# 4. Build all modules
mvn package -DskipTests

# 5. Start N replicas (one per terminal)
cd server && mvn exec:java -Dexec.args="<id> <N>"   # id = 0, 1, ..., N-1

# 6. Start C clients (one per terminal)
cd app && mvn exec:java -Dexec.args="<id> <N>"      # id = 0, 1, ..., C-1
```

## Usage

### Run with 4 replicas and 3 clients

```bash
# Key generation
./pki.sh 4 3
cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="4" && cd ..

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

### Client Commands

Once a client is running, it accepts interactive commands:

```
0 - DepCoin Transfer (native)       Transfer native currency between accounts
1 - ISTCoin Transfer (contract)     Transfer ERC-20 tokens via smart contract
2 - Set Allowance                   Approve a spender to transfer tokens on your behalf
3 - TransferFrom                    Transfer tokens using an existing allowance
4 - Get DepCoin Balance             Query native currency balance
5 - Get ISTCoin Balance             Query ERC-20 token balance
6 - Get Allowance                   Query an approved spending allowance
7 - Exit                            Disconnect client
```

### Run Tests

```bash
mvn test  # Runs all 46 tests
```

## Test Suite (46 Tests)

### Consensus Tests

**QCManagerTest** (6 tests)
- `testAddVoteRejectsDuplicateSender` — Prevents double voting from same replica
- `testAddVoteRejectsInvalidPartialSignature` — Rejects votes with invalid BLS signatures
- `testAddVoteRejectsInvalidJustifyQC` — Rejects new-view votes with invalid prepareQC
- `testQCWithNoSignature` — Rejects QCs without aggregated signature
- `testQCWithWrongNumberOfSigners` — Rejects QCs with insufficient quorum (< 2f+1)
- `testQCWithWrongSignature` — Rejects QCs with forged aggregated signature

**PhasesTest** (12 tests)
- `testHandlePrepareReplicaRejects*` — Validates prepare phase message rejection (WrongTypeQC, InvalidSender, InvalidJustifyQC, NullJustify, InvalidSignature)
- `testHandlePreCommitReplicaRejects*` — Validates pre-commit phase message rejection (WrongTypeQC, NullJustify, InvalidSender, InvalidJustifyQC)
- `testHandleCommitReplicaRejects*` — Validates commit phase message rejection (WrongTypeQC, NullJustify, InvalidSender, InvalidJustifyQC)

**SafeNodeTest** (4 tests)
- `testSafeNode_LockedQCNull_ExtendsFrom` — Allows voting when no lockedQC exists and node extends from justify
- `testSafeNode_LockedQCNotNull_ExtendsFrom` — Allows voting when node extends from lockedQC (safety)
- `testSafeNode_LockedQCNotNull_HigherView` — Allows voting when justify view > lockedQC view (liveness)
- `testSafeNode_LockedQCNotNull_FalseCase` — Rejects voting when conditions not met

**CatchUpTest** (1 test)
- `testCatchUpEndToEndBetweenTwoNodes` — Validates catch-up mechanism for syncing lagging replicas

### Blockchain Tests

**ISTCoinTest** (6 tests)
- `testDeploymentAndInitialAllocation` — Verifies total supply and initial balance distribution (50/50 split)
- `testTransfer` — Tests successful ERC-20 token transfer between accounts
- `testTransferInsufficientBalance` — Rejects transfer attempts exceeding sender's balance
- `testApproveAndTransferFrom` — Tests approve and transferFrom workflow with allowance tracking
- `testTransferFromInsufficientAllowance` — Rejects transferFrom when allowance is insufficient
- `testFrontrunningProtection` — Validates expectedCurrentValue protection against approve/transferFrom race conditions

**TransactionExecutionTest** (5 tests)
- `testNativeDepCoinTransfer` — Verifies native currency transfers with gas deduction
- `testInvalidNonce` — Rejects transactions with invalid nonce
- `testInsufficientBalance` — Rejects transactions exceeding balance (including gas)
- `testContractCallWithGas` — Verifies contract calls with gas deduction
- `testMultipleTransactionsWithNonceSequence` — Tests sequential nonce enforcement

**ReplayAttackTest** (2 tests)
- `testSameNonceReplayBlocked` — Prevents replay of transactions with same nonce
- `testOldTransactionReplayBlocked` — Rejects transactions with old nonces

**DoubleSpendTest** (1 test)
- `testSameBlockDoubleSpend` — Prevents double-spending when multiple transactions from same sender are in same block

**InvalidGasTest** (2 tests)
- `testGasLimitExceeded` — Rejects transactions with insufficient gas
- `testGasParametersInBlockOrdering` — Validates gas-price-based transaction ordering

**NegativeBalanceTest** (1 test)
- `testContractCallCausesNegativeBalance` — Prevents negative balances when contract calls exceed available balance

**TransactionSignatureTest** (2 tests)
- `testValidSignatureAccepted` — Accepts valid RSA signatures
- `testWrongSenderAddressWithValidSignature` — Rejects transactions with mismatched sender addresses

**GenesisBlockTest** (1 test)
- `testGenesisCreation` — Validates genesis block structure and initial account setup

**BlockStorePruneTest** (2 tests)
- `testPruneKeepsRecentBlocks` — Retains blocks in main chain while removing orphan blocks
- `testPruneRemovesOldBlocks` — Removes alternative fork blocks after pruning to locked QC

### Cryptography Tests

**CryptoLibTest** (1 test)
- `testHmacIntegrityFail` — Detects message tampering via HMAC verification

## What I Learned / Challenges

The hardest part was wiring together the safety and liveness properties of the BFT protocol correctly. The `safeBlock` rule has two branches — a block is safe if it extends from the `lockedQC` node (safety) *or* if its justifying QC has a higher view number than the locked one (liveness) — and getting the interaction between view changes, catch-up, and block-tree pruning right required careful state management. I also learned a lot about how Ethereum-style transaction execution actually works: nonces increment even on failed transactions, and max gas cost must be reserved upfront, which prevents several attack vectors that are easy to miss when building a naive implementation.

## Group 18

- 106147 Diogo Rodrigues
- 106378 Luca Dallalana
- 107157 Inês Alves

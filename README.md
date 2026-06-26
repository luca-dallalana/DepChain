# DepChain — Byzantine Fault-Tolerant Blockchain

DepChain is a permissioned blockchain built entirely in Java from first principles, implementing a HotStuff-variant four-phase BFT consensus protocol that tolerates up to f Byzantine-faulty replicas in a network of N = 3f+1 nodes, "Byzantine" meaning arbitrarily malicious, not just crashed. Every committed block carries a BLS-aggregated quorum certificate (QC) signed by at least 2f+1 replicas; the execution layer runs against Hyperledger Besu's full EVM at Cancun spec rather than a custom bytecode interpreter; and the transport layer provides authenticated perfect links over raw UDP through RSA-authenticated Diffie-Hellman handshakes and HMAC-SHA256 per-message authentication. Replicas don't accept the leader's block hash on faith: each one independently re-executes every transaction in the proposal against its local world state, recomputes the block hash, and only signs if the hashes match. The consensus, cryptography, and network layers are all implemented from primitives, no BFT framework, no reliability library.

## Why This Is Hard

The centerpiece of HotStuff correctness is the `safeBlock` predicate. A replica casts a vote in the Prepare phase only if `safeBlock(block, justifyQC)` returns true, and getting either branch wrong breaks the protocol in a distinct, hard-to-diagnose way.

The predicate, as implemented in `DepChainMember`:

```
safeBlock(b, qc):
  if lockedQC == null:
    return extendsFrom(b, qc.blockHash)
  return extendsFrom(b, lockedQC.blockHash)   // safety branch
      || qc.viewNumber > lockedQC.viewNumber    // liveness branch
```

"Locked" means a pre-commitQC exists: at least 2f+1 replicas have sent a pre-commit vote, meaning a quorum is aware of this block and any future commit must involve a set of replicas that overlaps with those voters. A block can only be committed if it was first locked.

**If you remove the safety branch**, a Byzantine leader can present different proposals to different partitions of the network. Both partitions might independently reach 2f+1 votes, from overlapping sets of correct replicas that don't know about each other's locks, and two different blocks commit at the same height. The fork is permanent and unrecoverable.

**If you remove the liveness branch**, the system can stall whenever a quorum locks on a block that no subsequent leader can extend via the safety branch alone. Concretely: suppose 2f+1 replicas process the commit message at view 5 and update their `lockedQC` to the pre-commitQC, but the leader then crashes before broadcasting the decide message. At view 6, a new leader proposes a block extending a different branch, its best prepareQC is at view 4, before the lock formed. Every replica that locked at view 5 rejects the proposal: the new block doesn't extend `lockedQC`, and without the liveness branch there's no alternative. If enough replicas are in this state, no quorum forms and the protocol halts indefinitely. The liveness branch provides the escape: when a leader presents a justify QC from a view strictly higher than the replica's lockedQC, the replica can vote even if the proposal doesn't extend the locked block. A higher-view QC implies a quorum has already progressed past that state, which means the locked block's commit window has closed.

The interaction between view changes and block-tree pruning is where I found the correctness argument hardest to hold in my head. Pruning happens at the Commit phase: when a replica receives a commit message carrying a pre-commitQC, it updates its `lockedQC` and immediately removes from its block store every block that isn't in the ancestry chain of the newly locked block. This keeps memory bounded and eliminates stale fork branches. But it creates a timing hazard: if a replica falls one or more views behind and misses the commit message, it may receive a prepare message for a block whose parent was just pruned from its store. The replica fires a catch-up request to the message sender, asking for the missing chain between its current lockedQC and the referenced block, and continues processing other messages while waiting for the response. When the missing blocks arrive, they're stored and the replica can resume voting normally. The catch-up protocol works precisely because pruning only removes blocks outside the locked subtree. A catch-up request asks for the chain between the requester's lockedQC and a target block hash, and a server always retains its full locked ancestry, so the requested blocks are always available.

## Architecture

The project is organized as four Maven modules: `server` (replica consensus and EVM execution), `app` (client CLI), `utils` (shared crypto, networking, and transaction model), and `tests` (unit and integration tests).

```
┌─────────────────────────────────────────────────────────────────┐
│  Clients                              Replicas                  │
│                                                                 │
│  ┌────────┐   Transaction (RSA sig)   ┌──────────────────────┐ │
│  │client 0├──────────────────────────►│                      │ │
│  └────────┘                           │   Leader (view % N)  │ │
│  ┌────────┐   new-view (prepareQC)    │                      │ │
│  │client 1│◄──────────────────────────┤◄──────────────────── │ │
│  └────────┘                           └──────────┬───────────┘ │
│                                                  │             │
│                                    ┌─────────────▼──────────┐ │
│                                    │  PREPARE               │ │
│                                    │  build block proposal  │ │
│                                    │  broadcast prepare ────┼─┼──► replicas
│                                    │  ◄── prepare votes ────┼─┼──  (BLS partial sig)
│                                    └─────────────┬──────────┘ │
│                                                  │             │
│                                    ┌─────────────▼──────────┐ │
│                                    │  PRE-COMMIT            │ │
│                                    │  form prepareQC        │ │
│                                    │  broadcast pre-commit ─┼─┼──► replicas
│                                    │  ◄── pre-commit votes ─┼─┼──  lockedQC updated
│                                    └─────────────┬──────────┘ │
│                                                  │  block tree │
│                                    ┌─────────────▼──────────┐ │  pruned here
│                                    │  COMMIT                │ │
│                                    │  form precommitQC      │ │
│                                    │  broadcast commit ─────┼─┼──► replicas
│                                    │  ◄── commit votes ─────┼─┼──
│                                    └─────────────┬──────────┘ │
│                                                  │             │
│                                    ┌─────────────▼──────────┐ │
│                                    │  DECIDE                │ │
│                                    │  form commitQC         │ │
│                                    │  broadcast decide ─────┼─┼──► replicas
│  ◄── TransactionResponse ──────────┤  execute block         │ │    execute block
│                                    │  persist to disk       │ │    persist to disk
│                                    └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Consensus: Four Phases

Each HotStuff round is a **view**. The leader for view `v` is `v % N` (round-robin, as computed by `MemberConfig.getLeader`). Every phase transition requires a quorum of 2f+1 votes, certified by a BLS-aggregated QC.

**Prepare**: The leader collects `new-view` messages from 2f+1 replicas, each carrying the sender's current highest `prepareQC`. It selects the one with the highest view number, retrieves the corresponding block from its store, and builds a new block proposal on top of it. The leader waits up to 5 seconds for the transaction mempool to fill the block gas limit (210,000 gas), then orders transactions by gas price (highest first, with nonces enforced sequentially per sender) and executes them against its local world state using the Besu EVM. The resulting block hash is a SHA-256 over the parent hash, block number, all transaction fields, and the full world state serialized in deterministic order (accounts by address, storage by key). The leader broadcasts a `prepare` message carrying this hash, the full transaction list, and the justify QC.

Replicas receive the prepare message and independently rebuild the block: they re-execute every transaction, verify each transaction's RSA signature, check the sender port against the from-address, and compare the resulting hash to what the leader sent. If the hashes match and `safeBlock(proposedBlock, justifyQC)` holds, the replica produces a BLS partial signature over `SHA-256(type_bytes || viewNumber_int32 || blockHash_bytes)` and sends it to the leader as a prepare vote.

**Pre-Commit**: The leader collects 2f+1 prepare votes, aggregates their BLS partial signatures using Teku's `BLS.aggregate`, and forms a `prepareQC`. It broadcasts a `pre-commit` message carrying this QC. Replicas verify the aggregated signature using `BLS.fastAggregateVerify` against the signer public key set, store the proposed block, update their `prepareQC`, and send a pre-commit vote.

**Commit**: The leader collects 2f+1 pre-commit votes, forms a `precommitQC`, and broadcasts a `commit` message. Both the leader and replicas then set `lockedQC` to this precommitQC and prune their block stores to the subtree rooted at the locked block, discarding all competing fork branches. Replicas send commit votes.

**Decide**: The leader collects 2f+1 commit votes, forms a `commitQC`, and broadcasts a `decide` message. Every replica (including the leader) retrieves the committed block, executes it against the current world state via the Besu EVM, and persists the result to `blockchain_data/block_N.json`. Clients are notified via `TransactionResponse` messages. Both leader and replicas then advance to the next view by sending `new-view` with their current `prepareQC`.

Timeouts use exponential backoff starting at 8 seconds and doubling up to 64×, which prevents livelock under a Byzantine leader that never completes a phase. A successful phase completion resets the multiplier to 1.

### Network: Authenticated Perfect Links

All inter-node communication flows over raw UDP. Before the first application message to any peer, the initiator generates a 2048-bit DH key pair, signs the public key with its RSA private key (SHA256withRSA), and sends a `DH REQ` packet. The responder verifies the RSA signature using the sender's known public key, generates its own DH key pair with the initiator's DH parameters, derives the shared secret, and replies with its own public key identically signed. Both sides derive an HMAC-SHA256 key from the first 32 bytes of the shared secret. This RSA-over-DH binding prevents a man-in-the-middle from substituting their own DH public key during the handshake.

Once the handshake completes, every outgoing application message is prefixed with a sequence number and appended with an HMAC tag computed over `SEQ=<n> <payload>` using the pairwise key. The receiver verifies the HMAC before delivering; a failed check silently drops the packet. ACK messages, sent as plaintext `ACK=<n>`, are not HMAC-tagged, since they carry no payload that could be forged to cause harm.

The stubborn link layer keeps every sent packet in an `unAcked` map keyed by (port, sequence). A background thread re-sends each unacknowledged packet every 600ms until an ACK for that sequence number arrives. The in-order delivery layer buffers out-of-order packets in `outOfOrderMessages` keyed by (port, seq) and delivers them to the application only when the sequence is contiguous from the last delivered, draining the buffer when a gap closes.

## What Makes This Different

Most portfolio blockchain projects either wrap an existing consensus library (Tendermint, LibHotstuff) or implement a simplified happy-path protocol with a toy interpreter and TCP sockets. This project differs on four axes:

**From the paper, not a library**: The entire consensus path, `safeBlock`, QC formation, QC verification, view change, and catch-up, is implemented from the protocol specification with no third-party BFT framework. The `QCManager`, `DepChainMember`, and `DepChainUtil` classes together constitute the full consensus engine, including the vote deduplication, BLS partial signature verification per vote, and the two-branch safety/liveness predicate.

**BLS aggregate signatures for QCs**: In most tutorial BFT implementations, a quorum certificate is a list of N individual replica signatures and verification means checking each one. Here, each vote is a BLS partial signature produced by the voter's own BLS key, and the leader aggregates them into a single constant-size signature using Teku's `BLS.aggregate`. Verification uses `BLS.fastAggregateVerify` against the signer public key set. This is the same QC representation used in Ethereum's beacon chain and Aptos: the QC size is independent of N, and verification is a single pairing operation rather than N independent checks.

**Full EVM via Hyperledger Besu**: Transactions are not interpreted by a custom stack machine. They run against Besu's `SimpleWorld` EVM at Cancun spec, with a real `EVMExecutor`. The `ISTCoin` contract is compiled with `solc`, and the deployment bytecode is embedded in the genesis block via an ABI-encoded deployment call. The `EVMHelper` wraps the Besu executor and extracts return data, gas used, and success/revert status from the EVM trace output.

**Raw UDP with a hand-implemented reliability layer**: No TCP, no Netty, no gRPC. The stubborn retransmission loop, ACK tracking, and out-of-order delivery buffer are all implemented directly in `NetworkLayerLib`. The DH handshake runs inline over the same UDP socket before the first application message.

## Test Suite

46 tests across 14 test classes, organized by the protocol property they protect:

### Safety

**`SafeNodeTest`** (4 tests) exercises the `safeBlock` predicate directly, covering the null-lockedQC base case, the safety branch (block extends from lockedQC), the liveness branch (justify QC has a higher view), and the rejection case (neither branch satisfied). A regression here means a replica would vote for a block it should have rejected — allowing a Byzantine leader to collect 2f+1 votes for two conflicting blocks at the same height and fork the committed chain.

### Byzantine Fault Tolerance

**`QCManagerTest`** (6 tests) covers the QC aggregation and verification layer: duplicate vote rejection, invalid BLS partial signature in a prepare vote, invalid justify QC in a new-view message, and three flavors of malformed QC verification failure (no aggregated signature, fewer signers than quorum, wrong aggregated signature). A failure means a Byzantine replica can forge or weaken a QC and advance consensus without a legitimate quorum.

**`PhasesTest`** (12 tests) covers each phase handler's input validation — null justify, wrong QC type, non-leader sender, and invalid aggregated signature — for each of the Prepare, Pre-Commit, and Commit phases. A failure means a Byzantine leader can push a replica into an incorrect phase state or cause it to accept a message that should have been rejected.

### Liveness

**`CatchUpTest`** (1 test) is an end-to-end two-node test: node 0 has blocks 1–4 in its store, node 1 has only blocks 1–2. Node 1 requests catch-up for block 4 from node 0, and the test verifies that node 1's store is updated with blocks 3 and 4, with correct parent-hash linkage. A failure means a lagging replica permanently diverges from the network.

### EVM Correctness

**`ISTCoinTest`** (6 tests) exercises the Solidity ERC-20 contract: initial supply allocation (50/50 split between the two clients), successful transfer, transfer with insufficient balance (must revert), approve + transferFrom with allowance tracking, transferFrom with insufficient allowance (must revert), and the front-running protection (`approve(spender, newValue, expectedCurrentValue)` must revert when the current allowance doesn't match `expectedCurrentValue`). A contract logic failure means replicas produce different storage states for the same transaction list and disagree on the block hash, breaking consensus.

**`TransactionExecutionTest`** (5 tests) covers core EVM accounting: native DepCoin transfer with exact gas deduction, invalid nonce rejection, insufficient balance rejection (when value + max gas cost exceeds balance), contract call with actual gas deduction, and sequential nonce enforcement across multiple transactions in a single block. A failure breaks the determinism guarantee: two replicas applying the same transaction list to the same initial state would produce different world states.

### Replay and Double-Spend Prevention

**`ReplayAttackTest`** (2 tests) covers same-nonce replay (a transaction resubmitted with the same nonce is rejected because the EVM has already incremented the sender's nonce) and old-transaction replay (a transaction with a nonce consumed several blocks prior fails the nonce check). A failure means an attacker can drain accounts by replaying previously signed transactions.

**`DoubleSpendTest`** (1 test) puts two transactions from the same sender in a single block: the first sends 80% of the balance at higher gas price, the second tries to send another 80% at lower gas price. After ordering by gas price, the second transaction executes against the post-first-transaction balance and fails. A failure means a sender can exceed their balance within a single consensus round.

### Cryptographic Integrity

**`TransactionSignatureTest`** (2 tests) verifies RSA signature checking: a valid signature from the correct client is accepted; a signature made with client 1's key but claiming to be from client 0's address is rejected, because the server derives the expected signer address from the `senderPort` field and compares it against the `from` field. A failure allows any node to forge transactions on behalf of any other account.

**`CryptoLibTest`** (1 test) verifies that `verifyHmac` returns false when the payload has been tampered with after the HMAC was computed. A failure means the network layer accepts unauthenticated messages, breaking the authenticated perfect link guarantee.

### Block Management

**`BlockStorePruneTest`** (2 tests) verifies pruning behavior: orphan blocks from the genesis are removed while the main chain is retained (test 1), and competing fork branches from an intermediate block are removed after locking to a different branch (test 2). In both cases `pruneToLockedSubtree` must return a count of exactly 2 pruned blocks and leave the main chain intact. A failure causes either unbounded memory growth or, in the worse case, a pruned-then-readmitted block re-entering the tree with broken ancestry, which the safety argument doesn't cover.

**`GenesisBlockTest`** (1 test) verifies genesis block structure: block number 0, null parent hash, one deployment transaction, four accounts in the initial world state (admin, ISTCoin contract, client 0, client 1), ISTCoin deployed as a contract at the expected address, and both client nonces at 0. A failure means replicas start from divergent world states, from which consensus cannot recover.

### Gas and Resource Limits

**`InvalidGasTest`** (2 tests) covers gas limit enforcement: a transaction submitted with a gasLimit of 100 (below the 21,000 minimum for a native transfer) fails with `executionSuccess = false` while still consuming gas; and `orderTransactionsForBlock` returns transactions sorted by gas price descending. Incorrect gas handling causes two replicas to include different transaction sets and produce different block hashes.

**`NegativeBalanceTest`** (1 test) sets a client's balance to 50,000 and submits a contract call with a gasLimit of 100,000. Since `totalGasCost = gasPrice * gasLimit > balance`, the transaction fails and the final balance remains non-negative. A failure allows gas charges to push a sender's balance below zero, creating DepCoin from nothing.

## Getting Started

**Prerequisites**: Java 11+, Maven 3.6+, OpenSSL, `solc` (Cancun-compatible)

```bash
# Generate RSA key pairs for 4 replicas and 3 clients
./pki.sh 4 3

# Generate BLS aggregate signature keys for 4 replicas
cd utils && mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args="4" && cd ..

# Build all modules
mvn package -DskipTests

# Start 4 replicas — one per terminal (id = 0..3)
cd server && mvn exec:java -Dexec.args="0 4"
cd server && mvn exec:java -Dexec.args="1 4"
cd server && mvn exec:java -Dexec.args="2 4"
cd server && mvn exec:java -Dexec.args="3 4"

# Start 3 clients — one per terminal (id = 0..2)
cd app && mvn exec:java -Dexec.args="0 4"
cd app && mvn exec:java -Dexec.args="1 4"
cd app && mvn exec:java -Dexec.args="2 4"

# Run all 46 tests
mvn test
```

Client commands: `0` DepCoin transfer · `1` ISTCoin transfer · `2` set allowance · `3` transferFrom · `4` get DepCoin balance · `5` get ISTCoin balance · `6` get allowance · `7` exit

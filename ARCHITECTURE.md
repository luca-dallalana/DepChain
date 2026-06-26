# DepChain Architecture

This document is for engineers who want to understand how the system works before reading code. It covers the four consensus phases in operational detail, the formal definition and correctness argument for the `safeBlock` predicate, and the design decisions behind the network layer.

---

## Consensus Protocol

DepChain implements a variant of the HotStuff BFT consensus protocol. The protocol operates over N = 3f+1 replicas and tolerates up to f Byzantine-faulty nodes, nodes that can deviate from the protocol in any way, including sending conflicting messages to different peers, selectively dropping messages, or colluding with other Byzantine nodes.

### Overview

Consensus progresses in rounds called **views**. Each view has a designated leader: for view `v`, the leader is replica `v % N` (round-robin). Within a view, the protocol executes four sequential phases, each requiring a quorum of 2f+1 votes before the next phase begins. A vote from a replica is a BLS partial signature over the tuple `(type, viewNumber, blockHash)`. The leader aggregates 2f+1 partial signatures using `BLS.aggregate` into a single constant-size quorum certificate (QC). Verification of a QC uses `BLS.fastAggregateVerify` against the set of signer public keys.

If a phase times out, the replica increments a timeout counter and waits `8000ms × timeoutCount` before proceeding to the next view (exponential backoff, capped at 64×). A successful phase completion resets the multiplier to 1. This prevents the system from spinning at full speed under a sustained Byzantine or crashed leader.

### Phase 1: Prepare

The view begins with each replica sending a `new-view` message to the leader carrying its current highest `prepareQC`. The leader waits until it has 2f+1 `new-view` messages, then:

1. Selects the `prepareQC` with the highest view number among all received messages. Call the block this QC covers `highBlock`.
2. Retrieves `highBlock` from its block store.
3. Builds a new block proposal by extending `highBlock`:
   - Waits up to 5 seconds for the transaction mempool to accumulate enough transactions to approach the 210,000 gas block cap.
   - Orders transactions by gas price descending. Within the same sender, transactions are ordered by nonce to maintain sequential execution.
   - Executes the ordered transaction list against the current world state via the Besu EVM (`BlockchainMember.buildBlockForProposal`).
   - Computes the block hash: `SHA-256` over a deterministic binary serialization of `(parentBlockHash, blockNumber, transactions[], worldState)`, where accounts are sorted by address and storage keys are sorted lexicographically.
4. Broadcasts a `prepare` message containing `(blockHash, transactions[], justifyQC)` to all replicas.

The leader also acts as a replica for its own proposal: it verifies the proposal (step below) and sends itself a prepare vote.

Each replica receiving a `prepare` message:
1. Verifies the sender is the expected leader for the current view.
2. Verifies the `justifyQC` is well-formed: it has at least 2f+1 signers and `BLS.fastAggregateVerify` passes.
3. Verifies that the `justifyQC` type is `"prepare"`.
4. Independently reconstructs the proposed block from the provided transaction list against its own local world state and computes the block hash. This re-execution includes verifying each transaction's RSA signature, checking the `from` address against the sender port's known public key, and enforcing nonce ordering.
5. Compares the reconstructed hash to the hash in the message. A mismatch means either the leader is Byzantine or the replica's state has diverged.
6. Evaluates `safeBlock(proposedBlock, justifyQC)` (see below).
7. If all checks pass, produces a BLS partial signature over `SHA-256(type_bytes || viewNumber_int32 || blockHash_bytes)` and sends it to the leader as a `prepare` vote.

### Phase 2: Pre-Commit

The leader waits until it has 2f+1 prepare votes. It then:
1. Verifies each vote's BLS partial signature using the sender's BLS public key.
2. Rejects duplicate votes from the same sender port.
3. Calls `BLS.aggregate` on the collected partial signatures to form a `prepareQC`.
4. Stores the proposed block in its block store (now confirmed by a quorum).
5. Broadcasts a `pre-commit` message carrying the `prepareQC`.

Each replica receiving a `pre-commit` message:
1. Verifies the sender is the current leader.
2. Verifies the `prepareQC`: type must be `"prepare"`, signer count must be ≥ 2f+1, `BLS.fastAggregateVerify` must pass.
3. Stores the block referenced by the QC.
4. Updates its local `prepareQC` to the received QC.
5. Sends a pre-commit vote (BLS partial signature) to the leader.

### Phase 3: Commit

The leader waits until it has 2f+1 pre-commit votes. It then:
1. Verifies and aggregates the votes into a `precommitQC`.
2. Sets its own `lockedQC` to this `precommitQC`.
3. Calls `BlockStore.pruneToLockedSubtree(lockedQC.blockHash)`, which removes all stored blocks that are not in the ancestry chain of the locked block. This eliminates all competing fork branches.
4. Broadcasts a `commit` message carrying the `precommitQC`.

Each replica receiving a `commit` message:
1. Verifies the sender is the current leader.
2. Verifies the `precommitQC`: type must be `"pre-commit"`, signer count ≥ 2f+1, signature valid.
3. Sets its `lockedQC` to the received `precommitQC`.
4. Prunes its block store to the locked subtree.
5. Sends a commit vote to the leader.

The act of setting `lockedQC` is the safety commitment: from this point, this replica will only vote for future blocks that extend from the locked block, unless a higher-view QC justifies an exception (see `safeBlock` below).

### Phase 4: Decide

The leader waits until it has 2f+1 commit votes. It then:
1. Aggregates the votes into a `commitQC`.
2. Broadcasts a `decide` message carrying the `commitQC`.

Every replica (including the leader) receiving a `decide` message:
1. Verifies the `commitQC`: type `"commit"`, signer count ≥ 2f+1, signature valid.
2. Retrieves the committed block from its store.
3. Executes the block against the current world state, traversing the block ancestry from `lastExecutedBlock` to the decided block to ensure all intermediate blocks are also executed in order (`BlockchainMember.executeBlock`).
4. Persists the block and resulting world state to `blockchain_data/block_N.json`.
5. Sends a `TransactionResponse` to each client whose transactions were included.
6. Advances to the next view by sending a `new-view` message carrying its current `prepareQC`.

---

## The safeBlock Predicate

### Formal Definition

```
safeBlock(b, qc) ≡
  if lockedQC = ⊥:
    extendsFrom(b, qc.blockHash)
  else:
    extendsFrom(b, lockedQC.blockHash)    -- safety branch
    ∨ qc.viewNumber > lockedQC.viewNumber  -- liveness branch
```

Where:
- `b` is the proposed block
- `qc` is the justify QC provided by the leader (the prepareQC the leader used to justify its proposal)
- `lockedQC` is the replica's current locked quorum certificate (the most recent precommitQC it has seen)
- `extendsFrom(b, h)` returns true if `b`'s hash is `h` or if `b`'s parent chain contains a block with hash `h`

### The Null Case

When `lockedQC` is null (no commit phase has completed yet), the replica has no locked commitment. The only constraint is that the proposed block must extend from the block referenced by the leader's justify QC. This ensures the leader is building on a block that at least some quorum has seen and signed, rather than proposing an arbitrary block.

### The Safety Branch

`extendsFrom(b, lockedQC.blockHash)` ensures that any new proposal must be a descendant of the block the replica has committed to via `lockedQC`. Because `lockedQC` is a precommitQC, formed from 2f+1 pre-commit votes, at least f+1 correct replicas have sent a pre-commit vote for the locked block. These f+1 replicas hold the same `lockedQC` and will enforce the same branch restriction. Since 2f+1 votes are required to form any QC, and at most f votes can come from Byzantine replicas, the f+1 honest replicas holding the safety constraint are sufficient to block any conflicting proposal from reaching quorum. Therefore, no block outside the locked subtree can ever collect 2f+1 prepare votes while the lock holds.

If this branch were absent, a Byzantine leader could present two unrelated block proposals to two disjoint partitions of correct replicas. Both could reach 2f+1 votes (drawing from their respective partitions plus Byzantine replicas), two commitQCs could form for conflicting blocks, and the chain would permanently fork.

### The Liveness Branch

`qc.viewNumber > lockedQC.viewNumber` allows a replica to vote for a block that doesn't extend from its locked block, provided the leader's justify QC is from a strictly higher view than the lockedQC. The argument for why this is safe: a QC at view `v` proves that 2f+1 replicas sent a vote at view `v`. Any set of 2f+1 replicas overlaps with the f+1 correct replicas that hold a lockedQC at view `v_lock`. For those overlapping replicas to have voted at view `v > v_lock`, they must have already seen a reason to abandon the lower lock, either this liveness branch (a higher-view justify QC) or the safety branch (the proposal extends from their locked block). Informally, the entire chain of view advances traces back to a point where no conflicting commit had occurred, because each step requires a quorum that overlaps the set holding the prior lock. The liveness branch thus allows progress while the safety invariant is maintained by the quorum intersection argument.

If this branch were absent, consider a scenario where 2f+1 replicas have set `lockedQC` at view 5 (received the commit message) but the view-5 leader crashes before broadcasting the decide message. The view-6 leader's best justify QC is at view 4 (the highest prepareQC across the new-view messages from replicas that didn't lock). It proposes a block extending the view-4 block. Every replica with lockedQC at view 5 rejects it, the proposal doesn't extend the locked block. If there are enough such replicas (possible since 2f+1 locked), the quorum threshold can never be reached and the system halts permanently.

### The Genesis Special Case

The genesis QC is a synthetic QC with `viewNumber = 0`, `type = "prepare"`, and no signature or signers. The `verifyQC` method special-cases it: a QC matching these fields and the known genesis block hash is considered valid without signature verification. This allows the first round of consensus (view 1) to proceed without any prior voting history.

---

## Network Layer

### Design Constraints

All communication is over raw UDP sockets (one per node, fixed port: 3000+id for replicas, 4000+id for clients). The protocol requires that messages between two correct nodes are eventually delivered exactly once in sending order, a property called **Authenticated Perfect Links** (APL). UDP provides none of these guarantees by default: packets can be lost, duplicated, reordered, and delivered without authentication. The network layer builds APL from these primitives in four stacked mechanisms.

### Layer 1: RSA-Authenticated Diffie-Hellman

Before the first application message to any peer, the initiator performs a DH handshake to establish a pairwise HMAC key. The handshake packet format is:

```
DH REQ= <base64-encoded 2048-bit DH public key> SEQ= <seq> RSA_SIG= <base64-encoded SHA256withRSA signature over the public key bytes>
```

The responder:
1. Parses the DH public key.
2. Looks up the sender's RSA public key by port (loaded from `rsa_keys/` at startup).
3. Calls `CryptoLib.verifySignature` — if this fails, the handshake is rejected.
4. Generates its own DH key pair using the initiator's DH parameters (`KeyPairGenerator` with the initiator's `DHParameterSpec`).
5. Computes `KeyAgreement.doPhase` with the initiator's public key to derive the shared secret.
6. Calls `CryptoLib.deriveHmacKey` — extracts the first 32 bytes of the shared secret as an `HmacSHA256` key.
7. Sends back a `DH RESP` packet with its own DH public key, similarly RSA-signed.

The initiator performs the same steps symmetrically. After the handshake, both sides store the derived HMAC key in a `sharedSecrets` map keyed by peer port.

The RSA binding is what prevents a man-in-the-middle from substituting their own DH public key: a MITM could intercept the DH key exchange, but they would need the sender's RSA private key to produce a valid `RSA_SIG` over their own DH public key, which they don't have.

### Layer 2: HMAC-SHA256 Per-Message Authentication

Every outgoing application message (not DH handshake, not ACK) is formatted as:

```
SEQ= <n> <payload> HMAC= <base64-encoded HMAC-SHA256>
```

The HMAC is computed over `SEQ=<n> <payload>` using the pairwise key from the handshake. The receiver calls `CryptoLib.verifyHmac` before processing any message. A failed verification silently drops the packet. This prevents both replay (an attacker replaying a packet with a different sequence number would fail HMAC since the sequence number is included in the authenticated prefix) and forgery (an attacker without the HMAC key cannot produce a valid tag).

### Layer 3: Stubborn Retransmission

The sender (`slSend`) adds every sent packet to an `unAcked` map keyed by `(peerPort, seqNum)`. A background retransmission thread re-sends every entry in `unAcked` every 600ms. When the receiver delivers a packet, it replies with a plaintext `ACK= <seq>` message. The sender removes the entry from `unAcked` when the matching ACK arrives. This provides eventual delivery: if the underlying UDP path is intermittently lossy, the sender will keep trying until the packet gets through.

ACKs are not HMAC-authenticated because an ACK carries no semantic payload, a forged or injected ACK can at most cause the sender to believe a packet was delivered when it was not. The sender would then stop retransmitting, but the receiver's in-order delivery layer will eventually detect the gap (the next packet would be out of sequence) and the missing packet would be retransmitted at the stubborn layer.

### Layer 4: In-Order Delivery

The receiver maintains a `nextExpectedSeq` counter per sender port and an `outOfOrderMessages` buffer keyed by `(senderPort, seqNum)`. When a packet arrives:

1. If `seqNum == nextExpectedSeq`: deliver immediately to the application, increment `nextExpectedSeq`, then drain the buffer for any already-stored packets that are now in sequence.
2. If `seqNum > nextExpectedSeq`: store in `outOfOrderMessages`. Send an ACK (so the sender stops retransmitting), but do not deliver yet.
3. If `seqNum < nextExpectedSeq`: the packet is a duplicate. Send an ACK and discard.

This provides exactly-once, in-order delivery to the application layer, which is what the consensus protocol requires.

### Sequence Number Scope

Sequence numbers are per pairwise connection (per `(localPort, remotePort)` pair), not global. Each side maintains independent sequence number counters for its own outgoing messages to each peer. There is no global ordering constraint between messages from different senders.

---

## Block Hashing and Determinism

Every replica independently rebuilds any proposed block from the transaction list and computes the block hash. Consensus on a block hash is therefore equivalent to consensus on the execution result, without requiring the leader to be trusted to report the correct world state.

The block hash is `SHA-256` over a deterministic binary serialization:

```
hash_input = parentBlockHash_bytes             (UTF-8, no length prefix)
           + blockNumber_int64
           + for each tx in transactions:
               senderPort_int32
               + from_address_bytes             (hex string, no length prefix)
               + to_address_bytes               (hex string, no length prefix)
               + value_int64
               + gasLimit_int64
               + gasPrice_int64
               + nonce_int32
               + data_bytes                     (no length prefix)
               + signature_bytes                (no length prefix)
           + for each account in worldState (sorted by address):
               address_bytes                    (hex string, no length prefix)
               + balance_int64
               + nonce_int64
               + code_bytes                     (no length prefix)
               + for each (key, value) in storage (sorted by key):
                   key_bytes + value_bytes       (no length prefix)
```

The sorting requirements (accounts by address, storage by key) are what make the serialization deterministic across replicas running independent JVM instances with different HashMap iteration orders.

---

## Block Store and Pruning

The `BlockStore` maintains a `ConcurrentHashMap<String, Block>` keyed by block hash. Blocks form a tree rooted at the genesis block, with each block's `parentBlockHash` field linking to its parent.

`pruneToLockedSubtree(lockedBlockHash)` removes all entries where the block is not in the ancestry chain of the locked block. The retained set is: the locked block itself, the genesis block, and every block on the path from genesis to the locked block. All sibling branches and any blocks not on this path are discarded.

This pruning is called in both `handleCommitLeader` (when the leader forms the precommitQC and sets its own lockedQC) and `handleCommitReplica` (when a replica receives the commit message). The consequence is that after every successful commit phase, the block store contains at most the linear ancestry chain from genesis to the current locked block, plus any blocks in the current view's proposal subtree that haven't been pruned yet.

The catch-up protocol (`handleCatchUp`) serves missing blocks by returning the chain of blocks between the requester's lockedQC and the requested block hash. This works correctly only because the server's block store always contains its full locked subtree — pruning never removes blocks that are ancestors of the locked block.

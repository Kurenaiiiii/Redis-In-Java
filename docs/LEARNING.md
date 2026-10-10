# Learning guide

What this project teaches, in the order you will meet the ideas while reading the code.

## Glossary

- **NIO selectors.** The OS can watch thousands of sockets and wake your program only when one has data ready. Java's `Selector` wraps that facility; our whole server is one thread around one selector, which is why 10,000 idle connections cost almost nothing.
- **Non-blocking I/O.** Sockets configured so `read`/`write` return immediately with whatever is available instead of waiting. Nothing ever sleeps mid-command, so the single loop thread is never stuck — partial data just waits in a per-connection buffer for the next readable event.
- **RESP.** The Redis wire protocol: a first byte (`+ - : $ *`) declares the type, lengths prefix bulk data. Because lengths are explicit, any byte sequence is a legal value, and because a parser can tell "incomplete" from "invalid", partial TCP reads are safe to buffer.
- **Pipelining.** Sending many commands without waiting for replies. Throughput jumps because round trips disappear; the server side needs no special case beyond "parse every complete command in the buffer". Our benchmark gap (12% of real Redis pipelined) is purely per-command CPU cost.
- **AOF.** Append-Only File: a log of every mutating command in RESP form. Replay rebuilds the dataset. Durability is a dial (`always`/`everysec`/`no`), not a boolean, trading fsync frequency against speed.
- **Lazy vs active expiry.** Lazy expiry checks the deadline on every access (correctness, zero background cost); active expiry periodically samples keys (reclaims memory nobody reads). Real systems pair them because neither alone suffices: lazy leaks unread keys, active alone would need a full scan.
- **Skip list.** A sorted linked list with random "express lanes" giving O(log n) search/insert/delete, plus span counters that make rank queries logarithmic. Simpler to implement correctly than a balanced tree, which is why Redis chose it for sorted sets.
- **Optimistic locking.** `WATCH` records key versions and `EXEC` proceeds only if nothing moved — no locks are held while the client thinks. It assumes conflicts are rare and retries are cheap, the opposite of pessimistic locking.
- **LRU (approximate).** Real LRU needs a global ordered structure touched on every access. Sampling 5 random keys and evicting the stalest approximates it in O(1) and is statistically close enough that Redis — and this project — ship it.

## Interview questions and answers

**1. Why is the server single-threaded, and what breaks if a command is slow?**
One thread means commands run to completion with no interleaving, so the keyspace needs no locks. The cost: any slow command (e.g. our synchronous `BGREWRITEAOF` on a huge dataset, or `KEYS *`) stalls every other client until it finishes. Real Redis has the same property, which is why it rewrites in a child process.

**2. How does one thread serve thousands of connections?**
Via the `Selector`: the OS reports exactly which sockets are readable/writable, so the loop only visits sockets with work. Idle connections cost a `Connection` object and a kernel entry — no thread, no polling.

**3. What happens when a command arrives split across two TCP packets?**
The first read feeds bytes into `RespParser`, which parses what it can and keeps the tail buffered, returning no value. The second read appends, the parse completes, and dispatch proceeds. `ConnectionTest.splitPacketCommandStillParses` pins this with a 150 ms gap between halves.

**4. How does pipelining work here, and why did the benchmark show a gap?**
The parser drains *all* complete values per read, and dispatch loops over them — no special case. Pipelined `SET` runs at 144k req/s vs real Redis at 1.16M: without network round trips, per-command CPU dominates (allocations, `HashMap` ops, an LRU map write on every read). Closing that gap is a CPU-optimization task, not an architecture change.

**5. Why does every read write to a `HashMap`?**
`KeySpace.get` refreshes the key's last-access timestamp for LRU inside the stored `Entry`, which means a map `put` per read. It is the main self-inflicted cost in the benchmark and a deliberate simplification documented in the code.

**6. How is binary safety achieved without byte-array map keys?**
Keys and values travel as ISO-8859-1 text: every byte 0–255 maps to exactly one char and back reversibly. `ConnectionTest.binarySafeRoundTrip` stores all 256 byte values through `SET`/`GET`. The trade-off is that strings are not validated UTF-8.

**7. Why a skip list instead of a `TreeMap` for sorted sets?**
Fidelity to real Redis, and rank queries: spans on forward pointers give O(log n) `ZRANK`/range-by-index, which a tree needs extra augmentation for. Correctness is carried by 9,000 randomized operations checked against a `TreeMap` reference in `SkipListTest`.

**8. How do equal scores order?**
By member bytes, lexicographically — verified byte-identical against real Redis (`ZADD myz 2 b 2 c` ranks `b` first). The comparator in `SkipList.compare` implements exactly this.

**9. What makes `EXEC` atomic?**
Nothing can interleave: the queue runs to completion on the loop thread, and no other code touches the keyspace concurrently. Atomicity falls out of the architecture rather than a lock.

**10. What is the difference between queue-time and run-time transaction errors?**
Queue-time errors (unknown command, wrong arity) dirty the transaction and `EXEC` answers `EXECABORT` without running anything. Run-time errors (e.g. `INCR` on a string) are collected as error elements and the rest of the queue still runs — matching real Redis.

**11. How does `WATCH` detect changes from another client?**
Every mutation bumps a per-key version counter — including deletes, expiries, and the watching client's own writes. `EXEC` compares snapshots to current versions and returns nil on any mismatch.

**12. How are pub/sub messages delivered without threads?**
`PUBLISH` looks up subscribers and the server appends the formatted message straight into each subscriber's existing write buffer, flushing opportunistically. Publisher and subscriber are both just connections on the same loop iteration.

**13. Why can a subscribed connection only run pub/sub commands?**
That mirrors real Redis: once subscribed, the connection's reply stream is reserved for pushed messages, so normal request/reply commands would corrupt the client's parsing. The dispatcher enforces the allowlist before lookup.

**14. What exactly is logged to the AOF, and why argv rather than results?**
The original argument strings of every successful write command. Replaying argv re-executes identical semantics, which sidesteps an entire class of re-serialization bugs (e.g. float formatting). `EXEC` logs its queued commands individually, like real Redis.

**15. What do the three fsync policies cost?**
`always` pays an fsync per command (durable, slow); `everysec` hands data to the OS per command and fsyncs on the 1-second tick (≤1 s loss window); `no` never fsyncs (OS decides). A crash-torn tail replays what parsed and warns.

**16. How does the rewrite stay crash-safe?**
It writes a temp file and atomically renames it over the live log; readers never see a half-written base. Because the rewrite runs on the loop thread, no writes can interleave mid-rewrite — at the cost of blocking serving.

**17. How does automatic rewrite decide to fire?**
Each second the loop checks `size >= min-size` and growth since the last base `>= percentage` (defaults 64 MB / 100%), the same two knobs as real Redis (`auto-aof-rewrite-percentage/min-size`, both `CONFIG SET`-able).

**18. How does approximate LRU eviction work, and when does it run out?**
Before each write command, if usage exceeds `maxmemory`, up to 10 rounds sample 5 keys and drop the stalest. With `noeviction` the write fails with `OOM` instead. Sampling keeps eviction O(1); pathological access patterns can protect garbage while evicting hot keys — the documented price of approximation.

**19. What did the Nagle/delayed-ACK incident teach?**
The test client did several small writes per command with Nagle enabled; the server couldn't reply until the full request arrived, and the ACK delay stacked to 40 ms per command — a 100x slowdown visible only in the 500-command persistence test. Real clients set `TCP_NODELAY`; the test client now does too, and the server sets it on accepted sockets.

**20. If you had one more week, what would you change?**
Background the AOF rewrite (fork or worker thread with a write buffer), add RDB snapshots, then profile the pipelined path: kill the per-read LRU map write (e.g. coarse timestamps), pool reply buffers, and add command fast paths — in that order, measuring with `scripts/benchmark.sh` after each step.

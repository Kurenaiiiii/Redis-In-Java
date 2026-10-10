# Architecture

A plain-English tour of how Redis in Java works. No prior systems knowledge assumed — every idea is introduced where it is used.

## The whole system in one picture

```
                    +------------------redis-cli / any client------------------+
                    |  speaks RESP2 over TCP (port 6379 by default)           |
                    +---------------------------+-----------------------------+
                                                | TCP bytes
                                                v
+----------------------------------------------------------------------------------+
|  EVENT LOOP (one thread, RedisServer)                                             |
|                                                                                  |
|   Selector -- watches all sockets at once, wakes only for sockets with work      |
|      |                                                                           |
|      +-- ACCEPT --> new Connection (read buffer, write queue, txn/pubsub state)  |
|      +-- READ ----> append bytes to Connection buffer, parse complete commands   |
|      +-- DISPATCH > registry lookup --> Command.execute --> reply bytes queued   |
|      +-- WRITE ---> flush queued bytes when the socket is writable               |
|      +-- TICK ----> every 100ms: active expiry ... every 1s: fsync/timeouts      |
|                                                                                  |
|   CommandContext handed to every command:                                        |
|   +----------+  +--------+  +-----+  +--------+  +----------+  +----------+     |
|   | KeySpace |  | PubSub |  | AOF |  | Config |  | Registry |  |  Server  |     |
|   | dataset  |  | broker |  | log |  | values |  | (EXEC)   |  | (time)   |     |
|   +----------+  +--------+  +-----+  +--------+  +----------+  +----------+     |
+----------------------------------------------------------------------------------+
```

One thread does everything. That is the whole trick real Redis uses: because commands run to completion without interruption, there are no locks, no races, no thread-per-connection overhead. The price is that a slow command stalls everyone — which is why `BGREWRITEAOF` blocking the loop is listed as a limitation.

## The event loop

A server has hundreds of mostly-idle connections. Reading them one thread each wastes memory; polling them in a loop wastes CPU. The operating system offers a third way: a **selector**. You hand it all your sockets and ask "wake me when any of these can be read from or written to". Java exposes this as `java.nio.channels.Selector`.

Our loop (`RedisServer.start`) repeats four steps:

1. `selector.select(100)` — sleep until some socket has work, or 100 ms pass.
2. Accept new connections (non-blocking `ServerSocketChannel`).
3. Read available bytes into each connection's buffer; parse every complete command (there may be several — that is pipelining); run each; queue reply bytes; try sending immediately, else ask the selector to wake us when writable.
4. Run periodic chores (see expiry and AOF below).

`select(100)` doubles as the timer: even with zero traffic the loop wakes ten times a second, so chores never starve.

## RESP: the wire protocol, with raw bytes

Clients and server talk RESP2: a tiny text protocol where the first byte says what follows.

```
Client sends "SET greeting hello":

    *3\r\n            array of 3
    $3\r\nSET\r\n     bulk string "SET"
    $8\r\ngreeting\r\n
    $5\r\nhello\r\n

Server replies:

    +OK\r\n          simple string
```

Other replies: `-ERR message\r\n` (error), `:42\r\n` (integer), `$-1\r\n` (nil bulk), `*-1\r\n` (nil array). Bulk strings carry an explicit length, so any byte — including `\r\n` and zero bytes — survives inside values.

Two properties shape the parser (`RespParser`):

- **Partial reads.** TCP may split `*3\r\n$3\r\nSET...` across packets. The parser keeps unparsed bytes in a per-connection buffer and returns "nothing complete yet" until the rest arrives.
- **Pipelining.** One packet may hold five commands. The parser drains *all* complete values, and the loop dispatches each in order.

The parser never allocates per byte: it scans one growable buffer with start/end cursors. Malformed input raises `ProtocolException`; the server answers with an error and closes the connection, like real Redis.

## A command's journey from socket to reply

1. Bytes land in `Connection`'s buffer via a non-blocking read.
2. `drain()` yields RESP arrays; each must be bulk strings or it is a protocol error.
3. The dispatcher lowercases `argv[0]` and looks it up in `CommandRegistry` — a `TreeMap` filled at startup, so adding a command is one class plus one `register` line, never a switch.
4. Three gates: subscribed-mode allowlist, transaction queueing (`QUEUED`), arity check.
5. `command.execute(ctx, argv)` runs. Commands are tiny: `GetCommand` is ~15 lines. Shared checks (type, integer parsing) live in `BaseCommand`.
6. If it is a write command and the reply is not an error, the original `argv` is appended to the AOF (logging argv, not results, replays byte-identically).
7. If `maxmemory` is set, the pre-write check evicts or fails with OOM.
8. Reply bytes are queued; a direct write is attempted first for latency, the remainder flushes on the next writable event. `QUIT` replies `+OK` and closes after draining.

## Expiry: lazy plus active

Each key stores an absolute deadline (epoch millis, -1 = persistent).

- **Lazy:** every read checks the deadline and drops the key if it passed. Reads never return ghosts, and no timer is needed for correctness.
- **Active:** every 100 ms the loop samples ~20 keys and deletes the expired ones, so abandoned keys with no reads still free memory. If a whole sample expired, it samples again (up to 3 rounds).

`EXPIRE` with a past timestamp deletes immediately and returns 1 (verified against real Redis). `SET` clears the TTL; `INCR`/`APPEND` preserve it — both verified, because getting this wrong corrupts real workloads.

## The skip list

Sorted sets need ordering by (score, member) plus rank queries ("what is 5th?"). A balanced tree works, but real Redis uses a **skip list**, so we do too (`store/SkipList.java`).

Picture a sorted linked list with express lanes:

```
level 2:  HEAD -------------------------> [3:c] ---------> NIL
level 1:  HEAD ---------> [1:a] ---------> [3:c] -> [4:d] -> NIL
level 0:  HEAD -> [1:a] -> [2:b] -> [3:c] -> [4:d] -> NIL
```

Each node flips a coin (p=0.25) for how many levels it joins. Search descends from the top lane, so average cost is O(log n). Every forward pointer carries a **span** (how many level-0 nodes it jumps over); summing spans while descending gives a member's rank in O(log n) instead of walking. Equal scores order members lexicographically — `ZADD z 2 b 2 c` ranks `b` before `c`, matching real Redis byte for byte.

A `HashMap` member→score sits beside the list for O(1) `ZSCORE` and re-score deletes. Correctness is enforced by randomized tests: 9,000 mixed inserts/deletes/ranks against a `TreeMap` reference across three seeds.

## Transactions

`MULTI` flips a per-connection flag; further commands are stored, not run, and answered `+QUEUED`. `EXEC` runs the queue back-to-back — atomic by construction, since the single thread cannot interleave anything else.

Two safety nets mirror real Redis:

- **Queue-time errors dirty the transaction.** Unknown commands and wrong arity are rejected immediately *and* mark the transaction; `EXEC` then answers `EXECABORT` and runs nothing.
- **Optimistic locking.** `WATCH` snapshots key versions (a counter bumped on every mutation, including expiry and other clients' writes — even your own). If any version moved before `EXEC`, it returns nil instead of running. `DISCARD`/`UNWATCH`/successful `EXEC` clear the watch.

Errors *during* `EXEC` do not abort: they are collected as reply elements, exactly like real Redis.

## Pub/sub

`PubSub` maps channels→subscriber connections and connections→glob patterns. `PUBLISH` walks both (patterns via the same glob matcher as `KEYS`) and hands each hit to the server, which enqueues a `["message", channel, payload]` (or 4-element `pmessage`) reply directly into the subscriber's write buffer — all on the loop thread, so delivery needs no queue between threads.

A subscribed connection enters a restricted mode: only `(P)SUBSCRIBE`, `(P)UNSUBSCRIBE`, `PING` (array form) and `QUIT` are accepted, matching real Redis. Unsubscribing from everything returns the connection to normal mode. Published messages are fire-and-forget and are *not* written to the AOF.

## AOF persistence and rewrite

Every successful write command is appended in RESP form — the log *is* the protocol, so replay is just "execute each command with logging off". `Main` replays before listening; a torn tail (crash mid-write) replays what parsed and warns.

- `appendfsync always`: fsync per command. Slowest, loses nothing.
- `everysec` (default): hand data to the OS per command, fsync once a second on the loop tick. Loses ≤1 s on a crash.
- `no`: never fsync; the OS decides.

`BGREWRITEAOF` compacts the log: it snapshots each live key into its minimal recreating command (`SET`, `RPUSH`, `HSET`, `SADD`, `ZADD`, plus `PEXPIREAT` to pin exact TTLs), writes a temp file, and atomically renames it over the old one. The loop's 1-second tick also triggers a rewrite automatically when growth since the last base exceeds `auto-aof-rewrite-percentage` (default 100%) past `auto-aof-rewrite-min-size` (default 64 MB).

Trade-off, stated plainly: real Redis rewrites in a child process; ours blocks the loop. Rewriting 100 keys takes milliseconds; rewriting 100 million would stall serving. That is the price of no fork, and it is documented in Limitations.

## Eviction

`usedMemoryBytes` is maintained on every put/delete from per-value `estimateSize` (payload plus constant overheads). When a write would exceed `maxmemory`:

- `noeviction` (default): the command fails with `OOM command not allowed...`, reads still work.
- `allkeys-lru`: sample 5 random keys, evict the least-recently-used (each key carries a last-access timestamp refreshed on read *and* write), repeat up to 10 times, then run the command regardless — the same approximate algorithm real Redis uses. Sampling keeps eviction O(1) instead of tracking a global LRU list.

## Main design decisions and their trade-offs

| Decision | Why | Cost |
|----------|-----|------|
| Single-threaded loop, no locks | Matches Redis; eliminates concurrency bugs | One slow command stalls all; no multi-core use |
| Strings as ISO-8859-1 text | Binary-safe with plain `String` map keys | Not UTF-8-validated; documented |
| Hand-written skip list | The point of the project; real Redis does this | More code than `TreeMap`, carried by tests |
| Log original argv to AOF | Replays byte-identically, no re-serialization bugs | Commands must keep argv replay-pure (they do) |
| Synchronous rewrite | No fork, no copy-on-write complexity | Blocks the loop (documented) |
| Zero runtime dependencies | Small jar (146 KB), nothing to audit | Hand-rolled logging/glob instead of libraries |
| `LinkedHashMap`/`LinkedHashSet` iteration order | Deterministic `HGETALL`/`SMEMBERS` for tests | Real Redis order is undefined; clients must not rely on order either way |
| Subscribe confirmations sent directly | One message per channel without complicating the reply path | `SUBSCRIBE` inside `MULTI` yields a plain `OK` in the `EXEC` array instead of the confirmation (extreme edge, noted) |

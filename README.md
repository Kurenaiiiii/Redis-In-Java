# Redis in Java

A Redis-compatible in-memory data store written in Java 21: real RESP2, a single-threaded NIO event loop, and zero runtime dependencies. Works with the official `redis-cli` and `redis-benchmark` unchanged.

[![CI](https://github.com/Kurenaiiiii/Redis-In-Java/actions/workflows/ci.yml/badge.svg)](https://github.com/Kurenaiiiii/Redis-In-Java/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Tests](https://img.shields.io/badge/tests-85%20passing-brightgreen.svg)](#testing)

> **Independence note:** this is an independent educational implementation written from the public Redis protocol and behavior. It is not affiliated with or endorsed by Redis Ltd.

## Table of contents

- [What it is](#what-it-is)
- [Features](#features)
- [Quick start](#quick-start)
- [Usage](#usage)
- [Supported commands](#supported-commands)
- [Benchmarks](#benchmarks)
- [Docker](#docker)
- [Configuration](#configuration)
- [Testing](#testing)
- [Limitations](#limitations)
- [Roadmap](#roadmap)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [License](#license)

## What it is

Redis in Java speaks enough of the Redis protocol (RESP2) to be a drop-in playground for learning how Redis works inside: the event loop, the protocol parser, the skip list behind sorted sets, transactions, pub/sub, AOF persistence and LRU eviction. The architecture mirrors real Redis on purpose — one thread, non-blocking I/O, no locks — so the lessons transfer.

## Features

- Real RESP2 with partial-read buffering and pipelining; verified against `redis-cli` 8.x
- Single-threaded NIO event loop (`Selector` + `ServerSocketChannel`), no thread-per-connection
- Strings, lists, hashes, sets, sorted sets (hand-written skip list, no `TreeMap`)
- Key expiry: lazy on access plus an active sweep every 100 ms
- Transactions (`MULTI`/`EXEC`/`DISCARD`, `WATCH` optimistic locking, `EXECABORT`)
- Pub/sub with channel and glob-pattern subscriptions
- AOF persistence: `always`/`everysec`/`no` fsync policies, replay on startup, `BGREWRITEAOF` compaction plus automatic rewrite on growth
- `maxmemory` with sampled approximate-LRU eviction (`allkeys-lru`) and `noeviction`
- Production touches: graceful SIGTERM shutdown, `maxclients`, idle `timeout`, leveled logging, `--help`
- Zero runtime dependencies (JUnit 5 for tests only)

## Quick start

```bash
mvn -B package -Puberjar        # builds target/redis-in-java-0.1.0.jar
java -jar target/redis-in-java-0.1.0.jar --port 6379
redis-cli ping                  # PONG
```

Requirements: a full JDK 21+ (`javac` included — a JRE alone cannot compile) and Maven 3.9+.

## Usage

Start the server, then use the official CLI exactly as with real Redis:

```
$ redis-cli -p 6399 set leaderboard:game1 "season 9"
OK
$ redis-cli -p 6399 zadd leaderboard 1500 alice 2300 bob 1800 carol
(integer) 3
$ redis-cli -p 6399 zrange leaderboard 0 -1 withscores
1) "alice"
2) "1500"
3) "carol"
4) "1800"
5) "bob"
6) "2300"
$ redis-cli -p 6399 hset user:42 name ada score 2300
(integer) 2
$ redis-cli -p 6399 hgetall user:42
1) "name"
2) "ada"
3) "score"
4) "2300"
$ redis-cli -p 6399 expire user:42 3600
(integer) 1
$ redis-cli -p 6399 ttl user:42
(integer) 3599
```

Transactions and pub/sub work too:

```
$ redis-cli -p 6399 multi
OK
$ redis-cli -p 6399 set a 1
QUEUED
$ redis-cli -p 6399 incr n
QUEUED
$ redis-cli -p 6399 exec
1) OK
2) (integer) 1
```

```
$ redis-cli -p 6399 subscribe news
1) "subscribe"
2) "news"
3) (integer) 1
1) "message"
2) "news"
3) "hello"
```

Persistence across restarts:

```
$ java -jar target/redis-in-java-0.1.0.jar --appendonly yes
$ redis-cli set greeting hello
OK
$ redis-cli bgrewriteaof
Background append only file rewriting started
# restart the server; the dataset is replayed from appendonly.aof
$ redis-cli get greeting
"hello"
```

## Supported commands

| Group        | Commands |
|--------------|----------|
| Connection   | `PING` `ECHO` `QUIT` `COMMAND` (minimal: name+arity, `COUNT`) `INFO` (basic sections) `SELECT` (db 0 only) `CONFIG GET`/`SET` (supported settings only) |
| Strings      | `SET` (`EX` `PX` `NX` `XX`) `GET` `DEL` `EXISTS` `INCR` `DECR` `INCRBY` `APPEND` `STRLEN` `MSET` `MGET` |
| Keys         | `EXPIRE` `PEXPIRE` `PEXPIREAT` `TTL` `PTTL` `PERSIST` `TYPE` `KEYS` (glob) `FLUSHALL` `DBSIZE` |
| Lists        | `LPUSH` `RPUSH` `LPOP` `RPOP` (optional count) `LLEN` `LRANGE` |
| Hashes       | `HSET` `HGET` `HDEL` `HGETALL` `HEXISTS` |
| Sets         | `SADD` `SREM` `SMEMBERS` `SISMEMBER` `SCARD` |
| Sorted sets  | `ZADD` `ZREM` `ZSCORE` `ZCARD` `ZRANK` `ZRANGE` (`WITHSCORES`) `ZRANGEBYSCORE` (`WITHSCORES` `LIMIT`, exclusive `(1` bounds, `inf`) |
| Transactions | `MULTI` `EXEC` `DISCARD` `WATCH` `UNWATCH` |
| Pub/Sub      | `SUBSCRIBE` `UNSUBSCRIBE` `PSUBSCRIBE` `PUNSUBSCRIBE` `PUBLISH` |
| Persistence  | `BGREWRITEAOF` |

Wrong-type access returns `WRONGTYPE`, bad arity returns the standard `ERR wrong number of arguments`, unknown commands return `ERR unknown command`. The server never crashes on bad input.

## Benchmarks

Measured with `scripts/benchmark.sh` (real `redis-benchmark` from Redis 8.10.2, 100k requests, 50 clients, localhost, Fedora 44):

| Test                 | Real Redis (req/s) | Redis in Java (req/s) | Ours / Real |
|----------------------|--------------------|----------------------:|------------:|
| SET                  | 85,179             |                81,169 | 95% |
| GET                  | 84,246             |                84,034 | 100% |
| LPUSH                | 82,713             |                84,746 | 102% |
| SET pipelined (P=16) | 1,162,791          |               143,885 | 12% |

Honest reading: single-shot commands are network-bound, so a Java NIO loop keeps up with C. Pipelining removes the round trip and exposes per-command CPU cost, where real Redis is ~8x faster — expected costs on our side are per-command allocation (parser buffers, reply objects), a `HashMap` write on every read for the LRU clock, and no command fast paths. That gap is the roadmap, not a surprise.

## Docker

```bash
docker build -t redis-in-java:0.1.0 .
docker run -p 6379:6379 redis-in-java:0.1.0
docker compose up --build   # persists the AOF in a volume
```

## Configuration

Flags (also visible via `--help`) and their `CONFIG SET` counterparts:

| Flag | Default | `CONFIG SET` name |
|------|---------|-------------------|
| `--port` | 6379 | — (startup only) |
| `--appendonly yes\|no` | no | `appendonly` |
| `--appendfsync always\|everysec\|no` | everysec | `appendfsync` |
| `--aof-file` | appendonly.aof | — (startup only) |
| `--maxmemory` (supports kb/mb/gb) | 0 (unlimited) | `maxmemory` |
| `--maxmemory-policy` | noeviction | `maxmemory-policy` (`noeviction`\|`allkeys-lru`) |
| `--maxclients` | 10000 | `maxclients` |
| `--timeout` | 0 (off) | `timeout` |
| `--loglevel` | info | `loglevel` |
| — | 100 | `auto-aof-rewrite-percentage` |
| — | 64mb | `auto-aof-rewrite-min-size` |

## Testing

```bash
mvn -B verify            # 85 tests: unit + live-server integration
./scripts/benchmark.sh   # needs redis-server/redis-benchmark on PATH (or .tools/bin)
```

Every command group has integration tests that boot the real server on an ephemeral port and speak RESP over a socket, including a 9,000-operation randomized skip-list check against a `TreeMap` reference.

## Limitations

- Single database (`SELECT 0` only), no RDB snapshots, no replication or clustering
- No blocking commands (`BLPOP`), no Lua, no keyspace notifications
- `BGREWRITEAOF` blocks the event loop instead of forking (fine for small datasets)
- Sorted-set extras missing: `ZADD` options (`NX`/`XX`/`GT`/`LT`/`CH`/`INCR`), `ZREVRANGE`, `ZRANK WITHSCORE`
- `COMMAND` returns name+arity only, not full command docs
- Key/value strings use ISO-8859-1 transparently — binary-safe, but not UTF-8-validated

## Roadmap

- Background (non-blocking) AOF rewrite
- RDB snapshots and `SAVE`/`BGSAVE`
- More sorted-set commands (`ZREVRANGE`, `ZRANGE` with `BYSCORE`/`REV`/`LIMIT`)
- Per-command fast paths to close the pipelined benchmark gap
- Replication (replica-of + RDB-less sync)

## Project structure

```
src/main/java/dev/redisinjava/
  Main.java                 arg parsing and wiring
  protocol/                 RESP2 parser, encoder, value types
  server/                   NIO event loop, connections, config
  command/                  Command interface, registry, one class per command
    connection/ strings/ keys/ lists/ hashes/ sets/ zsets/
    transactions/ pubsub/ persistence/
  store/                    keyspace, expiry, value types, skip list
  persistence/              AOF logger, flush policies, rewrite
  pubsub/                   channel and pattern subscriptions
  util/                     bytes, glob, logger
src/test/java/dev/redisinjava/  unit + live-server integration tests
scripts/benchmark.sh        redis-benchmark comparison vs real redis
docs/                       ARCHITECTURE.md, LEARNING.md
```

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — how it works, in plain English
- [docs/LEARNING.md](docs/LEARNING.md) — glossary plus 20 interview Q&As about this codebase
- [CONTRIBUTING.md](CONTRIBUTING.md) — build, test, add-a-command guide

## License

MIT — see [LICENSE](LICENSE).

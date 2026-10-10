# Contributing

## Build and test

Requirements: a full JDK 21+ (a JRE is not enough — compilation needs `javac`) and Maven 3.9+.

```bash
mvn -B verify              # compile + all 85 tests
mvn -B test -Dtest=SkipListTest   # one test class
./scripts/benchmark.sh     # compare against real redis (needs redis-server on PATH)
java -cp target/classes dev.redisinjava.Main --help
```

> If Maven fails with `release version 21 not supported`, your default `java` is a JRE without a compiler. Point `JAVA_HOME` at a full JDK, e.g. `export JAVA_HOME=/usr/lib/jvm/bellsoft-java25.x86_64`.

## Adding a command

1. Create one class in the right `command.*` subpackage implementing `Command` (extend `BaseCommand` for the error helpers). `argv` includes the command name at index 0.
2. Set `arity()` Redis-style: positive means exact count including the name, negative means minimum. Return `isWrite() == true` when the command mutates the dataset (it will then be AOF-logged automatically).
3. Register it with one line in `Commands.all()`.
4. Use `ctx.keySpace().get` (updates LRU) for reads, `put` (preserves TTL) vs `overwrite` (clears TTL) deliberately — see `SetCommand` vs `IncrBase`.
5. Return `WRONGTYPE` via `wrongType()` for type mismatches; never throw on bad input — every error must be a reply.
6. Add integration tests in the matching `*Test` class using `ServerHarness` (boots the real server on an ephemeral port). Run the full suite and check the new command with real `redis-cli` before committing.

## Commit style

Conventional commits, one concern per commit, no giant commits:

- `feat:` new commands or features
- `test:` new or fixed tests
- `docs:` documentation only
- `chore:` build, tooling, repo maintenance

## Reporting bugs

Use the bug report template (`.github/ISSUE_TEMPLATE/bug_report.yml`) and include the server log line, the exact `redis-cli` input/output, and whether real Redis behaves differently for the same input.

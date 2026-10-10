package dev.redisinjava.command.strings;

import dev.redisinjava.command.BaseCommand;
import dev.redisinjava.command.CommandContext;
import dev.redisinjava.protocol.RespValue;
import dev.redisinjava.util.Raw;
import java.util.List;

/**
 * SET key value [EX seconds|PX ms] [NX|XX]. Overwrites and clears any TTL,
 * like real Redis. NX/XX make it conditional; expiry options attach a TTL.
 */
public final class SetCommand extends BaseCommand {
    @Override
    public String name() {
        return "set";
    }

    @Override
    public int arity() {
        return -3;
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public RespValue execute(CommandContext ctx, List<String> argv) {
        String key = argv.get(1);
        String value = argv.get(2);
        Long expireMillis = null;
        boolean nx = false;
        boolean xx = false;

        for (int i = 3; i < argv.size(); i++) {
            switch (argv.get(i).toLowerCase()) {
                case "ex" -> {
                    Long seconds = i + 1 < argv.size() ? Raw.parseLong(argv.get(++i)) : null;
                    if (seconds == null) {
                        return notInteger();
                    }
                    if (seconds <= 0) {
                        return RespValue.err("ERR invalid expire time in 'set' command");
                    }
                    expireMillis = seconds * 1000;
                }
                case "px" -> {
                    Long millis = i + 1 < argv.size() ? Raw.parseLong(argv.get(++i)) : null;
                    if (millis == null) {
                        return notInteger();
                    }
                    if (millis <= 0) {
                        return RespValue.err("ERR invalid expire time in 'set' command");
                    }
                    expireMillis = millis;
                }
                case "nx" -> nx = true;
                case "xx" -> xx = true;
                default -> {
                    return syntaxError();
                }
            }
        }
        if (nx && xx) {
            return syntaxError();
        }

        boolean exists = ctx.keySpace().contains(key);
        if (nx && exists) {
            return RespValue.nilBulk();
        }
        if (xx && !exists) {
            return RespValue.nilBulk();
        }
        // overwrite drops any previous TTL, like real SET.
        ctx.keySpace().overwrite(key,
                new dev.redisinjava.store.RedisStringValue(Raw.encode(value)));
        if (expireMillis != null) {
            ctx.keySpace().setExpireAt(key, System.currentTimeMillis() + expireMillis);
        }
        return RespValue.ok();
    }
}

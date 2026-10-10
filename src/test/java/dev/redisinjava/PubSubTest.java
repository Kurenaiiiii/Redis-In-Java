package dev.redisinjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.redisinjava.ServerHarness.RedisError;
import dev.redisinjava.ServerHarness.TestClient;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end pub/sub tests with two live connections. */
class PubSubTest {
    private ServerHarness harness;
    private TestClient subscriber;
    private TestClient publisher;

    @BeforeEach
    void start() throws Exception {
        harness = new ServerHarness();
        subscriber = harness.client();
        publisher = harness.client();
    }

    @AfterEach
    void stop() throws Exception {
        subscriber.close();
        publisher.close();
        harness.close();
    }

    @Test
    void subscribePublishUnsubscribe() throws Exception {
        assertEquals(Arrays.asList("subscribe", "news", 1L),
                subscriber.cmd("SUBSCRIBE", "news"));
        assertEquals(1L, publisher.cmd("PUBLISH", "news", "hello"));
        assertEquals(Arrays.asList("message", "news", "hello"), subscriber.read());
        assertEquals(0L, publisher.cmd("PUBLISH", "nobody", "x"));
        assertEquals(Arrays.asList("unsubscribe", "news", 0L),
                subscriber.cmd("UNSUBSCRIBE", "news"));
        // back to normal mode: regular commands work again
        assertEquals("OK", subscriber.cmd("SET", "k", "v"));
    }

    @Test
    void patternSubscribe() throws Exception {
        assertEquals(Arrays.asList("psubscribe", "news.*", 1L),
                subscriber.cmd("PSUBSCRIBE", "news.*"));
        assertEquals(1L, publisher.cmd("PUBLISH", "news.tech", "item"));
        assertEquals(Arrays.asList("pmessage", "news.*", "news.tech", "item"),
                subscriber.read());
        assertEquals(0L, publisher.cmd("PUBLISH", "sports", "x"));
        assertEquals(Arrays.asList("punsubscribe", "news.*", 0L),
                subscriber.cmd("PUNSUBSCRIBE", "news.*"));
        assertEquals("PONG", subscriber.cmd("PING"));
    }

    @Test
    void subscribedModeRejectsNormalCommands() throws Exception {
        subscriber.cmd("SUBSCRIBE", "news");
        Object reply = subscriber.cmd("SET", "k", "v");
        assertInstanceOf(RedisError.class, reply);
        // but ping works in array form
        assertEquals(Arrays.asList("pong", ""), subscriber.cmd("PING"));
        assertEquals(Arrays.asList("pong", "hi"), subscriber.cmd("PING", "hi"));
        subscriber.cmd("UNSUBSCRIBE", "news");
    }

    @Test
    void multipleSubscribersAllReceive() throws Exception {
        TestClient second = harness.client();
        try {
            subscriber.cmd("SUBSCRIBE", "chan");
            second.cmd("SUBSCRIBE", "chan");
            assertEquals(2L, publisher.cmd("PUBLISH", "chan", "blast"));
            assertEquals(Arrays.asList("message", "chan", "blast"), subscriber.read());
            assertEquals(Arrays.asList("message", "chan", "blast"), second.read());
        } finally {
            second.close();
        }
    }
}

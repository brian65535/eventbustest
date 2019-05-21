import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.unit.*;
import io.vertx.ext.unit.report.ReportOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Reproducer to show the degraded performance of Vert.x eventbus in certain
 * situations, especially in clustered mode.
 *
 * The tests only instantiate a single Vertx at a time. But when it is created
 * with clusteredVertx, performance can be ~100x worse.
 *
 * The tester verticle used in this test is an echoer. A tester starts from
 * sending a message to an echo address. When a tester receives an echo, it
 * continues to send the next message. The metric is the number of round trips
 * made within a timespan.
 *
 * Test variables:
 *  - numInstances: number of deployed instances of Tester verticle
 *  - numTestEvents: number of simultaneous test events at a time
 *  - clusterMethod: which cluster manager to use when instantiate a Vert.x instance
 *  - useLocalConsumer: whether to use `localConsumer` or `consumer` when listening to an address
 *  - deliveryOptions: whether to use default `DeliveryOptions` or with `localOnly` set to true
 *  - deliveryMethod:
 *    - Send_Reply: typical send-reply to finish a round trip
 *    - Send_Send: a sender registers a consumer (or localConsumer) against a unique
 *                 reply address and sends the address in headers; a receiver, instead of
 *                 `reply` a message, `send` the reply message to the unique address in
 *                 the message header
 */
public class EventbusTest {

    static {
        BasicConfigurator.configure();
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.WARN);
        System.setProperty("vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
        System.setProperty("hazelcast.logging.type", "slf4j");
    }
    private static final Logger logger = LoggerFactory.getLogger(EventbusTest.class);
    private static final String MESSAGE = ":)";
    private static final String DURATION = "10";  // seconds

    private static final DeliveryOptions DEFAULT_DELIVERY = new DeliveryOptions();
    private static final DeliveryOptions LO_ONLY_DELIVERY = new DeliveryOptions().setLocalOnly(true);
    enum ClusterMethod { None, Hazelcast, Infinispan, Zookeeper }
    enum DeliveryMethod { Send_Reply, Send_Send }

    public static class TestVariables {
        int numInstances;
        int numTestEvents;
        ClusterMethod clusterMethod;
        boolean useLocalConsumer;
        DeliveryOptions deliveryOptions;
        DeliveryMethod deliveryMethod;
        TestVariables(int numInstances, int numTestEvents, ClusterMethod clusterMethod, boolean useLocalConsumer, DeliveryOptions deliveryOptions, DeliveryMethod deliveryMethod) {
            this.numInstances = numInstances;
            this.numTestEvents = numTestEvents;
            this.clusterMethod = clusterMethod;
            this.useLocalConsumer = useLocalConsumer;
            this.deliveryOptions = deliveryOptions;
            this.deliveryMethod = deliveryMethod;
        }
        @Override
        public String toString() {
            return "TestVariables: " +
                    "numInstances=" + numInstances +
                    ", numTestEvents=" + numTestEvents +
                    ", clusterMethod=" + clusterMethod +
                    ", useLocalConsumer=" + useLocalConsumer +
                    ", deliveryOptions=" + (deliveryOptions == DEFAULT_DELIVERY ? "default" : "localOnly") +
                    ", deliveryMethod=" + deliveryMethod;
        }
    }
    private TestVariables V(int numInstances, int numTestEvents, ClusterMethod clusterMethod, boolean useLocalConsumer, DeliveryOptions deliveryOptions, DeliveryMethod deliveryMethod) {
        return new TestVariables(numInstances, numTestEvents, clusterMethod, useLocalConsumer, deliveryOptions, deliveryMethod);
    }

    @DataProvider(name = "dataProvider")
    public Object[][] dataProvider() {
        return new Object[][] {
//                { V(4, 16, ClusterMethod.None, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.None, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.None, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.None, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.None, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.None, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.None, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.None, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Hazelcast, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Hazelcast, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Hazelcast, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Hazelcast, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Hazelcast, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Hazelcast, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Hazelcast, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Hazelcast, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Infinispan, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Infinispan, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Infinispan, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Infinispan, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Infinispan, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Infinispan, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
                { V(4, 16, ClusterMethod.Infinispan, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
                { V(4, 16, ClusterMethod.Infinispan, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.Zookeeper, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.Zookeeper, false, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.Zookeeper, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.Zookeeper, false, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.Zookeeper, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.Zookeeper, true, DEFAULT_DELIVERY, DeliveryMethod.Send_Send) },
//                { V(4, 16, ClusterMethod.Zookeeper, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Reply) },
//                { V(4, 16, ClusterMethod.Zookeeper, true, LO_ONLY_DELIVERY, DeliveryMethod.Send_Send) },
        };
    }

    @Test(invocationCount = 1, dataProvider = "dataProvider")
    public void test(TestVariables testVariables) {
        long duration = Long.parseLong(System.getProperty("duration", DURATION));
        TestSuite testSuite = TestSuite.create("");
        testSuite.test("", testContest -> runTest(testContest, duration, testVariables));
        Completion completion = testSuite.run(new TestOptions().addReporter(new ReportOptions().setTo("console")).setTimeout(10 * 60 * 1000L));
        completion.awaitSuccess();
    }

    private void runTest(TestContext testContext, long duration, TestVariables testVariables) {
        Async async = testContext.async();
        Future<Vertx> future = createVertx(testVariables);
        future.setHandler(res -> {
            Vertx vertx = res.result();

            final AtomicInteger numFinishedTesters = new AtomicInteger(0);
            final AtomicLong totalReceivedMessages = new AtomicLong(0);

            Handler<Message<Object>> handler = message -> {
                totalReceivedMessages.addAndGet((Long) message.body());
                if (numFinishedTesters.incrementAndGet() == testVariables.numTestEvents) {
                    logger.info("{}", totalReceivedMessages.get());
                    vertx.close(complete -> async.complete());
                }
            };
            if (testVariables.useLocalConsumer)
                vertx.eventBus().localConsumer("COLLECT", handler);
            else
                vertx.eventBus().consumer("COLLECT", handler);

            logger.info("{}", testVariables);
            for (int i = 0; i < testVariables.numTestEvents; i++) vertx.eventBus().send("TEST", duration, testVariables.deliveryOptions);
        });
    }

    private Future<Vertx> createVertx(TestVariables testVariables) {
        Future<Vertx> future = Future.future();
        Consumer<Vertx> runner = vertx -> {
            try {
                vertx.deployVerticle(() -> new Tester(testVariables), new DeploymentOptions().setInstances(testVariables.numInstances), res -> {
                    if (res.succeeded()) {
                        logger.info("Vertx started successfully");
                        future.complete(vertx);
                    } else {
                        logger.warn("Vertx failed to start", res.cause());
                        future.fail(res.cause());
                    }
                });
            } catch (Throwable t) {
                logger.warn("Vertx failed to start", t);
                future.fail(t);
            }
        };
        ClusterManager clusterManager;
        switch (testVariables.clusterMethod) {
            case Hazelcast: clusterManager = new HazelcastClusterManager(); break;
            case Infinispan: clusterManager = new InfinispanClusterManager(); break;
            case Zookeeper: clusterManager = new ZookeeperClusterManager(); break;
            default: clusterManager = null;
        }
        if (clusterManager != null) {
            Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager), res -> {
                if (res.succeeded()) {
                    Vertx vertx = res.result();
                    runner.accept(vertx);
                } else {
                    logger.warn("Vertx failed to start", res.cause());
                    future.fail(res.cause());
                }
            });
        } else {
            Vertx vertx = Vertx.vertx();
            runner.accept(vertx);
        }
        return future;
    }

    public static class Tester extends AbstractVerticle {
        private TestVariables testVariables;
        Tester(TestVariables testVariables) {
            this.testVariables = testVariables;
        }
        @Override
        public void start() {
            if (testVariables.useLocalConsumer) {
                vertx.eventBus().localConsumer("TEST", testHandler());
                switch (testVariables.deliveryMethod) {
                    case Send_Reply: vertx.eventBus().localConsumer("SEND_REPLY", message -> message.reply(message.body(), testVariables.deliveryOptions)); break;
                    case Send_Send: vertx.eventBus().localConsumer("SEND_SEND", message -> vertx.eventBus().send(message.headers().get("replyAddress"), message.body(), testVariables.deliveryOptions)); break;
                    default: throw new IllegalStateException();
                }
            } else {
                vertx.eventBus().consumer("TEST", testHandler());
                switch (testVariables.deliveryMethod) {
                    case Send_Reply: vertx.eventBus().consumer("SEND_REPLY", message -> message.reply(message.body(), testVariables.deliveryOptions)); break;
                    case Send_Send: vertx.eventBus().consumer("SEND_SEND", message -> vertx.eventBus().send(message.headers().get("replyAddress"), message.body(), testVariables.deliveryOptions)); break;
                    default: throw new IllegalStateException();

                }
            }
        }

        private Handler<Message<Object>> testHandler() {
            return message -> {
                final AtomicBoolean isFinished = new AtomicBoolean(false);
                final AtomicLong count = new AtomicLong(0);
                final long duration = (Long) message.body();
                final long startTime = System.nanoTime();

                switch (testVariables.deliveryMethod) {
                    case Send_Reply: sendReply(isFinished, count, duration, startTime); break;
                    case Send_Send: sendSend(message.toString(), isFinished, count, duration, startTime); break;
                    default: throw new IllegalStateException();
                }
            };
        }

        private void sendReply(AtomicBoolean isFinished, AtomicLong count, long duration, long startTime) {
            vertx.eventBus().send("SEND_REPLY", MESSAGE, testVariables.deliveryOptions, sendReplyHandler(isFinished, count, duration, startTime));
        }

        private Handler<AsyncResult<Message<Object>>> sendReplyHandler(AtomicBoolean isFinished, AtomicLong count, long duration, long startTime) {
            return res -> {
                if (res.succeeded() && MESSAGE.equals(res.result().body().toString())) count.incrementAndGet();
                else logger.warn("Incorrect reply message");

                if (System.nanoTime() - startTime >= duration * 1000000000L) {
                    if (!isFinished.getAndSet(true)) vertx.eventBus().send("COLLECT", count.get(), testVariables.deliveryOptions);
                } else {
                    sendReply(isFinished, count, duration, startTime);
                }
            };
        }

        private void sendSend(String replyAddress, AtomicBoolean isFinished, AtomicLong count, long duration, long startTime) {
            MessageConsumer<String> messageConsumer = testVariables.useLocalConsumer ? vertx.eventBus().localConsumer(replyAddress) : vertx.eventBus().consumer(replyAddress);
            messageConsumer.handler(sendSendHandler(messageConsumer, isFinished, count, duration, startTime));
            vertx.eventBus().send("SEND_SEND", MESSAGE, new DeliveryOptions(testVariables.deliveryOptions).addHeader("replyAddress", replyAddress));
        }

        private Handler<Message<String>> sendSendHandler(MessageConsumer<String> originalConsumer, AtomicBoolean isFinished, AtomicLong count, long duration, long startTime) {
            return message -> {
                originalConsumer.unregister();
                if (MESSAGE.equals(message.body())) count.incrementAndGet();
                else logger.warn("Incorrect reply message");

                if (System.nanoTime() - startTime >= duration * 1000000000L) {
                    if (!isFinished.getAndSet(true)) vertx.eventBus().send("COLLECT", count.get(), testVariables.deliveryOptions);
                } else {
                    sendSend(message.toString(), isFinished, count, duration, startTime);
                }
            };
        }
    }
}

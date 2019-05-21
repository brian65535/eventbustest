# eventbustest
Reproducer to show the degraded performance of Vert.x eventbus in certain situations, especially in clustered mode.

The tests only instantiate a single Vertx at a time. But when it is created with clusteredVertx, performance can be ~100x worse. ***For example, when it's clusteredVertx, localConsumer + send with default DeliveryOptions seems to be very bad performance.***

The tester verticle used in this test is an echoer. A tester starts from sending a message to an echo address. When a tester receives an echo, it continues to send the next message. The metric is the number of round trips made within a timespan (default 10 seconds).

Test variables:
- **numInstances**: number of deployed instances of Tester verticle
- **numTestEvents**: number of simultaneous test events at a time
- **clusterMethod**: which cluster manager to use when instantiate a Vert.x instance
- **useLocalConsumer**: whether to use `localConsumer` or `consumer` when listening to an address
- **deliveryOptions**: whether to use default `DeliveryOptions` or with `localOnly` set to true
- **deliveryMethod**:
  - *Send_Reply*: typical send-reply to finish a round trip
  - *Send_Send*: a sender registers a consumer (or localConsumer) against a unique reply address and sends the address in headers; a receiver, instead of `reply` a message, `send` the reply message to the unique address in the message header

# Test results:

ClusterManager | useLocalConsumer? | DeliveryOptions | DeliveryMethod | # of round trips
-------------- | ----------------- | --------------- | -------------- | ----------------:
No cluster | consumer | default | send-send | 11,515,381
No cluster | consumer | localOnly | send-reply | 10,983,083
No cluster | consumer | localOnly | send-send | 12,033,354
No cluster | localConsumer | default | send-reply | 10,695,797
No cluster | localConsumer | default | send-send | 11,129,014
No cluster | localConsumer | localOnly | send-reply | 10,246,302
No cluster | localConsumer | localOnly | send-send | 10,566,403
Hazelcast Vertx | consumer | default | send-reply | 4,369,049
Hazelcast Vertx | consumer | default | send-send | 162,708
Hazelcast Vertx | consumer | localOnly | send-reply | 4,487,854
Hazelcast Vertx | consumer | localOnly | send-send | 2,259,842
Hazelcast Vertx | localConsumer | default | send-reply | 499,722
Hazelcast Vertx | localConsumer | default | send-send | 279,049
Hazelcast Vertx | localConsumer | localOnly | send-reply | 4,339,591
Hazelcast Vertx | localConsumer | localOnly | send-send | 8,827,267
Infinispan Vertx | consumer | default | send-reply | 4,644,511
Infinispan Vertx | consumer | default | send-send | 394,958
Infinispan Vertx | consumer | localOnly | send-reply | 5,118,247
Infinispan Vertx | consumer | localOnly | send-send | 3,323,686
Infinispan Vertx | localConsumer | default | send-reply | 1,151,822
Infinispan Vertx | localConsumer | default | send-send | 766,255
Infinispan Vertx | localConsumer | localOnly | send-reply | 4,899,338
Infinispan Vertx | localConsumer | localOnly | send-send | 8,237,405
Zookeeper Vertx | consumer | default | send-reply | 3,564,268
Zookeeper Vertx | consumer | default | send-send | 10,623
Zookeeper Vertx | consumer | localOnly | send-reply | 5,145,771
Zookeeper Vertx | consumer | localOnly | send-send | 411,958
Zookeeper Vertx | localConsumer | default | send-reply | 208,365
Zookeeper Vertx | localConsumer | default | send-send | 112,768
Zookeeper Vertx | localConsumer | localOnly | send-reply | 4,835,042
Zookeeper Vertx | localConsumer | localOnly | send-send | 9,980,854

# To run the test
JDK8
```
$ mvn test -Dtest=EventbusTest#test
```
JDK9+ (to make Hazelcast happy)
```
$ mvn test -Dtest=EventbusTest#test -DargLine="--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"
```

package io.starwars.commons;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.RoutingContext;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class MicroServicesVerticleBase extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MicroServicesVerticleBase.class);

  protected ServiceDiscovery discovery;
  protected CircuitBreaker circuitBreaker;
  protected MetricsService metricsService;
  protected Set<Record> registeredRecords = new ConcurrentHashSet<>();
  protected HttpServer server;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    initCircuitBreaker();
    initServiceDiscovery();
    initMetricService();
  }

  @Override
  public void stop(Future<Void> stopFuture){
    logger.info("Stoping discovery services and circuit breaker");
    registeredRecords.stream().forEach(record -> {
      discovery.unpublish(record.getRegistration(), asyncResult -> {
        if (asyncResult.failed())
          logger.error("Fail to unpublish record {} - {}", asyncResult.cause(), record.getName(), record.getRegistration());
          stopFuture.fail(asyncResult.cause());
      });
    });

    if(circuitBreaker!=null)
      circuitBreaker.close();

    discovery.close();
    stopFuture.complete();
  }

  private void initCircuitBreaker(){

    var cbOptions = Optional
                      .ofNullable(config().getJsonObject("circuit-breaker"))
                      .orElse(new JsonObject());

    logger.debug("Initializing  circuit breaker with options :\n {}", Json.encodePrettily(cbOptions));
    circuitBreaker = CircuitBreaker.create(cbOptions.getString("name", "circuit-breaker"+ new Random().nextInt()), vertx,
      new CircuitBreakerOptions()
        .setMaxFailures(cbOptions.getInteger("max-failures", 5))
        .setTimeout(cbOptions.getLong("timeout", 10000L))
        .setFallbackOnFailure(cbOptions.getBoolean("fallback-failure", true))
        .setResetTimeout(cbOptions.getLong("reset-timeout", 30000L)));
  }

  private void initServiceDiscovery() {
    logger.debug("Initializing  service discovery");

    discovery = ServiceDiscovery.create(vertx);
  }

  private void initMetricService(){
    this.metricsService = MetricsService.create(vertx);
  }

  private void publish(Record record, Handler<AsyncResult<Void>> resultHandler) {
    logger.info("Publish record {} on service discovery", record.getName());

    if (discovery == null) {
      logger.error("Cannot found discovery service to publish record");
      resultHandler.handle(Future.failedFuture(new IllegalStateException("Cannot found discovery service")));
    }else {
      discovery.publish(record, ar -> {
        if (ar.succeeded()) {
          registeredRecords.add(record);
          logger.info("Service " + ar.result().getName() + " published");
        } else {
          resultHandler.handle(Future.failedFuture(ar.cause()));
        }
      });
    }
  }

  public void publishHttpEndpoint(String name, String host,int  port, Handler<AsyncResult<Void>> resultHandler) {
    logger.info("Unpublish record {} on service discovery", name);

    Record record = HttpEndpoint.createRecord(name, host, port, "/",
      new JsonObject().put("api.name", name));

    publish(record, resultHandler);
  }
  public void serverHealthCheckHandler(RoutingContext rc){
    var hc = HealthCheckHandler.create(vertx);
    hc.register("server-metrics",
      future -> future.complete(Status.OK(metricsService.getMetricsSnapshot(server))));
    hc.handle(rc);
  }


  public void metricsHealthCheckHandler(RoutingContext rc){
    var hc = HealthCheckHandler.create(vertx);
    hc.register("vertx-metrics",
      future -> future.complete(Status.OK(metricsService.getMetricsSnapshot(vertx))));
    hc.handle(rc);
  }

  public ServiceDiscovery getDiscovery() {
    return discovery;
  }

  public CircuitBreaker getCircuitBreaker() {
    return circuitBreaker;
  }

  public Set<Record> getRegisteredRecords() {
    return registeredRecords;
  }
}

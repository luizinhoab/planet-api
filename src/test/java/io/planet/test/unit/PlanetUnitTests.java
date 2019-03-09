package io.planet.test.unit;

import io.starwars.commons.HttpServerVerticleBase;
import io.starwars.planet.Planet;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.circuitbreaker.impl.CircuitBreakerImpl;
import io.vertx.core.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(VertxExtension.class)
public class PlanetUnitTests {

  private static final Logger logger = LoggerFactory.getLogger(PlanetUnitTests.class);
  private static final String PLANET_JSON = "{\"_id\":\"test\",\"name\":\"planet\",\"climate\":\"climate\",\"terrain\":\"terrain\",\"films-occurrence\":1}";


  private  Vertx vertx;
  private HttpServerVerticleBase httpServer;

  @BeforeEach
  protected void setUp(Vertx vertx, VertxTestContext testContext) {

    logger.info("Setup tests");

    this.vertx = vertx;
    var config = new JsonObject()
      .put("server", new JsonObject().put("host", "0.0.0.0").put("port", 8001))
      .put("circuit-breaker", new JsonObject().put("name", "circuit-breaker-test").put("max-failures", 5).put("timeout",500).put("reset-timeout",6000));
    var options = new DeploymentOptions().setConfig(config);

    this.httpServer = new HttpServerVerticleBase();
    httpServer.setRouter(Router.router(vertx));

    vertx.deployVerticle(httpServer, options, testContext.completing());
  }

  @BeforeEach
  protected void beforeTest(TestInfo testInfo){
    logger.info(String.format("About to execute [%s]", testInfo.getDisplayName()));
  }

  @AfterEach
  protected void afterTest(TestInfo testInfo) {
    logger.info(String.format("Finished executing [%s]", testInfo.getDisplayName()));
  }

  @AfterAll
  protected void tearDown(Vertx vertx){
    logger.info("After all tests, bye ...");
    vertx.close();
  }


  @DisplayName("test server starts")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(1)
  protected void startHttpServer(VertxTestContext testContext) {
    var client = WebClient.create(this.vertx);
    client.get(8001, "0.0.0.0", "/")
      .as(BodyCodec.string())
      .send(testContext.succeeding(response -> testContext.verify(() -> {
        assertEquals(200, response.statusCode());
        assertTrue(response.body().equals("up"));
        testContext.completeNow();
      })));
  }
  @DisplayName("Test initial state of circuit breaker")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(2)
  protected void initCircuitBreaker(VertxTestContext testContext){
    var cb = httpServer.getCircuitBreaker();
    assertTrue(cb.name().equals("circuit-breaker-test"), "Circuit breaker name");
    assertEquals( 0, cb.failureCount(),"Circuit breaker initial failures");
    testContext.completeNow();
  }
  @DisplayName("Test circuit breaker")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(3)
  protected void testCircuitBreaker(VertxTestContext testContext){

    var cb = httpServer.getCircuitBreaker();

    Future<Void> command1 = cb.execute(commandThatFails());
    Future<Void> command2 = cb.execute(commandThatWorks());
    Future<Void> command3 = cb.execute(commandThatWorks());
    Future<Void> command4 = cb.execute(commandThatFails());
    Future<Void> command5 = cb.execute(commandThatTimeout(100));

    CompositeFuture.join(command1, command2, command3, command4, command5)
      .setHandler(ar -> {
        assertTrue(cbMetrics().getString("state").equals(CircuitBreakerState.CLOSED.name()));
        assertEquals(3, cbMetrics().getInteger("totalErrorCount").intValue());
        assertEquals(2, cbMetrics().getInteger("totalSuccessCount").intValue());
        assertEquals(1, cbMetrics().getInteger("totalTimeoutCount").intValue());
        assertEquals(0, cbMetrics().getInteger("totalExceptionCount").intValue());
        assertEquals(2, cbMetrics().getInteger("totalFailureCount").intValue());
        assertEquals(5, cbMetrics().getInteger("totalOperationCount").intValue());
        assertEquals((2.0 / 5 * 100), cbMetrics().getInteger("totalSuccessPercentage").intValue());
        assertEquals((3.0 / 5 * 100), cbMetrics().getInteger("totalErrorPercentage").intValue());
        testContext.completeNow();
      });
  }

  @DisplayName("Test initial state of Service Discovery")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(4)
  protected void initServiceDiscovery(VertxTestContext testContext){
    var discovery = httpServer.getDiscovery();
    discovery.getRecords(new JsonObject(), event -> {
      if(event.succeeded() && event.result() != null){
        assertTrue(event.result().isEmpty());
      }else{
        testContext.failNow(event.cause());
      }
    });

    testContext.completeNow();
  }

  @DisplayName("Test Service Discovery")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(5)
  protected void testServiceDiscovery(VertxTestContext testContext){
    var discovery = httpServer.getDiscovery();
    var name = "test";
    Record newRecord = HttpEndpoint.createRecord(name, "0.0.0.0", 8001, "/", new JsonObject().put("api.name", name));


    discovery.publish(newRecord, testContext.completing());

    discovery.getRecord( new JsonObject().put("api.name", name), event -> {
      if (event.succeeded()) {
        var savedRecord = event.result();
        var location  = savedRecord.getLocation();
        var id = savedRecord.getRegistration();
        assertTrue(savedRecord.getName().equals(name));
        assertTrue(savedRecord.getType().equals("http-endpoint"));
        assertTrue(savedRecord.getStatus().name().equals("UP"));
        assertTrue(location.getString("host").equals("0.0.0.0"));
        assertEquals(8001,location.getInteger("port").intValue());
        discovery.unpublish(id,testContext.completing());
      }else{
        testContext.failNow(event.cause());
      }

      discovery.getRecords(new JsonObject(), async ->{
        if(async.succeeded()){
          assertTrue(async.result().isEmpty());
        }else{
          testContext.failNow(event.cause());
        }
        testContext.completeNow();
      });
    });

  }

  @DisplayName("Test planet json serialization")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(6)
  protected void serializeJsonPlanetModel(VertxTestContext testContext){
    Planet planet = new Planet();
    planet.setId("test");
    planet.setName("planet");
    planet.setClimate("climate");
    planet.setTerrain("terrain");
    planet.setOccurrenceFilms(1);

    var json = Json.encode(planet);
    var jsonPlanet = new JsonObject(json);

    assertTrue("test".equals(jsonPlanet.getString("_id")));
    assertTrue("planet".equals(jsonPlanet.getString("name")));
    assertTrue("climate".equals(jsonPlanet.getString("climate")));
    assertTrue("terrain".equals(jsonPlanet.getString("terrain")));
    assertEquals(1, jsonPlanet.getInteger("films-occurrence"));

    assertTrue(PLANET_JSON.equals(json));
    testContext.completeNow();

  }

  @DisplayName("Test planet json desserialization")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(7)
  protected void desserializeJsonPlanetModel(VertxTestContext testContext){
    Planet planet = Json.decodeValue(PLANET_JSON, Planet.class);

    assertTrue("test".equals(planet.getId()));
    assertTrue("planet".equals(planet.getName()));
    assertTrue("climate".equals(planet.getClimate()));
    assertTrue("terrain".equals(planet.getTerrain()));
    assertEquals(1, planet.getOccurrenceFilms());

    testContext.completeNow();


  }

  private Handler<Future<Void>> commandThatWorks() {
    return (future -> vertx.setTimer(4, l -> future.complete(null)));
  }

  private Handler<Future<Void>> commandThatFails() {
    return (future -> vertx.setTimer(4, l -> future.fail("expected failure")));
  }

  private Handler<Future<Void>> commandThatTimeout(int timeout) {
    return (future -> vertx.setTimer(timeout + 500, l -> future.complete(null)));
  }

  private JsonObject cbMetrics() {
    return ((CircuitBreakerImpl) httpServer.getCircuitBreaker()).getMetrics().toJson();
  }

}

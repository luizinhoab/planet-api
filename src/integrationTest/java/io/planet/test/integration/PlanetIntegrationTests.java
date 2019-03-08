package io.planet.test.integration;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.restassured.RestAssured;
import io.starwars.planet.PlanetAPIVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;



@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(VertxExtension.class)
public class PlanetIntegrationTests {

  private static final Logger logger = LoggerFactory.getLogger(PlanetIntegrationTests.class);

  private static final String PLANET_EXISTS = "{\"name\":\"Naboo\",\"climate\":\"temperate\",\"terrain\":\"grassy hills, swamps, forests, mountains\"}";

  private static final String PLANET_NOT_EXISTS = "{\"name\":\"aa\",\"climate\":\"climate\",\"terrain\":\"terrain\"}";

  private String idToDelete;

  private Vertx vertx;

  @BeforeEach
  protected void setUp(Vertx vertx, TestInfo testInfo, VertxTestContext testContext) {

    logger.info(String.format("About to execute [%s]", testInfo.getDisplayName()));
    var testPort = 8001;
    var testHost = "0.0.0.0";
    this.vertx = vertx;
    var config = new JsonObject()
      .put("server", new JsonObject().put("host", testHost).put("port", testPort))
      .put("circuit-breaker", new JsonObject().put("name", "circuit-breaker-test").put("max-failures", 5).put("timeout",500).put("reset-timeout",2000))
      .put("api", new JsonObject().put("name", "test-planet-api").put("swagger-file-path", "planet-api_v1.yaml"))
      .put("mongo", new JsonObject().put("connection_string","mongodb://tester:t3st3r@0.0.0.0:27016/starwars?authSource=starwars&authMechanism=SCRAM-SHA-1")
                                    .put("db_name","starwars").put("useObjectId",true))
      .put("swapi", new JsonObject().put("host","swapi.co").put("port",443).put("base-resource","/api/planets"));
    var options = new DeploymentOptions().setConfig(config);

    RestAssured.baseURI = "http://".concat(testHost);
    RestAssured.port = testPort;

    vertx.deployVerticle(PlanetAPIVerticle::new, options, testContext.completing());
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


  @DisplayName("Create planet")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(1)
  protected void createPlanet(VertxTestContext testContext) {

    this.idToDelete = given()
      .contentType(HttpHeaderValues.APPLICATION_JSON.toString())
      .body(new JsonObject(PLANET_EXISTS).getMap())
      .when().post("/planet")
      .then().statusCode(201).extract().jsonPath().getString("id");

    given()
      .contentType(HttpHeaderValues.APPLICATION_JSON.toString())
      .body(new JsonObject(PLANET_EXISTS).getMap())
      .when().post("/planet")
      .then().statusCode(409);

    given()
      .contentType(HttpHeaderValues.APPLICATION_JSON.toString())
      .body(new JsonObject(PLANET_NOT_EXISTS).getMap())
      .when().post("/planet")
      .then().statusCode(422);

    testContext.completeNow();
  }

  @DisplayName("Find by name")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(2)
  protected void findPlanetName(VertxTestContext testContext) {

    given()
      .when()
      .get("planet/search?name=".concat("Alderaan"))
      .then().statusCode(200)
      .and()
      .body("name", equalTo("Alderaan"))
      .and()
      .body("films-occurrence", equalTo(2));


    given()
      .when()
      .get("planet/search?name=".concat("unknown"))
      .then().statusCode(404);


    testContext.completeNow();
  }

  @DisplayName("Find by id")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(3)
  protected void findPlanetId(VertxTestContext testContext) {

    given()
      .when()
      .get("planet/".concat(idToDelete))
      .then()
      .body("name", equalTo("Naboo"))
      .and()
      .body("films-occurrence", equalTo(4))
      .and()
      .statusCode(200);

    given()
      .when()
      .get("planet/".concat("xxxxxxxxxxx"))
      .then().statusCode(404);

    testContext.completeNow();
  }

  @DisplayName("Delete planet")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(4)
  protected void deletePlanet(VertxTestContext testContext){

    given()
      .when()
      .delete("planet/".concat(this.idToDelete))
      .then().statusCode(202);

    given()
      .when()
      .delete("planet/".concat(this.idToDelete))
      .then().statusCode(404);

    testContext.completeNow();
  }

  @DisplayName("Test list planets")
  @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
  @Test
  @Order(5)
  protected void testListPlanets(VertxTestContext testContext){

      given()
        .when()
        .get("/planet")
        .then().statusCode(200)
        .and()
        .body("size", is(3))
        .and()
        .body("find {it.name=='Alderaan'}.films-occurrence", equalTo(2));

    testContext.completeNow();
  }

}

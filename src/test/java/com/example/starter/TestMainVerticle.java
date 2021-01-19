package com.example.starter;

import static io.restassured.RestAssured.*;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {

  @BeforeAll
  static void configureRestAssures() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext vtc) {
    vertx.deployVerticle(new MainVerticle(), vtc.succeedingThenComplete());
  }

  @Test
  void records() {
    given()
      .header("X-Okapi-Tenant", "fs09000000")
      .header("X-Okapi-Token", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtaWtoYWlsIiwidXNlcl9pZCI6IjkwNzJhMDNmLTEyNDgtNGM4ZC05MDkwLTRlMmM1ODQ0MzFjNyIsImlhdCI6MTYxMDk2OTg3MywidGVuYW50IjoiZnMwOTAwMDAwMCJ9.DHCPhN4mBiF_hihCCh4Yh2IxuvJ3yV40mEvmxh1NfKU")
      .header("X-Okapi-Url", "https://okapi-bugfest-honeysuckle.folio.ebsco.com:443")
    .when()
      .get("http://localhost:7596/oai/records?verb=ListRecords&metadataPrefix=marc21_withholdings")
    .then()
      .statusCode(200);
  }
}

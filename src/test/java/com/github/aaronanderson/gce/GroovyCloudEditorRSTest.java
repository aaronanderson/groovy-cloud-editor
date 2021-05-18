package com.github.aaronanderson.gce;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class GroovyCloudEditorRSTest {

    //@Test
    public void testScripts() {
        given()
                .when().get("/api/gce/scripts")
                .then()
                .statusCode(200)
                .body("status", is("ok"),
                        "scripts.size()", is(1),
                        "scripts[0].name", is("test.groovy"),
                        "scripts[0].lastModified", notNullValue());
    }

    private String buildScript(String path, boolean runMode) {
        JsonObjectBuilder request = Json.createObjectBuilder();
        request.add("name", path.substring(path.lastIndexOf("/") + 1));
        try {
            if (runMode) {
                request.add("contents", Base64.getEncoder().encodeToString(IOUtils.resourceToByteArray(path)));
            } else {
                request.add("script", IOUtils.resourceToString(path, Charset.defaultCharset()));
            }
        } catch (IOException e) {
            fail(e);
        }
        return request.build().toString();
    }

    //@Test
    public void testSuccessRun() throws IOException {
        given()
                .when()
                .body(buildScript("/scripts/run-success.groovy", true))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/run")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "result.value", is("Success"));
    }

    //@Test
    public void testInsecureRun() throws IOException {
        given()
                .when()
                .body(buildScript("/scripts/run-insecure.groovy", true))
                .contentType(ContentType.JSON)
                .post("/api/gce/run")
                .then()
                .statusCode(500)
                .log().body();

    }

    // @Test
    public void testValidate() throws IOException {
        given()
                .when()
                .body(buildScript("/scripts/validate-success.groovy", false))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/validate")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"));
    }

    //@Test
    public void testValidateError() throws IOException {
        given()
                .when()
                .body(buildScript("/scripts/validate-error.groovy", false))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/validate")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"),
                        "errors.size()", is(1),
                        "errors[0].sline", is(5),
                        "errors[0].eline", is(5),
                        "errors[0].scolumn", is(1),
                        "errors[0].ecolumn", is(2),
                        "errors[0].message", containsString("Unexpected input"));
    }

    private String buildHint(String path, int line, int ch, String sticky) {
        JsonObjectBuilder request = Json.createObjectBuilder();
        request.add("name", path.substring(path.lastIndexOf("/") + 1));
        try {
            request.add("script", IOUtils.resourceToString(path, Charset.defaultCharset()));
        } catch (IOException e) {
            fail(e);
        }
        request.add("line", line);
        request.add("ch", ch);
        request.add("sticky", sticky);
        return request.build().toString();
    }

    @Test
    public void testHintNewVariable() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-variable.groovy", 3, 14, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintNewVariablePartial() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-variable-partial.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintNewMethodParam() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-method-param.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintNewMethodParamPartial() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-method-param-partial.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintField() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-field.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintFieldPartial() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-field-partial.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintMethod() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }

    //@Test
    public void testHintMethodPartial() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-partial.groovy", 3, 18, "before"))
                .contentType(ContentType.JSON)
                .log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"));
        //        "result.value", is("Success"));
    }
}

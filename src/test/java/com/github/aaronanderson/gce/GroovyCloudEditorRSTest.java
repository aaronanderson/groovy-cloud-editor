package com.github.aaronanderson.gce;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
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
@QuarkusTest()
public class GroovyCloudEditorRSTest {

    @Test
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

    @Test
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

    @Test
    public void testInsecureRun() throws IOException {
        given()
                .when()
                .body(buildScript("/scripts/run-insecure.groovy", true))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/run")
                .then()
                //.log().body()
                .statusCode(500);
    }

    @Test
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

    @Test
    public void testValidateError() throws IOException {
        given()
                .when()
                .body(buildScript("/scripts/validate-error.groovy", false))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/validate")
                .then()
                .statusCode(200)
                //.log().body()
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
        if (sticky != null) {
            request.add("sticky", sticky);
        } else {
            request.addNull("sticky");
        }
        return request.build().toString();
    }

    @Test
    public void testHintVariable() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-variable.groovy", 2, 14, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(0));
    }

    @Test
    public void testHintVariableType() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-variable-type.groovy", 2, 16, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "java.lang.String()"), hasEntry("value", "String()"))));
        //hasEntry("entered[0]", 10), hasEntry("entered[1]", 0)
    }

    @Test
    public void testHintVariablePartial() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-variable-partial.groovy", 2, 18, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "java.lang.String()"), hasEntry("value", "String()"))));
        //"hints[0].entered[0]", is(10), "hints[0].entered[1]", is(4),

    }

    @Test
    public void testHintVariableConstructorParam() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-new-variable-constructor-param.groovy", 3, 23, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "java.lang.String(byte[] strBytes)"), hasEntry("value", "String(strBytes)"))));
    }

    @Test
    public void testHintMethodConstructor() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-constructor.groovy", 2, 44, "after"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "java.lang.String()"), hasEntry("value", "String()"))));
    }

    @Test
    public void testHintMethod() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method.groovy", 4, 5, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "concat(String str1) - String"), hasEntry("value", "concat(str1)"))));
    }

    @Test
    public void testHintMethodPartial() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-partial.groovy", 4, 8, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "length() - int"), hasEntry("value", "length()"))));
    }

    @Test
    public void testHintMethodStatic() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-static.groovy", 2, 14, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "valueOf(Object param) - String"), hasEntry("value", "valueOf(param)"))));
    }

    @Test
    public void testHintMethodParam() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-param.groovy", 5, 24, "after"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "concat(String str1) - String"), hasEntry("value", "concat(str1)"))));
    }

    @Test
    public void testHintMethodParam2() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-param2.groovy", 5, 24, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "concat(String str1) - String"), hasEntry("value", "concat(str1)"))));
    }

    @Test
    public void testHintMethodParam3() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-param3.groovy", 4, 20, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "delete(int 0, int len) - StringBuilder"), hasEntry("value", "delete(0, len)"))));
    }

    @Test
    public void testHintField() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-field.groovy", 4, 21, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "Comparator CASE_INSENSITIVE_ORDER"), hasEntry("value", "CASE_INSENSITIVE_ORDER"))));
    }

    @Test
    public void testImportSingle() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-import-single.groovy", 3, 33, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "createObjectBuilder() - JsonObjectBuilder"), hasEntry("value", "createObjectBuilder()"))));
    }

    @Test
    public void testImportMethod() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-import-method.groovy", 2, 4, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "abs(int param) - int"), hasEntry("value", "abs(param)"))));
    }

    @Test
    public void testImportMethod2() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-import-method2.groovy", 2, 4, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "abs(int param) - int"), hasEntry("value", "abs(param)"))));
    }

    @Test
    public void testHintFunction() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-function.groovy", 2, 3, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "add(int x, int y) - int"), hasEntry("value", "add(x, y)"))));
    }

    @Test
    public void testHintInnerClass() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-inner-class.groovy", 3, 17, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                .log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "Test()"), hasEntry("value", "Test()"))));
    }

}

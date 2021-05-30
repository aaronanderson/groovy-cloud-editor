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
                //.log().body()
                .statusCode(200)
                .body("status", is("ok"),
                        "scripts.size()", is(2),
                        "scripts[0].scriptId", is("1"),
                        "scripts[0].contents.name", is("test.groovy"),
                        "scripts[0].contents.lastModified", notNullValue(),
                        "scripts[0].contents.text", notNullValue());
    }

    private String buildScript(String path) {
        JsonObjectBuilder request = Json.createObjectBuilder();
        request.add("name", path.substring(path.lastIndexOf("/") + 1));
        try {
            request.add("script", IOUtils.resourceToString(path, Charset.defaultCharset()));
        } catch (IOException e) {
            fail(e);
        }
        return request.build().toString();
    }

    @Test
    public void testSuccessRun() throws IOException {
        given()
                .when()
                .contentType("multipart/form-data")
                .multiPart("contents", "run-success.groovy", IOUtils.resourceToByteArray("/scripts/run-success.groovy"), "text/plain")
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
                .contentType("multipart/form-data")
                .multiPart("contents", "run-insecure.groovy", IOUtils.resourceToByteArray("/scripts/run-insecure.groovy"), "text/plain")
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
                .body(buildScript("/scripts/validate-success.groovy"))
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
                .body(buildScript("/scripts/validate-error.groovy"))
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
    public void testHintMethodConstructorParam() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-constructor-param.groovy", 2, 45, "before"))
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
    public void testHintMethodReturn() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-return.groovy", 2, 34, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "add(String param, String param2) - JsonObjectBuilder"), hasEntry("value", "add(param, param2)"))));
    }
    
    
    @Test
    public void testHintMethodReturn2() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-return2.groovy", 2, 34, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "add(String param, String param2) - JsonObjectBuilder"), hasEntry("value", "add(param, param2)"))));
    }
    
    @Test
    public void testHintMethodReturn3() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-return3.groovy", 2, 36, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "add(String param, String param2) - JsonObjectBuilder"), hasEntry("value", "add(param, param2)"))));
    }
    
    @Test
    public void testHintMethodReturn4() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-return4.groovy", 3, 3, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "add(String param, String param2) - JsonObjectBuilder"), hasEntry("value", "add(param, param2)"))));
    }
    
    @Test
    public void testHintMethodReturn5() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-return5.groovy", 2, 59, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "build() - JsonObject"), hasEntry("value", "build()"))));
    }
    
    @Test
    public void testHintMethodReturn6() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-method-return6.groovy", 2, 12, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "createObjectBuilder() - JsonObjectBuilder"), hasEntry("value", "createObjectBuilder()"))));
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
    public void testImport() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-import.groovy", 0, 21, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "bind - package"), hasEntry("value", "bind"))));
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
                //.log().body()
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
    public void testImportMethod3() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-import-method3.groovy", 0, 29, "before"))
                .contentType(ContentType.JSON)
                //.log().body()
                .post("/api/gce/hint")
                .then()
                .statusCode(200)
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "abs"), hasEntry("value", "abs"))));
    }
    
    @Test
    public void testImportMethodParam() throws IOException {
        given()
                .when()
                .body(buildHint("/scripts/hint-import-method-param.groovy", 1, 4, "before"))
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
                //.log().body()
                .body("status", is("ok"),
                        "hints.size()", is(greaterThan(0)),
                        "hints", hasItem(allOf(hasEntry("displayed", "Test()"), hasEntry("value", "Test()"))));
    }

}

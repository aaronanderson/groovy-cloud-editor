package com.github.aaronanderson.gce;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.enterprise.context.RequestScoped;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
//import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

//import io.quarkus.oidc.IdToken;

@Path("/gce")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GoogleCloudEditorRS {

    static Logger logger = LoggerFactory.getLogger(GoogleCloudEditorRS.class);

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    @GET
    @Path("/scripts")
    public Response scripts() {
        try {
            JsonObjectBuilder status = Json.createObjectBuilder();
            JsonArrayBuilder scripts = Json.createArrayBuilder();

            scripts.add(buildScript("test.groovy"));
            status.add("scripts", scripts);
            status.add("status", "ok");
            return Response.status(200).entity(status.build()).build();

        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            // output.addFormData("status", status, MediaType.APPLICATION_JSON_TYPE);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();

        }

    }

    @POST
    @Path("/run")
    public Response run(JsonObject request) {
        try {
            String name = request.getString("name");
            String contents = request.getString("contents");
            byte[] scriptContents = Base64.getDecoder().decode(contents);
            JsonObjectBuilder status = Json.createObjectBuilder();
            GroovyClassLoader gcl = new GroovyClassLoader();
            Binding binding = new Binding();
            binding.setVariable("test", "test-binding");

            CompilerConfiguration config = new CompilerConfiguration();
            config.setDebug(true);
            config.setVerbose(true);
            GroovyShell gshell = new GroovyShell(gcl, binding, config);
            Script script = gshell.parse(IOUtils.toString(scriptContents, "UTF-8"), name);

            script.setBinding(binding);
            Object result = script.run();
            System.out.format("Groovy exec result: %s \n", result);
            if (result instanceof String) {
                status.add("result", (String) result);
            } else if (result instanceof JsonValue) {
                status.add("result", (JsonValue) result);
            }

            status.add("status", "ok");
            return Response.status(200).entity(status.build()).build();

        } catch (Throwable e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            // output.addFormData("status", status, MediaType.APPLICATION_JSON_TYPE);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();

        }

    }

    private static JsonObject buildScript(String name) throws IOException {
        JsonObjectBuilder script = Json.createObjectBuilder();
        byte[] testScript = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader().getResourceAsStream("scripts/" + name));
        script.add("name", name);
        String contents = Base64.getEncoder().encodeToString(testScript);
        script.add("contents", contents);
        script.add("lastModified", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));
        return script.build();

    }

}

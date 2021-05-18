package com.github.aaronanderson.gce;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.enterprise.context.RequestScoped;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
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
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

@Path("/gce")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GroovyCloudEditorRS {

    static Logger logger = LoggerFactory.getLogger(GroovyCloudEditorRS.class);
    static Pattern NEW_CONSTRUCTOR = Pattern.compile("new\\s*(\\w*)\\s*($|\\))");

    @ConfigProperty(name = "gce.importsBlacklist")
    List<String> importsBlacklist;

    @ConfigProperty(name = "gce.starImportsBlacklist")
    List<String> starImportsBlacklist;

    @GET
    @Path("scripts")
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
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @POST
    @Path("run")
    public Response run(JsonObject request) {
        try {
            String name = request.getString("name");
            String contents = request.getString("contents");
            byte[] scriptContents = Base64.getDecoder().decode(contents);
            JsonObjectBuilder status = Json.createObjectBuilder();
            GroovyClassLoader gcl = new GroovyClassLoader();
            Map<String, Object> context = new HashMap<>();
            context.put("basePath", "/gce");
            Binding binding = new Binding();
            binding.setVariable("ctx", context);

            CompilerConfiguration config = new CompilerConfiguration();
            config.setDebug(true);
            config.setVerbose(true);
            SecureASTCustomizer customizer = new SecureASTCustomizer();
            //Do not allow file access.
            //As an alternative to the SecureASTCustomizer for additional security guarantees when executing untrusted external code 
            //consider setting up a policy file and use a Java SecurityManager.
            //https://levelup.gitconnected.com/secure-groovy-script-execution-in-a-sandbox-ea39f80ee87

            customizer.setImportsBlacklist(importsBlacklist);
            customizer.setStarImportsBlacklist(starImportsBlacklist);
            customizer.setIndirectImportCheckEnabled(true);
            config.addCompilationCustomizers(customizer);
            GroovyCodeSource codeSource = new GroovyCodeSource(new String(scriptContents), name, GroovyShell.DEFAULT_CODE_BASE);
            //codeSource.setCachable(true); //source name should contain has for uniqueness. Cached at GroovyClassLoader level.
            StringWriter out = new StringWriter();
            GroovyShell gshell = new GroovyShell(gcl, binding, config);
            Script script = gshell.parse(codeSource);
            script.setProperty("out", new PrintWriter(out));
            script.setBinding(binding);
            Object result = script.run();
            status.add("out", out.toString());
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
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @POST
    @Path("validate")
    public Response validate(JsonObject request) {
        try {
            String name = request.getString("name");
            String scriptContents = request.getString("script");

            GroovyClassLoader gcl = new GroovyClassLoader();
            JsonObjectBuilder result = Json.createObjectBuilder();
            CompilerConfiguration cfg = new CompilerConfiguration();
            CompilationUnit compileUnit = new CompilationUnit(gcl);
            compileUnit.addSource(name, scriptContents);
            try {
                compileUnit.compile(Phases.CANONICALIZATION);
            } catch (MultipleCompilationErrorsException me) {
                if (me.getErrorCollector().getErrorCount() > 0) {
                    JsonArrayBuilder errors = Json.createArrayBuilder();
                    for (Message err : me.getErrorCollector().getErrors()) {
                        JsonObjectBuilder errJson = Json.createObjectBuilder();
                        if (err instanceof SyntaxErrorMessage) {
                            SyntaxErrorMessage serr = (SyntaxErrorMessage) err;
                            errJson.add("sline", serr.getCause().getStartLine());
                            errJson.add("eline", serr.getCause().getEndLine());
                            errJson.add("scolumn", serr.getCause().getStartColumn());
                            errJson.add("ecolumn", serr.getCause().getEndColumn());
                            errJson.add("message", serr.getCause().getMessage());
                        } else {
                            errJson.add("message", err.toString());
                        }
                        errors.add(errJson);
                    }
                    result.add("errors", errors);
                }
                if (me.getErrorCollector().getWarningCount() > 0) {
                    JsonArrayBuilder warnings = Json.createArrayBuilder();
                    for (WarningMessage warn : me.getErrorCollector().getWarnings()) {
                        JsonObjectBuilder warnJson = Json.createObjectBuilder();
                        warnJson.add("message", warn.getMessage());
                        warnings.add(warnJson);
                    }
                    result.add("warnings", warnings);
                }
            }
            result.add("status", "ok");
            return Response.status(200).entity(result.build()).build();

        } catch (Throwable e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @POST
    @Path("hint")
    public Response hint(JsonObject request) {
        try {
            JsonNumber line = request.getJsonNumber("line");
            JsonNumber ch = request.getJsonNumber("ch");
            String sticky = null;
            if (request.containsKey("sticky") && !request.isNull("sticky")) {
                sticky = request.getString("sticky");
            }
            String name = request.getString("name");
            String scriptContents = request.getString("script");

            System.out.format("Hint Request: %d %d %s - %s\n%s\n", line.intValue(), ch.intValue(), sticky, name, scriptContents);
            GroovyClassLoader gcl = new GroovyClassLoader();
            JsonObjectBuilder result = Json.createObjectBuilder();
            CompilerConfiguration cfg = new CompilerConfiguration();
            AutoCompleteOperation autoCompleteOperation = new AutoCompleteOperation(line.intValue(), ch.intValue(), sticky);
            //GroovyCodeSource codeSource = new GroovyCodeSource(scriptContents, name, "gce");
            //CompilationUnit compileUnit = new CompilationUnit(cfg, codeSource.getCodeSource(), gcl);
            //compileUnit.addSource(codeSource.getName(), codeSource.getScriptText());
            CompilationUnit compileUnit = new CompilationUnit(gcl);
            compileUnit.addSource(name, scriptContents);
            compileUnit.addPhaseOperation(autoCompleteOperation, Phases.CANONICALIZATION);
            try {
                compileUnit.compile(Phases.CANONICALIZATION);
            } catch (MultipleCompilationErrorsException me) {
                if (me.getErrorCollector().getErrorCount() == 1) {
                    //visitor.visitScriptStatements(ctx.scriptStatements())
                    List<String> lines = IOUtils.readLines(new StringReader(scriptContents));
                    String srcLine = lines.get(line.intValue());
                    Matcher m = NEW_CONSTRUCTOR.matcher(srcLine);
                    if (m.find()) {
                        StringBuilder modifedSrc = new StringBuilder(lines.get(line.intValue()));
                        String constructorHint = m.group(1);
                        int start = m.start(1);
                        int end = m.end(1);

                        modifedSrc.replace(start, end, "Object()");
                        System.out.format("constructor hint \"%s\" %d %d %s->%s\n", constructorHint, start, end, srcLine, modifedSrc);
                        lines.set(line.intValue(), modifedSrc.toString());
                        autoCompleteOperation.setConstructorHint(constructorHint);

                        String newScript = lines.stream().collect(Collectors.joining("\n"));
                        compileUnit = new CompilationUnit(gcl);
                        compileUnit.addSource(name, newScript);
                        compileUnit.addPhaseOperation(autoCompleteOperation, Phases.CANONICALIZATION);
                        try {
                            compileUnit.compile(Phases.CANONICALIZATION);
                        } catch (MultipleCompilationErrorsException me2) {
                            //ignore, unable to perform autocomplete.
                        }
                    }
                }
            }
            result.add("hints", autoCompleteOperation.hints());
            result.add("status", "ok");
            return Response.status(200).entity(result.build()).build();
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

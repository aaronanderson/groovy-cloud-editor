package com.github.aaronanderson.gce;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilationUnit.IPrimaryClassNodeOperation;
import org.codehaus.groovy.control.CompilationUnit.ISourceUnitOperation;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.tools.GroovyClass;

class AutoCompleteParser {

    static Pattern METHOD_PARAM = Pattern.compile("\\([^)]*$", Pattern.MULTILINE);
    static Pattern PROPERTY = Pattern.compile("\\.\\s*(\\w*)\\s*$", Pattern.MULTILINE);
    static Pattern IMPORT = Pattern.compile("import (.+[^\\.;]+)(\\.?)", Pattern.MULTILINE);

    private final AutoCompleteRequest autoCompleteRequest;
    private final String name;
    private final String scriptContents;

    private final List<SourceUnit> sourceUnits = new LinkedList<>();
    private final List<GroovyClass> groovyClasses = new LinkedList<>();

    AutoCompleteParser(AutoCompleteRequest autoCompleteRequest, String name, String scriptContents) {
        this.autoCompleteRequest = autoCompleteRequest;
        this.name = name;
        this.scriptContents = scriptContents;
        //source = sourceUnit;
    }

    public List<SourceUnit> getSourceUnits() {
        return sourceUnits;
    }

    public List<GroovyClass> getGroovyClasses() {
        return groovyClasses;
    }

    List<SourceUnit> parse() {
        try {
            compile(scriptContents);
        } catch (MultipleCompilationErrorsException me) {
            if (me.getErrorCollector().getErrorCount() == 1) {
                //attempt to adjust source so that it compiles
                try {
                    List<String> lines = IOUtils.readLines(new StringReader(scriptContents));
                    boolean handled = methodParamHint(lines);
                    handled = handled || importHint(lines);//needs to be before the property hint
                    handled = handled || propertyHint(lines);
                    handled = handled || constructorHint(lines);
                } catch (IOException e) {
                    AutoCompleteAnalyzer.logger.error("Unable to read script contents", e);
                }
            }
        }
        return sourceUnits;
    }

    private void compile(String compileScript) throws CompilationFailedException {
        //GroovyCodeSource codeSource = new GroovyCodeSource(scriptContents, name, GroovyShell.DEFAULT_CODE_BASE);
        //CompilationUnit compileUnit = new CompilationUnit(cfg, codeSource.getCodeSource(), gcl);
        //compileUnit.addSource(codeSource.getName(), codeSource.getScriptText());
        CompilerConfiguration cfg = new CompilerConfiguration();
        cfg.setParameters(true);
        cfg.setPreviewFeatures(true);
        CompilationUnit compileUnit = new CompilationUnit(cfg);
        compileUnit.addSource(name, compileScript);
        //first parse source to build AST tree.
        compileUnit.compile(Phases.CANONICALIZATION);
        //second check source for structures outside of the main block statement.
        boolean advancedScript = false;
        Iterator<SourceUnit> i = compileUnit.iterator();
        while (i.hasNext()) {
            SourceUnit sourceUnit = i.next();
            if (sourceUnit.getAST().getMethods().size() > 0 || sourceUnit.getAST().getClasses().size() > 1) {
                //script functions or classes declared, fully compile the source so that ClassPath can be used for further hint introspection    
                advancedScript = true;
            }
        }
        //third compile advanced scripts so all methods and inner classes are available for scanning by ClassGraph
        GCEClassLoader cl = new GCEClassLoader();
        if (advancedScript) {
            //compileUnit.addPhaseOperation(new AutoCompleteClassOperation(), Phases.FINALIZATION);
            compileUnit.compile(Phases.CLASS_GENERATION);
            for (GroovyClass clazz : compileUnit.getClasses()) {
                groovyClasses.add(clazz);
                cl.addClasspathEntry(clazz);
            }
        }
        //forth advanced script compilation was not necessary go back and analyze the source.
        i = compileUnit.iterator();
        while (i.hasNext()) {
            sourceUnits.add(i.next());
        }
    }

    private boolean compileUpdate(List<String> lines) {
        String newScript = lines.stream().collect(Collectors.joining("\n"));
        try {
            compile(newScript);
            return true;
        } catch (MultipleCompilationErrorsException me2) {
            //me2.printStackTrace();
            //ignore, source adjustment failed, unable to perform autocomplete.
        }
        return false;
    }

    private boolean constructorHint(List<String> lines) {
        String srcLine = lines.get(autoCompleteRequest.getLine());
        StringBuilder modifedSrc = new StringBuilder(srcLine);
        //first, replace the constructor value with Object. It is too difficult to determine if the constructor text is for a valid class or a partial match without full AST processing.
        StringBuilder constructorHint = new StringBuilder();
        int startIndex = -1;
        for (int i = autoCompleteRequest.getCh() - 1; i >= 0; i--) {
            char c = srcLine.charAt(i);
            if (c == '(' || c == ')' || c == ' ') {
                continue;
            }
            if (c == 'w' && i > 2) {
                if (srcLine.charAt(i - 1) == 'e' && srcLine.charAt(i - 2) == 'n') {
                    startIndex = i + 1;
                    break;
                }
            }
            constructorHint.insert(0, c);
        }
        if (startIndex != -1) {
            modifedSrc.replace(startIndex, autoCompleteRequest.getCh(), " Object()");
            autoCompleteRequest.setConstructorHint(constructorHint.toString());
            //second, make a crude attempt to balance parenthesis.
            int depth = 0;
            for (int i = 0; i < modifedSrc.length(); i++) {
                char c = modifedSrc.charAt(i);
                depth = c == '(' ? depth + 1 : depth;
                depth = c == ')' ? depth - 1 : depth;
                if (depth < 0) {
                    modifedSrc.insert(i, '(');
                    break;
                }
            }
            if (depth > 0) {
                modifedSrc.append(")");
            }
            lines.set(autoCompleteRequest.getLine(), modifedSrc.toString());
            return compileUpdate(lines);
        }
        return false;

    }

    private boolean methodParamHint(List<String> lines) {
        String srcLine = lines.get(autoCompleteRequest.getLine());
        Matcher m = METHOD_PARAM.matcher(srcLine);
        if (m.find()) {
            StringBuilder modifedSrc = new StringBuilder(lines.get(autoCompleteRequest.getLine()));
            modifedSrc.append(")");
            lines.set(autoCompleteRequest.getLine(), modifedSrc.toString());
            return compileUpdate(lines);
        }
        return false;
    }

    private boolean importHint(List<String> lines) {
        String srcLine = lines.get(autoCompleteRequest.getLine());
        Matcher m = IMPORT.matcher(srcLine);
        if (m.find()) {
            StringBuilder modifedSrc = new StringBuilder(lines.get(autoCompleteRequest.getLine()));
            if (m.group(2) != null) {
                int start = m.start(2);
                int end = m.end(2);
                modifedSrc.replace(start, end, ".*");
            }
            lines.set(autoCompleteRequest.getLine(), modifedSrc.toString());
            return compileUpdate(lines);
        }
        return false;
    }

    private boolean propertyHint(List<String> lines) {
        String srcLine = lines.get(autoCompleteRequest.getLine());
        Matcher m = PROPERTY.matcher(srcLine);
        if (m.find()) {
            StringBuilder modifedSrc = new StringBuilder(lines.get(autoCompleteRequest.getLine()));
            autoCompleteRequest.setPropertyHint(m.group(1));
            int start = m.start(1);
            int end = m.end(1);
            modifedSrc.replace(start, end, "_");
            lines.set(autoCompleteRequest.getLine(), modifedSrc.toString());
            return compileUpdate(lines);
        }
        return false;
    }

    private static class GCEClassLoader extends ClassLoader {

        private final Map<String, byte[]> classpath = new HashMap<>();

        private void addClasspathEntry(GroovyClass clazz) {
            classpath.put(clazz.getName(), clazz.getBytes());
        }

        public List<URL> files() throws MalformedURLException {
            List<URL> files = new ArrayList<>(classpath.size());
            for (String classname : classpath.keySet()) {
                files.add(new URL(null, "gce:///" + classname + ".class", new BytesHandler()));
            }
            return files;
        }

        private class BytesHandler extends URLStreamHandler {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return new ByteUrlConnection(u);
            }
        }

        private class ByteUrlConnection extends URLConnection {
            public ByteUrlConnection(URL url) {
                super(url);
            }

            @Override
            public void connect() throws IOException {
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(classpath.get(this.getURL().getPath().substring(1)));
            }
        }

    }

    //Not used
    public class AutoCompleteSourceOperation implements ISourceUnitOperation {

        @Override
        public void call(SourceUnit source) throws CompilationFailedException {
            // analyzeSource(source);
        }

    }

    public class AutoCompleteClassOperation implements IPrimaryClassNodeOperation {

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
            //analyzeSource(source, classNode);
        }

    }

}
package com.github.aaronanderson.gce;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.tools.GroovyClass;
import org.jboss.logging.Logger;

import com.google.common.jimfs.Jimfs;

import io.github.classgraph.ArrayTypeSignature;
import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.FieldInfoList;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodInfoList.MethodInfoFilter;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.PackageInfo;
import io.github.classgraph.PackageInfoList;
import io.github.classgraph.ReferenceTypeSignature;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeParameter;
import io.github.classgraph.TypeSignature;
import io.github.classgraph.TypeVariableSignature;

public class AutoCompleteAnalyzer implements AutoCloseable {

    static Logger logger = Logger.getLogger(AutoCompleteAnalyzer.class);

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES;
    static {
        WRAPPER_TYPES = new HashMap<Class<?>, Class<?>>();
        WRAPPER_TYPES.put(int.class, Integer.class);
        WRAPPER_TYPES.put(byte.class, Byte.class);
        WRAPPER_TYPES.put(char.class, Character.class);
        WRAPPER_TYPES.put(boolean.class, Boolean.class);
        WRAPPER_TYPES.put(double.class, Double.class);
        WRAPPER_TYPES.put(float.class, Float.class);
        WRAPPER_TYPES.put(long.class, Long.class);
        WRAPPER_TYPES.put(short.class, Short.class);
        WRAPPER_TYPES.put(void.class, Void.class);
        WRAPPER_TYPES.put(void.class, Void.class);
    }

    private boolean autoImport = false;
    final ScanResult globalScanResult;

    public AutoCompleteAnalyzer(boolean autoImport, List<String> acceptPackages, List<String> rejectPackages) {
        this.autoImport = autoImport;
        ClassGraph classGraph = new ClassGraph();
        classGraph.acceptPackages(acceptPackages.toArray(new String[acceptPackages.size()]));
        classGraph.rejectPackages(rejectPackages.toArray(new String[rejectPackages.size()]));
        classGraph.enableSystemJarsAndModules().enableClassInfo().enableMethodInfo().enableFieldInfo();
        globalScanResult = classGraph.scan();
    }

    @Override
    public void close() throws Exception {
        if (globalScanResult != null) {
            globalScanResult.close();
        }

    }

    public List<Hint> analyze(AutoCompleteRequest autoCompleteRequest, String name, String scriptContents) {
        AutoCompleteParser parser = new AutoCompleteParser(autoCompleteRequest, name, scriptContents);
        List<SourceUnit> sources = parser.parse();

        if (!parser.getGroovyClasses().isEmpty()) {
            try (FileSystem memFs = Jimfs.newFileSystem()) {
                final Path memFsRoot = memFs.getPath("");
                for (GroovyClass clazz : parser.getGroovyClasses()) {
                    Path classPath = memFsRoot.resolve(clazz.getName() + ".class");
                    Files.write(classPath, clazz.getBytes(), StandardOpenOption.CREATE);
                }
                final URL memFsRootURL = memFsRoot.toUri().toURL();
                try (URLClassLoader childClassLoader = new URLClassLoader(new URL[] { memFsRootURL })) {
                    ClassGraph classGraph = new ClassGraph().enableURLScheme(memFsRootURL.getProtocol())
                            .overrideClassLoaders(childClassLoader).ignoreParentClassLoaders().acceptPackagesNonRecursive("").enableClassInfo().enableMethodInfo().enableFieldInfo();
                    try (ScanResult scriptScanResult = classGraph.scan()) {
                        for (SourceUnit source : sources) {
                            List<Hint> hints = new SourceUnitInspector(source, autoCompleteRequest, scriptScanResult).scan();
                            if (!hints.isEmpty()) {
                                return hints;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("JimFS error", e);
            }
        } else {
            for (SourceUnit source : sources) {
                List<Hint> hints = new SourceUnitInspector(source, autoCompleteRequest).scan();
                if (!hints.isEmpty()) {
                    return hints;
                }
            }
        }

        return Collections.emptyList();
    }

    private class SourceUnitInspector {

        private final SourceUnit sourceUnit;
        private final AutoCompleteRequest autoCompleteRequest;
        private final ScanResult scriptResult;
        private int lastImportLine;
        private final List<Hint> hints = new LinkedList<>();
        private final List<PackageInfo> importedPackages = new LinkedList<>();
        private final List<ClassInfo> importedClasses = new LinkedList<>();
        private final List<MethodInfo> importedMethods = new LinkedList<>();
        private final LinkedList<VariableScope> targetVariableScopes = new LinkedList<>();
        private final Map<TypeSignature, String> paramNameCache = new HashMap<>();
        private final Set<String> ignoreVarNames = new HashSet<>();

        private String constructorHint = null;
        private String propertyHint = null;

        private SourceUnitInspector(SourceUnit sourceUnit, AutoCompleteRequest autoCompleteRequest, ScanResult scriptResult) {
            this.sourceUnit = sourceUnit;
            this.autoCompleteRequest = autoCompleteRequest;
            this.scriptResult = scriptResult;
        }

        private SourceUnitInspector(SourceUnit sourceUnit, AutoCompleteRequest autoCompleteRequest) {
            this(sourceUnit, autoCompleteRequest, null);
        }

        private ClassInfoList allClassInfo() {
            ClassInfoList classInfoList = ClassInfoList.emptyList();
            List<ClassInfoList> importedClassInfo = new LinkedList<>();
            for (PackageInfo packageInfo : importedPackages) {
                importedClassInfo.add(packageInfo.getClassInfo());
            }
            Set<ClassInfo> importedClassInfoList = new HashSet<>();
            for (ClassInfo classInfo : importedClasses) {
                importedClassInfoList.add(classInfo);
            }
            importedClassInfo.add(new ClassInfoList(importedClassInfoList));
            if (scriptResult != null) {
                importedClassInfo.add(scriptResult.getAllClasses());
            }
            return classInfoList.union(importedClassInfo.toArray(new ClassInfoList[importedClassInfo.size()]));
        }

        private ClassInfo getClassInfo(String className) {
            ClassInfo classInfo = null;
            if (scriptResult != null) {
                classInfo = scriptResult.getClassInfo(className);
                if (classInfo != null) {
                    return classInfo;
                }
            }
            return globalScanResult.getClassInfo(className);
        }

        private List<Hint> scan() {
            constructorHint = autoCompleteRequest.getConstructorHint();
            propertyHint = autoCompleteRequest.getPropertyHint();
            importedPackages.add(globalScanResult.getPackageInfo("java.lang"));
            lastImportLine = scanImports();

            AutoCompleteVisitor visitor = new AutoCompleteVisitor(autoCompleteRequest);
            visitor.visitImports(sourceUnit.getAST());
            visitor.getVariableScopes().push(sourceUnit.getAST().getStatementBlock().getVariableScope());
            for (ClassNode classNode : sourceUnit.getAST().getClasses()) {
                for (MethodNode method : classNode.getMethods()) {
                    if (method.getCode() != null) {
                        visitor.getVariableScopes().push(method.getVariableScope());
                        method.getCode().visit(visitor);
                        visitor.getVariableScopes().pop();
                    }
                }
            }

            targetVariableScopes.addAll(visitor.getTargetVariableScopes());
            Collections.reverse(targetVariableScopes);
            LinkedList<ASTNode> targetNodes = visitor.getTargetNodes();
            if (targetNodes.size() > 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Target %d ASTNodes found for line %d column %d", targetNodes.size(), autoCompleteRequest.getLine() + 1, autoCompleteRequest.getCh()));
                }
                while (targetNodes.size() > 0) {
                    ASTNode node = targetNodes.pop();
                    if (nodeMatch(node)) {
                        ASTNode prevNode = null;
                        while (!targetNodes.isEmpty()) {
                            ASTNode currentNode = targetNodes.pop();
                            if (currentNode instanceof VariableExpression) {
                                prevNode = prevNode != null ? prevNode : currentNode;
                                ignoreVarNames.add(((VariableExpression) currentNode).getName());
                            } else if (currentNode instanceof MethodCallExpression && prevNode == null) {
                                prevNode = currentNode;
                            }
                        }

                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format("Target ASTNode %s found for line %d-%d column %d-%d %s", node, node.getLineNumber(), node.getLastLineNumber(), node.getColumnNumber(), node.getLastColumnNumber(), node.getText()));
                            if (prevNode != null) {
                                logger.debug(String.format("Prev ASTNode %s found for line %d-%d column %d-%d %s", prevNode, prevNode.getLineNumber(), prevNode.getLastLineNumber(), prevNode.getColumnNumber(), prevNode.getLastColumnNumber(), prevNode.getText()));
                            }
                        }

                        if (node instanceof ConstructorCallExpression) {
                            ConstructorCallExpression constructor = (ConstructorCallExpression) node;
                            if (prevNode instanceof VariableExpression) {
                                newConstructorHint(constructor, (VariableExpression) prevNode);
                            } else if (prevNode instanceof MethodCallExpression) {
                                newConstructorHint(constructor, (MethodCallExpression) prevNode);
                            }
                        } else if (node instanceof MethodCallExpression) {
                            if (propertyHint != null || prevNode instanceof MethodCallExpression) {
                                methodReturnHint((MethodCallExpression) node);
                            } else {
                                methodHint((MethodCallExpression) node);
                            }

                        } else if (node instanceof PropertyExpression) {
                            PropertyExpression prop = (PropertyExpression) node;
                            if (prevNode instanceof VariableExpression) {
                                propertyHint(prop, (VariableExpression) prevNode);
                            } else {
                                propertyHint(prop);
                            }
                        } else if (node instanceof VariableExpression) {
                            propertyHint((VariableExpression) node);
                        } else if (node instanceof ImportNode) {
                            importHint((ImportNode) node);
                        }

                        break;
                    }
                }
            } else {
                logger.warn(String.format("Target ASTNodes unavailable for line %d column %d", autoCompleteRequest.getLine() + 1, autoCompleteRequest.getCh()));
            }

            return hints;
        }

        private boolean nodeMatch(ASTNode node) {
            int targetLine = autoCompleteRequest.getLine() + 1;
            int targetColumn = autoCompleteRequest.getCh();

            boolean lineMatch = node.getLineNumber() <= targetLine && targetLine <= node.getLastLineNumber();
            boolean lineSplit = node.getLineNumber() != node.getLastLineNumber();
            boolean colMatch = node.getColumnNumber() <= targetColumn && targetColumn <= node.getLastColumnNumber();

            return lineMatch && (colMatch || lineSplit);
        }

        private int scanImports() {
            int lastImportLine = 0;
            for (ImportNode i : sourceUnit.getAST().getImports()) {
                printASTDetails(i, "Import %s\n", i.getPackageName());
                ClassInfo classInfo = globalScanResult.getClassInfo(i.getType().getTypeClass().getName());
                if (classInfo != null) {
                    importedClasses.add(classInfo);
                }
                lastImportLine = i.getLastLineNumber() > lastImportLine ? i.getLastLineNumber() : lastImportLine;
            }
            for (ImportNode i : sourceUnit.getAST().getStarImports()) {
                printASTDetails(i, "Star Import %s\n", i.getPackageName());
                String packageName = i.getPackageName().substring(0, i.getPackageName().length() - 1);
                PackageInfo packageInfo = globalScanResult.getPackageInfo(packageName);
                if (packageInfo != null) {
                    importedPackages.add(packageInfo);
                }
                lastImportLine = i.getLastLineNumber() > lastImportLine ? i.getLastLineNumber() : lastImportLine;
            }
            for (Entry<String, ImportNode> i : sourceUnit.getAST().getStaticImports().entrySet()) {
                String className = i.getValue().getType().getTypeClass().getName();
                printASTDetails(i.getValue(), "Static Import %s %s\n", i.getKey(), className);
                ClassInfo classInfo = globalScanResult.getClassInfo(className);
                if (classInfo != null) {
                    importedMethods.addAll(classInfo.getMethodInfo().filter(m -> m.getName().equals(i.getValue().getAlias())));
                }
                lastImportLine = i.getValue().getLastLineNumber() > lastImportLine ? i.getValue().getLastLineNumber() : lastImportLine;
            }
            for (Entry<String, ImportNode> i : sourceUnit.getAST().getStaticStarImports().entrySet()) {
                String className = i.getValue().getType().getTypeClass().getName();
                printASTDetails(i.getValue(), "Static Star Import %s %s\n", i.getKey(), className);
                ClassInfo classInfo = globalScanResult.getClassInfo(className);
                if (classInfo != null) {
                    importedMethods.addAll(classInfo.getMethodInfo());
                }
                lastImportLine = i.getValue().getLastLineNumber() > lastImportLine ? i.getValue().getLastLineNumber() : lastImportLine;
            }
            return lastImportLine;
        }

        private void importHint(ImportNode importNode) {
            int[] entered = new int[] { 0, 0 };
            if (importNode.getPackageName() != null) {
                String packageName = importNode.getPackageName();
                packageName = packageName.substring(0, packageName.length() - 1);
                PackageInfo packageInfo = globalScanResult.getPackageInfo(packageName);
                if (packageInfo != null) {
                    PackageInfoList childPackageInfoList = packageInfo.getChildren();
                    ClassInfoList classInfoList = packageInfo.getClassInfo();
                    //.filter(p-> importHint!=null? p.(): true))
                    for (PackageInfo childPackageInfo : childPackageInfoList) {
                        String hint = childPackageInfo.getName().substring(packageInfo.getName().length() + 1);
                        StringBuilder display = new StringBuilder(hint).append(" - package");
                        hints.add(new Hint("import-package", entered, display.toString(), hint));
                    }
                    for (ClassInfo classInfo : classInfoList) {
                        String hint = classInfo.getSimpleName();
                        hints.add(new Hint("import-class", entered, hint, hint));
                    }
                }
            } else {
                String className = importNode.getType().getTypeClass().getName();
                ClassInfo classInfo = globalScanResult.getClassInfo(className);
                if (classInfo != null) {
                    MethodInfoList methods = classInfo.getMethodInfo();
                    //property parser may have inserted underscore placeholder
                    if (importNode.getAlias() != null) {
                        methods = methods.filter(m -> m.getName().startsWith(importNode.getAlias()));
                    }
                    methods.getNames().stream().distinct().sorted().forEach(method -> hints.add(new Hint("import-method", entered, method, method)));
                }
            }
        }

        private void newConstructorHint(ConstructorCallExpression constructorNode, VariableExpression varNode) {
            String varType = varNode.getType().getName();
            boolean isObject = varNode.isDynamicTyped();//java.lang.Object.class.equals(varType);
            ClassInfoList classInfoList = ClassInfoList.emptyList();
            if (!isObject) {
                classInfoList = constructorClasses(constructorNode, false);
                ClassInfo classInfo = getClassInfo(varType);
                classInfoList = classInfoList.getStandardClasses().getAssignableTo(classInfo);
            } else {
                classInfoList = constructorClasses(constructorNode, true);
            }
            constructorHints(classInfoList, (ArgumentListExpression) constructorNode.getArguments());

        }

        private void newConstructorHint(ConstructorCallExpression constructorNode, MethodCallExpression methodNode) {
            int constructorIndex = -1;

            ArgumentListExpression argumentExpressions = (ArgumentListExpression) methodNode.getArguments();
            for (int i = 0; i < argumentExpressions.getExpressions().size(); i++) {
                Expression e = argumentExpressions.getExpression(i);
                if (e == constructorNode) {
                    constructorIndex = i;
                }
            }

            ClassInfoList classInfoList = constructorClasses(constructorNode, true);
            MethodInfoList methods = findMethods(methodNode, true);
            for (MethodInfo methodInfo : methods) {
                MethodParameterInfo parameterInfo = methodInfo.getParameterInfo()[constructorIndex];
                classInfoList = classInfoList.getStandardClasses().filter(c -> isAssignable(c.loadClass(), parameterInfo.getTypeSignatureOrTypeDescriptor()));
                constructorHints(classInfoList, (ArgumentListExpression) constructorNode.getArguments());
            }
        }

        private ClassInfoList constructorClasses(ConstructorCallExpression constructorNode, boolean restricted) {
            String constType = constructorNode.getType().getTypeClass().getName();
            if (!Object.class.getName().equals(constType)) {
                ClassInfoList list = new ClassInfoList();
                list.add(getClassInfo(constType));
                return list;
            } else if (!constructorHint.isBlank() || !restricted) {
                ClassInfoList classInfoList = allClassInfo();
                if (!constructorHint.isBlank()) {
                    classInfoList = classInfoList.filter(c -> c.getSimpleName().startsWith(constructorHint));
                }
                return classInfoList;
            }
            return ClassInfoList.emptyList();
        }

        private void propertyHint(VariableExpression var) {
            if (var.isDynamicTyped()) {
                if (var.getAccessedVariable() instanceof DynamicVariable) {
                    DynamicVariable dvar = (DynamicVariable) var.getAccessedVariable();
                    List<MethodInfo> methodList = new LinkedList<>();
                    for (Entry<String, ImportNode> importMethod : sourceUnit.getAST().getStaticImports().entrySet()) {
                        if (importMethod.getKey().startsWith(dvar.getName())) {
                            ClassInfo classInfo = getClassInfo(importMethod.getValue().getType().getName());
                            classInfo.getMethodInfo().filter(mi -> mi.getName().equals(importMethod.getKey())).forEach(mi -> methodList.add(mi));
                        }
                    }
                    for (Entry<String, ImportNode> importMethod : sourceUnit.getAST().getStaticStarImports().entrySet()) {
                        ClassInfo classInfo = getClassInfo(importMethod.getKey());
                        classInfo.getMethodInfo().filter(mi -> mi.getName().startsWith(dvar.getName())).forEach(mi -> methodList.add(mi));
                    }
                    methodHints(dvar.getName(), new MethodInfoList(methodList), new ArgumentListExpression());
                }
            } else {
                String clazz = var.getType().getName();
                addPropertyHints(clazz, propertyHint != null ? propertyHint : "", null);
            }

        }

        private void addPropertyHints(String clazz, String propertyName, MethodInfoFilter filter) {
            if (clazz != null && !Object.class.getName().equals(clazz)) {
                ClassInfo classInfo = getClassInfo(clazz);
                MethodInfoList methodList = classInfo.getMethodInfo().filter(m -> m.getName().startsWith(propertyName));
                if (filter != null) {
                    methodList = methodList.filter(filter);
                }
                methodHints(propertyName, methodList, new ArgumentListExpression());
                fieldHints(propertyName, classInfo.getFieldInfo());
            }
        }

        private void propertyHint(PropertyExpression prop, VariableExpression var) {
            Class<?> returnType = var.getType().getTypeClass();
            propertyHint(prop, (m) -> isAssignable(returnType, m.getTypeDescriptor().getResultType()));
        }

        private void propertyHint(PropertyExpression prop) {
            propertyHint(prop, (MethodInfoFilter) null);
        }

        private void propertyHint(PropertyExpression prop, MethodInfoFilter filter) {
            String clazz = null;
            if (prop.getObjectExpression() instanceof VariableExpression) {
                VariableExpression varNode = (VariableExpression) prop.getObjectExpression();
                if (varNode.getAccessedVariable() instanceof VariableExpression) {
                    clazz = varNode.getAccessedVariable().getType().getName();
                }
            } else if (prop.getObjectExpression() instanceof ClassExpression) {
                clazz = ((ClassExpression) prop.getObjectExpression()).getType().getName();
            } else if (prop.getObjectExpression() instanceof MethodCallExpression) {
                clazz = methodReturnType((MethodCallExpression) prop.getObjectExpression());
            }
            addPropertyHints(clazz, propertyHint != null ? propertyHint : prop.getPropertyAsString(), filter);
        }

        private void methodReturnHint(MethodCallExpression methodNode) {
            addPropertyHints(methodReturnType(methodNode), propertyHint != null ? propertyHint : "", null);
        }

        private void methodHint(MethodCallExpression methodNode) {
            ASTNode targetNode = methodNode;
            while (targetNode instanceof MethodCallExpression) {
                MethodCallExpression targetMethodNode = (MethodCallExpression) targetNode;
                if (nodeMatch(targetMethodNode.getObjectExpression())) {
                    methodNode = targetMethodNode;
                    break;
                } else {
                    targetNode = targetMethodNode.getObjectExpression();
                }
            }
            int parameterIndex = -1;
            ArgumentListExpression argumentExpressions = (ArgumentListExpression) methodNode.getArguments();
            int targetLine = autoCompleteRequest.getLine() + 1;
            int targetColumn = autoCompleteRequest.getCh();
            for (int i = 0; i < argumentExpressions.getExpressions().size(); i++) {
                Expression e = argumentExpressions.getExpression(i);
                if ((e.getLineNumber() <= targetLine && targetLine <= e.getLastLineNumber()) && (e.getColumnNumber() <= targetColumn && targetColumn <= e.getLastColumnNumber())) {
                    parameterIndex = i;
                }
            }
            final int fparameterIndex = parameterIndex;
            String methodHintValue = ((ConstantExpression) methodNode.getMethod()).getText();

            MethodInfoList methodList = findMethods(methodNode, false);
            methodList = methodList.filter(m -> m.getParameterInfo().length > fparameterIndex);
            methodHints(methodHintValue, methodList, argumentExpressions);

        }

        private String methodReturnType(MethodCallExpression methodNode) {
            MethodInfoList methodList = findMethods(methodNode, true);
            if (methodList.size() == 1) {
                TypeSignature returnType = methodList.get(0).getTypeDescriptor().getResultType();
                if (returnType instanceof ClassRefTypeSignature) {
                    return ((ClassRefTypeSignature) returnType).getClassInfo().getName();

                }
            }
            return null;
        }

        private MethodInfoList findMethods(MethodCallExpression methodNode, boolean exact) {
            ArgumentListExpression argumentExpressions = (ArgumentListExpression) methodNode.getArguments();
            ConstantExpression methodReference = (ConstantExpression) methodNode.getMethod();
            String methodName = (String) methodReference.getValue();
            String clazz = null;
            List<MethodInfo> localMethods = new LinkedList<>();
            if (methodNode.getObjectExpression() instanceof VariableExpression) {
                VariableExpression varNode = (VariableExpression) methodNode.getObjectExpression();
                if (varNode.getAccessedVariable() instanceof VariableExpression) {
                    clazz = varNode.getAccessedVariable().getType().getTypeClass().getName();
                } else if (varNode.getName().startsWith("this")) {
                    clazz = sourceUnit.getAST().getScriptClassDummy().getName();
                    localMethods.addAll(importedMethods);
                } else {
                    clazz = methodNode.getObjectExpression().getType().getTypeClass().getName();
                }
            } else if (methodNode.getObjectExpression() instanceof ConstructorCallExpression) {
                clazz = methodNode.getObjectExpression().getType().getTypeClass().getName();
            } else if (methodNode.getObjectExpression() instanceof ClassExpression) {
                clazz = methodNode.getObjectExpression().getType().getTypeClass().getName();
            } else if (methodNode.getObjectExpression() instanceof MethodCallExpression) {
                clazz = methodReturnType((MethodCallExpression) methodNode.getObjectExpression());
            }
            if (clazz == null || Object.class.getName().equals(clazz)) {
                return MethodInfoList.emptyList();
            }
            ClassInfo classInfo = getClassInfo(clazz);
            //may be null due to script class reference but a full class generation was not performed.
            if (classInfo != null) {
                final int size = argumentExpressions.getExpressions().size();
                MethodInfoFilter nameFilter = (m) -> m.getName().startsWith(methodName);
                MethodInfoFilter countFilter = (m) -> exact ? m.getParameterInfo().length == size : m.getParameterInfo().length >= size;
                MethodInfoFilter typeFilter = (m) -> {
                    if (m.isSynthetic()) {
                        return false;
                    }
                    boolean matches = true;
                    for (int i = 0; i < argumentExpressions.getExpressions().size(); i++) {
                        Expression arg = argumentExpressions.getExpression(i);
                        Class<?> argType = arg.getType().getTypeClass();
                        if (!Object.class.equals(argType)) {
                            MethodParameterInfo paramInfo = m.getParameterInfo()[i];
                            if (!isAssignable(argType, paramInfo.getTypeSignatureOrTypeDescriptor())) {
                                matches = false;
                            }
                        }
                    }
                    return matches;
                };
                MethodInfoList infoList = classInfo.getMethodInfo().filter(m -> nameFilter.accept(m) && countFilter.accept(m) && typeFilter.accept(m));
                infoList.addAll(localMethods);
                return infoList;
            } else {
                return new MethodInfoList(localMethods);
            }
        }

        private void constructorHints(ClassInfoList classInfoList, ArgumentListExpression argumentExpressions) {
            for (ClassInfo classInfo : classInfoList) {
                for (MethodInfo constInfo : classInfo.getConstructorInfo()) {
                    addHint(constructorHint != null ? constructorHint : "", classInfo.getName(), classInfo.getSimpleName(), constInfo, argumentExpressions);
                }
            }
        }

        private void methodHints(String methodHint, MethodInfoList methodList, ArgumentListExpression argumentExpressions) {
            for (MethodInfo methodInfo : methodList) {
                addHint(methodHint, methodInfo.getName(), methodInfo.getName(), methodInfo, argumentExpressions);
            }
        }

        private void addHint(String hint, String displayed2, String value2, MethodInfo methodInfo, ArgumentListExpression argumentExpressions) {
            StringBuilder displayed = new StringBuilder(displayed2).append("(");
            StringBuilder value = new StringBuilder(value2).append("(");
            for (int i = 0; i < methodInfo.getParameterInfo().length; i++) {
                MethodParameterInfo param = methodInfo.getParameterInfo()[i];
                String paramName = argumentParameterName(i, param.getTypeSignatureOrTypeDescriptor(), argumentExpressions);
                if (paramName == null) {
                    paramName = variableParameterName(param.getTypeSignatureOrTypeDescriptor());
                }
                if (paramName == null) {
                    paramName = param.getName() != null ? param.getName() : "param" + (i > 0 ? i + 1 : "");
                }
                displayed.append(param.getTypeSignatureOrTypeDescriptor().toStringWithSimpleNames()).append(" ").append(paramName);
                value.append(paramName);
                if (i != methodInfo.getParameterInfo().length - 1) {
                    displayed.append(", ");
                    value.append(", ");
                }
            }
            displayed.append(")");
            value.append(")");
            boolean isConstructor = methodInfo.getName().startsWith("<");
            if (methodInfo.getTypeSignatureOrTypeDescriptor() != null && !isConstructor) {
                displayed.append(" - ").append(methodInfo.getTypeSignatureOrTypeDescriptor().getResultType().toStringWithSimpleNames());
            }
            int[] entered = new int[] { 0, hint.length() };
            if (isConstructor) {
                int offset = displayed2.lastIndexOf('.');
                if (offset > 0) {
                    entered[0] = entered[0] + offset + 1;
                }

            }
            hints.add(new Hint(isConstructor ? "constructor" : "method", entered, displayed.toString(), value.toString()));

        }

        private void fieldHints(String hint, FieldInfoList fieldList) {
            fieldList = fieldList.filter(m -> m.getName().startsWith(hint));
            for (FieldInfo fieldInfo : fieldList) {
                StringBuilder displayed = new StringBuilder();
                StringBuilder value = new StringBuilder();
                displayed.append(fieldInfo.getTypeDescriptor().toStringWithSimpleNames()).append(" ");
                int offset = displayed.length();
                displayed.append(fieldInfo.getName());
                value.append(fieldInfo.getName());
                int[] entered = new int[] { offset, hint.length() };
                hints.add(new Hint("field", entered, displayed.toString(), value.toString()));
            }
        }

        //Java code should be compiled with -parameters option. The same option should be set on the Groovy CompilerConfiguration
        private String argumentParameterName(int paramIndex, TypeSignature type, ArgumentListExpression argumentExpressions) {
            if (paramIndex < argumentExpressions.getExpressions().size()) {
                Expression argument = argumentExpressions.getExpressions().get(paramIndex);
                if (argument instanceof ConstantExpression) {
                    ConstantExpression constNode = (ConstantExpression) argument;
                    return constNode.getText();//display text will contain constants type but that is acceptable.
                } else if (argument instanceof VariableExpression) {
                    VariableExpression varNode = (VariableExpression) argument;
                    return varNode.getName();
                }
            }

            return null;
        }

        private String variableParameterName(TypeSignature type) {
            return paramNameCache.computeIfAbsent(type, k -> findScopedVariableName(type));
        }

        private String findScopedVariableName(TypeSignature type) {
            for (VariableScope varScope : targetVariableScopes) {
                Iterator<Variable> iterator = varScope.getDeclaredVariablesIterator();
                while (iterator.hasNext()) {
                    Variable var = iterator.next();
                    if (!var.isDynamicTyped()) {
                        if (!ignoreVarNames.contains(var.getName())) {
                            Class<?> varTypeClass = var.getType().getTypeClass();
                            if (isAssignable(varTypeClass, type)) {
                                return var.getName();
                            }
                        }
                    }
                }
            }
            return null;
        }

    }

    private static boolean isAssignable(Class<?> clazz, TypeSignature type) {
        if (type instanceof ArrayTypeSignature) {
            ArrayTypeSignature arrayTypeSignature = (ArrayTypeSignature) type;
            if (clazz.isAssignableFrom(arrayTypeSignature.loadClass())) {
                return true;
            }
        } else if (type instanceof BaseTypeSignature) {
            BaseTypeSignature baseTypeSignature = (BaseTypeSignature) type;
            if (clazz.isPrimitive()) {
                clazz = WRAPPER_TYPES.get(clazz);
            }
            if (clazz.equals(WRAPPER_TYPES.get(baseTypeSignature.getType()))) {
                return true;
            }
        } else if (type instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature classRefTypeSignature = (ClassRefTypeSignature) type;
            if (clazz.isAssignableFrom(classRefTypeSignature.loadClass())) {
                return true;
            }
        } else if (type instanceof TypeVariableSignature) {
            TypeVariableSignature typeVariableSignature = (TypeVariableSignature) type;
            System.err.format("TypeVariable - unable to match %s\n", typeVariableSignature);
            TypeParameter typeParameter = typeVariableSignature.resolve();
            if (isAssignable(clazz, typeParameter.getClassBound())) {
                return true;
            }
            for (ReferenceTypeSignature interfaze : typeParameter.getInterfaceBounds()) {
                if (isAssignable(clazz, interfaze)) {
                    return true;
                }
            }
        }
        return false;

    }

    static void printASTDetails(ASTNode e, String format, Object... args) {
        if (logger.isDebugEnabled()) {
            Object[] lineArgs = new Object[] { e.getLineNumber(), e.getLastLineNumber(), e.getColumnNumber(), e.getLastColumnNumber(), e.getText() };
            Object[] formatArgs = Arrays.copyOf(lineArgs, lineArgs.length + args.length);
            System.arraycopy(args, 0, formatArgs, lineArgs.length, args.length);

            logger.debug(String.format("line: %d-%d %d-%d - %s\t" + format, formatArgs));
        }
    }

}
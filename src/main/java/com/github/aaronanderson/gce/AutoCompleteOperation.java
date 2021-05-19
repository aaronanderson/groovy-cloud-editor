package com.github.aaronanderson.gce;

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
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.LambdaExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.MethodReferenceExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.expr.PostfixExpression;
import org.codehaus.groovy.ast.expr.PrefixExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.RangeExpression;
import org.codehaus.groovy.ast.expr.SpreadExpression;
import org.codehaus.groovy.ast.expr.SpreadMapExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.UnaryMinusExpression;
import org.codehaus.groovy.ast.expr.UnaryPlusExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.BreakStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ContinueStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.classgen.BytecodeExpression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit.ISourceUnitOperation;
import org.codehaus.groovy.control.SourceUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.classgraph.ArrayTypeSignature;
import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ReferenceTypeSignature;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeParameter;
import io.github.classgraph.TypeSignature;
import io.github.classgraph.TypeVariableSignature;

public class AutoCompleteOperation implements ISourceUnitOperation {

    static Logger logger = LoggerFactory.getLogger(AutoCompleteOperation.class);

    private final AutoCompleteOperationConfig config;
    private List<Hint> hints;
    private String constructorHint = null;

    private List<String> scanPackages;
    private List<String> scanClasses;
    private int lastImportLine;
    private LinkedList<VariableScope> targetVariableScopes;
    private Map<TypeSignature, String> paramNameCache;
    private ClassGraph classGraph;
    private Set<String> ignoreVarNames;

    public AutoCompleteOperation(AutoCompleteOperationConfig config) {
        this.config = config;
    }

    public String getConstructorHint() {
        return constructorHint;
    }

    public void setConstructorHint(String constructorHint) {
        this.constructorHint = constructorHint;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        System.out.format("AST Source Unit Called %s\n", source.getName());
        hints = new LinkedList<>();

        scanPackages = new LinkedList<>();
        scanPackages.add("java.lang.");
        scanClasses = new LinkedList<>();
        lastImportLine = scanImports(source, scanPackages, scanClasses);
        paramNameCache = new HashMap<>();
        classGraph = buildClassGraph();
        ignoreVarNames = new HashSet<>();

        AutoCompleteVisitor visitor = new AutoCompleteVisitor();
        visitor.getVariableScopes().push(source.getAST().getStatementBlock().getVariableScope());
        source.getAST().getStatementBlock().visit(visitor);
        visitor.getVariableScopes().pop();
        for (MethodNode method : source.getAST().getMethods()) {
            visitor.getVariableScopes().push(method.getVariableScope());
            method.getCode().visit(visitor);
            visitor.getVariableScopes().pop();
        }

        targetVariableScopes = new LinkedList<>(visitor.getTargetVariableScopes());
        Collections.reverse(targetVariableScopes);
        LinkedList<ASTNode> targetNodes = visitor.getTargetNodes();
        if (targetNodes.size() > 0) {
            logger.info(String.format("Target %d ASTNodes found for line %d column %d", targetNodes.size(), config.getLine() + 1, config.getCh()));
            while (targetNodes.size() > 0) {
                ASTNode node = targetNodes.pop();
                if (node.getLineNumber() <= config.getLine() + 1 && config.getLine() + 1 <= node.getLastLineNumber()) {
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
                    if (prevNode != null) {
                        logger.info(String.format("Target ASTNode %s found for line %d-%d column %d-%d %s", node, node.getLineNumber(), node.getLastLineNumber(), node.getColumnNumber(), node.getLastColumnNumber(), node.getText()));
                        logger.info(String.format("Prev   ASTNode %s found for line %d-%d column %d-%d %s", prevNode, prevNode.getLineNumber(), prevNode.getLastLineNumber(), prevNode.getColumnNumber(), prevNode.getLastColumnNumber(), prevNode.getText()));
                        if (node instanceof ConstructorCallExpression) {
                            ConstructorCallExpression constructor = (ConstructorCallExpression) node;
                            if (prevNode instanceof VariableExpression) {
                                newConstructorHint(constructor, (VariableExpression) prevNode);
                            } else if (prevNode instanceof MethodCallExpression) {
                                newConstructorHint(constructor, (MethodCallExpression) prevNode);
                            }

                        }
                    }
                    break;
                }
            }
        } else {
            logger.warn(String.format("Target ASTNodes unavailable for line %d column %d", config.getLine() + 1, config.getCh()));
        }
    }

    private int scanImports(SourceUnit source, List<String> scanPackages, List<String> scanClasses) {
        int lastImportLine = 0;
        for (ImportNode i : source.getAST().getImports()) {
            printASTDetails(i, "Import %s\n", i.getPackageName());
            scanPackages.add(i.getType().getName());
            lastImportLine = i.getLastLineNumber() > lastImportLine ? i.getLastLineNumber() : lastImportLine;
        }
        for (ImportNode i : source.getAST().getStarImports()) {
            printASTDetails(i, "Star Import %s\n", i.getPackageName());
            scanPackages.add(i.getPackageName());
            lastImportLine = i.getLastLineNumber() > lastImportLine ? i.getLastLineNumber() : lastImportLine;
        }
        for (Entry<String, ImportNode> i : source.getAST().getStaticImports().entrySet()) {
            printASTDetails(i.getValue(), "Static Import %s %s\n", i.getKey(), i.getValue().getPackageName());
            scanPackages.add(i.getValue().getPackageName());
            lastImportLine = i.getValue().getLastLineNumber() > lastImportLine ? i.getValue().getLastLineNumber() : lastImportLine;
        }
        for (Entry<String, ImportNode> i : source.getAST().getStaticStarImports().entrySet()) {
            printASTDetails(i.getValue(), "Static Star Import %s %s\n", i.getKey(), i.getValue().getPackageName());
            scanPackages.add(i.getValue().getPackageName());
            lastImportLine = i.getValue().getLastLineNumber() > lastImportLine ? i.getValue().getLastLineNumber() : lastImportLine;
        }
        return lastImportLine;
    }

    private ClassGraph buildClassGraph() {
        ClassGraph classGraph = new ClassGraph().enableClassInfo().enableMethodInfo().enableSystemJarsAndModules();
        if (config.isAutoImport()) {
            classGraph.acceptPackages(config.getAcceptPackages().toArray(new String[config.getAcceptPackages().size()]));
            classGraph.rejectPackages(config.getRejectPackages().toArray(new String[config.getRejectPackages().size()]));
        } else {
            classGraph.acceptPackagesNonRecursive(scanPackages.toArray(new String[scanPackages.size()]));
            classGraph.acceptClasses(scanClasses.toArray(new String[scanClasses.size()]));
        }
        return classGraph;
    }

    private void newConstructorHint(ConstructorCallExpression constructorNode, VariableExpression varNode) {
        Class<?> varType = varNode.getType().getTypeClass();
        boolean isObject = varNode.isDynamicTyped();//java.lang.Object.class.equals(varType);
        if (!isObject || !constructorHint.isBlank()) {
            try (ScanResult scanResult = classGraph.scan()) {
                ClassInfoList classInfoList = scanResult.getAllClasses();
                if (!constructorHint.isBlank()) {
                    classInfoList = classInfoList.filter(c -> c.getSimpleName().startsWith(constructorHint));
                }
                if (!isObject) {
                    ClassInfo classInfo = scanResult.getClassInfo(varType.getName());
                    classInfoList = classInfoList.getAssignableTo(classInfo);
                }

                constructorHints(classInfoList);

            }
        } else {
            logger.debug("Object class type and no constructor prefix, unable to provide specific hint.");
        }

    }

    private void newConstructorHint(ConstructorCallExpression constructorNode, MethodCallExpression methodNode) {
        ConstantExpression methodReference = null;
        int constructorIndex = -1;

        ArgumentListExpression argumentExpressions = (ArgumentListExpression) methodNode.getArguments();
        for (int i = 0; i < argumentExpressions.getExpressions().size(); i++) {
            Expression e = argumentExpressions.getExpression(i);
            if (e == constructorNode) {
                if (methodNode.getMethod() instanceof ConstantExpression) {
                    methodReference = (ConstantExpression) methodNode.getMethod();
                    constructorIndex = i;
                }

            }
        }
        System.out.format("%s\n", argumentExpressions);
        //methodNode.getArguments().

        if (methodReference != null || !constructorHint.isBlank()) {
            try (ScanResult scanResult = classGraph.scan()) {
                ClassInfoList classInfoList = scanResult.getAllClasses();
                MethodInfoList methods = findMethods(methodReference, argumentExpressions, scanResult);
                for (MethodInfo methodInfo : methods) {
                    MethodParameterInfo parameterInfo = methodInfo.getParameterInfo()[constructorIndex];
                    if (!constructorHint.isBlank()) {
                        classInfoList = classInfoList.filter(c -> c.getSimpleName().startsWith(constructorHint));
                    }
                    classInfoList = classInfoList.filter(c -> isAssignable(c.loadClass(), parameterInfo.getTypeSignatureOrTypeDescriptor()));
                    constructorHints(classInfoList);
                }

            }
        } else {
            logger.debug("Object class type and no constructor prefix, unable to provide specific hint.");
        }

    }

    private MethodInfoList findMethods(ConstantExpression methodReference, ArgumentListExpression argumentExpressions, ScanResult scanResult) {
        ClassInfo classInfo = scanResult.getClassInfo(methodReference.getType().getName());
        MethodInfoList methodList = classInfo.getMethodInfo().filter(m -> m.getName().equals((String) methodReference.getValue()) && m.getParameterInfo().length == argumentExpressions.getExpressions().size());
        //TODO filter further by arguement types
        return methodList;

    }

    private void constructorHints(ClassInfoList classInfoList) {
        for (ClassInfo classInfo : classInfoList) {
            int packageLength = classInfo.getPackageName() != null ? classInfo.getPackageName().length() + 1 : 0;
            int[] entered = new int[] { packageLength, constructorHint.length() };
            for (MethodInfo constInfo : classInfo.getConstructorInfo()) {
                StringBuilder displayed = new StringBuilder(classInfo.getName()).append("(");
                StringBuilder value = new StringBuilder(classInfo.getSimpleName()).append("(");
                for (int i = 0; i < constInfo.getParameterInfo().length; i++) {
                    MethodParameterInfo param = constInfo.getParameterInfo()[i];
                    String paramName = null;
                    paramName = variableNameByType(param.getTypeSignatureOrTypeDescriptor());
                    if (paramName == null) {
                        paramName = param.getName() != null ? param.getName() : "param" + (i > 0 ? i + 1 : "");
                    }
                    displayed.append(param.getTypeDescriptor().toStringWithSimpleNames()).append(" ").append(paramName);
                    value.append(paramName);
                    if (i != constInfo.getParameterInfo().length - 1) {
                        displayed.append(", ");
                        value.append(", ");
                    }
                }
                displayed.append(")");
                value.append(")");
                hints.add(new Hint(entered, displayed.toString(), value.toString()));
            }
        }
    }

    private String variableNameByType(TypeSignature type) {
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

    private static boolean isAssignable(Class<?> clazz, TypeSignature type) {
        if (type instanceof ArrayTypeSignature) {
            ArrayTypeSignature arrayTypeSignature = (ArrayTypeSignature) type;
            if (clazz.isAssignableFrom(arrayTypeSignature.loadClass())) {
                return true;
            }
        } else if (type instanceof BaseTypeSignature) {
            BaseTypeSignature baseTypeSignature = (BaseTypeSignature) type;
            if (clazz.isAssignableFrom(baseTypeSignature.getType())) {
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

    List<Hint> hints() {
        return hints != null ? hints : Collections.emptyList();
    }

    private void printASTDetails(ASTNode e, String format, Object... args) {
        if (true) {
            Object[] lineArgs = new Object[] { e.getLineNumber(), e.getLastLineNumber(), e.getColumnNumber(), e.getLastColumnNumber(), e.getText() };
            Object[] formatArgs = Arrays.copyOf(lineArgs, lineArgs.length + args.length);
            System.arraycopy(args, 0, formatArgs, lineArgs.length, args.length);

            GroovyCloudEditorRS.logger.info(String.format("line: %d-%d %d-%d - %s\t" + format, formatArgs));

        }
    }

    public static class AutoCompleteOperationConfig {
        private final int line;
        private final int ch;
        private final String sticky;
        private boolean autoImport = false;
        private List<String> acceptPackages;
        private List<String> rejectPackages;

        public AutoCompleteOperationConfig(int line, int ch, String sticky) {
            this.line = line;
            this.ch = ch;
            this.sticky = sticky;
        }

        public int getLine() {
            return line;
        }

        public int getCh() {
            return ch;
        }

        public String getSticky() {
            return sticky;
        }

        public boolean isAutoImport() {
            return autoImport;
        }

        public void setAutoImport(boolean autoImport) {
            this.autoImport = autoImport;
        }

        public AutoCompleteOperationConfig withAutoImport(boolean autoImport) {
            this.autoImport = autoImport;
            return this;
        }

        public List<String> getAcceptPackages() {
            return acceptPackages;
        }

        public void setAcceptPackages(List<String> acceptPackages) {
            this.acceptPackages = acceptPackages;
        }

        public AutoCompleteOperationConfig withAcceptPackages(List<String> acceptPackages) {
            this.acceptPackages = acceptPackages;
            return this;
        }

        public List<String> getRejectPackages() {
            return rejectPackages;
        }

        public void setRejectPackages(List<String> rejectPackages) {
            this.rejectPackages = rejectPackages;
        }

        public AutoCompleteOperationConfig withRejectPackages(List<String> rejectPackages) {
            this.rejectPackages = rejectPackages;
            return this;
        }

    }

    public static class Hint {
        final private int[] entered;
        final private String displayed;
        final private String value;
        final private String importValue;
        final private String importLine;

        public Hint(int[] enteredText, String displayText, String value, String importValue, String importLine) {
            this.entered = enteredText;
            this.displayed = displayText;
            this.value = value;
            this.importValue = importValue;
            this.importLine = importLine;
        }

        public Hint(int[] entered, String displayed, String value) {
            this.entered = entered;
            this.displayed = displayed;
            this.value = value;
            this.importValue = null;
            this.importLine = null;
        }

        public int[] getEntered() {
            return entered;
        }

        public String getDisplayed() {
            return displayed;
        }

        public String getValue() {
            return value;
        }

        public String getImportValue() {
            return importValue;
        }

        public String getImportLine() {
            return importLine;
        }

    }

    private class AutoCompleteVisitor extends CodeVisitorSupport {

        private final LinkedList<ASTNode> targetNodes = new LinkedList<>();
        private final LinkedList<VariableScope> targetVariableScopes = new LinkedList<>();
        private final LinkedList<VariableScope> variableScopes = new LinkedList<>();

        public LinkedList<ASTNode> getTargetNodes() {
            return targetNodes;
        }

        public LinkedList<VariableScope> getTargetVariableScopes() {
            return targetVariableScopes;
        }

        public LinkedList<VariableScope> getVariableScopes() {
            return variableScopes;
        }

        private void evaluateAutoComplete(ASTNode node) {
            if (node.getLineNumber() <= config.getLine() + 1 && config.getLine() + 1 <= node.getLastLineNumber()) {
                if (targetNodes.isEmpty()) {
                    targetVariableScopes.addAll(variableScopes);
                }
                targetNodes.push(node);
            }
            //full source needs to be evaluated in case there are function definitions after the hint match
        }

        //visitor methods;
        @Override
        public void visitBlockStatement(BlockStatement block) {
            printASTDetails(block, "visitBlockStatement\n");
            getVariableScopes().push(block.getVariableScope());
            super.visitBlockStatement(block);
            getVariableScopes().pop();
        }

        @Override
        public void visitForLoop(ForStatement forLoop) {
            printASTDetails(forLoop, "visitForLoop\n");
            getVariableScopes().push(forLoop.getVariableScope());
            super.visitForLoop(forLoop);
            getVariableScopes().pop();

        }

        @Override
        public void visitWhileLoop(WhileStatement loop) {
            printASTDetails(loop, "visitWhileLoop\n");
            super.visitWhileLoop(loop);

        }

        @Override
        public void visitDoWhileLoop(DoWhileStatement loop) {
            printASTDetails(loop, "visitDoWhileLoop\n");
            super.visitDoWhileLoop(loop);

        }

        @Override
        public void visitIfElse(IfStatement ifElse) {
            printASTDetails(ifElse, "visitIfElse\n");
            super.visitIfElse(ifElse);
        }

        @Override
        public void visitExpressionStatement(ExpressionStatement statement) {
            printASTDetails(statement, "visitExpressionStatement\n");
            super.visitExpressionStatement(statement);
        }

        @Override
        public void visitReturnStatement(ReturnStatement statement) {
            printASTDetails(statement, "visitReturnStatement\n");
            super.visitReturnStatement(statement);
        }

        @Override
        public void visitAssertStatement(AssertStatement statement) {
            printASTDetails(statement, "visitAssertStatement\n");
            super.visitAssertStatement(statement);
        }

        @Override
        public void visitTryCatchFinally(TryCatchStatement statement) {
            printASTDetails(statement, "visitTryCatchFinally\n");
            super.visitTryCatchFinally(statement);
        }

        @Override
        public void visitEmptyStatement(EmptyStatement statement) {
            printASTDetails(statement, "visitEmptyStatement\n");
            super.visitEmptyStatement(statement);
        }

        @Override
        public void visitSwitch(SwitchStatement statement) {
            printASTDetails(statement, "visitSwitch\n");
            super.visitSwitch(statement);
        }

        @Override
        protected void afterSwitchConditionExpressionVisited(SwitchStatement statement) {
            printASTDetails(statement, "afterSwitchConditionExpressionVisited\n");
            super.afterSwitchConditionExpressionVisited(statement);
        }

        @Override
        public void visitCaseStatement(CaseStatement statement) {
            printASTDetails(statement, "visitCaseStatement\n");
            super.visitCaseStatement(statement);
        }

        @Override
        public void visitBreakStatement(BreakStatement statement) {
            printASTDetails(statement, "visitBreakStatement\n");
            super.visitBreakStatement(statement);
        }

        @Override
        public void visitContinueStatement(ContinueStatement statement) {
            printASTDetails(statement, "visitContinueStatement\n");
            super.visitContinueStatement(statement);
        }

        @Override
        public void visitSynchronizedStatement(SynchronizedStatement statement) {
            printASTDetails(statement, "visitSynchronizedStatement\n");
            super.visitSynchronizedStatement(statement);
        }

        @Override
        public void visitThrowStatement(ThrowStatement statement) {
            printASTDetails(statement, "visitThrowStatement\n");
            super.visitThrowStatement(statement);
        }

        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression call) {
            printASTDetails(call, "visitConstructorCallExpression\n");
            evaluateAutoComplete(call);
            super.visitConstructorCallExpression(call);
        }

        @Override
        public void visitBinaryExpression(BinaryExpression expression) {
            printASTDetails(expression, "visitBinaryExpression\n");
            super.visitBinaryExpression(expression);
        }

        @Override
        public void visitTernaryExpression(TernaryExpression expression) {
            printASTDetails(expression, "visitTernaryExpression\n");
            super.visitTernaryExpression(expression);
        }

        @Override
        public void visitShortTernaryExpression(ElvisOperatorExpression expression) {
            printASTDetails(expression, "visitShortTernaryExpression\n");
            super.visitShortTernaryExpression(expression);
        }

        @Override
        public void visitPostfixExpression(PostfixExpression expression) {
            printASTDetails(expression, "visitPostfixExpression\n");
            super.visitPostfixExpression(expression);
        }

        @Override
        public void visitPrefixExpression(PrefixExpression expression) {
            printASTDetails(expression, "visitPrefixExpression\n");
            super.visitPrefixExpression(expression);
        }

        @Override
        public void visitBooleanExpression(BooleanExpression expression) {
            printASTDetails(expression, "visitBooleanExpression\n");
            super.visitBooleanExpression(expression);
        }

        @Override
        public void visitNotExpression(NotExpression expression) {
            printASTDetails(expression, "visitNotExpression\n");
            super.visitNotExpression(expression);
        }

        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            printASTDetails(expression, "visitClosureExpression\n");
            super.visitClosureExpression(expression);
        }

        @Override
        public void visitLambdaExpression(LambdaExpression expression) {
            printASTDetails(expression, "visitLambdaExpression\n");
            getVariableScopes().push(expression.getVariableScope());
            super.visitLambdaExpression(expression);
            getVariableScopes().pop();
        }

        @Override
        public void visitTupleExpression(TupleExpression expression) {
            printASTDetails(expression, "visitTupleExpression\n");
            super.visitTupleExpression(expression);
        }

        @Override
        public void visitListExpression(ListExpression expression) {
            printASTDetails(expression, "visitListExpression\n");
            super.visitListExpression(expression);
        }

        @Override
        public void visitArrayExpression(ArrayExpression expression) {
            printASTDetails(expression, "visitArrayExpression\n");
            super.visitArrayExpression(expression);
        }

        @Override
        public void visitMapExpression(MapExpression expression) {
            printASTDetails(expression, "visitMapExpression\n");
            super.visitMapExpression(expression);
        }

        @Override
        public void visitMapEntryExpression(MapEntryExpression expression) {
            printASTDetails(expression, "visitMapEntryExpression\n");
            super.visitMapEntryExpression(expression);
        }

        @Override
        public void visitRangeExpression(RangeExpression expression) {
            printASTDetails(expression, "visitRangeExpression\n");
            super.visitRangeExpression(expression);
        }

        @Override
        public void visitSpreadExpression(SpreadExpression expression) {
            printASTDetails(expression, "visitSpreadExpression\n");
            super.visitSpreadExpression(expression);
        }

        @Override
        public void visitSpreadMapExpression(SpreadMapExpression expression) {
            printASTDetails(expression, "visitSpreadMapExpression\n");
            super.visitSpreadMapExpression(expression);
        }

        @Override
        public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
            printASTDetails(expression, "visitUnaryMinusExpression\n");
            super.visitUnaryMinusExpression(expression);
        }

        @Override
        public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
            printASTDetails(expression, "visitUnaryPlusExpression\n");
            super.visitUnaryPlusExpression(expression);
        }

        @Override
        public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
            printASTDetails(expression, "visitBitwiseNegationExpression\n");
            super.visitBitwiseNegationExpression(expression);
        }

        @Override
        public void visitCastExpression(CastExpression expression) {
            printASTDetails(expression, "visitCastExpression\n");
            super.visitCastExpression(expression);
        }

        @Override
        public void visitConstantExpression(ConstantExpression expression) {
            printASTDetails(expression, "visitConstantExpression\n");
            super.visitConstantExpression(expression);
        }

        @Override
        public void visitClassExpression(ClassExpression expression) {
            printASTDetails(expression, "visitClassExpression\n");
            super.visitClassExpression(expression);
        }

        @Override
        public void visitDeclarationExpression(DeclarationExpression expression) {
            printASTDetails(expression, "visitDeclarationExpression\n");
            super.visitDeclarationExpression(expression);
        }

        @Override
        public void visitPropertyExpression(PropertyExpression expression) {
            printASTDetails(expression, "visitPropertyExpression\n");
            super.visitPropertyExpression(expression);
        }

        @Override
        public void visitAttributeExpression(AttributeExpression expression) {
            printASTDetails(expression, "visitAttributeExpression\n");
            super.visitAttributeExpression(expression);
        }

        @Override
        public void visitFieldExpression(FieldExpression expression) {
            printASTDetails(expression, "visitFieldExpression\n");
            super.visitFieldExpression(expression);
        }

        @Override
        public void visitGStringExpression(GStringExpression expression) {
            printASTDetails(expression, "visitGStringExpression\n");
            super.visitGStringExpression(expression);
        }

        @Override
        public void visitCatchStatement(CatchStatement statement) {
            printASTDetails(statement, "visitCatchStatement\n");
            super.visitCatchStatement(statement);
        }

        @Override
        public void visitArgumentlistExpression(ArgumentListExpression expression) {
            printASTDetails(expression, "visitArgumentlistExpression\n");
            super.visitArgumentlistExpression(expression);
        }

        @Override
        public void visitClosureListExpression(ClosureListExpression expression) {
            printASTDetails(expression, "visitClosureListExpression\n");
            super.visitClosureListExpression(expression);
        }

        @Override
        public void visitBytecodeExpression(BytecodeExpression expression) {
            printASTDetails(expression, "visitBytecodeExpression\n");
            super.visitBytecodeExpression(expression);
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            printASTDetails(call, "visitMethodCallExpression\n");
            evaluateAutoComplete(call);
            super.visitMethodCallExpression(call);
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
            printASTDetails(call, "visitStaticMethodCallExpression\n");
            super.visitStaticMethodCallExpression(call);
        }

        @Override
        public void visitMethodPointerExpression(MethodPointerExpression expression) {
            printASTDetails(expression, "visitMethodPointerExpression\n");
            super.visitMethodPointerExpression(expression);
        }

        @Override
        public void visitMethodReferenceExpression(MethodReferenceExpression expression) {
            printASTDetails(expression, "visitMethodReferenceExpression\n");
            super.visitMethodReferenceExpression(expression);
        }

        @Override
        public void visitVariableExpression(VariableExpression expression) {
            printASTDetails(expression, "visitVariableExpression\n");
            evaluateAutoComplete(expression);
            super.visitVariableExpression(expression);
        }

    }

}
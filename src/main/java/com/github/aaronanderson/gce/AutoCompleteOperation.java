package com.github.aaronanderson.gce;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

public class AutoCompleteOperation implements ISourceUnitOperation {

    static Logger logger = LoggerFactory.getLogger(AutoCompleteOperation.class);

    private final int line;
    private final int ch;
    private final String sticky;

    private JsonArray hints;
    private String constructorHint = null;

    public AutoCompleteOperation(int line, int ch, String sticky) {
        this.line = line;
        this.ch = ch;
        this.sticky = sticky;
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

        List<String> packages = new LinkedList<>();
        packages.add("java.lang");
        List<String> classes = new LinkedList<>();

        source.getAST().getImports().forEach(i -> {
            printASTDetails(i, "Import %s\n", i.getPackageName());
            packages.add(i.getPackageName());
        });
        source.getAST().getStarImports().forEach(i -> {
            printASTDetails(i, "Star Import %s\n", i.getPackageName());
            packages.add(i.getPackageName());
        });
        source.getAST().getStaticImports().entrySet().forEach(i -> {
            printASTDetails(i.getValue(), "Static Import %s %s\n", i.getKey(), i.getValue().getPackageName());
            packages.add(i.getValue().getPackageName());
        });
        source.getAST().getStaticStarImports().entrySet().forEach(i -> {
            printASTDetails(i.getValue(), "Static Star Import %s %s\n", i.getKey(), i.getValue().getPackageName());
            packages.add(i.getValue().getPackageName());
        });

        AutoCompleteVisitor visitor = new AutoCompleteVisitor();
        try {
            source.getAST().getStatementBlock().visit(visitor);
            for (MethodNode method : source.getAST().getMethods()) {
                method.getCode().visit(visitor);
            }
        } catch (LineMatchException le) {
            //ignore, thrown to skip processing the rest of the file
        }

        LinkedList<ASTNode> targetNodes = visitor.getTargetNodes();
        if (targetNodes.size() > 0) {
            logger.info(String.format("Target %d ASTNodes found for line %d column %d", targetNodes.size(), line + 1, ch));
            while (targetNodes.size() > 0) {
                ASTNode node = targetNodes.pop();
                if (node.getLineNumber() <= line + 1 && line + 1 <= node.getLastLineNumber() && targetNodes.peek() != null) {
                    ASTNode prevNode = targetNodes.peek();
                    logger.info(String.format("Target ASTNode %s found for line %d-%d column %d-%d %s", node, node.getLineNumber(), node.getLastLineNumber(), node.getColumnNumber(), node.getLastColumnNumber(), node.getText()));
                    logger.info(String.format("Prev   ASTNode %s found for line %d-%d column %d-%d %s", prevNode, prevNode.getLineNumber(), prevNode.getLastLineNumber(), prevNode.getColumnNumber(), prevNode.getLastColumnNumber(), prevNode.getText()));
                    break;
                }
            }

        } else {
            logger.warn(String.format("Target ASTNodes unavailable for line %d column %d", line + 1, ch));
        }

        //System.out.format("Scan packages: %s\n", packages);

        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackagesNonRecursive(packages.toArray(new String[packages.size()]))
                .enableSystemJarsAndModules()
                .scan()) {
            for (String name : scanResult.getAllClasses().getNames()) {
                // System.out.println(name);
            }

        }

    }

    JsonArray hints() {
        return hints != null ? hints : Json.createArrayBuilder().build();
    }

    private void printASTDetails(ASTNode e, String format, Object... args) {
        if (true) {
            Object[] lineArgs = new Object[] { e.getLineNumber(), e.getLastLineNumber(), e.getColumnNumber(), e.getLastColumnNumber(), e.getText() };
            Object[] formatArgs = Arrays.copyOf(lineArgs, lineArgs.length + args.length);
            System.arraycopy(args, 0, formatArgs, lineArgs.length, args.length);

            GroovyCloudEditorRS.logger.info(String.format("line: %d-%d %d-%d - %s\t" + format, formatArgs));

        }
    }

    private class AutoCompleteVisitor extends CodeVisitorSupport {

        private final LinkedList<ASTNode> targetNodes = new LinkedList<>();

        public LinkedList<ASTNode> getTargetNodes() {
            return targetNodes;
        }

        private void evaluateAutoComplete(ASTNode node) {
            if (node.getLineNumber() <= line + 1 && line + 1 <= node.getLastLineNumber()) {
                targetNodes.push(node);
            }
            if (node.getLineNumber() > line + 1) {
                throw new LineMatchException(); //short circut
            }
        }

        //visitor methods;
        @Override
        public void visitBlockStatement(BlockStatement block) {
            printASTDetails(block, "visitBlockStatement\n");
            super.visitBlockStatement(block);

        }

        @Override
        public void visitForLoop(ForStatement forLoop) {
            printASTDetails(forLoop, "visitForLoop\n");
            super.visitForLoop(forLoop);

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
            super.visitLambdaExpression(expression);
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
            String variable = "";
            if (call.getReceiver() instanceof VariableExpression) {
                VariableExpression target = (VariableExpression) call.getReceiver();
                variable = (target.getAccessedVariable() != null ? target.getAccessedVariable().getName() + " " : "") + target.getType().getText();
            }
            printASTDetails(call, "visitMethodCallExpression %s  %s -> %s\n", variable, call.getArguments().getText(), call.getMethodAsString());
            evaluateAutoComplete(call);
            super.visitMethodCallExpression(call);
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
            printASTDetails(call, "visitStaticMethodCallExpression %s\n", call.getMethod());
            super.visitStaticMethodCallExpression(call);
        }

        @Override
        public void visitMethodPointerExpression(MethodPointerExpression expression) {
            printASTDetails(expression, "visitMethodPointerExpression %s\n", expression.getMethodName().getText());
            super.visitMethodPointerExpression(expression);
        }

        @Override
        public void visitMethodReferenceExpression(MethodReferenceExpression expression) {
            printASTDetails(expression, "visitMethodReferenceExpression %s\n", expression.getMethodName().getText());
            super.visitMethodReferenceExpression(expression);
        }

        @Override
        public void visitVariableExpression(VariableExpression expression) {
            printASTDetails(expression, "visitVariableExpression %s (%s)\n", expression.getAccessedVariable() != null ? expression.getAccessedVariable().getName() : "", expression.getType().getText());
            evaluateAutoComplete(expression);
            super.visitVariableExpression(expression);
        }

    }

    public static class LineMatchException extends RuntimeException {

    }

}
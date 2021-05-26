package com.github.aaronanderson.gce;

import static com.github.aaronanderson.gce.AutoCompleteAnalyzer.printASTDetails;

import java.util.LinkedList;
import java.util.Map.Entry;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.ModuleNode;
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

class AutoCompleteVisitor extends CodeVisitorSupport {

    private final AutoCompleteRequest autoCompleteRequest;

    AutoCompleteVisitor(AutoCompleteRequest autoCompleteRequest) {
        this.autoCompleteRequest = autoCompleteRequest;
    }

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

    void visitImports(ModuleNode module) {
        for (ImportNode node : module.getImports()) {
            if (lineMatch(node)) {
                targetNodes.push(node);
            }
        }
        for (ImportNode node : module.getStarImports()) {
            if (lineMatch(node)) {
                targetNodes.push(node);
            }
        }
        for (Entry<String, ImportNode> i : module.getStaticImports().entrySet()) {
            if (lineMatch(i.getValue())) {
                targetNodes.push(i.getValue());
            }
        }
        for (Entry<String, ImportNode> i : module.getStaticStarImports().entrySet()) {
            if (lineMatch(i.getValue())) {
                targetNodes.push(i.getValue());
            }
        }
    }

    private boolean lineMatch(ASTNode node) {
        return node.getLineNumber() <= this.autoCompleteRequest.getLine() + 1 && this.autoCompleteRequest.getLine() + 1 <= node.getLastLineNumber();
    }

    private void evaluateAutoComplete(ASTNode node) {
        if (lineMatch(node)) {
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

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        printASTDetails(expression, "visitPropertyExpression\n");
        evaluateAutoComplete(expression);
        super.visitPropertyExpression(expression);
    }

}
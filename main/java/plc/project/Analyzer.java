package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;

    private Environment.Type currentFunctionReturnType = null;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        for (Ast.Global global : ast.getGlobals()) {
            visit(global);
        }

        for (Ast.Function function : ast.getFunctions()) {
            visit(function);
        }
        boolean mainFunctionExists = false;
        for (Ast.Function function : ast.getFunctions()) {
            if (function.getName().equals("main") && function.getParameters().isEmpty()) {
                mainFunctionExists = true;
                if (!function.getReturnTypeName().orElse("").equals("Integer")) {
                    throw new RuntimeException("The main/0 function does not have an Integer return type.");
                }
                break;
            }
        }

        if (!mainFunctionExists) {
            throw new RuntimeException("A main/0 function does not exist.");
        }

        return null;

    }

    @Override
    public Void visit(Ast.Global ast) {
        Environment.Type type;
        String typeName = ast.getTypeName();
        type = Environment.getType(typeName);

        if (ast.getValue().isPresent()) {
            Ast.Expression value = ast.getValue().get();
            visit(value); // Visit the value expression first
            Analyzer.requireAssignable(type, value.getType());
        }

        Environment.Variable variable = new Environment.Variable(ast.getName(), ast.getName(), type, ast.getMutable(), Environment.NIL);
        this.scope.defineVariable(ast.getName(), ast.getName(), type, ast.getMutable(), Environment.NIL);
        ast.setVariable(variable);

        return null;
    }
    @Override
    public Void visit(Ast.Function ast) {
//        List<Environment.Type> parameterTypes = ast.getParameters().stream()
//                .map(param -> Environment.getType(param))
//                .collect(Collectors.toList());
//        Environment.Type returnType = ast.getReturnTypeName().isPresent() ?
//                Environment.getType(ast.getReturnTypeName().get()) : Environment.Type.NIL;
//        Environment.Function function = new Environment.Function(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
//        this.scope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
//        ast.setFunction(function);
//        Scope functionScope = new Scope(this.scope);
//        for (String param : ast.getParameters()) {
//            functionScope.defineVariable(param, param, Environment.getType(param), false, Environment.NIL);
//        }
//        this.scope = functionScope;
//        for (Ast.Statement stmt : ast.getStatements()) {
//            visit(stmt);
//        }
//        this.scope = this.scope.getParent();
//
//        return null;
        List<Environment.Type> parameterTypes = ast.getParameterTypeNames().stream()
                .map(Environment::getType)
                .collect(Collectors.toList());
        Environment.Type returnType = ast.getReturnTypeName().isPresent() ?
                Environment.getType(ast.getReturnTypeName().get()) : Environment.Type.NIL;
        Environment.Function function = new Environment.Function(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        this.scope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        ast.setFunction(function);
        Scope functionScope = new Scope(this.scope);
        for (int i = 0; i < ast.getParameters().size(); i++) {
            String paramName = ast.getParameters().get(i);
            Environment.Type paramType = parameterTypes.get(i);
            functionScope.defineVariable(paramName, paramName, paramType, false, Environment.NIL);
        }
        this.scope = functionScope;
        this.currentFunctionReturnType = returnType;
        for (Ast.Statement stmt : ast.getStatements()) {
            if (stmt instanceof Ast.Statement.Expression) {
                Ast.Expression expr = ((Ast.Statement.Expression) stmt).getExpression();
                if (expr instanceof Ast.Expression.Function) {
                    visit(expr); // Visit the function expression
                }
            }
        }
        for (Ast.Statement stmt : ast.getStatements()) {
            visit(stmt);
        }
        this.scope = this.scope.getParent();
        this.currentFunctionReturnType = null;

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Invalid expression statement: only function calls can cause a side effect.");
        }
        visit(ast.getExpression());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        Environment.Type type;
        if (ast.getValue().isPresent()) {
            Ast.Expression value = ast.getValue().get();
            visit(value); // Visit the value expression first
            type = value.getType();
        } else {
            String typeName = ast.getTypeName().orElseThrow(() -> new RuntimeException("Type not found in variable declaration."));
            type = Environment.getType(typeName);
        }
        Environment.Variable variable = new Environment.Variable(ast.getName(), ast.getName(), type, true, Environment.NIL);
        this.scope.defineVariable(ast.getName(), ast.getName(), type, true, Environment.NIL);
        ast.setVariable(variable);
        if (ast.getValue().isPresent()) {
            Ast.Expression value = ast.getValue().get();
            Analyzer.requireAssignable(type, value.getType());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {

        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Invalid assignment statement: receiver must be an access expression.");
        }
        visit(ast.getReceiver());
        visit(ast.getValue());
        if (!ast.getValue().getType().getName().equals(ast.getReceiver().getType().getName())) {
            throw new RuntimeException("Type mismatch in assignment statement.");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        if (!ast.getCondition().getType().getName().equals("Boolean")) {
            throw new RuntimeException("Invalid if statement: condition must be of type Boolean.");
        }
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("Invalid if statement: thenStatements list cannot be empty.");
        }
        Scope thenScope = new Scope(this.scope);
        this.scope = thenScope;
        for (Ast.Statement stmt : ast.getThenStatements()) {
            visit(stmt);
        }
        this.scope = this.scope.getParent(); // Reset the scope to the parent scope
        if (!ast.getElseStatements().isEmpty()) {
            Scope elseScope = new Scope(this.scope);
            this.scope = elseScope;
            for (Ast.Statement stmt : ast.getElseStatements()) {
                visit(stmt);
            }
            this.scope = this.scope.getParent(); // Reset the scope to the parent scope
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        visit(ast.getCondition());
        Environment.Type conditionType = ast.getCondition().getType();
        List<Ast.Statement.Case> cases = ast.getCases();
        for (int i = 0; i < cases.size(); i++) {
            Ast.Statement.Case caseStmt = cases.get(i);
            if (caseStmt.getValue().isPresent()) {
                visit(caseStmt.getValue().get());
                if (i == cases.size() - 1) {
                    throw new RuntimeException("Default switch case must not have a value.");
                }
                if (!caseStmt.getValue().get().getType().equals(conditionType)) {
                    throw new RuntimeException("Type mismatch in switch case value.");
                }
            }
            visit(caseStmt);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        Scope caseScope = new Scope(this.scope);
        this.scope = caseScope;
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }
        for (Ast.Statement stmt : ast.getStatements()) {
            visit(stmt);
        }
        this.scope = this.scope.getParent();

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {

        visit(ast.getCondition());
        if (!ast.getCondition().getType().getName().equals("Boolean")) {
            throw new RuntimeException();
        }
        Scope loopScope = new Scope(this.scope);
        this.scope = loopScope;
        for (Ast.Statement stmt : ast.getStatements()) {
            visit(stmt);
        }
        this.scope = this.scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        visit(ast.getValue());
        Analyzer.requireAssignable(this.currentFunctionReturnType, ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {

        Object value = ast.getLiteral();

        if (value instanceof BigInteger) {
            BigInteger bigIntegerValue = (BigInteger) value;
            if (bigIntegerValue.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 ||
                    bigIntegerValue.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new RuntimeException();
            }
            ast.setType(Environment.Type.INTEGER);
        } else if (value instanceof BigDecimal) {
            BigDecimal bigDecimalValue = (BigDecimal) value;
            double doubleValue = bigDecimalValue.doubleValue();
            if (doubleValue == Double.NEGATIVE_INFINITY || doubleValue == Double.POSITIVE_INFINITY) {
                throw new RuntimeException();
            }
            ast.setType(Environment.Type.DECIMAL);
        } else if (value instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (value instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (value instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (value == null) {
            ast.setType(Environment.Type.NIL);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {

        if (!(ast.getExpression() instanceof Ast.Expression.Binary)) {
            throw new RuntimeException();
        }
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());
        Environment.Type leftType = ast.getLeft().getType();
        Environment.Type rightType = ast.getRight().getType();

        switch (ast.getOperator()) {
            case "&&":
            case "||":
                if (!leftType.equals(Environment.Type.BOOLEAN) || !rightType.equals(Environment.Type.BOOLEAN)) {
                    throw new RuntimeException();
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "<":
            case ">":
            case "==":
            case "!=":
                if (!leftType.equals(rightType) ||
                        (!leftType.equals(Environment.Type.INTEGER) && !leftType.equals(Environment.Type.DECIMAL) &&
                                !leftType.equals(Environment.Type.CHARACTER) && !leftType.equals(Environment.Type.STRING))) {
                    throw new RuntimeException();
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "+":
                if (leftType.equals(Environment.Type.STRING) || rightType.equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                } else if ((leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL)) && leftType.equals(rightType)) {
                    ast.setType(leftType);
                } else {
                    throw new RuntimeException();
                }
                break;
            case "-":
            case "*":
            case "/":
                if ((leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL)) && leftType.equals(rightType)) {
                    ast.setType(leftType);
                } else {
                    throw new RuntimeException();
                }
                break;
            case "^":
                if (!leftType.equals(Environment.Type.INTEGER) || !rightType.equals(Environment.Type.INTEGER)) {
                    throw new RuntimeException();
                }
                ast.setType(Environment.Type.INTEGER);
                break;
            default:
                throw new RuntimeException();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {

//        if (ast.getOffset().isPresent()) {
//            visit(ast.getOffset().get());
//            if (!ast.getOffset().get().getType().getName().equals("Integer")) {
//                throw new RuntimeException();
//            }
//        }
//        Environment.Variable variable = this.scope.lookupVariable(ast.getName());
//        ast.setVariable(variable);
//        return null;
        if (ast.getOffset().isPresent()) {
            visit(ast.getOffset().get());
            if (!ast.getOffset().get().getType().getName().equals("Integer")) {
                throw new RuntimeException();
            }
        }
        Environment.Variable variable = this.scope.lookupVariable(ast.getName());
        ast.setVariable(variable);
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {

        Environment.Function function = this.scope.lookupFunction(ast.getName(), ast.getArguments().size());
        if (function == null) {
            throw new RuntimeException("Function '" + ast.getName() + "' with " + ast.getArguments().size() + " arguments not found in current scope.");
        }
        ast.setFunction(function);

        for (int i = 0; i < ast.getArguments().size(); i++) {
            Ast.Expression argument = ast.getArguments().get(i);
            visit(argument);
            Environment.Type parameterType = function.getParameterTypes().get(i);
            if (!parameterType.equals(Environment.Type.ANY) && !argument.getType().equals(parameterType)) {
                throw new RuntimeException("Type of argument " + (i+1) + " does not match the corresponding parameter type in function '" + ast.getName() + "'.");
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        for (Ast.Expression expression : ast.getValues()) {
            visit(expression);
            if (!expression.getType().getScope().equals(ast.getType().getScope())) {
                throw new RuntimeException();
            }
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target.equals(Environment.Type.ANY)) {
            return;
        }
        if (target.equals(Environment.Type.COMPARABLE) &&
                (type.equals(Environment.Type.INTEGER) || type.equals(Environment.Type.DECIMAL) ||
                        type.equals(Environment.Type.CHARACTER) || type.equals(Environment.Type.STRING))) {
            return;
        }
        if (target.equals(type)) {
            return;
        }
        throw new RuntimeException("Cannot assign a value of type " + type + " to a variable of type " + target);
    }

}

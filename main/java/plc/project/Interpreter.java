package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for(Ast.Global global : ast.getGlobals()){
            visit(global);
        }
        for(Ast.Function function : ast.getFunctions()){
            visit(function);
        }
        if(scope.lookupFunction("main", 0) == null){
            throw new RuntimeException("main function not defined");
        }
        return scope.lookupFunction("main", 0).invoke(Collections.emptyList());
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        if (ast.getValue().isPresent()) {
            Environment.PlcObject value = visit(ast.getValue().get());
            scope.defineVariable(ast.getName(), ast.getMutable(), value);
        } else {
            scope.defineVariable(ast.getName(), ast.getMutable(), Environment.NIL);
        }
        return Environment.NIL; // Global variable declarations do not have a value
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(), arguments -> {
            Scope oldScope = scope;
            scope = new Scope(oldScope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), false, arguments.get(i));
            }
            try {
                for (Ast.Statement statement : ast.getStatements()) {
                    visit(statement);
                }
            } catch (Return returnStatement) {
                return returnStatement.value;
            } finally {
                scope = oldScope;
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        Optional optional = ast.getValue();
        Optional<Ast.Expression> op1 = ast.getValue();
        Boolean present =  optional.isPresent();
        if(present){
            Object object = optional.get();
            Ast.Expression expr = (Ast.Expression) optional.get();
            Ast.Expression expr1= op1.get();

            scope.defineVariable(ast.getName(), true, visit(expr));
        }
        else{
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expression.Access access)) {
            throw new RuntimeException("Only variables can be assigned to.");
        }
        Environment.Variable variable = scope.lookupVariable(access.getName());
        if (!variable.getMutable()) {
            throw new RuntimeException("Cannot assign to final variable " + access.getName());
        }
        Environment.PlcObject value = visit(ast.getValue());
        if (access.getOffset().isPresent()) {
            Environment.PlcObject offset = visit(access.getOffset().get());
            BigInteger offsetValue = requireType(BigInteger.class, offset);
            List<Environment.PlcObject> list = requireType(List.class, variable.getValue());
            if (offsetValue.compareTo(BigInteger.ZERO) < 0 || offsetValue.compareTo(BigInteger.valueOf(list.size())) >= 0) {
                throw new RuntimeException("Index out of bounds: " + offsetValue);
            }
            list.set(offsetValue.intValue(), value);
        } else {
            variable.setValue(value);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Environment.PlcObject condition = visit(ast.getCondition());
        Boolean conditionValue = requireType(Boolean.class, condition);
        Scope childScope = new Scope(scope);
        scope = childScope;
        if (conditionValue) {
            for (Ast.Statement statement : ast.getThenStatements()) {
                visit(statement);
            }
        } else {
            for (Ast.Statement statement : ast.getElseStatements()) {
                visit(statement);
            }
        }
        scope = childScope.getParent();
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        Environment.PlcObject value = visit(ast.getCondition());
        Scope childScope = new Scope(scope);
        scope = childScope;

        boolean matchFound = false;
        Ast.Statement.Case defaultCase = null;

        for (Ast.Statement.Case caseStatement : ast.getCases()) {
            if (caseStatement.getValue().isPresent()) {
                Environment.PlcObject caseValue = visit(caseStatement.getValue().get());

                if (value.equals(caseValue)) {
                    visit(caseStatement);
                    matchFound = true;
                    break;
                }
            } else {
                defaultCase = caseStatement;
            }
        }

        if (!matchFound && defaultCase != null) {
            visit(defaultCase);
        }

        scope = childScope.getParent();
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition()))){
            try{
                scope = new Scope(scope);
                for(Ast.Statement stmt: ast.getStatements()){
                    visit(stmt);
                }
            }
            finally{
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        Environment.PlcObject value = visit(ast.getValue());
        throw new Return(value);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if(ast.getLiteral() == null){
            return Environment.NIL;
        }
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        Environment.PlcObject left = visit(ast.getLeft());
        Environment.PlcObject right = null;

        switch (ast.getOperator()) {
            case "&&":
                Boolean leftBool = requireType(Boolean.class, left);
                if (!leftBool) {
                    return Environment.create(false);
                } else {
                    right = visit(ast.getRight());
                    Boolean rightBool = requireType(Boolean.class, right);
                    return Environment.create(leftBool && rightBool);
                }
            case "||":
                leftBool = requireType(Boolean.class, left);
                if (leftBool) {
                    return Environment.create(true);
                } else {
                    right = visit(ast.getRight());
                    Boolean rightBool = requireType(Boolean.class, right);
                    return Environment.create(leftBool || rightBool);
                }
            case "<":
            case ">":
                right = visit(ast.getRight());
                Comparable leftComp = requireType(Comparable.class, left);
                Comparable rightComp = requireType(leftComp.getClass(), right);
                int comparison = leftComp.compareTo(rightComp);
                return Environment.create(ast.getOperator().equals("<") ? comparison < 0 : comparison > 0);
            case "==":
            case "!=":
                right = visit(ast.getRight());
                boolean equals = Objects.equals(left.getValue(), right.getValue());
                return Environment.create(ast.getOperator().equals("==") ? equals : !equals);
            case "+":
                right = visit(ast.getRight());
                if (left.getValue() instanceof String || right.getValue() instanceof String) {
                    return Environment.create(String.valueOf(left.getValue()) + String.valueOf(right.getValue()));
                } else if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    BigInteger leftInt = (BigInteger) left.getValue();
                    BigInteger rightInt = (BigInteger) right.getValue();
                    return Environment.create(leftInt.add(rightInt));
                }
                // Fall through to the next case if neither operand is a String or BigInteger
            case "-":
            case "*":
            case "/":
                right = visit(ast.getRight());
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    BigInteger leftInt = (BigInteger) left.getValue();
                    BigInteger rightInt = (BigInteger) right.getValue();
                    switch (ast.getOperator()) {
                        case "-":
                            return Environment.create(leftInt.subtract(rightInt));
                        case "*":
                            return Environment.create(leftInt.multiply(rightInt));
                        case "/":
                            if (rightInt.equals(BigInteger.ZERO)) {
                                throw new RuntimeException("Division by zero");
                            }
                            return Environment.create(leftInt.divide(rightInt));
                    }
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    BigDecimal leftNum = (BigDecimal) left.getValue();
                    BigDecimal rightNum = (BigDecimal) right.getValue();
                    if (ast.getOperator().equals("/")) {
                        if (rightNum.compareTo(BigDecimal.ZERO) == 0) {
                            throw new RuntimeException("Division by zero");
                        }
                        return Environment.create(leftNum.divide(rightNum, RoundingMode.HALF_EVEN));
                    }
                    return Environment.create(ast.getOperator().equals("+") ? leftNum.add(rightNum) : ast.getOperator().equals("-") ? leftNum.subtract(rightNum) : leftNum.multiply(rightNum));
                } else {
                    throw new RuntimeException("Invalid operand types for " + ast.getOperator() + " operator");
                }
            case "^":
                right = visit(ast.getRight());
                BigInteger leftInt = requireType(BigInteger.class, left);
                BigInteger rightInt = requireType(BigInteger.class, right);
                return Environment.create(leftInt.pow(rightInt.intValueExact()));
            default:
                throw new UnsupportedOperationException("Unknown operator: " + ast.getOperator());
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        Environment.Variable variable = scope.lookupVariable(ast.getName());
        Environment.PlcObject value = variable.getValue();

        if (ast.getOffset().isPresent()) {
            // If there's an offset, this is a list access
            Environment.PlcObject offsetObject = visit(ast.getOffset().get());
            BigInteger offsetValue;
            if (offsetObject.getValue() instanceof BigInteger) {
                offsetValue = (BigInteger) offsetObject.getValue();
            } else {
                throw new RuntimeException("Expected type java.math.BigInteger, received " + offsetObject.getValue().getClass().getName() + ".");
            }
            List<Environment.PlcObject> list = requireType(List.class, value);
            if (offsetValue.compareTo(BigInteger.ZERO) < 0 || offsetValue.compareTo(BigInteger.valueOf(list.size())) >= 0) {
                throw new RuntimeException("Index out of bounds: " + offsetValue);
            }
            return list.get(offsetValue.intValue());
        } else {
            // If there's no offset, this is a variable access
            return value;
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());

        List<Environment.PlcObject> arguments = new ArrayList<>();
        for (Ast.Expression expression : ast.getArguments()) {
            arguments.add(visit(expression));
        }
        return function.invoke(arguments);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        List<Object> list = new ArrayList<>();
        for (Ast.Expression expression : ast.getValues()) {
            Environment.PlcObject plcObject = visit(expression);
            Object value = plcObject.getValue();
            list.add(value);
        }
        return Environment.create(list);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}

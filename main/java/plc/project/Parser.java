package plc.project;
import java.util.*;
import java.math.BigInteger;
import java.math.BigDecimal;
/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {
    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Global> globals = new ArrayList<>();
        List<Ast.Function> funcs = new ArrayList<>();
        while(tokens.has(0) && (peek("LIST") || peek("VAR") || peek("VAL"))){
            globals.add(parseGlobal());
        }
        while(tokens.has(0) && peek("FUN")){
            funcs.add(parseFunction());
        }
        if(tokens.has(0)){
            throw new ParseException("Unexpected token at "+tokens.get(0), tokens.index);
        }
        return new Ast.Source(globals, funcs);
    }
    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        //global(string name, bool mutable, optional value)
        if (!tokens.has(0)) {
            throw new ParseException("No more tokens", tokens.index);
        }

        if (match("LIST")) {
            return parseList();
        } else if (match("VAR")) {
            return parseMutable();
        } else if (match("VAL")) {
            return parseImmutable();
        } else {
            throw new ParseException("Expected LIST, VAR, or VAL, got " + tokens.get(0), tokens.index);
        }
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        String identifier = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        match(":");
        match(":");
        String typeName = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        match("=");
        match("[");
        List<Ast.Expression> expressions = new ArrayList<>();
        while (!peek("]")) {
            expressions.add(parseExpression());
            if (peek(",")) {
                match(",");
            }
        }
        match("]");
        if (!match(";")) {
            throw new ParseException("Expected ';', got " + tokens.get(0), tokens.index);
        }
        return new Ast.Global(identifier, typeName, true, Optional.of(new Ast.Expression.PlcList(expressions)));
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        if (!tokens.has(0) || !(tokens.get(0).getType() == Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after VAR", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        match(":");
        String typeName = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        // Parse optional '=' and expression
        Ast.Expression value = null;
        if (match("=")) {
            value = parseExpression();
        }
        if (!match(";")) {
            throw new ParseException("Expected ';', got " + tokens.get(0), tokens.index);
        }
        // Create and return the Ast.Global object
        return new Ast.Global(name, typeName, true, Optional.ofNullable(value));
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        if (!tokens.has(0) || !(tokens.get(0).getType() == Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after VAL", tokens.index);
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance();
        match(":");
        String typeName = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        // Parse '='
        if (!match("=")) {
            throw new ParseException("Expected '=' after identifier", tokens.index);
        }

        // Parse expression
        Ast.Expression value = parseExpression();
        if (!match(";")) {
            throw new ParseException("Expected ';', got " + tokens.get(0), tokens.index);
        }
        // Create and return the Ast.Global object
        return new Ast.Global(name, typeName, false, Optional.of(value));
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        match("FUN");
        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        match("(");
        List<String> parameters = new ArrayList<>();
        List<String> parameterTypeNames = new ArrayList<>();
        while (!peek(")")) {
            parameters.add(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
            match(":");
            String typeName = tokens.get(0).getLiteral();
            parameterTypeNames.add(typeName);
            match(Token.Type.IDENTIFIER);
            if (peek(",")) {
                match(",");
            }
        }
        match(")");
        Optional<String> returnTypeName = Optional.of("Any");
        if (peek(":")) {
            match(":");
            returnTypeName = Optional.of(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
        }
        match("DO");
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }
        match("END");
        return new Ast.Function(name, parameters, parameterTypeNames, returnTypeName, statements);
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END") && !peek("ELSE") && !peek("CASE") && !peek("DEFAULT")) {
            statements.add(parseStatement());
        }
        return statements;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        if(match("LET")){
           return parseDeclarationStatement();
        }
        else if(match("SWITCH")){
            return parseSwitchStatement();
        }
        else if (match("IF")){
            return parseIfStatement();
        }
        else if (match("WHILE")){
            return parseWhileStatement();
        }
        else if (match("RETURN")){
            return parseReturnStatement();
        }
        else {
            Ast.Expression expression = parseExpression();
            if (match("=")) {
                Ast.Expression value = parseExpression();
                if (!match(";")) {
                    throw new ParseException("Expected ';' at the end of assignment statement", tokens.index);
                }
                return new Ast.Statement.Assignment(expression, value);
            } else {
                if (!match(";")) {
                    throw new ParseException("Expected ';' at the end of expression statement", tokens.index);
                }

                return new Ast.Statement.Expression(expression);
            }
        }

    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws
            ParseException {
        if(!tokens.has(0) || !(tokens.get(0).getType() == Token.Type.IDENTIFIER)){
            throw new ParseException("Expected Identifier after LET ", tokens.index);
        }
        String identifier = tokens.get(0).getLiteral();
        tokens.advance();
        Optional<String> typeName = Optional.of("Any");
        if(peek(":")){
            match(":");
            typeName = Optional.of(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
        }
        Ast.Expression expression = null;
        if(match("=")){
            expression = parseExpression();
        }
        if (!match(";")) {
            throw new ParseException("Expected ';', got " + tokens.get(0), tokens.index);
        }
        return new Ast.Statement.Declaration(identifier, typeName, Optional.ofNullable(expression));

    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        if(!match("DO")){
            throw new ParseException("Expected DO after expression", tokens.index);
        }
        List<Ast.Statement> thenStatements = parseBlock();
        List<Ast.Statement> elseStatements = new ArrayList<>();
        if(match("ELSE")){
            elseStatements = parseBlock();
        }
        if(!match("END")){
            throw new ParseException("Expected END", tokens.index);
        }
        return new Ast.Statement.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        Ast.Expression expression = parseExpression();

        // Parse the cases
        List<Ast.Statement.Case> cases = new ArrayList<>();
        while (match("CASE") || match("DEFAULT")) {
            // Parse the case or default block
            Ast.Statement.Case caseStatement = parseCaseStatement();

            // Add the case to the list
            cases.add(caseStatement);
        }

        // Expect the END keyword
        if (!match("END")) {
            throw new ParseException("Expected END at the end of SWITCH statement", tokens.index);
        }

        return new Ast.Statement.Switch(expression, cases);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule.
     * This method should only be called if the next tokens start the case or
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        boolean isDefault = tokens.get(0).getLiteral().equals("DEFAULT");

        // Parse the case value
        Optional<Ast.Expression> value = Optional.empty();
        if (!isDefault) {
            value = Optional.of(parseExpression());
        }
        if (!match(":")) {
            throw new ParseException("Expected ':' after case value", tokens.index);
        }
        List<Ast.Statement> statements = parseBlock();

        return new Ast.Statement.Case(value, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        if (!match("DO")) {
            throw new ParseException("Expected DO after condition in WHILE statement", tokens.index);
        }
        List<Ast.Statement> statements = parseBlock();
        if (!match("END")) {
            throw new ParseException("Expected END at the end of WHILE statement", tokens.index);
        }

        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        Ast.Expression value = parseExpression();
        if (!match(";")) {
            throw new ParseException("Expected ';' at the end of RETURN statement", tokens.index);
        }
        return new Ast.Statement.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression expression = parseComparisonExpression();

        while (tokens.has(0) && (peek("&&") || peek("||"))) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expression right = parseComparisonExpression();
            expression = new Ast.Expression.Binary(operator, expression, right);
        }

        return expression;
    }

    /**
     * Parses the {@code comparison-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression expression = parseAdditiveExpression();

        while (tokens.has(0) && (peek("<") || peek(">") || peek("==") || peek("!="))) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expression right = parseAdditiveExpression();
            expression = new Ast.Expression.Binary(operator, expression, right);
        }

        return expression;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression expression = parseMultiplicativeExpression();

        while (tokens.has(0) && (peek("+") || peek("-"))) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expression right = parseMultiplicativeExpression();
            expression = new Ast.Expression.Binary(operator, expression, right);
        }

        return expression;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression expression = parsePrimaryExpression();

        while (tokens.has(0) && (peek("*") || peek("/") || peek("^"))) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expression right = parsePrimaryExpression();
            expression = new Ast.Expression.Binary(operator, expression, right);
        }

        return expression;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
            if (peek("NIL")) {
                tokens.advance();
                return new Ast.Expression.Literal(null);
            } else if (peek("TRUE") || peek("FALSE")) {
                Boolean value = Boolean.parseBoolean(tokens.get(0).getLiteral());
                tokens.advance();
                return new Ast.Expression.Literal(value);
            } else if (peek(Token.Type.INTEGER)) {
                BigInteger value = new BigInteger(tokens.get(0).getLiteral());
                tokens.advance();
                return new Ast.Expression.Literal(value);
            } else if (peek(Token.Type.DECIMAL)) {
                BigDecimal value = new BigDecimal(tokens.get(0).getLiteral());
                tokens.advance();
                return new Ast.Expression.Literal(value);
            } else if (peek(Token.Type.CHARACTER)) {
                char value = tokens.get(0).getLiteral().replace("\\'", "'").charAt(1); // Remove quotes and handle escape characters
                tokens.advance();
                return new Ast.Expression.Literal(value);
            } else if (peek(Token.Type.STRING)) {
                String value = tokens.get(0).getLiteral();
                tokens.advance();
                //map of escape sequences
                Map<String, String> escapeSequences = new HashMap<>();
                escapeSequences.put("\\n", "\n");
                escapeSequences.put("\\t", "\t");
                escapeSequences.put("\\b", "\b");
                escapeSequences.put("\\r", "\r");
                escapeSequences.put("\\f", "\f");
                escapeSequences.put("\\\"", "\"");
                escapeSequences.put("\\\\", "\\");
                //replace escape sequences with actual values
                for (Map.Entry<String, String> entry : escapeSequences.entrySet()) {
                    value = value.replace(entry.getKey(), entry.getValue());
                }
                value = value.substring(1, value.length() - 1);
                return new Ast.Expression.Literal(value);
            } else if (peek("(")) {
                tokens.advance();
                Ast.Expression expression = parseExpression();
                if (!match(")")) {
                    throw new ParseException("Expected ')' after expression", tokens.index);
                }
                return new Ast.Expression.Group(expression);
            } else if (peek(Token.Type.IDENTIFIER)) {
                String name = tokens.get(0).getLiteral();
                tokens.advance();
                if (peek("(")) {
                    tokens.advance();
                    List<Ast.Expression> arguments = new ArrayList<>();
                    while (!peek(")")) {
                        arguments.add(parseExpression());
                        match(",");
                    }
                    if (!match(")")) {
                        throw new ParseException("Expected ')' after function arguments", tokens.index);
                    }
                    return new Ast.Expression.Function(name, arguments);
                } else if (peek("[")) {
                    tokens.advance();
                    Ast.Expression index = parseExpression();
                    if (!match("]")) {
                        throw new ParseException("Expected ']' after array index", tokens.index);
                    }
                    return new Ast.Expression.Access(Optional.of(index), name);
                } else {
                    return new Ast.Expression.Access(Optional.empty(), name);
                }
            } else {
                throw new ParseException("Expected a primary expression but found: " + tokens.get(0), tokens.index);
            }

    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     * <p>
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for(int i = 0; i < patterns.length; i++){
            if(!tokens.has(i)){
                return false;
            }
            else if (patterns[i] instanceof Token.Type){
                if(patterns[i]!=tokens.get(i).getType()){
                    return false;
                }
            }
            else if(patterns[i] instanceof String){
                if(!patterns[i].equals(tokens.get(i).getLiteral())){
                    return false;
                }
            }
            else{
                throw new AssertionError("Invalid pattern object: "+
                        patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if(peek){
            for(int i = 0; i < patterns.length; i++){
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {
        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }
    }
}

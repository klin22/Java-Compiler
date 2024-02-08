package plc.project;

import java.util.List;
import java.util.ArrayList;

/**
 * The lexer works through three main functions:
 * <p>
 * - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 * - {@link #lexToken()}, which lexes the next token
 * - {@link CharStream}, which manages the state of the lexer and literals
 * <p>
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid.
 * <p>
 * The {@link #peek(String...)} and {@link #match(String...)} functions are *
 * helpers you need to use, they will make the implementation a lot easier.
 */
public final class Lexer {
    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();
        while (chars.has(0)) {
            //skip whitespace
            if (Character.isWhitespace(chars.get(0))) {
                chars.advance();
                chars.skip();
            } else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     * <p>
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        //identifier until operator
        //after operator cannot be identifier
        //just make it match a pattern for now

        //how do i peek into just the next token if peek
        //compares the entire charstream to the regex
        if (peek("[A-Za-z]") || peek("@")) {
            return lexIdentifier();
        } else if (peek("[0-9]") || peek("-")) {
            return lexNumber();
        } else if (peek("'")) {
            return lexCharacter();
        } else if (peek("\"")) {
            return lexString();
        } else if (!Character.isWhitespace(chars.get(0))) {
            return lexOperator();
        } else {
            throw new ParseException("Unexpected character: " + chars.get(0), chars.index);
        }
    }

    public Token lexIdentifier() {

        if (peek("@")) {
            chars.advance();
        }
        if (peek("[A-Za-z]")) {
            while (peek("[A-Za-z0-9_-]")) {
                chars.advance();
            }
            return chars.emit(Token.Type.IDENTIFIER);
        } else {
            throw new ParseException("Cannot contain nonalphanumeric", chars.index);
        }
    }

    public Token lexNumber() {
        //check int first
        //if peek -?, if peek 1-9, while peek 0-9
        boolean negative = false;

        if (match("-")) {
            negative = true;
        }
        if (!peek("[0-9]")) {
            throw new ParseException("Expected digit", chars.index);
        }
        if (match("0")) {
            if (match("\\.")) {
                if (!peek("[0-9]")) {
                    throw new ParseException("Digit must follow decimal point", chars.index);
                }
                while (peek("[0-9]")) {
                    chars.advance();
                }
                return chars.emit(Token.Type.DECIMAL);
            } else if (peek("[0-9]")) {
                throw new ParseException("Cannot have leading zeros", chars.index);
            }
            if (negative) {
                throw new ParseException("Cannot be negative without decimal", chars.index);
            } else {
                return chars.emit(Token.Type.INTEGER);
            }
        } else {
            while (peek("[0-9]")) {
                chars.advance();
            }
            if (match("\\.")) {
                if (!peek("[0-9]")) {
                    throw new ParseException("Digit must follow decimal point", chars.index);
                }
                while (peek("[0-9]")) {
                    chars.advance();
                }
                return chars.emit(Token.Type.DECIMAL);
            }
            return chars.emit(Token.Type.INTEGER);
        }
    }

    public Token lexCharacter() {
        if (!match("'")) {
            throw new ParseException("Expected opening single quote for character literal", chars.index);
        }

        if (peek("'")) {
            throw new ParseException("Character literal cannot be empty", chars.index);
        }
        if (match("\\\\")) {
            lexEscape();
        } else {
            if (!peek("[^'\n]")) {
                throw new ParseException("Invalid character: " + chars.get(0), chars.index);
            }
            chars.advance();
        }
        if (!match("'")) {
            throw new ParseException("Expected closing quote", chars.index);
        }

        return chars.emit(Token.Type.CHARACTER);
    }

    public Token lexString() {
        if (!match("\"")) {
            throw new ParseException("String must start with quote", chars.index);
        }
        while (!peek("\"")) {
            if (!chars.has(0)) {
                throw new ParseException("Unterminated string", chars.index);
            }
            if (match("\\\\")) {
                lexEscape();
                continue;
            }
            if (peek("\\n") || peek("\\r")) {
                throw new ParseException("Cannot span multiple lines", chars.index);
            }
            chars.advance();
        }
        if (!match("\"")) {
            throw new ParseException("Expected end quote", chars.index);
        }

        return chars.emit(Token.Type.STRING);
    }

    public void lexEscape() {
        if (!peek("[bnrt'\"\\\\]")) {
            throw new ParseException("Invalid escape sequence: ", chars.index);
        }
        chars.advance();
    }

    public Token lexOperator() {
        //booleans
        if ((peek("!") && chars.has(1) && chars.get(1) == '=') || (peek("=") && chars.has(1) && chars.get(1) == '=') ||
                (peek("&") && chars.has(1) && chars.get(1) == '&') ||
                (peek("\\|") && chars.has(1) && chars.get(1) == '|')) {
            chars.advance();
            chars.advance();
            return chars.emit(Token.Type.OPERATOR);
        }
        //any other operator/character
        if (chars.has(0) && !Character.isWhitespace(chars.get(0)) &&
                !"A-Za-z0-9'\"".contains(String.valueOf(chars.get(0)))) {
            chars.advance();
            return chars.emit(Token.Type.OPERATOR);
        }

        throw new ParseException("Unexpected operator ", chars.index);

    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     * <p>
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {
        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }
    }
}


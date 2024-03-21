package plc.homework;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.regex.Pattern;
import java.util.stream.Stream;
/**
 * Contains JUnit tests for {@link Regex}. A framework of the test structure
 * is provided, you will fill in the remaining pieces.
 *
 * To run tests, either click the run icon on the left margin, which can be used
 * to run all tests or only a specific test. You should make sure your tests are
 * run through IntelliJ (File > Settings > Build, Execution, Deployment > Build
 * Tools > Gradle > Run tests using <em>IntelliJ IDEA</em>). This ensures the
 * name and inputs for the tests are displayed correctly in the run window.
 */
public class RegexTests {
    /**
     * This is a parameterized test for the {@link Regex#EMAIL} regex. The
     * {@link ParameterizedTest} annotation defines this method as a
     * parameterized test, and {@link MethodSource} tells JUnit to look for the
     * static method {@link #testEmailRegex()}.
     *
     * For personal preference, I include a test name as the first parameter
     * which describes what that test should be testing - this is visible in
     * IntelliJ when running the tests (see above note if not working).
     */


    @ParameterizedTest
    @MethodSource
    public void testEmailRegex(String test, String input, boolean success) {
        test(input, Regex.EMAIL, success);
    }
    /**
     * This is the factory method providing test cases for the parameterized
     * test above - note that it is static, takes no arguments, and has the same
     * name as the test. The {@link Arguments} object contains the arguments for
     * each test to be passed to the function above.
     */
    //things to test:
    //matching:
    // @xyz.xyz.com
    //@xyz..com
    //what am i testing??

    //nonmatching:
    //missing @ symbols
    //2 letter domain: .co.uk
    //one letter: x@gmail.com
    //symbols in domain: xyz@@gmail.com
    //nonlexical in domain extension: xyz@gmail.123
    public static Stream<Arguments> testEmailRegex() {
        return Stream.of(
                Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
                Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
                Arguments.of("Dots", "dot.domain@gmail.com", true),
                Arguments.of("Underscore", "under_score@gmail.com", true),
                Arguments.of("4th Level Domain", "fourthlvl@docs.developer.google.com", true),
                Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
                Arguments.of("Missing @", "missingatgmail.com", false),
                Arguments.of("Symbols", "symbols#$%@gmail.com", false),
                Arguments.of("TLD < 3", "toplvl@domain.io", false),
                Arguments.of("Long Domain", "longdomain@domain.info", false),
                Arguments.of("Short User", "t@gmail.com", false),
                Arguments.of("No User", "@gmail.com", false)
        );
    }
    @ParameterizedTest
    @MethodSource
    public void testOddStringsRegex(String test, String input, boolean success) {
        test(input, Regex.ODD_STRINGS, success);
    }
    public static Stream<Arguments> testOddStringsRegex() {
        return Stream.of(
// what have eleven letters and starts with gas?
                Arguments.of("11 Characters", "automobiles", true),
                Arguments.of("13 Characters", "i<3pancakes13", true),
                Arguments.of("15 Characters", "...pancakes1515", true),
                Arguments.of("17 Characters", "___pancakes171717", true),
                Arguments.of("19 Characters", "~~~pancakes19191919", true),
                Arguments.of("5 Characters", "5five", false),
                Arguments.of("12 Characters", "<3pancakes12", false),
                Arguments.of("14 Characters", "i<3pancakes14!", false),
                Arguments.of("16 Characters", "i<3pancakes16!16", false),
                Arguments.of("20 Characters", "i<3i<3i<3pancakes20!", false)
        );
    }
    @ParameterizedTest
    @MethodSource
    public void testCharacterListRegex(String test, String input, boolean success)
    {
        test(input, Regex.CHARACTER_LIST, success);
    }
    public static Stream<Arguments> testCharacterListRegex() {
        return Stream.of(
                Arguments.of("Single Element", "['a']", true),
                Arguments.of("Empty List", "[]", true),
                Arguments.of("Multiple Elements", "['a','b','c']", true),
                Arguments.of("Mixed Spaces", "['a','b', 'c']", true),
                Arguments.of("Even Spaces", "['a', 'b', 'c']", true),
                Arguments.of("Missing Brackets", "'a','b','c'", false),
                Arguments.of("Missing Commas", "['a' 'b' 'c']", false),
                Arguments.of("Space before Comma", "['a' ,'b', 'c']", false),
                Arguments.of("Space before End", "['a','b','c' ]", false),
                Arguments.of("Trailing Comma", "['a','b','c',]", false),
                Arguments.of("Space at Start", "[ 'a','b','c']", false)
        );
    }
    @ParameterizedTest
    @MethodSource
    public void testDecimalRegex(String test, String input, boolean success) {
        test(input, Regex.DECIMAL, success);
    }
    public static Stream<Arguments> testDecimalRegex() {
        return Stream.of(
                Arguments.of("Normal", "10100.001", true),
                Arguments.of("Negative", "-1.0", true),
                Arguments.of("Zero", "0.0", true),
                Arguments.of("Trailing zeros", "1.000", true),
                Arguments.of("Integer on the right", "0.5", true),
                Arguments.of("Integer", "1", false),
                Arguments.of("Missing Int Left", ".5", false),
                Arguments.of("Left Most Zero", "0001.10", false),
                Arguments.of("Negative Integer", "-5", false),
                Arguments.of("Negative without Integer", "-.01", false)
        );
    }
    @ParameterizedTest
    @MethodSource
    public void testStringRegex(String test, String input, boolean success) {
        test(input, Regex.STRING, success);
    }
    public static Stream<Arguments> testStringRegex() {
        return Stream.of(
                Arguments.of("Empty String", "\"\"", true),
                Arguments.of("Hello World", "\"Hello, World!\"", true),
                Arguments.of("Valid Escape t", "\"1\\t2\"", true),
                Arguments.of("Valid Escape b", "\"a\\bb\"", true),
                Arguments.of("Valid Escape \\", "\"x\\\\y\"", true),
                Arguments.of("Missing End Quote", "\"unterminated", false),
                Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),
                Arguments.of("More than 2 quotes", "\"\"\"", false),
                Arguments.of("Unquoted", "unquoted", false),
                Arguments.of("Outside Double Quotes", "\"inside\"outside", false)
        );
    }
    /**
     * Asserts that the input matches the given pattern. This method doesn't do
     * much now, but you will see this concept in future assignments.
     */
    private static void test(String input, Pattern pattern, boolean success) {
        Assertions.assertEquals(success, pattern.matcher(input).matches());
    }
}


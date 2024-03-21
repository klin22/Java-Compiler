package plc.homework;
import java.util.regex.Pattern;
public class Regex {
    public static final Pattern
            EMAIL = Pattern.compile("[A-Za-z0-9._]{2,}@[A-Za-z0-9~]+\\.([A-Za-z0-9-]+\\.)*[a-z]{3}"),
    ODD_STRINGS = Pattern.compile("^.{11}$|^.{13}$|^.{15}$|^.{17}$|^.{19}$"), //TODO
    CHARACTER_LIST = Pattern.compile("\\[(('[a-zA-Z]'(,\\s?'[a-zA-Z]')*)?)\\]"), //TODO
    DECIMAL = Pattern.compile("(-)?(0|[1-9]\\d*)\\.\\d+"), //TODO
    STRING = Pattern.compile("\"([^\"\\\\]|\\\\[bnrt'\"\\\\])*\""); //TODO
}

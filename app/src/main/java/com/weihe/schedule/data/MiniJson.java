package com.weihe.schedule.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer that touches no Android API.
 *
 * <p>The storage layer used to talk to {@code org.json}, which comes from
 * {@code android.jar}. That made it impossible to unit test: on the JVM the
 * Android classes are stubs, and with {@code returnDefaultValues} enabled every
 * call silently answers {@code null} instead of failing. Writing a minimal
 * implementation removes the dependency, works identically on the device and on
 * the JVM, and lets {@link BellScheduleJson} -- and through it the stored form of
 * every schedule -- be covered by ordinary tests.</p>
 *
 * <p>The value model is plain: objects are {@code Map<String, Object>}, arrays
 * are {@code List<Object>}, and the leaves are {@code String}, {@code Double},
 * {@code Boolean} and {@code null}. Callers use {@link #optInt} and friends
 * rather than casting directly.</p>
 *
 * <p>This is not a general-purpose library: it parses and writes the shapes the
 * app stores and reports anything else as {@code null}/empty rather than
 * throwing.</p>
 */
public final class MiniJson {

    private MiniJson() {
    }

    // ------------------------------------------------------------------ reading

    /**
     * Parses a JSON document.
     *
     * @return the parsed value, or {@code null} when the text is not valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            return null;
        }
        Parser parser = new Parser(text);
        try {
            parser.skipWhitespace();
            Object value = parser.readValue();
            parser.skipWhitespace();
            if (!parser.atEnd()) {
                return null;
            }
            return value;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** The array in {@code text}, or an empty list when there is not one. */
    public static List<Object> parseArray(String text) {
        Object value = parse(text);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) value;
            return array;
        }
        return new ArrayList<>();
    }

    /** The object at {@code index} of {@code array}, or {@code null}. */
    public static Map<String, Object> objectAt(List<Object> array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        Object value = array.get(index);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            return object;
        }
        return null;
    }

    /** A long field, or {@code fallback} when it is missing or not a number. */
    public static long optLong(Map<String, Object> object, String key, long fallback) {
        Object value = object == null ? null : object.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException notANumber) {
                return fallback;
            }
        }
        return fallback;
    }

    /** An int field, or {@code fallback} when it is missing or not a number. */
    public static int optInt(Map<String, Object> object, String key, int fallback) {
        return (int) optLong(object, key, fallback);
    }

    /** A string field, or {@code fallback} when it is missing or not a string. */
    public static String optString(Map<String, Object> object, String key,
            String fallback) {
        Object value = object == null ? null : object.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    /** An array field, or an empty list when it is missing or not an array. */
    public static List<Object> optArray(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) value;
            return array;
        }
        return new ArrayList<>();
    }

    // ------------------------------------------------------------------ writing

    /** A new, empty object. */
    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    /** A new, empty array. */
    public static List<Object> array() {
        return new ArrayList<>();
    }

    /** Serialises {@code value}, or {@code "null"} when it cannot be written. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (value instanceof Number) {
            writeNumber(out, (Number) value);
        } else if (value instanceof Map) {
            writeObject(out, (Map<?, ?>) value);
        } else if (value instanceof List) {
            writeArray(out, (List<?>) value);
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeNumber(StringBuilder out, Number number) {
        double asDouble = number.doubleValue();
        long asLong = number.longValue();
        if (asDouble == (double) asLong) {
            out.append(asLong);
        } else {
            out.append(asDouble);
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> object) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, List<?> array) {
        out.append('[');
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(out, array.get(i));
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (character < 0x20) {
                        // Locale.ROOT: an escape is machine text, and a locale
                        // with different digits would corrupt it.
                        out.append(String.format(java.util.Locale.ROOT,
                                "\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                    break;
            }
        }
        out.append('"');
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return position >= text.length();
        }

        void skipWhitespace() {
            while (position < text.length()) {
                char character = text.charAt(position);
                if (character == ' ' || character == '\t'
                        || character == '\n' || character == '\r') {
                    position++;
                } else {
                    return;
                }
            }
        }

        Object readValue() {
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end of input");
            }
            char character = text.charAt(position);
            switch (character) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    return readLiteral("true", Boolean.TRUE);
                case 'f':
                    return readLiteral("false", Boolean.FALSE);
                case 'n':
                    return readLiteral("null", null);
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> object = new LinkedHashMap<>();
            position++;
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException("expected a key");
                }
                String key = readString();
                skipWhitespace();
                if (peek() != ':') {
                    throw new IllegalArgumentException("expected ':'");
                }
                position++;
                skipWhitespace();
                object.put(key, readValue());
                skipWhitespace();
                char next = peek();
                position++;
                if (next == '}') {
                    return object;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            List<Object> array = new ArrayList<>();
            position++;
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return array;
            }
            while (true) {
                skipWhitespace();
                array.add(readValue());
                skipWhitespace();
                char next = peek();
                position++;
                if (next == ']') {
                    return array;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("expected ',' or ']'");
                }
            }
        }

        private String readString() {
            position++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char character = text.charAt(position++);
                if (character == '"') {
                    return out.toString();
                }
                if (character != '\\') {
                    out.append(character);
                    continue;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated escape");
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        out.append(readUnicodeEscape());
                        break;
                    default:
                        throw new IllegalArgumentException("unknown escape");
                }
            }
        }

        private char readUnicodeEscape() {
            if (position + 4 > text.length()) {
                throw new IllegalArgumentException("truncated unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(position + i), 16);
                if (digit < 0) {
                    throw new IllegalArgumentException("bad unicode escape");
                }
                value = value * 16 + digit;
            }
            position += 4;
            return (char) value;
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, position)) {
                throw new IllegalArgumentException("expected " + literal);
            }
            position += literal.length();
            return value;
        }

        private Double readNumber() {
            int start = position;
            if (peek() == '-' || peek() == '+') {
                position++;
            }
            while (!atEnd()) {
                char character = text.charAt(position);
                if ((character >= '0' && character <= '9') || character == '.'
                        || character == 'e' || character == 'E'
                        || character == '-' || character == '+') {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new IllegalArgumentException("expected a value");
            }
            try {
                return Double.valueOf(text.substring(start, position));
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException("bad number");
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end of input");
            }
            return text.charAt(position);
        }
    }
}

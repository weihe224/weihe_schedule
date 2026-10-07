package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Tests for the JSON reader and writer the storage layer uses.
 *
 * <p>This is the one place in the project where a parser had to be written by
 * hand, because {@code org.json} lives in {@code android.jar} and answers
 * {@code null} on the JVM. A hand-written parser without tests would be a worse
 * trade than the dependency it replaced, so everything the stored payloads
 * actually rely on is pinned here.</p>
 */
public class MiniJsonTest {

    @Test
    public void anArrayOfObjectsRoundTrips() {
        List<Object> array = MiniJson.array();
        Map<String, Object> first = MiniJson.object();
        first.put("id", Long.valueOf(7L));
        first.put("name", "\u9AD8\u7B49\u6570\u5B66");
        first.put("parity", Integer.valueOf(1));
        array.add(first);

        List<Object> parsed = MiniJson.parseArray(MiniJson.write(array));

        assertEquals(1, parsed.size());
        Map<String, Object> restored = MiniJson.objectAt(parsed, 0);
        assertEquals(7L, MiniJson.optLong(restored, "id", 0L));
        assertEquals("\u9AD8\u7B49\u6570\u5B66",
                MiniJson.optString(restored, "name", ""));
        assertEquals(1, MiniJson.optInt(restored, "parity", 0));
    }

    @Test
    public void stringsSurviveQuotesBackslashesAndNewlines() {
        Map<String, Object> object = MiniJson.object();
        object.put("text", "a\"b\\c\nd\te\u0001");

        Map<String, Object> restored = MiniJson.objectAt(
                MiniJson.parseArray("[" + MiniJson.write(object) + "]"), 0);

        assertEquals("a\"b\\c\nd\te\u0001",
                MiniJson.optString(restored, "text", ""));
    }

    @Test
    public void unicodeEscapesAreReadBack() {
        Map<String, Object> restored = MiniJson.objectAt(MiniJson.parseArray(
                "[{\"name\":\"\\u6807\\u51c6\\u4f5c\\u606f\"}]"), 0);

        assertEquals("\u6807\u51C6\u4F5C\u606F",
                MiniJson.optString(restored, "name", ""));
    }

    @Test
    public void whitespaceAndNestingDoNotMatter() {
        String raw = "[\n  {\"a\": [1, 2, {\"b\": \"c\"}], \"d\": null}\n]";

        Map<String, Object> object = MiniJson.objectAt(MiniJson.parseArray(raw), 0);

        assertEquals(3, MiniJson.optArray(object, "a").size());
        assertEquals("c", MiniJson.optString(MiniJson.objectAt(
                MiniJson.optArray(object, "a"), 2), "b", ""));
    }

    @Test
    public void numbersKeepTheirIntegerShape() {
        Map<String, Object> object = MiniJson.objectAt(MiniJson.parseArray(
                "[{\"color\": 4280391411, \"ratio\": 1.5, \"negative\": -12}]"), 0);

        assertEquals(4280391411L, MiniJson.optLong(object, "color", 0L));
        assertEquals(-12, MiniJson.optInt(object, "negative", 0));
        assertEquals("{\"color\":4280391411,\"ratio\":1.5,\"negative\":-12}",
                MiniJson.write(object));
    }

    @Test
    public void literalsAndEmptyContainersAreUnderstood() {
        List<Object> parsed = MiniJson.parseArray("[{}, [], true, false, null]");

        assertEquals(5, parsed.size());
        assertTrue(parsed.get(0) instanceof Map);
        assertTrue(parsed.get(1) instanceof List);
        assertEquals(Boolean.TRUE, parsed.get(2));
        assertEquals(Boolean.FALSE, parsed.get(3));
        assertNull(parsed.get(4));
    }

    @Test
    public void brokenDocumentsAreRejectedInsteadOfThrowing() {
        assertNull(MiniJson.parse("{\"a\":1"));
        assertNull(MiniJson.parse("[1,2"));
        assertNull(MiniJson.parse("[1,2]]"));
        assertNull(MiniJson.parse("{\"a\" 1}"));
        assertNull(MiniJson.parse("[\"unterminated]"));
        assertNull(MiniJson.parse("nope"));
        assertNull(MiniJson.parse(""));
        assertNull(MiniJson.parse(null));
        assertTrue(MiniJson.parseArray("not json").isEmpty());
    }

    @Test
    public void theWrongShapeReadsAsTheDefault() {
        Map<String, Object> object = MiniJson.objectAt(
                MiniJson.parseArray("[{\"n\":\"text\",\"s\":5}]"), 0);

        assertEquals(9, MiniJson.optInt(object, "n", 9));
        assertEquals("", MiniJson.optString(object, "wrongTypeKey", ""));
        assertEquals(5, MiniJson.optInt(object, "s", 0));
        assertEquals(7L, MiniJson.optLong(object, "n", 7L));
        assertEquals("\u7F3A", MiniJson.optString(object, "missing", "\u7F3A"));
        assertTrue(MiniJson.optArray(object, "missing").isEmpty());
        assertNull(MiniJson.objectAt(MiniJson.parseArray("[1]"), 0));
        assertNull(MiniJson.objectAt(MiniJson.parseArray("[]"), 3));
    }

    @Test
    public void writingNullAndEmptyContainersMatchesJson() {
        assertEquals("null", MiniJson.write(null));
        assertEquals("[]", MiniJson.write(MiniJson.array()));
        assertEquals("{}", MiniJson.write(MiniJson.object()));
    }
}

package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

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
    // ============================================ nesting depth
    //
    // The reader recurses: readValue -> readObject/readArray -> readValue. Nothing
    // used to bound that, and the byte cap is not a depth cap -- a few kilobytes of
    // brackets nests thousands deep. These tests state the boundary rather than
    // relying on a particular stack size to notice it.

    @Test
    public void containersNestedToTheLimitAreAccepted() {
        assertNotNull("exactly at the limit must parse",
                MiniJson.parse(arraysOfDepth(MiniJson.MAX_DEPTH)));
        assertNotNull("and so must a mixed chain that deep",
                MiniJson.parse(mixedOfDepth(MiniJson.MAX_DEPTH)));
    }

    @Test
    public void oneLevelPastTheLimitIsRefused() {
        assertNull(MiniJson.parse(arraysOfDepth(MiniJson.MAX_DEPTH + 1)));
        assertNull(MiniJson.parse(mixedOfDepth(MiniJson.MAX_DEPTH + 1)));
    }

    @Test
    public void fiveThousandLevelsAreRefusedRatherThanRunningOutOfStack() {
        String deep = arraysOfDepth(5000);
        assertTrue("the input is small enough to pass any size check",
                deep.length() < 20000);
        assertNull("the reader must refuse it, not recurse until the stack ends",
                MiniJson.parse(deep));
    }

    @Test
    public void depthCountsNestingAndNotLength() {
        // A flat array holding far more items than the depth limit is still flat.
        // A counter that never fell on the way out would reject this one.
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < MiniJson.MAX_DEPTH * 4; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"i\":").append(i).append('}');
        }
        out.append(']');
        assertEquals(MiniJson.MAX_DEPTH * 4,
                MiniJson.parseArray(out.toString()).size());
    }

    @Test
    public void aDocumentAtTheLimitStillReadsCorrectly() {
        Object value = MiniJson.parse(arraysOfDepth(MiniJson.MAX_DEPTH));
        int unwrapped = 0;
        while (value instanceof List) {
            value = ((List<?>) value).get(0);
            unwrapped++;
        }
        assertEquals("every level is still there", MiniJson.MAX_DEPTH, unwrapped);
        assertEquals("and the value at the bottom survives", "leaf", value);
    }

    @Test
    public void aBackupShapedDocumentIsWellInsideTheLimit() {
        String backup = "{\"format\":\"weihe-schedule-backup\",\"version\":1,"
                + "\"semesters\":[{\"id\":1,\"start\":\"2026-08-31\",\"name\":\"\","
                + "\"courses\":[{\"id\":1,\"name\":\"\u9AD8\u7B49\u6570\u5B66\","
                + "\"color\":4280842421}]}]}";
        Object parsed = MiniJson.parse(backup);
        assertNotNull("the deepest shape the app writes must never hit the limit",
                parsed);
        assertTrue(parsed instanceof Map);
    }

    @Test
    public void aRefusedDocumentIsIndistinguishableFromOtherBadInput() {
        // The contract is unchanged: parse answers null for anything it will not
        // read, so callers keep the one error path they already have.
        assertNull(MiniJson.parse(arraysOfDepth(5000)));
        assertNull(MiniJson.parse("{\"a\":"));
        assertTrue(MiniJson.parseArray(arraysOfDepth(5000)).isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private static String arraysOfDepth(int depth) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            out.append('[');
        }
        out.append("\"leaf\"");
        for (int i = 0; i < depth; i++) {
            out.append(']');
        }
        return out.toString();
    }

    /** Alternating array/object nesting: the rule must not depend on which. */
    private static String mixedOfDepth(int depth) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            out.append(i % 2 == 0 ? "[" : "{\"a\":");
        }
        out.append("\"leaf\"");
        for (int i = depth - 1; i >= 0; i--) {
            out.append(i % 2 == 0 ? "]" : "}");
        }
        return out.toString();
    }
}

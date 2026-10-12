package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * The kept payload as a value: what it says it is, and what it writes out.
 *
 * <p>Framework-free, so it runs on a plain JVM. The store half -- whether a copy
 * is found at all -- is covered by {@code CourseRepositoryStorageTest}, because
 * that needs a repository and a preferences stand-in.</p>
 *
 * <p>The recurring theme is that this class is not allowed to be clever. It does
 * not parse, does not repair, and does not decide that something unreadable is
 * the same as something missing.</p>
 */
public class DamagedCourseCopyTest {

    /** A payload of the kind the slot actually holds: text that is not a course list. */
    private static final String PAYLOAD =
            "[{\"id\":1,\"name\":\"\u9AD8\u7B49\u6570\u5B66\"},42,{\"id\":2}]";

    @Test
    public void anAbsentCopySaysSoAndHasNothingToExport() {
        DamagedCourseCopy copy = DamagedCourseCopy.of(null);

        assertEquals(DamagedCourseCopy.State.ABSENT, copy.state());
        assertFalse(copy.exists());
        assertFalse(copy.isExportable());
        assertNull(copy.raw());
        assertEquals(0, copy.length());
    }

    @Test
    public void anEmptyCopyIsItsOwnStateRatherThanAbsence() {
        DamagedCourseCopy copy = DamagedCourseCopy.of("");

        assertEquals(DamagedCourseCopy.State.EMPTY, copy.state());
        assertTrue("the slot exists, even when it holds nothing", copy.exists());
        assertFalse("there is nothing to export", copy.isExportable());
        assertEquals("", copy.raw());
        assertEquals(0, copy.length());
    }

    @Test
    public void theTextIsCarriedExactlyAsItWasKept() {
        DamagedCourseCopy copy = DamagedCourseCopy.of(PAYLOAD);

        assertEquals(DamagedCourseCopy.State.PRESENT, copy.state());
        assertTrue(copy.exists());
        assertTrue(copy.isExportable());
        assertEquals(PAYLOAD, copy.raw());
        assertEquals(PAYLOAD.length(), copy.length());
    }

    @Test
    public void aPayloadThatIsNotJsonAtAllIsStillPresent() {
        String html = "<html>this is not a timetable</html>";

        DamagedCourseCopy copy = DamagedCourseCopy.of(html);

        assertEquals("readability is not the question existence answers",
                DamagedCourseCopy.State.PRESENT, copy.state());
        assertEquals(html, copy.raw());
    }

    @Test
    public void aSlotHoldingSomethingElseIsNotReportedAsAbsent() {
        DamagedCourseCopy copy = DamagedCourseCopy.unreadable();

        assertEquals(DamagedCourseCopy.State.UNREADABLE, copy.state());
        assertTrue("the slot is occupied, so it is not absent", copy.exists());
        assertFalse(copy.isExportable());
        assertNull(copy.raw());
    }

    @Test
    public void writingPutsTheSameCharactersOnTheStream() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DamagedCourseCopy.of(PAYLOAD).writeTo(out);

        assertEquals(PAYLOAD, new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void whatIsWrittenCarriesNoByteOrderMark() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DamagedCourseCopy.of(PAYLOAD).writeTo(out);

        byte[] bytes = out.toByteArray();
        assertEquals('[', bytes[0]);
        assertFalse("a marker this app did not put there would be an invention",
                bytes.length > 3 && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF);
    }

    @Test
    public void writingDoesNotChangeTheCopy() throws Exception {
        DamagedCourseCopy copy = DamagedCourseCopy.of(PAYLOAD);

        copy.writeTo(new ByteArrayOutputStream());

        assertEquals(DamagedCourseCopy.State.PRESENT, copy.state());
        assertEquals(PAYLOAD, copy.raw());
    }

    @Test
    public void aStreamThatFailsToWriteReportsTheFailure() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("no space left on device");
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("no space left on device");
            }
        };

        try {
            DamagedCourseCopy.of(PAYLOAD).writeTo(broken);
            fail("expected the failure to reach the caller, so success is never claimed");
        } catch (IOException expected) {
            assertEquals("no space left on device", expected.getMessage());
        }
    }

    @Test
    public void anEmptyOrAbsentCopyRefusesToBeWritten() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (DamagedCourseCopy copy : new DamagedCourseCopy[] {
                DamagedCourseCopy.absent(), DamagedCourseCopy.empty(),
                DamagedCourseCopy.unreadable() }) {
            try {
                copy.writeTo(out);
                fail("expected " + copy + " to refuse");
            } catch (IOException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
        assertEquals("a refused write must leave the stream untouched",
                0, out.size());
    }

    @Test
    public void aNullStreamIsRefusedRatherThanCrashingTheCaller() {
        try {
            DamagedCourseCopy.of(PAYLOAD).writeTo(null);
            fail("expected a null stream to be refused");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}

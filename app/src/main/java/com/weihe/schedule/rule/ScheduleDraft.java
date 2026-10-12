package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.List;


/**
 * What the bell-schedule editor is holding before it is saved.
 *
 * <p>{@link BellScheduleEditor} already keeps the form as text and parses it only
 * when asked to build, so half-typed times live there rather than in the views.
 * This class is the part of that editor worth carrying across a process death:
 * the name, the venue keywords, and every period's two raw strings.</p>
 *
 * <h2>Text stays text</h2>
 *
 * <p>Nothing here parses, normalises or re-formats. {@code 8.0} comes back as
 * {@code 8.0} and {@code 8:} comes back as {@code 8:}, because the user is
 * mid-keystroke and a field that rewrites itself under the caret is worse than one
 * that briefly holds something invalid. A time that cannot be read yet is not an
 * error to be cleared; it is a time being typed. The editor's own validation
 * already reports it when the save button is pressed, which is the moment it
 * matters.</p>
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p>Not the name this editor opened with. That comes from the intent, which
 * survives a rebuild on its own, and carrying a second copy of it would create a
 * way for the editor and the intent to disagree about which schedule is being
 * edited -- the one mistake this screen has already made once, when it addressed
 * schedules by list position. Restoring text cannot change the target; only a
 * saved identity could.</p>
 */
public final class ScheduleDraft {

    private final String name;
    private final String keywords;
    private final String[] startTexts;
    private final String[] endTexts;

    /**
     * @param name       typed schedule name, or null when not carried
     * @param keywords   typed venue keywords, or null when not carried
     * @param startTexts raw start text per period, or null when not carried
     * @param endTexts   raw end text per period, or null when not carried
     */
    public ScheduleDraft(String name, String keywords, String[] startTexts,
            String[] endTexts) {
        this.name = name;
        this.keywords = keywords;
        this.startTexts = startTexts == null ? null : startTexts.clone();
        this.endTexts = endTexts == null ? null : endTexts.clone();
    }

    /** Everything {@code editor} currently holds, ready to be carried. */
    public static ScheduleDraft of(BellScheduleEditor editor) {
        int count = editor.periodCount();
        String[] starts = new String[count];
        String[] ends = new String[count];
        for (int period = 1; period <= count; period++) {
            starts[period - 1] = editor.startText(period);
            ends[period - 1] = editor.endText(period);
        }
        return new ScheduleDraft(editor.name(), editor.keywords(), starts, ends);
    }

    /** Typed schedule name, or null when the state did not carry one. */
    public String name() {
        return name;
    }

    /** Typed venue keywords, or null. */
    public String keywords() {
        return keywords;
    }

    /** How many periods the state carries. */
    public int periodCount() {
        return startTexts == null ? 0 : startTexts.length;
    }

    /** Raw start text of {@code period} (1-based); empty when out of range. */
    public String startText(int period) {
        return textAt(startTexts, period);
    }

    /** Raw end text of {@code period} (1-based); empty when out of range. */
    public String endText(int period) {
        return textAt(endTexts, period);
    }

    /**
     * Writes this state back into an editor, field by field.
     *
     * <p>Only what the state carries is written, so an editor that has just been
     * initialised from storage keeps whatever the state was silent about. The two
     * can disagree about how many periods there are -- a schedule may have been
     * stored with a different count than the one being edited -- so the shorter of
     * the two ends the loop, and the periods past it keep their initialised
     * text.</p>
     *
     * <p>This writes to the editor and to nothing else. There is no repository in
     * sight, so restoring a draft cannot save, delete or rename anything.</p>
     */
    public void applyTo(BellScheduleEditor editor) {
        if (name != null) {
            editor.setName(name);
        }
        if (keywords != null) {
            editor.setKeywords(keywords);
        }
        int count = Math.min(periodCount(), editor.periodCount());
        for (int period = 1; period <= count; period++) {
            editor.setStartText(period, startText(period));
            editor.setEndText(period, endText(period));
        }
    }

    /** The raw texts as a list, for storing and for assertions. */
    public List<String> startTexts() {
        return asList(startTexts);
    }

    /** The raw texts as a list, for storing and for assertions. */
    public List<String> endTexts() {
        return asList(endTexts);
    }


    private static String textAt(String[] texts, int period) {
        if (texts == null || period < 1 || period > texts.length) {
            return "";
        }
        return texts[period - 1] == null ? "" : texts[period - 1];
    }

    private static List<String> asList(String[] texts) {
        List<String> values = new ArrayList<>();
        if (texts != null) {
            for (String text : texts) {
                values.add(text == null ? "" : text);
            }
        }
        return values;
    }
}

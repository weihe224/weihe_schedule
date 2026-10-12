package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.ScheduleUtils;


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

    /**
     * "This state says nothing about the period count."
     *
     * <p>Distinct from zero, which would be an instruction to empty the form -- and
     * an illegal one, since a schedule always has at least one period.</p>
     */
    public static final int NOT_CARRIED = -1;

    private final String name;
    private final String keywords;
    private final String durationText;
    private final String[] startTexts;
    private final String[] endTexts;
    private final int periodCount;

    /**
     * A state that says which texts belong to which period, but not how many
     * periods the form has.
     *
     * <p>The two arrays describe the periods they mention and stay silent about the
     * ones beyond them, so this constructor deliberately carries no count. A caller
     * that has a whole form in hand -- the editor itself, or the activity taking its
     * state -- uses {@link #of} or the constructor that takes a length, because for
     * those the count is part of what is being carried.</p>
     *
     * @param name       typed schedule name, or null when not carried
     * @param keywords   typed venue keywords, or null when not carried
     * @param startTexts raw start text per period, or null when not carried
     * @param endTexts   raw end text per period, or null when not carried
     */
    public ScheduleDraft(String name, String keywords, String[] startTexts,
            String[] endTexts) {
        this(name, keywords, null, NOT_CARRIED, startTexts, endTexts);
    }

    /**
     * As above, plus the period length.
     *
     * @param durationText raw period-length text, or null when not carried
     */
    public ScheduleDraft(String name, String keywords, String durationText,
            String[] startTexts, String[] endTexts) {
        this(name, keywords, durationText, NOT_CARRIED, startTexts, endTexts);
    }

    /**
     * A state, with the count filled in separately.
     *
     * <p>A caller that has taken a whole form from the editor passes
     * {@code startTexts.length} for {@code periodCount}, which is what {@link #of}
     * does. Passing {@link #NOT_CARRIED} says "here are some texts, but do not touch
     * how many periods there are" -- the honest description of a state that only ever
     * mentioned one of them. See {@link #applyTo} for why those two readings have to
     * be told apart.</p>
     *
     * @param periodCount how many periods the form has, or {@link #NOT_CARRIED} to
     *                    leave the editor's own count alone
     */
    public ScheduleDraft(String name, String keywords, String durationText,
            int periodCount, String[] startTexts, String[] endTexts) {
        this.name = name;
        this.keywords = keywords;
        this.durationText = durationText;
        this.periodCount = periodCount;
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
        return new ScheduleDraft(editor.name(), editor.keywords(),
                editor.durationText(), count, starts, ends);
    }

    /** Typed schedule name, or null when the state did not carry one. */
    public String name() {
        return name;
    }

    /** Typed venue keywords, or null. */
    public String keywords() {
        return keywords;
    }

    /** Typed period length, or null when the state did not carry one. */
    public String durationText() {
        return durationText;
    }

    /** How many periods the state carries. */
    public int periodCount() {
        return periodCount == NOT_CARRIED
                ? (startTexts == null ? 0 : startTexts.length)
                : periodCount;
    }

    /** Whether the state says anything at all about the period count. */
    public boolean carriesPeriodCount() {
        return periodCount != NOT_CARRIED;
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
     * initialised from storage keeps whatever the state was silent about.</p>
     *
     * <h2>Why the count is its own value</h2>
     *
     * <p>How many periods the editor should end up with is carried <em>explicitly</em>
     * -- as a number, not as the length of the text arrays -- because the two
     * meanings that length could have are opposites:</p>
     *
     * <ul>
     *   <li>A state taken from this editor moments ago, after the user added or
     *       removed a period, describes the whole form and must bring that count
     *       back. The editor underneath was rebuilt from storage, which still holds
     *       the count that was last <em>saved</em>, so without this the sixteenth
     *       period the user had just added would vanish across a rotation.</li>
     *   <li>A state that mentions a few periods and nothing else is a fragment of
     *       a form, not a description of one. Trimming the editor down to the
     *       fragment's length would delete periods the user never touched, and
     *       treat an absent state as an instruction to keep one period and throw
     *       away fourteen.</li>
     * </ul>
     *
     * <p>Inferring the count from the array length cannot tell these apart, because
     * both arrive as "shorter than the editor". Only the caller knows which it
     * meant, so the count travels beside the texts instead of being read out of
     * them.</p>
     *
     * <p>Changes are made at the end of the list -- {@link
     * BellScheduleEditor#addPeriod} and {@link BellScheduleEditor#removePeriod} --
     * so applying a count never renumbers or moves a period that already exists,
     * and the count can never fall below
     * {@link BellSchedule#MIN_PERIODS}.</p>
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
        if (durationText != null) {
            editor.setDurationText(durationText);
        }
        if (carriesPeriodCount()) {
            applyPeriodCount(editor);
        }
        if (startTexts != null) {
            int count = Math.min(startTexts.length, editor.periodCount());
            for (int period = 1; period <= count; period++) {
                editor.setStartText(period, startText(period));
            }
        }
        if (endTexts != null) {
            int count = Math.min(endTexts.length, editor.periodCount());
            for (int period = 1; period <= count; period++) {
                editor.setEndText(period, endText(period));
            }
        }
    }

    /**
     * Brings the editor's period count to what the state says, by growing or
     * shrinking the end of the list only.
     */
    private void applyPeriodCount(BellScheduleEditor editor) {
        int target = Math.max(BellSchedule.MIN_PERIODS,
                Math.min(periodCount, ScheduleUtils.MAX_PERIODS));
        while (editor.periodCount() < target) {
            if (editor.addPeriod() == 0) {
                break;
            }
        }
        while (editor.periodCount() > target) {
            if (!editor.removePeriod()) {
                break;
            }
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

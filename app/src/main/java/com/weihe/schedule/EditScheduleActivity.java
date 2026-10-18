package com.weihe.schedule;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.BellScheduleEditor;
import com.weihe.schedule.rule.ScheduleDraft;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.Pickers;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;
import com.weihe.schedule.widget.CourseWidgetUpdater;

/**
 * Edits one bell schedule: its name, the venue keywords that select it, how long
 * one period lasts, and how many periods the day has.
 *
 * <p>The rules live in {@link BellScheduleEditor}, which never touches Android;
 * this screen only turns its {@code Problem} list into field errors.</p>
 *
 * <h2>The period rows are a list, not fifteen fields</h2>
 *
 * <p>Rows are built from {@link BellScheduleEditor#periodCount()} and rebuilt
 * whenever that changes, so adding a period is an ordinary edit rather than a
 * special case. The rows themselves are held in a {@link List}: a fixed
 * {@code TextView[15]} is exactly the assumption this screen used to encode, and
 * an array that has to be resized by hand every time a row is appended is the
 * shape of bug that only shows up on the sixteenth period.</p>
 *
 * <h2>Nothing here writes until the save button is pressed</h2>
 *
 * <p>Adding a period, removing one, changing the length and picking a start all
 * touch the editor only. The repository is read twice -- once for the schedules,
 * once to check a course reference before removing a period -- and written once,
 * by {@link #validateAndSave()}.</p>
 */
public class EditScheduleActivity extends Activity {
    /**
     * Name of the schedule to edit, or absent for a new one.
     *
     * <p>A name rather than a list position. An index is only valid against the
     * list it was read from, and this screen holds its repository for its whole
     * life without ever reloading it, so anything another screen did in between
     * turned the index into a pointer at a different table.</p>
     */
    public static final String EXTRA_NAME = "schedule_name";

    private CourseRepository repository;
    private BellScheduleEditor editor;
    /** Name this editor opened with; empty when it is creating a new schedule. */
    private String originalName = "";
    private boolean existingSchedule;

    private EditText nameInput;
    private EditText keywordInput;
    private EditText durationInput;
    private static final int DEFAULT_START_HOUR = 8;
    // TextView, and a list that really holds TextViews: the fields became pickers
    // rather than text boxes, and `new EditText[...]` behind a `TextView[]` compiles
    // -- arrays are covariant -- then throws ArrayStoreException on the first plain
    // TextView stored into it, which is the moment the editor opens.
    private final List<TextView> startInputs = new ArrayList<>();
    private final List<TextView> endInputs = new ArrayList<>();
    /** Holds the period rows, so adding or removing one is a rebuild of this child. */
    private LinearLayout periodRows;
    private TextView periodCountLabel;
    private TextView errorBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);

        List<BellSchedule> schedules = repository.getBellSchedules();
        BellSchedule source = BellSchedules.byName(schedules,
                getIntent().getStringExtra(EXTRA_NAME));
        existingSchedule = source != null;
        if (source != null) {
            originalName = source.name();
            editor = new BellScheduleEditor(source, otherNames(schedules, originalName));
        } else {
            editor = BellScheduleEditor.blank(otherNames(schedules, null));
            editor.setName(uniqueName(schedules));
        }
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        // The editor is the form. It holds the raw text each field shows and the
        // fields are built from it, so putting the draft back here -- before any
        // view exists -- means the form comes up already holding what the user had
        // typed. Nothing is written twice, no row is built twice, and no listener
        // fires to say the text changed when it did not.
        ScheduleDraft draft = draftFrom(savedInstanceState);
        if (draft != null) {
            draft.applyTo(editor);
        }
        // Restoring reaches the editor and stops there. There is no repository call
        // on this path, so it cannot save, delete or rename a schedule.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
    }

    /** Every schedule name except {@code skipName}, for the duplicate check. */
    private List<String> otherNames(List<BellSchedule> schedules, String skipName) {
        List<String> names = new ArrayList<>();
        for (BellSchedule schedule : schedules) {
            if (skipName == null || !skipName.equals(schedule.name())) {
                names.add(schedule.name());
            }
        }
        return names;
    }

    private String uniqueName(List<BellSchedule> schedules) {
        String base = "\u65B0\u4F5C\u606F\u8868";
        String candidate = base;
        int suffix = 2;
        while (BellSchedules.byName(schedules, candidate) != null) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.SURFACE);
        root.addView(createHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(createBasicsSection());
        body.addView(createPeriodsSection());
        root.addView(createSaveBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View createHeader() {
        return UiKit.detailAppBar(this,
                existingSchedule ? "\u7F16\u8F91\u4F5C\u606F\u8868" : "\u65B0\u5EFA\u4F5C\u606F\u8868",
                null, view -> finish());
    }

    private View createBasicsSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(15), dp(14), dp(15), dp(15));
        section.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L,
                Palette.OUTLINE_VARIANT, 1));
        section.addView(UiKit.text(this, "\u57FA\u672C\u4FE1\u606F",
                12, Palette.ON_SURFACE, true));

        nameInput = input("\u4F8B\uFF1A\u5B9E\u9A8C\u697C\u4F5C\u606F", false);
        nameInput.setText(editor.name());
        nameInput.addTextChangedListener(watcher(() ->
                editor.setName(nameInput.getText().toString())));
        section.addView(field("\u4F5C\u606F\u8868\u540D\u79F0", nameInput, 11));

        keywordInput = input("\u4F8B\uFF1A\u5B9E\u9A8C\u697C\u3001\u5B9E\u8BAD\u697C", false);
        keywordInput.setText(editor.keywords());
        keywordInput.addTextChangedListener(watcher(() ->
                editor.setKeywords(keywordInput.getText().toString())));
        section.addView(field("\u9002\u7528\u5730\u70b9\u5173\u952E\u5B57", keywordInput, 11));

        // The length belongs in the basics card rather than above the rows: it is
        // one property of the whole table, not something each period has, and every
        // row below already shows its consequence in the end column.
        durationInput = input("", true);
        durationInput.setHint("40");
        durationInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durationInput.setText(editor.durationText());
        durationInput.addTextChangedListener(watcher(() -> {
            editor.setDurationText(durationInput.getText().toString());
            refreshEnds();
        }));
        LinearLayout durationRow = new LinearLayout(this);
        durationRow.setOrientation(LinearLayout.HORIZONTAL);
        durationRow.setGravity(Gravity.CENTER_VERTICAL);
        durationRow.addView(durationInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView unit = UiKit.text(this, "\u5206\u949F",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        unitParams.leftMargin = dp(8);
        durationRow.addView(unit, unitParams);
        section.addView(field("\u6BCF\u8282\u8BFE\u65F6\u957F", durationRow, 11));

        TextView durationHint = UiKit.text(this,
                "\u6BCF\u8282\u7ED3\u675F\u65F6\u95F4 = \u5F00\u59CB\u65F6\u95F4 + \u8FD9\u91CC"
                        + "\u7684\u65F6\u957F\uFF1B\u4FEE\u6539\u540E\u4E0B\u65B9\u7ED3\u675F"
                        + "\u65F6\u95F4\u7ACB\u5373\u91CD\u7B97\u3002",
                11, Palette.OUTLINE, false);
        LinearLayout.LayoutParams durationHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        durationHintParams.topMargin = dp(4);
        section.addView(durationHint, durationHintParams);

        TextView hint = UiKit.text(this,
                "\u8BFE\u7A0B\u7684\u4E0A\u8BFE\u5730\u70B9\u5305\u542B\u8FD9\u4E9B\u8BCD\u65F6\uFF0C"
                        + "\u81EA\u52A8\u4F7F\u7528\u672C\u4F5C\u606F\u8868\uFF1B\u7559\u7A7A\u8868\u793A"
                        + "\u4E0D\u81EA\u52A8\u5339\u914D\u3002",
                11, Palette.OUTLINE, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(4);
        section.addView(hint, hintParams);
        return section;
    }

    private View createPeriodsSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(15), dp(14), dp(15), dp(15));
        section.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L,
                Palette.OUTLINE_VARIANT, 1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(UiKit.text(this, "\u6BCF\u8282\u4E0A\u4E0B\u8BFE\u65F6\u95F4",
                12, Palette.ON_SURFACE, true), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        periodCountLabel = UiKit.text(this, periodCountText(),
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, false);
        titleRow.addView(periodCountLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(titleRow);

        // Said once, above the rows, because every row below depends on it: the end
        // column is not a field, it is the configured length after the start.
        TextView note = UiKit.text(this,
                "\u7ED3\u675F\u65F6\u95F4\u7531\u4E0A\u65B9\u7684\u8BFE\u65F6\u957F\u81EA"
                        + "\u52A8\u7B97\u51FA\uFF0C\u4E0D\u5355\u72EC\u7F16\u8F91\u3002",
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(4);
        section.addView(note, noteParams);

        errorBanner = UiKit.text(this, "", UiTokens.Type.LABEL_MEDIUM, Palette.ERROR, false);
        errorBanner.setVisibility(View.GONE);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerParams.topMargin = dp(6);
        section.addView(errorBanner, bannerParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(columnLabel("\u8282\u6B21", 0.8f, 0));
        header.addView(columnLabel("\u5F00\u59CB", 1.4f, 8));
        header.addView(columnLabel("\u7ED3\u675F", 1.4f, 8));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(9);
        section.addView(header, headerParams);

        // The rows live in their own container so adding or removing one is a
        // rebuild of this child. Removing only the row views from `section` would
        // mean tracking which of its children were rows and which were the header,
        // the note and the banner.
        periodRows = new LinearLayout(this);
        periodRows.setOrientation(LinearLayout.VERTICAL);
        section.addView(periodRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        section.addView(createPeriodButtons());

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = dp(12);
        section.setLayoutParams(sectionParams);
        rebuildPeriodRows();
        return section;
    }

    /** "共 15 节", so the count is visible without counting rows. */
    private String periodCountText() {
        return "\u5171 " + editor.periodCount() + " \u8282";
    }

    /**
     * The add and remove controls, below the rows.
     *
     * <p>Below rather than above because that is where the list ends, which is
     * where a reader looks for "and another one". They sit inside the same card as
     * the rows so they scroll with them -- a fixed bar at the bottom of the screen
     * would cover the last few periods, which is exactly the region these controls
     * are about.</p>
     *
     * <p>The height is taken from the bar rather than left to each button, and each
     * button fills it. Both buttons are weighted, and a weighted child of a
     * wrap-content row ends up sized from its text: the pills came out about a
     * third shorter than the compact button height and read as if they had been
     * cut off. An explicit height on the row is also how the course-detail row
     * does it, so the two rows match.</p>
     */
    private View createPeriodButtons() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(UiTokens.BUTTON_HEIGHT_COMPACT));
        barParams.topMargin = dp(UiTokens.SPACE_M);
        bar.setLayoutParams(barParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);

        bar.addView(UiKit.tonalButton(this, "\u6DFB\u52A0\u4E00\u8282",
                view -> addPeriod()), params);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightParams.leftMargin = dp(8);
        bar.addView(UiKit.outlinedButton(this, "\u51CF\u5C11\u4E00\u8282",
                view -> removePeriod()), rightParams);
        return bar;
    }

    /**
     * Appends a period and redraws the rows.
     *
     * <p>Nothing is written: the editor grows, the list is rebuilt from it, and the
     * save button is still the only thing that stores anything.</p>
     */
    private void addPeriod() {
        if (editor.addPeriod() == 0) {
            Toast.makeText(this,
                    "\u6700\u591A " + ScheduleUtils.MAX_PERIODS + " \u8282",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        rebuildPeriodRows();
        clearErrors();
    }

    /**
     * Removes the last period, refusing while a course still sits on it.
     *
     * <p>The refusal is the interesting half. A course keeps its period as a
     * number and looks its time up through the table that applies to it, so a
     * course left on a period the table no longer has would not fail visibly: it
     * would quietly be shown at the time of the period above. So the courses are
     * listed by name and the removal does not happen -- trimming a course or
     * renumbering it is a change to the user's timetable, not a side effect of
     * editing a bell.</p>
     *
     * <p>When nothing references the period, the removal is confirmed first: it
     * discards the last row's times, which is cheap to undo but not free.</p>
     */
    private void removePeriod() {
        if (editor.periodCount() <= BellSchedule.MIN_PERIODS) {
            Toast.makeText(this, "\u81F3\u5C11\u4FDD\u7559 1 \u8282",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int last = editor.periodCount();
        // Asked by the name the table will have once saved, because that is the name
        // courses resolve against. A rename and a trim in the same edit is rare, but
        // checking against the old name would silently pass while the new one is
        // what gets stored.
        String savingAs = editor.name().trim();
        String looked = savingAs.isEmpty() ? originalName : savingAs;
        List<Course> affected = repository.coursesUsingPeriodsFrom(looked, last);
        if (!affected.isEmpty()) {
            warnAboutReferencedPeriod(last, affected);
            return;
        }
        UiKit.dialog(this, "\u51CF\u5C11\u4E00\u8282",
                "\u786E\u5B9A\u5220\u9664\u7B2C " + last
                        + " \u8282\u5417\uFF1F\u5B83\u7684\u5F00\u59CB\u65F6\u95F4"
                        + "\u4F1A\u4E00\u5E76\u79FB\u9664\u3002",
                null, null,
                UiKit.Action.of("\u4FDD\u7559", null),
                UiKit.Action.destructive("\u5220\u9664\u7B2C " + last + " \u8282", () -> {
                    if (editor.removePeriod()) {
                        rebuildPeriodRows();
                        clearErrors();
                    }
                }));
    }

    /** Names the courses that block a removal, and says what unblocks it. */
    private void warnAboutReferencedPeriod(int period, List<Course> affected) {
        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (Course course : affected) {
            if (shown == 4) {
                names.append("\u7B49 ").append(affected.size()).append(" \u95E8\u8BFE\u7A0B");
                break;
            }
            if (names.length() > 0) {
                names.append("\u3001");
            }
            names.append(course.name);
            shown++;
        }
        String message = "\u7B2C " + period + " \u8282\u4ECD\u88AB\u8BFE\u7A0B"
                + "\u4F7F\u7528\uFF1A" + names
                + "\u3002\u8BF7\u5148\u5728\u8FD9\u4E9B\u8BFE\u7A0B\u91CC\u628A"
                + "\u7ED3\u675F\u8282\u6B21\u6539\u5230 " + (period - 1)
                + " \u6216\u4EE5\u524D\uFF0C\u518D\u56DE\u6765\u5220\u9664\u3002";
        if (errorBanner != null) {
            errorBanner.setText(message);
            errorBanner.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Rebuilds every period row from the editor.
     *
     * <p>The whole list is rebuilt rather than the one row that changed. The
     * numbers are positional -- period 3 is the third row -- so an append or a trim
     * shifts what every row below it should say, and rebuilding is both simpler and
     * the only version that cannot leave a stale number behind.</p>
     */
    private void rebuildPeriodRows() {
        if (periodRows == null) {
            return;
        }
        periodRows.removeAllViews();
        startInputs.clear();
        endInputs.clear();
        for (int period = 1; period <= editor.periodCount(); period++) {
            periodRows.addView(periodRow(period));
        }
        if (periodCountLabel != null) {
            periodCountLabel.setText(periodCountText());
        }
    }

    /**
     * Rewrites every end field from the current length.
     *
     * <p>Called when the length changes. Only the end column moves: a start is the
     * user's arrangement and the break between two periods belongs to the later
     * one, so recomputing starts here would quietly reflow a timetable that was set
     * on purpose. Each end is recomputed from its own start, which is also why the
     * periods below do not shift -- their starts are untouched.</p>
     */
    private void refreshEnds() {
        for (int period = 1; period <= editor.periodCount(); period++) {
            String suggested = editor.suggestedEndText(period);
            if (suggested.isEmpty()) {
                continue;
            }
            editor.setEndText(period, suggested);
            int index = period - 1;
            if (index < endInputs.size() && endInputs.get(index) != null) {
                Pickers.show(endInputs.get(index), suggested);
            }
        }
        clearErrors();
    }

    private TextView columnLabel(String label, float weight, int leftMargin) {
        TextView view = UiKit.text(this, label, UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, true);
        view.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = dp(leftMargin);
        view.setLayoutParams(params);
        return view;
    }

    private View periodRow(final int period) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView number = UiKit.text(this, String.valueOf(period),
                13, Palette.ON_SURFACE, true);
        number.setGravity(Gravity.CENTER);
        row.addView(number, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));

        // Tapping the start opens a clock. The editor stays the source of truth --
        // it is what validates and what builds -- so a choice is written straight
        // into it, exactly as a keystroke used to be.
        //
        // Only the start is a control. The end is the configured length after it, so
        // it is shown rather than asked for, and it is written into the editor as the
        // row is built so that what is on screen and what the save carries cannot
        // disagree. Only this period's end is affected by a change; the next
        // period's start is its own arrangement and the break between the two
        // belongs to the later one.
        String derived = editor.suggestedEndText(period);
        if (derived.isEmpty()) {
            // No readable start to work from -- an empty row, or one holding
            // something unreadable. Whatever is there is shown as it is; the checks
            // on save report it.
            derived = editor.endText(period);
        } else {
            editor.setEndText(period, derived);
        }
        final TextView end = Pickers.readOnlyField(this, derived);
        TextView start = Pickers.timeField(this, editor.startText(period),
                DEFAULT_START_HOUR, value -> {
                    editor.setStartText(period, value);
                    String suggested = editor.suggestedEndText(period);
                    if (!suggested.isEmpty()) {
                        editor.setEndText(period, suggested);
                        Pickers.show(end, suggested);
                    }
                    clearErrors();
                });
        // Appended, not indexed. The rows are rebuilt whenever the count changes, so
        // these two lists are cleared and refilled together and their order is the
        // rows' order -- which is what makes `endInputs` above reusable by period.
        startInputs.add(start);
        endInputs.add(end);
        row.addView(start, timeParams(8));
        row.addView(end, timeParams(8));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);
        return row;
    }

    private LinearLayout.LayoutParams timeParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(42), 1.4f);
        params.leftMargin = dp(leftMargin);
        return params;
    }

    private View field(String label, View input, int topMargin) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(UiKit.text(this, label, UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, true));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(6);
        wrapper.addView(input, inputParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        wrapper.setLayoutParams(params);
        return wrapper;
    }

    private EditText input(String hint, boolean centered) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Palette.OUTLINE);
        input.setTextColor(Palette.ON_SURFACE);
        input.setTextSize(UiTokens.Type.BODY_LARGE);
        input.setSingleLine(true);
        input.setMinHeight(dp(46));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER_LOW, UiTokens.RADIUS_M,
                Palette.OUTLINE_VARIANT, 1));
        if (centered) {
            input.setGravity(Gravity.CENTER);
        }
        return input;
    }

    private EditText timeInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        input.setText(value);
        input.setTextColor(Palette.ON_SURFACE);
        input.setTextSize(UiTokens.Type.BODY_MEDIUM);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setPadding(dp(6), dp(6), dp(6), dp(6));
        input.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER_LOW, UiTokens.RADIUS_S,
                Palette.OUTLINE_VARIANT, 1));
        return input;
    }

    private TextWatcher watcher(final Runnable onChange) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onChange.run();
                clearErrors();
            }
        };
    }

    private View createSaveBar() {
        LinearLayout bar = UiKit.actionBar(this);
        bar.addView(UiKit.primaryAction(this, "\u4FDD\u5B58\u4F5C\u606F\u8868",
                view -> validateAndSave()));

        if (existingSchedule) {
            TextView delete = UiKit.textButton(this, "\u5220\u9664\u4F5C\u606F\u8868",
                    Palette.ERROR, view -> confirmDelete());
            bar.addView(delete, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return bar;
    }

    /**
     * Asks before deleting the schedule this editor is showing.
     *
     * <p>The "keep one" rule is checked here as well as in the repository, so the
     * user is told instead of watching a confirmation dialog do nothing. The
     * repository still enforces it, because this is not the only way in.</p>
     */
    private void confirmDelete() {
        if (repository.getBellSchedules().size() <= 1) {
            Toast.makeText(this, "\u81F3\u5C11\u4FDD\u7559\u4E00\u4E2A\u4F5C\u606F\u8868",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        UiKit.dialog(this, "\u5220\u9664\u4F5C\u606F\u8868",
                "\u786E\u5B9A\u5220\u9664\u201C" + editor.name()
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664\u4F5C\u606F\u8868", () -> {
                    // By name, and the repository clears the courses that named it.
                    if (repository.deleteBellSchedule(editor.originalName())) {
                        // The times of every course that rang this table just moved:
                        // both the reminder plan and the widget are built from them.
                        ReminderScheduler.sync(this);
                        CourseWidgetUpdater.refreshAll(this);
                        setResult(RESULT_OK);
                        finish();
                    }
                }));
    }

    /**
     * Saves the editor's contents, or says why it cannot.
     *
     * <p>Problems go to the banner above the period rows rather than to a toast:
     * a toast disappears before a fifteen-row form can be scrolled to the field it
     * is complaining about. Overlapping periods are only a warning -- the school
     * rings one period's bell while another is still running in the C/D/E group,
     * so treating them as errors would reject the school's own timetable.</p>
     */
    private void validateAndSave() {
        List<BellScheduleEditor.Problem> problems = editor.problems();
        if (!problems.isEmpty()) {
            showProblems(problems);
            return;
        }
        // Overlaps are checked before the write rather than after it. A period lasts
        // whatever the length field says, so one choice can push its end into the
        // period above; the old order stored the table first and then said it was
        // wrong, which left an unusable timetable on disk and told the user about it
        // afterwards.
        List<Integer> overlaps = editor.overlappingPeriods();
        if (!overlaps.isEmpty()) {
            showOverlaps(overlaps);
            return;
        }
        // One call does three things that have to agree: replace the table this
        // editor opened (by name, so a list that moved underneath cannot redirect
        // the write), store it, and repoint every course that pinned the old name.
        // Saving the list directly, as this used to, silently left those courses
        // naming a table that no longer existed.
        repository.saveBellSchedule(originalName, editor.build());
        // Every course that rings this table now starts at a different minute, so
        // the alarms and the widget are both stale the moment this returns.
        ReminderScheduler.sync(this);
        CourseWidgetUpdater.refreshAll(this);
        Toast.makeText(this, "\u4F5C\u606F\u8868\u5DF2\u4FDD\u5B58",
                Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    /**
     * Reports periods that run into the one above, and stores nothing.
     *
     * <p>Says what is wrong and what fixes it: with a length of N minutes, the
     * starts have to be at least N minutes apart. The number comes from the form
     * rather than being written into the sentence, because the sentence used to
     * hard-code forty and would now be wrong on any table that rings longer.</p>
     */
    private void showOverlaps(List<Integer> periods) {
        String message = "\u7B2C " + joinPeriods(periods)
                + " \u8282\u4E0E\u4E0A\u4E00\u8282\u91CD\u53E0\uFF1A\u6BCF\u8282 "
                + editor.effectiveDurationText() + " \u5206\u949F"
                + "\uFF0C\u5F00\u59CB\u65F6\u95F4\u9700\u8981\u9694\u5F00\u3002";
        if (errorBanner != null) {
            errorBanner.setText(message);
            errorBanner.setVisibility(View.VISIBLE);
        }
        for (Integer period : periods) {
            if (period != null && period >= 1 && period <= startInputs.size()) {
                startInputs.get(period - 1).setError(message);
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** "3" or "3, 4, 5", for the overlap warning. */
    private static String joinPeriods(List<Integer> periods) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < periods.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(periods.get(i));
        }
        return text.toString();
    }

    /** Clears the banner and every field error, as soon as the user edits anything. */
    private void clearErrors() {
        if (errorBanner != null) {
            errorBanner.setVisibility(View.GONE);
        }
        if (nameInput != null) {
            nameInput.setError(null);
        }
        if (durationInput != null) {
            durationInput.setError(null);
        }
        for (TextView field : startInputs) {
            if (field != null) {
                field.setError(null);
            }
        }
        for (TextView field : endInputs) {
            if (field != null) {
                field.setError(null);
            }
        }
    }

    /**
     * Puts each problem where the user can act on it.
     *
     * <p>Problems that are about the schedule rather than one of its rows go to the
     * field they belong to rather than to the name box: {@code DURATION_INVALID} is
     * something the user fixes in the length field, and putting it on the name would
     * send them looking in the wrong place.</p>
     */
    private void showProblems(List<BellScheduleEditor.Problem> problems) {
        StringBuilder summary = new StringBuilder();
        for (BellScheduleEditor.Problem problem : problems) {
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append(problemText(problem));
            if (problem.period() >= 1 && problem.period() <= startInputs.size()) {
                startInputs.get(problem.period() - 1).setError(problemText(problem));
            } else if (problem.reason() == BellScheduleEditor.Reason.DURATION_INVALID
                    && durationInput != null) {
                durationInput.setError(problemText(problem));
            } else if (nameInput != null) {
                nameInput.setError(problemText(problem));
            }
        }
        if (errorBanner != null) {
            errorBanner.setText(summary.toString());
            errorBanner.setVisibility(View.VISIBLE);
        }
    }

    /** The sentence shown for one problem, in the user's language. */
    private String problemText(BellScheduleEditor.Problem problem) {
        switch (problem.reason()) {
            case NAME_REQUIRED:
                return "\u8BF7\u586B\u5199\u4F5C\u606F\u8868\u540D\u79F0\u3002";
            case NAME_TAKEN:
                return "\u5DF2\u6709\u540C\u540D\u4F5C\u606F\u8868\uFF0C\u8BF7\u6362\u4E00\u4E2A\u3002";
            case DURATION_INVALID:
                return "\u6BCF\u8282\u8BFE\u65F6\u957F\u8BF7\u586B "
                        + BellSchedule.MIN_PERIOD_MINUTES + "-"
                        + BellSchedule.MAX_PERIOD_MINUTES
                        + " \u4E4B\u95F4\u7684\u6574\u6570\u5206\u949F\u3002";
            case TIME_INVALID:
                return "\u7B2C " + problem.period()
                        + " \u8282\u7684\u65F6\u95F4\u8BF7\u586B\u6210 HH:mm\u3002";
            case INVERTED:
                return "\u7B2C " + problem.period()
                        + " \u8282\u7684\u7ED3\u675F\u65F6\u95F4\u9700\u665A\u4E8E\u5F00\u59CB\u65F6\u95F4\u3002";
            case TOO_FEW_PERIODS:
                return "\u81F3\u5C11\u4FDD\u7559 " + BellSchedule.MIN_PERIODS + " \u8282\u3002";
            case TOO_MANY_PERIODS:
                return "\u6700\u591A " + ScheduleUtils.MAX_PERIODS + " \u8282\u3002";
            default:
                return problem.toString();
        }
    }
    // ------------------------------------------------- unsaved form state

    /** Keys for the editor's uncommitted values, kept between rebuilds. */
    private static final String STATE_NAME = "draft_schedule_name";
    private static final String STATE_KEYWORDS = "draft_keywords";
    private static final String STATE_DURATION = "draft_duration";
    private static final String STATE_STARTS = "draft_starts";
    private static final String STATE_ENDS = "draft_ends";

    /**
     * Captures the editor as it stands, including times still being typed.
     *
     * <p>Every value here is text, and it is stored as the text it is. A field
     * showing {@code 8.0} or {@code 8:} is a time half-entered, not a time that is
     * wrong, and parsing it on the way out would either lose the characters the
     * caret is sitting in or replace them with a guess. The validation that will
     * eventually judge these strings runs when the save button is pressed, which is
     * the only moment its opinion is wanted.</p>
     *
     * <p>The period count travels as its own number beside the two text arrays.
     * Their length happens to match it because both come from the same editor, but
     * the number is what {@link ScheduleDraft#applyTo} acts on -- see there for why
     * reading the count out of the arrays cannot work.</p>
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ScheduleDraft draft = ScheduleDraft.of(editor);
        outState.putString(STATE_NAME, draft.name());
        outState.putString(STATE_KEYWORDS, draft.keywords());
        outState.putString(STATE_DURATION, draft.durationText());
        outState.putStringArray(STATE_STARTS,
                draft.startTexts().toArray(new String[0]));
        outState.putStringArray(STATE_ENDS, draft.endTexts().toArray(new String[0]));
    }

    /**
     * The state, or null on a first visit.
     *
     * <p>A missing name, keyword or length reads back as null and is left alone, so
     * a partial state leaves those fields on whatever initialisation chose. The two
     * per-period arrays are carried whole, because a period's text means nothing
     * without its neighbours: applying half a table would pair one row's start with
     * another row's end.</p>
     *
     * <p>The period count is carried as its own number rather than read off the
     * arrays, because a state saved from this screen describes the whole form -- it
     * is the form, not a fragment of one -- and its count is what the user had just
     * arranged by adding or removing a period. The form it is restored into was
     * rebuilt from storage, which still holds the count that was last saved, so
     * without the number a sixteenth period added a moment before the rebuild would
     * come back missing. {@link ScheduleDraft} has the two cases in full.</p>
     */
    private static ScheduleDraft draftFrom(Bundle state) {
        if (state == null) {
            return null;
        }
        String[] starts = state.getStringArray(STATE_STARTS);
        return new ScheduleDraft(
                state.getString(STATE_NAME),
                state.getString(STATE_KEYWORDS),
                state.getString(STATE_DURATION),
                starts == null ? ScheduleDraft.NOT_CARRIED : starts.length,
                starts,
                state.getStringArray(STATE_ENDS));
    }
    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

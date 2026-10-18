package com.weihe.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.CourseColorSync;
import com.weihe.schedule.rule.CourseConflicts;
import com.weihe.schedule.rule.CourseDraft;
import com.weihe.schedule.rule.CourseFormValidator;
import com.weihe.schedule.rule.WeekSelection;
import com.weihe.schedule.rule.CourseSuggestions;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

public class EditCourseActivity extends Activity {
    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_DAY = "day";
    /**
     * Set when this editor was opened to make a copy rather than to change the
     * course it was given. The fields are carried over and the identity is not,
     * so saving writes a new record and the original is untouched.
     */
    public static final String EXTRA_COPY = "copy";
    /**
     * Set when the user asked for the note specifically -- from the detail screen's
     * "add a note" or "edit" control -- rather than for the course as a whole.
     *
     * <p>It only decides where the caret starts. Nothing about what is read or
     * written changes, so a caller that omits it gets exactly the editor it had
     * before.</p>
     */
    public static final String EXTRA_FOCUS_NOTE = "focus_note";

    private static final int QUICK_WEEKS = 8;
    private static final int FIRST_HALF_END = Math.min(
            QUICK_WEEKS, ScheduleUtils.SEMESTER_WEEKS);
    private static final int SECOND_HALF_START = Math.max(
            1, ScheduleUtils.SEMESTER_WEEKS - QUICK_WEEKS + 1);

    private CourseRepository repository;
    private Course course;
    private boolean existingCourse;    /** Whether this editor is making a copy rather than changing a course. */    private boolean copying;
    private int selectedColor;
    /** The colour the stored course had when this editor opened. */
    private int originalColor;

    /** The answer to the colour question, or null while it has not been asked. */
    private Boolean colorSyncChoice;
    /** The form's uncommitted values, when this is a rebuild rather than a first visit. */
    private CourseDraft draft;

    private EditText nameInput;
    private EditText teacherInput;
    private EditText locationInput;
    /**
     * The course note.
     *
     * <p>A field of its own rather than a sixth line in the basic section: it is
     * the only thing on this form that is not about when or where the lesson
     * happens, and it is the only multi-line one.</p>
     */
    private EditText noteInput;
    /** The form's scroll view, kept so the note field can be scrolled to. */
    private ScrollView formScroll;
    private List<String> locationSuggestions = new ArrayList<>();
    private List<String> teacherSuggestions = new ArrayList<>();
    private EditText startWeekInput;
    private EditText endWeekInput;
    private Spinner daySpinner;
    private Spinner startPeriodSpinner;
    private Spinner endPeriodSpinner;
    /**
     * True while the period spinners are being resized.
     *
     * <p>Swapping their adapters fires their selection callbacks, which run
     * {@link #updateSchedulePreview()} and come straight back into
     * {@link #syncPeriodSpinners}. The flag makes the inner call a no-op instead of
     * a second resize in the middle of the first one.</p>
     */
    private boolean syncingPeriodSpinners;
    private UiKit.Choice parityChoice;
    private LinearLayout colorRow;
    private Spinner scheduleSpinner;
    private TextView schedulePreview;
    private TextView scheduleChip;
    private String[] scheduleOptions;
    private String[] scheduleValues;

    private TextView allWeeksChip;
    private TextView firstHalfChip;
    private TextView secondHalfChip;    private TextView weekSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        long id = getIntent().getLongExtra(EXTRA_COURSE_ID, 0L);
        Course source = id == 0L ? null : repository.findCourse(id);
        copying = getIntent().getBooleanExtra(EXTRA_COPY, false) && source != null;
        if (copying) {
            // Same fields, no identity: the id is what makes a course the one it
            // is, so dropping it is what turns "this course" into "another one
            // like it", and the save below writes a new record.
            course = source.copy();
            course.id = 0L;
        } else {
            course = source == null ? new Course() : source;
        }
        existingCourse = !copying && course.id != 0L;

        // Remembered before the form can touch it: whether the user changed the
        // colour is the difference between this and what the picker holds now.
        originalColor = course.color;
        if (course == null) {
            course = new Course();
        }
        if (!existingCourse) {
            course.dayOfWeek = Math.max(1,
                    Math.min(7, getIntent().getIntExtra(EXTRA_DAY, 1)));
            course.startWeek = 1;
            course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        }
        // Read once, before the form is built, from the courses the term already
        // holds. Opening the editor must change nothing, so this only reads -- and it
        // reads the active term list the repository returns, so no other term's names
        // can reach these rows.
        locationSuggestions = CourseSuggestions.locations(repository.getCourses());
        teacherSuggestions = CourseSuggestions.teachers(repository.getCourses());

        // Read before the form is built: the colour and the two radio-style rows are

        // construction parameters, not things that can be poked in afterwards. The

        // text fields and the plain spinners are applied by applyDraft() below.

        draft = draftFrom(savedInstanceState);

        selectedColor = draft != null && draft.hasColor() ? draft.color() : course.color;
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
        // Last, so the form already holds what storage said and the draft only says
        // what the user had changed on top of it. This writes into views and nothing
        // else: no save, no delete, no repository call.
        applyDraft();
        if (getIntent().getBooleanExtra(EXTRA_FOCUS_NOTE, false)) {
            focusNote();
        }
    }

    /**
     * Puts the caret in the note field, scrolls it into view and raises the keyboard.
     *
     * <p>Used when the detail screen sent the user here for the note itself. Without
     * it they arrive on a long form whose note field is at the very bottom, which
     * makes "add a note" a two-step action for no reason the user can see.</p>
     *
     * <p>Posted rather than run inline: nothing has been measured yet at this point,
     * and scrolling to a view with no position goes nowhere. The offset is worked
     * out from where the field and the scroll view actually are on screen rather
     * than from {@code getTop()}, which is relative to the field's own parent and
     * would be wrong by however many sections sit above it.</p>
     */
    private void focusNote() {
        if (noteInput == null || formScroll == null) {
            return;
        }
        formScroll.post(() -> {
            noteInput.requestFocus();
            int[] field = new int[2];
            int[] viewport = new int[2];
            noteInput.getLocationInWindow(field);
            formScroll.getLocationInWindow(viewport);
            int target = formScroll.getScrollY() + field[1] - viewport[1]
                    - dp(UiTokens.SPACE_XL);
            formScroll.smoothScrollTo(0, Math.max(0, target));
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(noteInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.SURFACE);
        root.addView(createHeader());

        ScrollView scroll = new ScrollView(this);
        formScroll = scroll;
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

        nameInput = input("\u8BFE\u7A0B\u540D\u79F0");
        nameInput.setText(course.name);

        locationInput = input("\u5982\uFF1A\u535A\u5B66\u697C A203");
        locationInput.setText(course.location);

        teacherInput = input("\u5982\uFF1A\u5F20\u8001\u5E08");
        teacherInput.setText(course.teacher);

        body.addView(createBasicSection());

        body.addView(createTimeSection());
        body.addView(createWeekSection());
        body.addView(createParitySection());
        body.addView(createScheduleSection());
        body.addView(createColorSection());
        body.addView(createNoteSection());
        // Read through the repository rather than off the record: the note belongs
        // to the course, so opening the second arrangement of a course has to show
        // what was written on the first one.
        noteInput.setText(repository.noteOf(course));

        locationInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSchedulePreview();
            }
        });
        updateSchedulePreview();

        root.addView(createSaveBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View createHeader() {
        return UiKit.detailAppBar(this,
                existingCourse ? "\u7F16\u8F91\u8BFE\u7A0B"
                : copying ? "\u6DFB\u52A0\u526F\u672C" : "\u6DFB\u52A0\u8BFE\u7A0B",
                null, view -> finish());
    }

    private View formField(String label, View field, int topMargin) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(UiKit.text(this, label, UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, true));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(6);
        wrapper.addView(field, fieldParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        wrapper.setLayoutParams(params);
        return wrapper;
    }

    /**
     * Shortcuts for one text field: tap one and it goes into that field.
     *
     * <p>The row belongs to the field above it and to no other. Tapping a venue
     * writes the venue and nothing else -- not the name, not the day, not the
     * weeks -- so the shortcut can never turn into an accidental edit of something
     * the user did not touch. The field stays a text field: what is offered is a
     * shortcut, not a set of allowed answers.</p>
     *
     * <p>Laid out three to a line, the way the week picker lays out its cells, so
     * the row cannot grow sideways on a narrow phone. Cells share the width, and a
     * short last line is padded with spacers so its chips are the same size as the
     * ones above rather than stretching to fill.</p>
     *
     * <p>Nothing to offer means nothing is drawn: an empty band under a field
     * would read as a part of the form that failed to load.</p>
     */
    private View suggestions(final EditText target, List<String> values, int topMargin) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        if (values.isEmpty()) {
            grid.setVisibility(View.GONE);
            return grid;
        }
        final int columns = 3;
        LinearLayout line = null;
        for (int index = 0; index < values.size(); index++) {
            if (index % columns == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lineParams.topMargin = index == 0 ? dp(topMargin) : dp(UiTokens.SPACE_S);
                grid.addView(line, lineParams);
            }
            final String value = values.get(index);
            TextView chip = UiKit.chip(this, value, false);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setOnClickListener(view -> fill(target, value));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    0, dp(UiTokens.CHIP_HEIGHT), 1f);
            if (index % columns != 0) {
                chipParams.leftMargin = dp(UiTokens.SPACE_S);
            }
            line.addView(chip, chipParams);
        }
        int remainder = values.size() % columns;
        if (remainder != 0 && line != null) {
            for (int slot = remainder; slot < columns; slot++) {
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                        0, 1, 1f);
                spacerParams.leftMargin = dp(UiTokens.SPACE_S);
                line.addView(new View(this), spacerParams);
            }
        }
        return grid;
    }

    /**
     * Puts a suggested value into its field.
     *
     * <p>It replaces what was there, which is what a tap on a named shortcut is
     * understood to mean. The caret moves to the end and the field takes focus, so
     * a user who wants {@code 教学楼 A203 三层} can keep typing on top of the
     * shortcut instead of having to tap the field first.</p>
     */
    private void fill(EditText target, String value) {
        target.setText(value);
        target.setSelection(target.getText().length());
        target.requestFocus();
    }
    private View createBasicSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(13), dp(14), dp(14));
        section.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        section.addView(UiKit.text(this, "\u8BFE\u7A0B\u4FE1\u606F",
                12, Palette.ON_SURFACE, true));
        section.addView(formField("\u8BFE\u7A0B\u540D\u79F0", nameInput, 11));
        section.addView(formField("\u4E0A\u8BFE\u5730\u70B9", locationInput, 11));
        section.addView(suggestions(locationInput, locationSuggestions, 6));
        section.addView(formField("\u4EFB\u8BFE\u6559\u5E08", teacherInput, 11));
        section.addView(suggestions(teacherInput, teacherSuggestions, 6));
        return section;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Palette.OUTLINE);
        input.setTextColor(Palette.ON_SURFACE);
        input.setTextSize(UiTokens.Type.BODY_LARGE);
        input.setMinHeight(dp(46));
        input.setSingleLine(true);
        // "Next" rather than a bare newline: the three text fields of a course are
        // filled in one pass, so the keyboard should walk them instead of asking
        // the user to reach for each one. The last field says "done".
        input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_LOW, UiTokens.RADIUS_M));
        return input;
    }

    private View createTimeSection() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(13), dp(14), dp(14));
        wrapper.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        wrapper.addView(UiKit.text(this, "\u4E0A\u8BFE\u65F6\u95F4",
                12, Palette.ON_SURFACE, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] days = new String[7];
        for (int i = 0; i < days.length; i++) {
            days[i] = ScheduleUtils.dayName(i + 1);
        }
        daySpinner = spinner(days, course.dayOfWeek - 1);
        row.addView(daySpinner, new LinearLayout.LayoutParams(
                0, dp(44), 1.25f));

        String[] periods = ScheduleUtils.periodLabels();
        startPeriodSpinner = spinner(periods, course.startPeriod - 1);
        endPeriodSpinner = spinner(periods, course.endPeriod - 1);
        AdapterView.OnItemSelectedListener periodListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        updateSchedulePreview();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                };
        startPeriodSpinner.setOnItemSelectedListener(periodListener);
        endPeriodSpinner.setOnItemSelectedListener(periodListener);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        spinnerParams.leftMargin = dp(8);
        row.addView(startPeriodSpinner, spinnerParams);
        TextView divider = UiKit.text(this, "\u81F3", UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE_VARIANT, true);
        divider.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                dp(28), ViewGroup.LayoutParams.WRAP_CONTENT);
        dividerParams.leftMargin = dp(4);
        row.addView(divider, dividerParams);
        row.addView(endPeriodSpinner, new LinearLayout.LayoutParams(
                0, dp(44), 1f));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        wrapper.addView(row, rowParams);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.topMargin = dp(12);
        wrapper.setLayoutParams(wrapperParams);
        return wrapper;
    }

    private Spinner spinner(String[] values, int selectedIndex) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList(values));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(values.length - 1, selectedIndex)));
        spinner.setPadding(dp(10), 0, dp(6), 0);
        spinner.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_LOW, UiTokens.RADIUS_M));
        return spinner;
    }

    private View createWeekSection() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(13), dp(14), dp(14));
        wrapper.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        wrapper.addView(UiKit.text(this, "\u4E0A\u8BFE\u5468\u6B21",
                12, Palette.ON_SURFACE, true));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        allWeeksChip = quickChip("\u5168\u5B66\u671F");
        firstHalfChip = quickChip("\u524D\u516B\u5468");
        secondHalfChip = quickChip("\u540E\u516B\u5468");
        allWeeksChip.setOnClickListener(view ->
                applyWeeks(1, ScheduleUtils.SEMESTER_WEEKS));
        firstHalfChip.setOnClickListener(view -> applyWeeks(1, FIRST_HALF_END));
        secondHalfChip.setOnClickListener(view ->
                applyWeeks(SECOND_HALF_START, ScheduleUtils.SEMESTER_WEEKS));
        quickRow.addView(allWeeksChip, chipParams(0, 0));
        quickRow.addView(firstHalfChip, chipParams(8, 0));
        quickRow.addView(secondHalfChip, chipParams(8, 0));
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        quickParams.topMargin = dp(10);
        wrapper.addView(quickRow, quickParams);

        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        startWeekInput = numberInput(course.startWeek);
        endWeekInput = numberInput(course.endWeek);
        rangeRow.addView(startWeekInput, new LinearLayout.LayoutParams(
                0, dp(42), 1f));
        TextView divider = UiKit.text(this, "\u81F3", UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE_VARIANT, true);
        divider.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                dp(34), ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeRow.addView(divider, dividerParams);
        rangeRow.addView(endWeekInput, new LinearLayout.LayoutParams(
                0, dp(42), 1f));
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = dp(10);
        wrapper.addView(rangeRow, rangeParams);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateWeekChipState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        startWeekInput.addTextChangedListener(watcher);
        endWeekInput.addTextChangedListener(watcher);

        // What the range and the parity add up to, on one line. The two controls
        // above and beside this say which weeks are in range and which parity was
        // picked; neither says how many weeks the course actually meets, and the
        // combination can even come to none at all.
        weekSummary = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(8);
        wrapper.addView(weekSummary, summaryParams);

        updateWeekChipState();

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.topMargin = dp(12);
        wrapper.setLayoutParams(wrapperParams);
        return wrapper;
    }

    private TextView quickChip(String value) {
        TextView chip = UiKit.text(this, value, UiTokens.Type.LABEL_LARGE, Palette.ON_SURFACE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(8), dp(8), dp(8));
        chip.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_HIGH, UiTokens.RADIUS_S));
        return chip;
    }

    private LinearLayout.LayoutParams chipParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(38), 1f);
        params.leftMargin = dp(left);
        params.rightMargin = dp(right);
        return params;
    }

    private EditText numberInput(int value) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextColor(Palette.ON_SURFACE);
        input.setTextSize(UiTokens.Type.BODY_MEDIUM);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setPadding(dp(8), dp(8), dp(8), dp(8));
        input.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_LOW, UiTokens.RADIUS_S));
        return input;
    }

    private void applyWeeks(int start, int end) {
        startWeekInput.setText(String.valueOf(start));
        endWeekInput.setText(String.valueOf(end));
        updateWeekChipState();
    }

    private void updateWeekChipState() {
        Integer start = CourseFormValidator.parseWeekOrNull(
                startWeekInput.getText().toString());
        Integer end = CourseFormValidator.parseWeekOrNull(
                endWeekInput.getText().toString());

        styleQuickChip(allWeeksChip,
                isRange(start, end, 1, ScheduleUtils.SEMESTER_WEEKS));
        styleQuickChip(firstHalfChip, isRange(start, end, 1, FIRST_HALF_END));
        styleQuickChip(secondHalfChip,
                isRange(start, end, SECOND_HALF_START,
                        ScheduleUtils.SEMESTER_WEEKS));

        refreshWeekSummary();
    }

    /**
     * Says what the selected weeks and parity come to.
     *
     * <p>Called from every control that can change either half, so the line can
     * never describe a selection that is no longer on screen. A range that meets on
     * no week at all is the one case worth colouring: it is a real configuration
     * the form accepts, and saving it produces a course that never appears.</p>
     */
    private void refreshWeekSummary() {
        if (weekSummary == null) {
            return;
        }
        Integer start = CourseFormValidator.parseWeekOrNull(
                startWeekInput.getText().toString());
        Integer end = CourseFormValidator.parseWeekOrNull(
                endWeekInput.getText().toString());
        if (start == null || end == null) {
            // Half-typed: the summary has nothing true to say, and guessing would
            // describe a range the user has not chosen.
            weekSummary.setText("");
            return;
        }
        int parity = selectedParity();
        weekSummary.setText(WeekSelection.summary(start, end, parity));
        weekSummary.setTextColor(WeekSelection.meetsOnNoWeek(start, end, parity)
                ? Palette.ERROR : Palette.ON_SURFACE_VARIANT);
    }

    private boolean isRange(Integer start, Integer end, int from, int to) {
        return start != null && end != null
                && start.intValue() == from && end.intValue() == to;
    }

    private void styleQuickChip(TextView chip, boolean selected) {
        if (chip == null) {
            return;
        }
        chip.setTextColor(selected ? Palette.ON_PRIMARY : Palette.ON_SURFACE);
        chip.setBackground(UiKit.fill(this,
                selected ? Palette.PRIMARY : Palette.SURFACE_CONTAINER_HIGH, UiTokens.RADIUS_S));
    }

    /**
     * The every/odd/even row.
     *
     * <p>Suppressed for the same reason as the week swipe in {@code MainActivity}:
     * the touch listener replaces no click and returns false, so the row keeps
     * handling its own taps. It exists only to notice that the parity changed, so
     * the week summary above can be brought up to date.</p>
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private View createParitySection() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(13), dp(14), dp(12));
        wrapper.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        wrapper.addView(UiKit.text(this, "\u5355\u53CC\u5468",
                12, Palette.ON_SURFACE, true));
        // Drawn here rather than with RadioButton: three platform buttons next to
        // this app's own typography read as a different application.
        parityChoice = UiKit.radioRow(this, PARITY_LABELS, parityIndexFor(draft));
        // The radio row has no change callback, so the summary is refreshed by
        // watching the touch instead. Returning false leaves the row to handle the
        // event itself: this observes the tap, it does not take it.
        parityChoice.view().setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                view.post(this::refreshWeekSummary);
            }
            return false;
        });
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        groupParams.topMargin = dp(UiTokens.SPACE_S);
        wrapper.addView(parityChoice.view(), groupParams);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.topMargin = dp(12);
        wrapper.setLayoutParams(wrapperParams);
        return wrapper;
    }

    /** Position of {@code parity} in the \u6BCF\u5468 / \u5355\u5468 / \u53CC\u5468 row. */
    private static int parityIndex(int parity) {
        if (parity == Course.PARITY_ODD) {
            return 1;
        }
        if (parity == Course.PARITY_EVEN) {
            return 2;
        }
        return 0;
    }

    /** The parity the radio row is showing. */
    private int selectedParity() {
        int index = parityChoice == null ? 0 : parityChoice.selected();
        if (index == 1) {
            return Course.PARITY_ODD;
        }
        if (index == 2) {
            return Course.PARITY_EVEN;
        }
        return Course.PARITY_ALL;
    }

    /**
     * Which bell schedule the course rings to.
     *
     * <p>The first entry means "decide by venue", which is resolved through
     * {@link BellSchedules#resolve}: the location decides, and the standard
     * schedule is the fallback. Anything else pins one schedule explicitly, so a
     * course in an unlabelled room can still use the right hours.</p>
     */
    private View createScheduleSection() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(13), dp(14), dp(14));
        wrapper.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(UiKit.text(this, "\u4E0A\u4E0B\u8BFE\u65F6\u95F4",
                12, Palette.ON_SURFACE, true), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        scheduleChip = UiKit.text(this, "", UiTokens.Type.LABEL_SMALL, Palette.PRIMARY, true);
        scheduleChip.setGravity(Gravity.CENTER);
        scheduleChip.setPadding(dp(7), dp(3), dp(7), dp(3));
        scheduleChip.setBackground(UiKit.fill(this, Palette.PRIMARY_CONTAINER, 6));
        titleRow.addView(scheduleChip);
        wrapper.addView(titleRow);

        List<BellSchedule> schedules = repository.getBellSchedules();
        scheduleOptions = new String[schedules.size() + 1];
        scheduleValues = new String[schedules.size() + 1];
        scheduleOptions[0] = "\u81EA\u52A8\uFF08\u6309\u4E0A\u8BFE\u5730\u70B9\uFF09";
        scheduleValues[0] = "";
        for (int i = 0; i < schedules.size(); i++) {
            scheduleOptions[i + 1] = schedules.get(i).name();
            scheduleValues[i + 1] = schedules.get(i).name();
        }

        // Positions are checked against this spinner's own list, which is read
        // from storage and may have changed while the state was away.
        int restoredSchedule = draft == null ? CourseDraft.NO_INDEX
                : CourseDraft.usableIndex(draft.scheduleIndex(), scheduleOptions.length);
        scheduleSpinner = spinner(scheduleOptions, restoredSchedule != CourseDraft.NO_INDEX
                ? restoredSchedule : indexOfSchedule(course.scheduleName));
        scheduleSpinner.setContentDescription("\u9009\u62E9\u4F5C\u606F\u8868");
        scheduleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                updateSchedulePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        spinnerParams.topMargin = dp(10);
        wrapper.addView(scheduleSpinner, spinnerParams);

        schedulePreview = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE, Palette.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        previewParams.topMargin = dp(8);
        wrapper.addView(schedulePreview, previewParams);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.topMargin = dp(12);
        wrapper.setLayoutParams(wrapperParams);
        return wrapper;
    }

    private int indexOfSchedule(String name) {
        if (name == null || name.trim().isEmpty()) {
            return 0;
        }
        for (int i = 0; i < scheduleValues.length; i++) {
            if (scheduleValues[i].equals(name.trim())) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Shows the clock times the current form would produce.
     *
     * <p>This is the payoff of per-course schedules: changing the venue of a
     * course in the lab building visibly moves its times.</p>
     */
    private void updateSchedulePreview() {
        if (schedulePreview == null || scheduleSpinner == null) {
            return;
        }
        int position = Math.max(0, Math.min(scheduleValues.length - 1,
                scheduleSpinner.getSelectedItemPosition()));
        String chosen = scheduleValues[position];
        Course scratch = new Course();
        scratch.startPeriod = startPeriodSpinner == null ? 1
                : startPeriodSpinner.getSelectedItemPosition() + 1;
        scratch.endPeriod = endPeriodSpinner == null ? scratch.startPeriod
                : endPeriodSpinner.getSelectedItemPosition() + 1;
        scratch.scheduleName = chosen;
        scratch.location = locationInput == null ? ""
                : locationInput.getText().toString().trim();

        BellSchedule applied = repository.scheduleFor(scratch);
        schedulePreview.setText(chosen.isEmpty()
                ? "\u5F53\u524D\u9002\u7528\uFF1A" + applied.name() + "\uFF0C"
                        + scratch.startPeriod + "-" + scratch.endPeriod + "\u8282 "
                        + applied.timeRange(scratch.startPeriod, scratch.endPeriod)
                : scratch.startPeriod + "-" + scratch.endPeriod + "\u8282 "
                        + applied.timeRange(scratch.startPeriod, scratch.endPeriod)
                        + "\uFF0C\u56FA\u5B9A\u4F7F\u7528\u8BE5\u4F5C\u606F\u8868");
        scheduleChip.setText(chosen.isEmpty() ? "\u81EA\u52A8" : "\u6307\u5B9A");
        syncPeriodSpinners(applied);
    }

    /**
     * Grows or shrinks the period spinners to the applying schedule's day.
     *
     * <p>The day is not one number for the whole app any more, so the list of
     * periods on offer has to come from the schedule this course will actually ring
     * to. Offering the default fifteen to a course in a twenty-period venue would
     * make its eighteenth period unselectable; offering twenty to one on a
     * fifteen-period table would let the user pick a period that then resolves to
     * the wrong bell.</p>
     *
     * <p>The current selection is kept where it still fits, so resizing does not
     * move a choice the user already made. Both spinners are resized together, and
     * the end is pulled back up to the start when the shorter day no longer reaches
     * it -- otherwise a shrinking day would leave the course inverted, which the
     * form validator would then report as a problem the user did not cause.</p>
     *
     * <p>Re-entrancy is guarded rather than merely unlikely: {@link #resyncSpinner}
     * swaps an adapter, which fires the period spinners' own selection callback,
     * which comes back here through {@link #updateSchedulePreview}. Without the
     * flag that is an adapter swap inside an adapter swap, and the second swap
     * would re-enter before the first has restored its selection.</p>
     */
    private void syncPeriodSpinners(BellSchedule applied) {
        if (syncingPeriodSpinners || applied == null
                || startPeriodSpinner == null || endPeriodSpinner == null) {
            return;
        }
        int count = Math.max(1, Math.min(ScheduleUtils.MAX_PERIODS,
                applied.periodCount()));
        if (startPeriodSpinner.getCount() == count
                && endPeriodSpinner.getCount() == count) {
            return;
        }
        syncingPeriodSpinners = true;
        try {
            resyncSpinner(startPeriodSpinner, count);
            resyncSpinner(endPeriodSpinner, count);
            int start = startPeriodSpinner.getSelectedItemPosition() + 1;
            if (endPeriodSpinner.getSelectedItemPosition() + 1 < start) {
                endPeriodSpinner.setSelection(start - 1);
            }
        } finally {
            syncingPeriodSpinners = false;
        }
    }

    /** Replaces a period spinner's entries with {@code 1..count}, keeping its choice. */
    private void resyncSpinner(Spinner spinner, int count) {
        int selected = spinner.getSelectedItemPosition();
        if (spinner.getCount() == count) {
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList(ScheduleUtils.periodLabels(count)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // setAdapter resets the selection, so the old index is restored explicitly.
        // Clamped: a period that the shorter day no longer has lands on the last one
        // it does, which is the nearest thing to it that still exists.
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(count - 1, selected)));
    }

    /**
     * A colour swatch, and the space between two of them.
     *
     * <p>30dp is the size this strip has always used. The gap used to be 8dp, which
     * put nine swatches at 334dp and pushed the last one off a 360dp screen; at 4dp
     * the nine come to 302dp, inside the 355dp this card can use on a 411dp screen.
     * The size is not shrunk to fit narrower screens -- the strip scrolls instead,
     * because a swatch below 30dp stops being a comfortable target.</p>
     */
    private static final int SWATCH_DP = 30;
    private static final int SWATCH_GAP_DP = 4;

    /** The ring drawn on the chosen swatch, in dp. */
    private static final int SWATCH_RING_DP = 2;

    private View createColorSection() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(13), dp(14), dp(14));
        wrapper.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        wrapper.addView(UiKit.text(this, "\u8BFE\u7A0B\u989C\u8272",
                12, Palette.ON_SURFACE, true));
        // The strip scrolls sideways rather than wrapping. Nine colours fit a normal
        // screen in a single row; a tenth appears only for a course saved under an
        // older palette, and one extra swatch is a reason to move the row, not to
        // add a second one -- a grid turns a strip of colours into a block of them
        // and stops reading as a palette.
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(colorRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(strip);
        renderColorSwatches();
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.topMargin = dp(12);
        wrapper.setLayoutParams(wrapperParams);
        return wrapper;
    }

    /**
     * The course note, last on the form.
     *
     * <p>Last because everything above decides when and where the lesson happens,
     * and this is the only place to write down something about the course itself.
     * The label says 课程备注 rather than 备注 on purpose: the note belongs to the
     * course, not to the arrangement open in front of the user, and a shorter label
     * would suggest the other thing.</p>
     *
     * <p>The field grows to eight lines and then scrolls inside itself. It is inside
     * a scroll view already, so letting it grow without limit would make a long note
     * push the save button an unpredictable distance away, while a fixed two-line
     * box would make writing one unpleasant.</p>
     */
    private View createNoteSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(13), dp(14), dp(14));
        section.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L,
                Palette.OUTLINE_VARIANT, 1));
        section.addView(UiKit.text(this, "\u8BFE\u7A0B\u5907\u6CE8",
                12, Palette.ON_SURFACE, true));

        noteInput = new EditText(this);
        noteInput.setHint("\u8BB0\u5F55\u4F5C\u4E1A\u3001\u8003\u8BD5\u5B89\u6392\u3001"
                + "\u5B66\u4E60\u8D44\u6599\u7B49");
        noteInput.setHintTextColor(Palette.OUTLINE);
        noteInput.setTextColor(Palette.ON_SURFACE);
        noteInput.setTextSize(UiTokens.Type.BODY_MEDIUM);
        noteInput.setSingleLine(false);
        noteInput.setMinLines(3);
        noteInput.setMaxLines(8);
        noteInput.setGravity(Gravity.TOP | Gravity.START);
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        // Enter inserts a line break here: this is the one field on the form where
        // a newline is content rather than a reason to move on.
        noteInput.setImeOptions(EditorInfo.IME_ACTION_NONE
                | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        noteInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        noteInput.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_M));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(8);
        section.addView(noteInput, fieldParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        section.setLayoutParams(params);
        return section;
    }

    /**
     * Draws the course colours as one row of swatches.
     *
     * <p>One row, not a grid. Values that belong together read as a strip; wrapping
     * the same nine three by three reads as a block, which is a different thing on
     * the page, a taller one, and no longer a palette.</p>
     *
     * <p>{@link Palette#swatchesFor(int)} may hand back a tenth colour: one the
     * course was saved with that the palette no longer lists. It is kept, last, and
     * the strip scrolls to reach it rather than dropping it or wrapping onto a
     * second row -- see {@link #createColorSection()}.</p>
     */
    private void renderColorSwatches() {
        if (colorRow == null) {
            return;
        }
        colorRow.removeAllViews();
        int[] swatches = Palette.swatchesFor(selectedColor);
        for (int i = 0; i < swatches.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(SWATCH_DP), dp(SWATCH_DP));
            if (i < swatches.length - 1) {
                params.rightMargin = dp(SWATCH_GAP_DP);
            }
            colorRow.addView(colorSwatch(swatches[i]), params);
        }
    }

    /**
     * One swatch: the colour, rounded, with a ring when it is the chosen one.
     *
     * <p>The mark is a stroke on the swatch's own background rather than a separate
     * view, so it cannot drift away from the colour it marks and never becomes a
     * second thing to read. An unselected swatch carries a zero-width stroke, which
     * keeps every swatch the same size and keeps the ring from reading as a colour
     * of its own.</p>
     */
    private View colorSwatch(final int color) {
        boolean selected = color == selectedColor;
        View swatch = new View(this);
        swatch.setContentDescription(selected
                ? "\u5DF2\u9009\u989C\u8272" : "\u9009\u62E9\u989C\u8272");
        swatch.setBackground(UiKit.outline(this, color, UiTokens.RADIUS_S,
                selected ? Palette.ON_SURFACE : Color.TRANSPARENT,
                selected ? SWATCH_RING_DP : 0));
        swatch.setOnClickListener(view -> {
            selectedColor = color;
            renderColorSwatches();
        });
        return swatch;
    }

    private View createSaveBar() {
        LinearLayout bar = UiKit.actionBar(this);
        bar.addView(UiKit.primaryAction(this, "\u4FDD\u5B58\u8BFE\u7A0B",
                view -> validateAndSave()));

        if (existingCourse) {
            TextView delete = UiKit.textButton(this,
                    "\u5220\u9664\u8BFE\u7A0B", Palette.ERROR, view -> confirmDelete());
            bar.addView(delete, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return bar;
    }

    /**
     * Validates the form and hands over to {@link #commitSave()}.
     *
     * <p>The rules themselves live in {@link CourseFormValidator}, which knows
     * nothing about Android. This method only turns the reported problems into
     * field errors and wording. When the edited course would clash with an
     * existing one the user gets a confirmation instead of a silent double
     * booking, courtesy of {@link CourseConflicts}.</p>
     */
    private void validateAndSave() {
        nameInput.setError(null);
        startWeekInput.setError(null);
        endWeekInput.setError(null);

        Integer startWeek = CourseFormValidator.parseWeekOrNull(
                startWeekInput.getText().toString());
        Integer endWeek = CourseFormValidator.parseWeekOrNull(
                endWeekInput.getText().toString());
        int startPeriod = startPeriodSpinner.getSelectedItemPosition() + 1;
        int endPeriod = endPeriodSpinner.getSelectedItemPosition() + 1;

        // Checked against the day of the schedule that applies, not against the
        // app's default fifteen: the spinner only offers what that schedule has, so
        // this catches the one case the spinner cannot -- a table edited on another
        // screen since this form was opened.
        Course scratch = new Course();
        scratch.scheduleName = scheduleValues[Math.max(0, Math.min(
                scheduleValues.length - 1, scheduleSpinner.getSelectedItemPosition()))];
        scratch.location = locationInput.getText().toString().trim();
        int periodCount = repository.scheduleFor(scratch).periodCount();
        List<CourseFormValidator.Problem> problems = CourseFormValidator.check(
                nameInput.getText().toString(), startWeek, endWeek,
                startPeriod, endPeriod, periodCount);
        if (report(problems)) {
            return;
        }

        // No problems means both week fields were readable.
        applyForm(startWeek.intValue(), endWeek.intValue(), startPeriod, endPeriod);

        List<Course> conflicts = CourseConflicts.findConflicts(
                repository.getCourses(), course);
        if (conflicts.isEmpty()) {
            commitSave();
            return;
        }
        UiKit.dialog(this, "\u65F6\u95F4\u51B2\u7A81",
                conflictMessage(conflicts), null, null,
                UiKit.Action.of("\u8FD4\u56DE\u4FEE\u6539", null),
                UiKit.Action.of("\u4ECD\u7136\u4FDD\u5B58", () -> commitSave()));
    }

    /**
     * Shows every reported problem.
     *
     * <p>A problem tied to a text field becomes a field error; the period range
     * has no text field, so it is reported as a toast instead.</p>
     *
     * @return true when saving must wait
     */
    private boolean report(List<CourseFormValidator.Problem> problems) {
        if (problems.isEmpty()) {
            return false;
        }
        EditText firstInvalid = null;
        for (CourseFormValidator.Problem problem : problems) {
            String message = messageFor(problem);
            EditText field = fieldFor(problem);
            if (field == null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else {
                field.setError(message);
                if (firstInvalid == null) {
                    firstInvalid = field;
                }
            }
        }
        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }
        return true;
    }

    /** The wording shown for a problem. Keeping it here, not in the rules. */
    private String messageFor(CourseFormValidator.Problem problem) {
        switch (problem.reason()) {
            case REQUIRED:
                return "\u8BF7\u8F93\u5165\u8BFE\u7A0B\u540D\u79F0";
            case NOT_A_NUMBER:
                return "\u8BF7\u8F93\u5165\u6709\u6548\u5468\u6B21";
            case OUT_OF_RANGE:
                if (problem.field() == CourseFormValidator.Field.PERIODS) {
                    return "\u8282\u6B21\u8D85\u51FA\u8303\u56F4";
                }
                return "\u5468\u6B21\u5FC5\u987B\u5728 1-"
                        + ScheduleUtils.SEMESTER_WEEKS + " \u4E4B\u95F4";
            case START_AFTER_END:
                return "\u5F00\u59CB\u5468\u4E0D\u80FD\u665A\u4E8E\u7ED3\u675F\u5468";
            case INVERTED:
                return "\u5F00\u59CB\u8282\u6B21\u4E0D\u80FD\u665A\u4E8E\u7ED3\u675F\u8282\u6B21";
            default:
                return "";
        }
    }

    /** The field a problem belongs to, or null when it has no text field. */
    private EditText fieldFor(CourseFormValidator.Problem problem) {
        switch (problem.field()) {
            case NAME:
                return nameInput;
            case START_WEEK:
                return startWeekInput;
            case END_WEEK:
                return endWeekInput;
            default:
                return null;
        }
    }

    /** Copies the validated form into the course that is about to be stored. */
    private void applyForm(int startWeek, int endWeek, int startPeriod, int endPeriod) {
        course.name = nameInput.getText().toString().trim();
        course.location = locationInput.getText().toString().trim();
        course.teacher = teacherInput.getText().toString().trim();
        course.dayOfWeek = daySpinner.getSelectedItemPosition() + 1;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = startWeek;
        course.endWeek = endWeek;
        course.parity = selectedParity();
        course.color = selectedColor;
        int schedulePosition = scheduleSpinner == null ? 0
                : scheduleSpinner.getSelectedItemPosition();
        if (scheduleValues != null && schedulePosition >= 0
                && schedulePosition < scheduleValues.length) {
            course.scheduleName = scheduleValues[schedulePosition];
        }
        course.timeRange = repository.scheduleFor(course)
                .timeRange(startPeriod, endPeriod);
    }

    private String conflictMessage(List<Course> conflicts) {
        StringBuilder message = new StringBuilder(
                "\u4E0E\u4EE5\u4E0B\u8BFE\u7A0B\u65F6\u95F4\u91CD\u53E0\uFF1A");
        for (Course conflict : conflicts) {
            message.append('\n').append('\u00B7').append(' ')
                    .append(CourseConflicts.describe(conflict));
        }
        message.append('\n').append("\u4ECD\u8981\u4FDD\u5B58\u5417\uFF1F");
        return message.toString();
    }

    /**
     * Writes the form, after asking about the colour if there is anything to ask.
     *
     * <p>The question is put here rather than in the validation because this is the
     * one point every save passes through -- the plain path and the one confirmed
     * after a clash both arrive here -- so it is asked exactly once, and only when
     * the colour was actually changed and other arrangements of the same course
     * exist to change with it.</p>
     */
    private void commitSave() {
        if (colorSyncChoice == null && selectedColor != originalColor) {
            List<Course> affected = CourseColorSync.sameCourseAs(
                    repository.getCourses(), course);
            if (!CourseColorSync.needingColor(affected, selectedColor).isEmpty()) {
                showColorSyncChoice(affected);
                return;
            }
        }
        // One write for the record and its course-wide note: the note belongs to
        // the course, so saving from any arrangement has to reach all of them.
        repository.saveCourse(course, noteInput.getText().toString());
        String note = "\u8BFE\u7A0B\u5DF2\u4FDD\u5B58";
        if (Boolean.TRUE.equals(colorSyncChoice)) {
            // One batch call for every other arrangement. Read after the save above,
            // so the record being edited is already carrying the new colour and is
            // not counted twice.
            int changed = repository.recolorCourses(
                    CourseColorSync.needingColor(
                            CourseColorSync.sameCourseAs(repository.getCourses(), course),
                            selectedColor),
                    selectedColor);
            note = "\u8BFE\u7A0B\u5DF2\u4FDD\u5B58\uFF0C\u5DF2\u540C\u6B65 " + changed
                    + " \u6761\u540C\u540D\u5B89\u6392\u7684\u989C\u8272";
        }
        setResult(RESULT_OK);
        // The alarms were planned from the timetable as it was a minute ago. Rebuild
        // them here rather than waiting for the home screen to resume: a user who
        // edits a class and then leaves the app never passes through it, and would
        // otherwise be reminded at the old time until the next launch.
        ReminderScheduler.sync(this);
        Toast.makeText(this, note, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Asks whether the new colour covers every arrangement of this course.
     *
     * <p>The list is the point of the dialog: the name is identical on every line,
     * so the times are what let the user recognise what they are about to change.
     * Dismissing it cancels the save rather than answering for them -- writing
     * either answer on a dismissal would store something they did not choose.</p>
     */
    private void showColorSyncChoice(List<Course> affected) {
        int count = CourseColorSync.needingColor(affected, selectedColor).size();
        String message = "\u672C\u5B66\u671F\u8FD8\u6709 " + count
                + " \u6761\u540C\u540D\u5B89\u6392\uFF1A\n"
                + CourseColorSync.describe(affected)
                + "\n\n\u8981\u628A\u65B0\u989C\u8272\u4E00\u8D77\u6539\u6389\u5417\uFF1F"
                + "\u53EA\u6539\u989C\u8272\uFF0C\u4E0A\u8BFE\u65F6\u95F4\u3001\u5730\u70B9"
                + "\u3001\u6559\u5E08\u548C\u5468\u6B21\u90FD\u4E0D\u53D8\u3002";
        UiKit.dialog(this, "\u540C\u6B65\u8BFE\u7A0B\u989C\u8272", message, null, null,
                UiKit.Action.of("\u4EC5\u6539\u5F53\u524D\u8BFE\u6B21", () -> {
                    colorSyncChoice = Boolean.FALSE;
                    commitSave();
                }),
                UiKit.Action.of("\u540C\u6B65\u8FD9 " + count + " \u6761", () -> {
                    colorSyncChoice = Boolean.TRUE;
                    commitSave();
                }));
    }

    private void confirmDelete() {
        UiKit.dialog(this, "\u5220\u9664\u8BFE\u7A0B",
                "\u786E\u5B9A\u5220\u9664\u201C" + course.name
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664\u8BFE\u7A0B", () -> {
                    repository.deleteCourse(course.id);
                    // A deleted class must not be announced. Rebuilding drops it from
                    // the plan and cancels the slot it occupied in the same pass.
                    ReminderScheduler.sync(this);
                    setResult(RESULT_OK);
                    finish();
                }));
    }

    // ------------------------------------------------- unsaved form state

    /** Keys for the form's uncommitted values, kept between rebuilds. */
    private static final String STATE_NAME = "draft_name";
    private static final String STATE_TEACHER = "draft_teacher";
    private static final String STATE_LOCATION = "draft_location";
    private static final String STATE_NOTE = "draft_note";
    private static final String STATE_START_WEEK = "draft_start_week";
    private static final String STATE_END_WEEK = "draft_end_week";
    private static final String STATE_DAY = "draft_day";
    private static final String STATE_START_PERIOD = "draft_start_period";
    private static final String STATE_END_PERIOD = "draft_end_period";
    private static final String STATE_PARITY = "draft_parity";
    private static final String STATE_SCHEDULE = "draft_schedule";
    private static final String STATE_COLOR = "draft_color";

    /** Every week / odd week / even week, in the order parityIndex() expects. */
    private static final CharSequence[] PARITY_LABELS = {
        "\u6BCF\u5468", "\u5355\u5468", "\u53CC\u5468"
    };

    /**
     * Captures the form as it stands, including what could not be saved yet.
     *
     * <p>Read from the views, not from {@link #course}. The course object still
     * holds what storage said, because the edits are not applied to it until the
     * user saves; a week field caught mid-keystroke holds text that has no place on
     * the model at all. The views are the only record of either.</p>
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_NAME, nameInput.getText().toString());
        outState.putString(STATE_TEACHER, teacherInput.getText().toString());
        outState.putString(STATE_LOCATION, locationInput.getText().toString());
        outState.putString(STATE_NOTE, noteInput.getText().toString());
        // Raw text. The field may hold "" or "1a" at this moment, and neither is a
        // number this could store instead.
        outState.putString(STATE_START_WEEK, startWeekInput.getText().toString());
        outState.putString(STATE_END_WEEK, endWeekInput.getText().toString());
        outState.putInt(STATE_DAY, daySpinner.getSelectedItemPosition());
        outState.putInt(STATE_START_PERIOD,
                startPeriodSpinner.getSelectedItemPosition());
        outState.putInt(STATE_END_PERIOD, endPeriodSpinner.getSelectedItemPosition());
        outState.putInt(STATE_PARITY, parityChoice == null ? CourseDraft.NO_INDEX
                : parityChoice.selected());
        outState.putInt(STATE_SCHEDULE, scheduleSpinner.getSelectedItemPosition());
        outState.putInt(STATE_COLOR, selectedColor);
    }

    /**
     * The state, or null on a first visit.
     *
     * <p>A missing key reads back as null, which is exactly "not carried" for the
     * texts: a field the user emptied was saved as {@code ""} and comes back empty,
     * while a field that was never saved keeps whatever initialisation gave it.
     * The two are different states and must not collapse into one.</p>
     */
    private static CourseDraft draftFrom(Bundle state) {
        if (state == null) {
            return null;
        }
        return new CourseDraft(
                state.getString(STATE_NAME),
                state.getString(STATE_TEACHER),
                state.getString(STATE_LOCATION),
                state.getString(STATE_START_WEEK),
                state.getString(STATE_END_WEEK),
                state.getInt(STATE_DAY, CourseDraft.NO_INDEX),
                state.getInt(STATE_START_PERIOD, CourseDraft.NO_INDEX),
                state.getInt(STATE_END_PERIOD, CourseDraft.NO_INDEX),
                state.getInt(STATE_PARITY, CourseDraft.NO_INDEX),
                state.getInt(STATE_SCHEDULE, CourseDraft.NO_INDEX),
                state.getInt(STATE_COLOR, CourseDraft.NO_COLOR),
                state.getString(STATE_NOTE));
    }

    /** The parity row's starting position: the draft's, or the course's. */
    private int parityIndexFor(CourseDraft state) {
        int restored = state == null ? CourseDraft.NO_INDEX
                : CourseDraft.usableIndex(state.parityIndex(), PARITY_LABELS.length);
        return restored != CourseDraft.NO_INDEX ? restored : parityIndex(course.parity);
    }

    /**
     * Lays the saved form back over the freshly initialised one.
     *
     * <p>Does nothing without a state bundle, which is the point of the whole
     * exercise: a first visit keeps the values it was initialised with, from a
     * stored course or from a new one, and a rebuild gets the user's typing
     * back.</p>
     *
     * <p>Positions are checked against the control they are going into rather than
     * trusted, so a state written against a bell-schedule list that has since
     * changed cannot select past the end of the current one.</p>
     */
    private void applyDraft() {
        if (draft == null) {
            return;
        }
        if (draft.name() != null) {
            nameInput.setText(draft.name());
        }
        if (draft.teacher() != null) {
            teacherInput.setText(draft.teacher());
        }
        if (draft.location() != null) {
            locationInput.setText(draft.location());
        }
        if (draft.note() != null) {
            noteInput.setText(draft.note());
        }
        if (draft.startWeek() != null) {
            startWeekInput.setText(draft.startWeek());
        }
        if (draft.endWeek() != null) {
            endWeekInput.setText(draft.endWeek());
        }
        int day = CourseDraft.usableIndex(draft.dayIndex(), daySpinner.getCount());
        if (day != CourseDraft.NO_INDEX) {
            daySpinner.setSelection(day);
        }
        int startPeriod = CourseDraft.usableIndex(draft.startPeriodIndex(),
                startPeriodSpinner.getCount());
        if (startPeriod != CourseDraft.NO_INDEX) {
            startPeriodSpinner.setSelection(startPeriod);
        }
        int endPeriod = CourseDraft.usableIndex(draft.endPeriodIndex(),
                endPeriodSpinner.getCount());
        if (endPeriod != CourseDraft.NO_INDEX) {
            endPeriodSpinner.setSelection(endPeriod);
        }
        // The week chips, the schedule preview and the schedule chip are all
        // derived from the fields above, so they are recomputed rather than
        // restored: setting the text already redrew the chips through their
        // watcher, and these two cover the spinners and the preview text.
        updateWeekChipState();
        updateSchedulePreview();
    }
    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

package com.weihe.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class EditCourseActivity extends Activity {
    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_DAY = "day";

    private static final int QUICK_WEEKS = 8;
    private static final int FIRST_HALF_END = Math.min(
            QUICK_WEEKS, ScheduleUtils.SEMESTER_WEEKS);
    private static final int SECOND_HALF_START = Math.max(
            1, ScheduleUtils.SEMESTER_WEEKS - QUICK_WEEKS + 1);

    private CourseRepository repository;
    private Course course;
    private boolean existingCourse;
    private int selectedColor;

    private EditText nameInput;
    private EditText teacherInput;
    private EditText locationInput;
    private EditText startWeekInput;
    private EditText endWeekInput;
    private Spinner daySpinner;
    private Spinner startPeriodSpinner;
    private Spinner endPeriodSpinner;
    private UiKit.Choice parityChoice;
    private LinearLayout colorRow;
    private Spinner scheduleSpinner;
    private TextView schedulePreview;
    private TextView scheduleChip;
    private String[] scheduleOptions;
    private String[] scheduleValues;

    private TextView allWeeksChip;
    private TextView firstHalfChip;
    private TextView secondHalfChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        long id = getIntent().getLongExtra(EXTRA_COURSE_ID, 0L);
        course = id == 0L ? new Course() : repository.findCourse(id);
        existingCourse = course != null && course.id != 0L;
        if (course == null) {
            course = new Course();
        }
        if (!existingCourse) {
            course.dayOfWeek = Math.max(1,
                    Math.min(7, getIntent().getIntExtra(EXTRA_DAY, 1)));
            course.startWeek = 1;
            course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        }
        selectedColor = course.color;
        setContentView(buildRoot());
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
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(7), dp(16), dp(7));
        header.setBackgroundColor(Palette.INVERSE_SURFACE);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setContentDescription("\u8FD4\u56DE");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = UiKit.text(this,
                existingCourse ? "\u7F16\u8F91\u8BFE\u7A0B" : "\u6DFB\u52A0\u8BFE\u7A0B",
                18, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        header.addView(title, titleParams);
        return header;
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

    private View createBasicSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(13), dp(14), dp(14));
        section.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        section.addView(UiKit.text(this, "\u57FA\u672C\u4FE1\u606F",
                12, Palette.ON_SURFACE, true));
        section.addView(formField("\u8BFE\u7A0B\u540D\u79F0", nameInput, 11));
        section.addView(formField("\u4E0A\u8BFE\u5730\u70B9", locationInput, 11));
        section.addView(formField("\u4EFB\u8BFE\u6559\u5E08", teacherInput, 11));
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
    }

    private boolean isRange(Integer start, Integer end, int from, int to) {
        return start != null && end != null
                && start.intValue() == from && end.intValue() == to;
    }

    private void styleQuickChip(TextView chip, boolean selected) {
        if (chip == null) {
            return;
        }
        chip.setTextColor(selected ? Color.WHITE : Palette.ON_SURFACE);
        chip.setBackground(UiKit.fill(this,
                selected ? Palette.PRIMARY : Palette.SURFACE_CONTAINER_HIGH, UiTokens.RADIUS_S));
    }

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
        parityChoice = UiKit.radioRow(this,
                new CharSequence[] {"\u6BCF\u5468", "\u5355\u5468", "\u53CC\u5468"},
                parityIndex(course.parity));
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

        scheduleSpinner = spinner(scheduleOptions, indexOfSchedule(course.scheduleName));
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
    }

    private View createColorSection() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(14), dp(13), dp(14), dp(14));
        wrapper.setBackground(UiKit.outline(this,
                Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT, 1));
        wrapper.addView(UiKit.text(this, "\u8BFE\u7A0B\u989C\u8272",
                12, Palette.ON_SURFACE, true));
        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.addView(colorRow);
        renderColorSwatches();
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.topMargin = dp(12);
        wrapper.setLayoutParams(wrapperParams);
        return wrapper;
    }

    private void renderColorSwatches() {
        if (colorRow == null) {
            return;
        }
        colorRow.removeAllViews();
        for (int color : Palette.COURSE_COLORS) {
            View swatch = new View(this);
            boolean selected = color == selectedColor;
            swatch.setBackground(UiKit.outline(this, color, 8,
                    selected ? Palette.ON_SURFACE : Color.TRANSPARENT,
                    selected ? 2 : 1));
            swatch.setOnClickListener(view -> {
                selectedColor = color;
                renderColorSwatches();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(30), dp(30));
            params.rightMargin = dp(8);
            params.topMargin = dp(11);
            colorRow.addView(swatch, params);
        }
    }

    private View createSaveBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(11));
        bar.setBackgroundColor(Palette.CARD);
        bar.setElevation(dp(7));

        TextView save = UiKit.text(this, "\u4FDD\u5B58\u8BFE\u7A0B",
                15, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(UiKit.fill(this, Palette.PRIMARY, UiTokens.RADIUS_M));
        save.setOnClickListener(view -> validateAndSave());
        bar.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        if (existingCourse) {
            TextView delete = UiKit.text(this,
                    "\u5220\u9664\u8BFE\u7A0B", UiTokens.Type.BODY_MEDIUM, Palette.ERROR, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(dp(8), dp(10), dp(8), dp(4));
            delete.setOnClickListener(view -> confirmDelete());
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

        List<CourseFormValidator.Problem> problems = CourseFormValidator.check(
                nameInput.getText().toString(), startWeek, endWeek,
                startPeriod, endPeriod);
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

    private void commitSave() {
        repository.upsertCourse(course);
        setResult(RESULT_OK);
        Toast.makeText(this, "\u8BFE\u7A0B\u5DF2\u4FDD\u5B58",
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        UiKit.dialog(this, "\u5220\u9664\u8BFE\u7A0B",
                "\u786E\u5B9A\u5220\u9664\u201C" + course.name
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664", () -> {
                    repository.deleteCourse(course.id);
                    setResult(RESULT_OK);
                    finish();
                }));
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

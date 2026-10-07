package com.weihe.schedule;

import android.app.Activity;
import android.graphics.Color;
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

import java.util.List;

/**
 * Edits one bell schedule: its name, the venue keywords that select it, and the
 * start and end time of every period.
 *
 * <p>The rules live in {@link BellScheduleEditor}, which never touches Android;
 * this screen only turns its {@code Problem} list into field errors.</p>
 */
public class EditScheduleActivity extends Activity {
    /** Index of the schedule to edit, or -1 for a new one. */
    public static final String EXTRA_INDEX = "index";

    private CourseRepository repository;
    private BellScheduleEditor editor;
    private int index;
    private String originalName;

    private EditText nameInput;
    private EditText keywordInput;
    private final EditText[] startInputs = new EditText[BellSchedule.PERIODS];
    private final EditText[] endInputs = new EditText[BellSchedule.PERIODS];
    private TextView errorBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        index = getIntent().getIntExtra(EXTRA_INDEX, -1);

        List<BellSchedule> schedules = repository.getBellSchedules();
        if (index >= 0 && index < schedules.size()) {
            BellSchedule source = schedules.get(index);
            originalName = source.name();
            editor = new BellScheduleEditor(source, otherNames(schedules, index));
        } else {
            editor = BellScheduleEditor.blank(otherNames(schedules, -1));
            editor.setName(uniqueName(schedules));
        }
        setContentView(buildRoot());
    }

    private List<String> otherNames(List<BellSchedule> schedules, int skip) {
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < schedules.size(); i++) {
            if (i != skip) {
                names.add(schedules.get(i).name());
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
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(16), dp(8));
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
                index >= 0 ? "\u7F16\u8F91\u4F5C\u606F\u8868" : "\u65B0\u5EFA\u4F5C\u606F\u8868",
                18, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        header.addView(title, titleParams);
        return header;
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
        section.addView(UiKit.text(this, "\u6BCF\u8282\u4E0A\u4E0B\u8BFE\u65F6\u95F4",
                12, Palette.ON_SURFACE, true));

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

        for (int period = 1; period <= editor.periodCount(); period++) {
            section.addView(periodRow(period));
        }

        TextView footnote = UiKit.text(this,
                "\u65F6\u95F4\u652F\u6301 8:00\u30018.00\u3001800 \u51E0\u79CD\u5199\u6CD5\uFF1B"
                        + "\u540E\u4E00\u8282\u4E0D\u80FD\u65E9\u4E8E\u524D\u4E00\u8282\u7ED3\u675F\u3002",
                11, Palette.OUTLINE, false);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        footParams.topMargin = dp(10);
        section.addView(footnote, footParams);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = dp(12);
        section.setLayoutParams(sectionParams);
        return section;
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

        EditText start = timeInput(editor.startText(period));
        start.addTextChangedListener(watcher(() ->
                editor.setStartText(period, start.getText().toString())));
        EditText end = timeInput(editor.endText(period));
        end.addTextChangedListener(watcher(() ->
                editor.setEndText(period, end.getText().toString())));
        startInputs[period - 1] = start;
        endInputs[period - 1] = end;
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
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(11));
        bar.setBackgroundColor(Palette.CARD);
        bar.setElevation(dp(7));

        TextView save = UiKit.text(this, "\u4FDD\u5B58\u4F5C\u606F\u8868",
                15, Color.WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(UiKit.fill(this, Palette.PRIMARY, UiTokens.RADIUS_M));
        save.setOnClickListener(view -> validateAndSave());
        bar.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        if (index >= 0) {
            TextView delete = UiKit.textButton(this, "\u5220\u9664\u4F5C\u606F\u8868",
                    Palette.ERROR, view -> confirmDelete());
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            deleteParams.topMargin = dp(6);
            bar.addView(delete, deleteParams);
        }
        return bar;
    }

    /** Asks before deleting the schedule this editor is showing. */
    private void confirmDelete() {
        UiKit.dialog(this, "\u5220\u9664\u4F5C\u606F\u8868",
                "\u786E\u5B9A\u5220\u9664\u201C" + editor.name()
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664", () -> {
                    List<BellSchedule> remaining = BellSchedules.copyOf(
                            repository.getBellSchedules());
                    if (index >= 0 && index < remaining.size()) {
                        remaining.remove(index);
                    }
                    repository.saveBellSchedules(remaining);
                    repository.clearScheduleReferences(editor.originalName());
                    setResult(RESULT_OK);
                    finish();
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
        List<BellSchedule> schedules = BellSchedules.copyOf(
                repository.getBellSchedules());
        BellSchedule built = editor.build();
        if (index >= 0 && index < schedules.size()) {
            schedules.set(index, built);
        } else {
            schedules.add(built);
        }
        repository.saveBellSchedules(schedules);
        List<Integer> overlaps = editor.overlappingPeriods();
        String note = overlaps.isEmpty() ? "\u4F5C\u606F\u8868\u5DF2\u4FDD\u5B58"
                : "\u5DF2\u4FDD\u5B58\uFF0C\u4F46\u7B2C " + joinPeriods(overlaps)
                        + " \u8282\u4E0E\u4E0A\u4E00\u8282\u91CD\u53E0\uFF0C\u8BF7\u786E\u8BA4";
        Toast.makeText(this, note, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
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
        for (int period = 1; period <= BellSchedule.PERIODS; period++) {
            if (startInputs[period - 1] != null) {
                startInputs[period - 1].setError(null);
            }
            if (endInputs[period - 1] != null) {
                endInputs[period - 1].setError(null);
            }
        }
    }

    /** Puts each problem where the user can act on it. */
    private void showProblems(List<BellScheduleEditor.Problem> problems) {
        StringBuilder summary = new StringBuilder();
        for (BellScheduleEditor.Problem problem : problems) {
            if (summary.length() > 0) {
                summary.append("\n");
            }
            summary.append(problem.toString());
            if (problem.period() >= 1 && problem.period() <= BellSchedule.PERIODS) {
                EditText field = startInputs[problem.period() - 1];
                if (field != null) {
                    field.setError(problem.toString());
                }
            } else if (nameInput != null) {
                nameInput.setError(problem.toString());
            }
        }
        if (errorBanner != null) {
            errorBanner.setText(summary.toString());
            errorBanner.setVisibility(View.VISIBLE);
        }
    }
    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

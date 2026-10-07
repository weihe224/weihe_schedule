package com.weihe.schedule;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything that is not the timetable itself.
 *
 * <p>This replaced a three-item {@code AlertDialog}: importing, the bell
 * schedules and the semester start are each a different kind of decision, and
 * squashing them into a menu of verbs hid what the current state actually is.
 * Here every row states its value first -- how many courses were imported, how
 * many schedules there are, which Monday the term starts on -- and only then
 * offers the action.</p>
 */
public class SettingsActivity extends Activity {
    private static final int REQUEST_IMPORT_TIMETABLE = 3001;

    private CourseRepository repository;
    private TextView semesterValue;
    private TextView semesterDetail;
    private TextView semesterStartValue;
    private TextView semesterStartDetail;
    private TextView schedulesValue;
    private TextView coursesValue;
    private TextView weekValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        setContentView(buildRoot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (semesterValue != null) {
            // Re-read storage; another screen may have changed it. See
            // CourseRepository#reload.
            repository.reload();
            refresh();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_TIMETABLE
                && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importTimetable(uri);
            }
        }
    }

    // ---------------------------------------------------------------- layout

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
        body.setPadding(dp(14), dp(14), dp(14), dp(20));

        body.addView(sectionLabel("\u8BFE\u8868\u6570\u636E"));
        body.addView(createImportCard());

        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.topMargin = dp(16);
        body.addView(sectionLabel("\u5B66\u671F"), gap);
        body.addView(createSemesterCard());

        body.addView(sectionLabel("\u5F53\u524D\u5B66\u671F\u7684\u7B2C\u4E00\u5468"),
                margins(dp(16)));
        body.addView(createSemesterStartCard());

        body.addView(sectionLabel("\u4E0A\u4E0B\u8BFE\u65F6\u95F4"),
                margins(dp(16)));
        body.addView(createScheduleCard());

        body.addView(createAboutCard(), margins(dp(20)));

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(UiTokens.SPACE_S), dp(UiTokens.SPACE_S),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_S));
        header.setBackgroundColor(Palette.INVERSE_SURFACE);

        ImageButton back = UiKit.backButton(this, view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                dp(UiTokens.TOUCH_TARGET - UiTokens.SPACE_S),
                dp(UiTokens.TOUCH_TARGET - UiTokens.SPACE_S)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.addView(UiKit.text(this, "\u8BBE\u7F6E",
                UiTokens.Type.TITLE_MEDIUM, Palette.ON_INVERSE, Typeface.BOLD));
        titleBlock.addView(UiKit.text(this,
                "\u8BFE\u8868\u6570\u636E\u3001\u5B66\u671F\u4E0E\u4F5C\u606F\u65F6\u95F4",
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_INVERSE_VARIANT,
                Typeface.NORMAL));
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blockParams.leftMargin = dp(UiTokens.SPACE_S);
        header.addView(titleBlock, blockParams);
        return header;
    }

    private TextView sectionLabel(String text) {
        return UiKit.sectionLabel(this, text);
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        return params;
    }

    /** A card with a title, a value and an optional action row. */
    private LinearLayout card() {
        return UiKit.card(this);
    }

    private void addTitle(LinearLayout card, int icon, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.ImageView image = new android.widget.ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(Palette.PRIMARY);
        row.addView(image, new LinearLayout.LayoutParams(
                dp(UiTokens.ICON_SIZE), dp(UiTokens.ICON_SIZE)));

        TextView label = UiKit.text(this, title, UiTokens.Type.TITLE_SMALL,
                Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(UiTokens.SPACE_S + 2);
        row.addView(label, labelParams);
        card.addView(row);
    }

    private TextView valueLine(String text, int topMargin) {
        TextView value = UiKit.text(this, text, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, Typeface.NORMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        value.setLayoutParams(params);
        return value;
    }

    private TextView hintLine(String text, int topMargin) {
        TextView hint = UiKit.caption(this, text);
        hint.setTextColor(Palette.OUTLINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        hint.setLayoutParams(params);
        return hint;
    }

    private View createImportCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_add, "\u6279\u91CF\u5BFC\u5165\u8BFE\u8868");
        coursesValue = valueLine("", 11);
        card.addView(coursesValue);
        card.addView(hintLine("\u8BFB\u53D6\u6559\u52A1\u7CFB\u7EDF\u5BFC\u51FA\u7684 .xls \u8BFE\u8868\uFF0C"
                + "\u5BFC\u5165\u4F1A\u66FF\u6362\u5F53\u524D\u5168\u90E8\u8BFE\u7A0B\u3002", 6));
        card.addView(actionRow(
                action("\u9009\u62E9\u6587\u4EF6\u5BFC\u5165", Palette.PRIMARY, true,
                        view -> openImportPicker())));
        return card;
    }

    /** Which term is on screen, and the way to look at another one. */
    private View createSemesterCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_semester, "\u5B66\u671F");
        semesterValue = valueLine("", 11);
        card.addView(semesterValue);
        semesterDetail = hintLine("", 6);
        card.addView(semesterDetail);
        card.addView(actionRow(
                action("\u67E5\u770B\u4E0E\u5207\u6362\u5B66\u671F", Palette.PRIMARY, true,
                        view -> startActivity(new Intent(this,
                                SemesterListActivity.class)))));
        return card;
    }

    private View createSemesterStartCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_semester, "\u5F00\u5B66\u7B2C\u4E00\u5468\u5468\u4E00");
        semesterStartValue = valueLine("", 11);
        card.addView(semesterStartValue);
        semesterStartDetail = hintLine("", 6);
        card.addView(semesterStartDetail);
        card.addView(actionRow(
                action("\u524D\u79FB\u4E00\u5468", Palette.INK, false,
                        view -> shiftSemesterStart(-1)),
                action("\u540E\u79FB\u4E00\u5468", Palette.INK, false,
                        view -> shiftSemesterStart(1)),
                action("\u8F93\u5165\u65E5\u671F", Palette.PRIMARY, true,
                        view -> editSemesterStart())));
        return card;
    }

    private View createScheduleCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_settings, "\u4F5C\u606F\u65F6\u95F4\u4E0E\u8282\u6B21");
        schedulesValue = valueLine("", 11);
        card.addView(schedulesValue);
        card.addView(hintLine("\u4E0D\u540C\u6559\u5B66\u697C\u53EF\u4EE5\u4E0A\u4E0B\u8BFE\u65F6\u95F4\u4E0D\u540C\uFF0C"
                + "\u8BFE\u7A0B\u53EF\u6309\u5730\u70B9\u81EA\u52A8\u5339\u914D\u3002", 6));
        card.addView(actionRow(
                action("\u7BA1\u7406\u4F5C\u606F\u8868", Palette.PRIMARY, true,
                        view -> startActivity(new Intent(this,
                                ScheduleListActivity.class)))));
        return card;
    }

    private View createAboutCard() {
        LinearLayout card = card();
        card.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        addTitle(card, R.drawable.ic_grid, "\u5173\u4E8E");
        weekValue = valueLine("", 11);
        card.addView(weekValue);
        card.addView(hintLine("\u6570\u636E\u53EA\u5B58\u5728\u672C\u673A\uFF08SharedPreferences\uFF09\uFF0C"
                + "\u5378\u8F7D\u5E94\u7528\u4F1A\u4E00\u5E76\u5220\u9664\u3002", 6));
        return card;
    }

    /** Buttons in a card, side by side, sharing the row equally. */
    private View actionRow(View... actions) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(UiTokens.SPACE_M);
        row.setLayoutParams(rowParams);
        for (int i = 0; i < actions.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(UiTokens.BUTTON_HEIGHT_COMPACT), 1f);
            if (i > 0) {
                params.leftMargin = dp(UiTokens.SPACE_S);
            }
            row.addView(actions[i], params);
        }
        return row;
    }

    /**
     * A card action.
     *
     * @param filled true for the one action the card is about, false for the
     *               alternatives beside it
     */
    private TextView action(String label, int color, boolean filled,
            View.OnClickListener listener) {
        if (filled) {
            return UiKit.filledButton(this, label, listener);
        }
        return UiKit.outlinedButton(this, label, listener);
    }

    // ----------------------------------------------------------------- state

    private void refresh() {
        Semester active = repository.getActiveSemester();
        List<Semester> semesters = repository.getSemesters();
        List<Course> courses = repository.getCourses();
        List<BellSchedule> schedules = repository.getBellSchedules();
        LocalDate start = repository.getSemesterStart();
        int currentWeek = ScheduleUtils.currentWeek(start, LocalDate.now());
        boolean inSession = !LocalDate.now().isBefore(active.start())
                && !LocalDate.now().isAfter(active.lastDay());

        coursesValue.setText(courses.isEmpty()
                ? "\u5F53\u524D\u6CA1\u6709\u8BFE\u7A0B"
                : "\u5F53\u524D\u5171 " + courses.size() + " \u6761\u5B89\u6392");

        semesterValue.setText("\u5F53\u524D\uFF1A" + active.label());
        semesterDetail.setText("\u5171 " + semesters.size() + " \u4E2A\u5B66\u671F \u00B7 "
                + ScheduleUtils.formatDate(active.start()) + " - "
                + ScheduleUtils.formatDate(active.lastDay())
                + (inSession ? " \u00B7 \u8FDB\u884C\u4E2D" : " \u00B7 \u5DF2\u7ED3\u675F"));

        semesterStartValue.setText(ScheduleUtils.formatFullDate(start));
        semesterStartDetail.setText("\u7B2C 1 \u5468\uFF1A" + start
                + " \u00B7 \u5171 " + ScheduleUtils.SEMESTER_WEEKS
                + " \u5468 \u00B7 \u4ECA\u5929\u662F\u7B2C " + Math.min(currentWeek,
                        ScheduleUtils.SEMESTER_WEEKS) + " \u5468");

        schedulesValue.setText(schedules.size() + " \u4E2A\u4F5C\u606F\u8868 \u00B7 "
                + "\u6BCF\u8282 " + BellSchedule.PERIODS + " \u8282");
        weekValue.setText("WeiheSchedule 1.0 \u00B7 minSdk 26");
    }

    /**
     * Moves week 1 of the semester currently on screen.
     *
     * <p>Rejected when it would move the active semester past today, because
     * "the term has not started yet" is not a useful state to edit into; a
     * future semester is created from the semester screen instead.</p>
     */
    private void shiftSemesterStart(int weeks) {
        LocalDate start = repository.getSemesterStart();
        if (weeks < 0 && ScheduleUtils.currentWeek(start, LocalDate.now()) <= 1) {
            Toast.makeText(this, "\u5DF2\u7ECF\u662F\u7B2C 1 \u5468\u4E86",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        applySemesterStart(start.plusWeeks(weeks));
    }

    private void editSemesterStart() {
        final LocalDate start = repository.getSemesterStart();
        final EditText input = UiKit.field(this, "yyyy-MM-dd", false);
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        input.setText(start.toString());
        input.setSelection(input.getText().length());
        input.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M));

        UiKit.dialog(this, "\u5F00\u5B66\u7B2C\u4E00\u5468\u5468\u4E00",
                "\u683C\u5F0F\uFF1A2026-02-23\u3002\u4E0D\u662F\u5468\u4E00\u4F1A\u81EA\u52A8"
                        + "\u5F52\u5230\u5F53\u5468\u5468\u4E00\u3002",
                input, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.validated("\u4FDD\u5B58", () -> {
                    LocalDate parsed = ScheduleUtils.parseDate(
                            input.getText().toString());
                    if (parsed == null) {
                        input.setError("\u65E5\u671F\u683C\u5F0F\u5E94\u4E3A 2026-02-23");
                        input.requestFocus();
                        return false;
                    }
                    applySemesterStart(parsed);
                    return true;
                }));
    }

    private void applySemesterStart(LocalDate date) {
        LocalDate monday = ScheduleUtils.mondayOf(date);
        repository.setSemesterStart(monday);
        refresh();
        Toast.makeText(this, "\u5F00\u5B66\u65E5\u5DF2\u8BBE\u4E3A "
                + ScheduleUtils.formatFullDate(monday), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- import

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/vnd.ms-excel", "application/xls",
                "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_IMPORT_TIMETABLE);
    }

    private void importTimetable(Uri uri) {
        Dialog progress = UiKit.dialog(this, "\u6B63\u5728\u5BFC\u5165",
                "\u6B63\u5728\u8BC6\u522B\u8BFE\u8868\u6587\u4EF6\u2026",
                null, null);

        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new TimetableImportException(
                            "\u65E0\u6CD5\u6253\u5F00\u6240\u9009\u6587\u4EF6");
                }
                List<Course> parsed = ExcelTimetableReader.read(input);
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (!isFinishing()) {
                        confirmImport(parsed);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (!isFinishing()) {
                        showImportError(error);
                    }
                });
            }
        }).start();
    }

    private void confirmImport(List<Course> courses) {
        if (courses.isEmpty()) {
            Toast.makeText(this, "\u6CA1\u6709\u8BC6\u522B\u5230\u8BFE\u7A0B",
                    Toast.LENGTH_LONG).show();
            return;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Course course : courses) {
            names.add(course.name);
        }
        int lastSourceWeek = 1;
        for (Course course : courses) {
            lastSourceWeek = Math.max(lastSourceWeek, course.endWeek);
        }
        int latestStart = Math.max(1,
                ScheduleUtils.SEMESTER_WEEKS - lastSourceWeek + 1);
        String[] starts = new String[latestStart];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = "\u7B2C " + (i + 1) + " \u5468";
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(UiTokens.SPACE_XL - 2), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_XL - 2), 0);
        String summary = "\u8BC6\u522B\u5230 " + names.size() + " \u95E8\u8BFE\u7A0B\uFF0C"
                + "\u5171 " + courses.size() + " \u6761\u5B89\u6392";
        content.addView(UiKit.title(this, summary));
        content.addView(hintLine(
                "\u5BFC\u5165\u5C06\u8986\u76D6\u5F53\u524D\u8BFE\u8868", UiTokens.SPACE_XS + 1));

        TextView label = UiKit.text(this,
                "\u8BFE\u8868\u7B2C 1 \u5468\u5BF9\u5E94\u672C\u5B66\u671F\uFF1A",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(UiTokens.SPACE_L);
        content.addView(label, labelParams);

        android.widget.Spinner startWeekSpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, starts);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        startWeekSpinner.setAdapter(adapter);
        startWeekSpinner.setSelection(0);
        startWeekSpinner.setContentDescription("\u9009\u62E9\u5BFC\u5165\u8D77\u59CB\u5468");
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        spinnerParams.topMargin = dp(4);
        content.addView(startWeekSpinner, spinnerParams);

        content.addView(hintLine("\u6587\u4EF6\u4E2D\u6700\u540E\u4E00\u5468\u4E3A\u7B2C"
                + lastSourceWeek + "\u5468\uFF0C\u5DF2\u8FC7\u6EE4\u4F1A\u8D85\u51FA"
                + ScheduleUtils.SEMESTER_WEEKS + "\u5468\u7684\u9009\u9879", 5));

        UiKit.dialog(this, "\u5BFC\u5165\u8BFE\u8868", null, content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u8986\u76D6\u5BFC\u5165", () -> {
                    int sourceFirstWeek = startWeekSpinner
                            .getSelectedItemPosition() + 1;
                    try {
                        List<Course> shifted = TimetableImportParser.shiftWeeks(
                                courses, sourceFirstWeek);
                        repository.replaceCourses(shifted);
                        refresh();
                        Toast.makeText(this, "\u8BFE\u8868\u5DF2\u5BFC\u5165",
                                Toast.LENGTH_SHORT).show();
                    } catch (TimetableImportException error) {
                        showImportError(error);
                    }
                }));
    }

    private void showImportError(Exception error) {
        String detail = error.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = "\u8BF7\u786E\u8BA4\u6587\u4EF6\u662F\u6559\u52A1\u7CFB\u7EDF\u5BFC\u51FA\u7684 .xls \u8BFE\u8868";
        } else if (detail.contains(".xlsx")
                || detail.toLowerCase(java.util.Locale.ROOT).contains("zip")) {
            detail += "\n\u63D0\u793A\uFF1A\u8BF7\u53E6\u5B58\u4E3A .xls \u683C\u5F0F\u540E\u518D\u5BFC\u5165";
        }
        UiKit.message(this, "\u5BFC\u5165\u5931\u8D25", detail);
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

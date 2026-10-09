package com.weihe.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.List;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * The list of semesters, and the way to look at a past one.
 *
 * <p>Each semester owns its own courses and bell schedules, so switching here
 * changes what every other screen shows rather than filtering the current term.
 * The active semester is marked, and each row states its date range and how many
 * arrangements are filed under it, which is what makes "the term before this
 * one" recognisable at a glance.</p>
 */
public class SemesterListActivity extends Activity {
    /** Result code meaning "the active semester changed". */
    public static final int RESULT_SWITCHED = RESULT_FIRST_USER;

    private CourseRepository repository;
    private LinearLayout list;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) {
            // Re-read storage; another screen may have changed it. See
            // CourseRepository#reload.
            repository.reload();
            render();
        }
    }

    private View buildRoot() {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(Palette.SURFACE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        frame.addView(page, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(createHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.FAB_SIZE + UiTokens.SPACE_XXL));

        summary = UiKit.caption(this, "");
        body.addView(summary);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(UiTokens.SPACE_S + 2);
        body.addView(list, listParams);

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ImageButton add = UiKit.fab(this, R.drawable.ic_add,
                "\u65B0\u5EFA\u5B66\u671F", view -> createNextSemester());
        android.widget.FrameLayout.LayoutParams fabParams =
                new android.widget.FrameLayout.LayoutParams(
                        dp(UiTokens.FAB_SIZE), dp(UiTokens.FAB_SIZE),
                        Gravity.END | Gravity.BOTTOM);
        fabParams.setMargins(0, 0, dp(UiTokens.SPACE_L + 2),
                dp(UiTokens.SPACE_XL - 2));
        frame.addView(add, fabParams);
        return frame;
    }

    private View createHeader() {
        return UiKit.detailAppBar(this, "\u5B66\u671F",
                "\u6BCF\u4E2A\u5B66\u671F\u6709\u81EA\u5DF1\u7684\u8BFE\u8868\u4E0E\u4F5C\u606F\u65F6\u95F4",
                view -> finish());
    }

    private void render() {
        List<Semester> semesters = repository.getSemesters();
        Semester active = repository.getActiveSemester();
        list.removeAllViews();
        summary.setText("\u5171 " + semesters.size() + " \u4E2A\u5B66\u671F \u00B7 "
                + "\u5F53\u524D\uFF1A" + active.label());

        // Newest first: the term being looked at is almost always the recent one.
        for (int i = semesters.size() - 1; i >= 0; i--) {
            Semester semester = semesters.get(i);
            list.addView(semesterCard(semester, semester.id() == active.id(),
                    repository.countCoursesOf(semester)));
        }
    }

    private View semesterCard(final Semester semester, final boolean isActive,
            int courseCount) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M + 2),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M + 2));
        // The active term is lifted onto its own tonal surface, and the accent
        // border says which one the rest of the app is showing.
        UiKit.styleAsCard(this, card, UiTokens.RADIUS_L,
                isActive ? UiTokens.ELEVATION_MEDIUM : UiTokens.ELEVATION_LOW);
        if (isActive) {
            card.setBackground(UiKit.outline(this, Palette.PRIMARY_CONTAINER,
                    UiTokens.RADIUS_L, Palette.PRIMARY, UiTokens.STROKE_EMPHASIS));
            UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_MEDIUM);
        }

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(UiKit.text(this, semester.label(),
                        UiTokens.Type.TITLE_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD),
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (isActive) {
            titleRow.addView(UiKit.badge(this, "\u5F53\u524D", Palette.PRIMARY,
                    Palette.ON_PRIMARY));
        }
        card.addView(titleRow);

        TextView range = UiKit.text(this,
                ScheduleUtils.formatDate(semester.start()) + " - "
                        + ScheduleUtils.formatDate(semester.lastDay())
                        + "  \u00B7  \u5171 " + ScheduleUtils.SEMESTER_WEEKS + " \u5468",
                UiTokens.Type.LABEL_LARGE, Palette.ON_SURFACE_VARIANT,
                Typeface.NORMAL);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = dp(UiTokens.SPACE_XS + 2);
        card.addView(range, rangeParams);

        TextView courses = UiKit.text(this, courseCount == 0
                ? "\u8FD8\u6CA1\u6709\u8BFE\u7A0B"
                : courseCount + " \u6761\u5B89\u6392",
                UiTokens.Type.LABEL_LARGE,
                courseCount == 0 ? Palette.OUTLINE : Palette.ON_SURFACE,
                courseCount == 0 ? Typeface.NORMAL : Typeface.BOLD);
        LinearLayout.LayoutParams coursesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        coursesParams.topMargin = dp(UiTokens.SPACE_XS);
        card.addView(courses, coursesParams);

        card.setOnClickListener(view -> switchTo(semester, isActive));
        card.setOnLongClickListener(view -> {
            confirmDelete(semester);
            return true;
        });
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(UiTokens.SPACE_M);
        card.setLayoutParams(cardParams);
        return card;
    }

    private void switchTo(Semester semester, boolean isActive) {
        if (isActive) {
            finish();
            return;
        }
        repository.setActiveSemester(semester);
        Toast.makeText(this, "\u5DF2\u5207\u6362\u5230 " + semester.label(),
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_SWITCHED);
        finish();
    }

    /**
     * Asks for the new semester's details and creates exactly what was typed.
     *
     * <p>The date field is pre-filled as a starting point but is not treated as
     * an answer, and the name field starts empty: a semester is only created
     * once the user has said when it starts. Carrying the current timetable over
     * is offered, not assumed.</p>
     */
    private void createNextSemester() {
        final List<Semester> semesters = repository.getSemesters();
        final Semester newest = semesters.get(semesters.size() - 1);
        final Semester active = repository.getActiveSemester();

        final EditText dateInput = dateField(newest.nextStart().toString());
        final EditText nameInput = textField(
                "\u4F8B\uFF1A\u5927\u4E09\u4E0A\uFF08\u53EF\u7559\u7A7A\uFF09");
        final int carried = repository.countCoursesOf(active);
        final UiKit.Check carryOver = UiKit.checkbox(this,
                "\u628A\u201C" + active.label() + "\u201D\u7684 " + carried
                        + " \u6761\u8BFE\u7A0B\u590D\u5236\u8FC7\u6765",
                false);

        UiKit.dialog(this, "\u65B0\u5EFA\u5B66\u671F", null,
                createForm(dateInput, nameInput, carryOver, active, carried), null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.validated("\u521B\u5EFA",
                        () -> submitNewSemester(dateInput, nameInput, carryOver, active)));
    }
    private View createForm(final EditText dateInput, final EditText nameInput,
            final UiKit.Check carryOver, final Semester source, final int carried) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(UiTokens.SPACE_XL - 2), dp(UiTokens.SPACE_S),
                dp(UiTokens.SPACE_XL - 2), 0);

        content.addView(label("\u7B2C 1 \u5468\u5468\u4E00"));
        content.addView(dateInput);
        content.addView(hint("\u683C\u5F0F 2027-02-22\uFF1B\u4E0D\u662F\u5468\u4E00\u4F1A\u81EA\u52A8"
                + "\u5F52\u5230\u5F53\u5468\u5468\u4E00\u3002"));

        content.addView(label("\u5B66\u671F\u540D\u79F0"));
        content.addView(nameInput);
        content.addView(hint("\u7559\u7A7A\u5219\u6839\u636E\u5F00\u5B66\u65E5\u671F\u81EA\u52A8\u547D\u540D"
                + "\uFF08\u5982 2026 \u79CB\u5B63\u5B66\u671F\uFF09\u3002"));

        carryOver.view().setEnabled(carried > 0);
        LinearLayout.LayoutParams carryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        carryParams.topMargin = dp(UiTokens.SPACE_L);
        content.addView(carryOver.view(), carryParams);
        content.addView(hint("\u4E0D\u52FE\u9009\u5219\u65B0\u5B66\u671F\u4E3A\u7A7A\uFF0C"
                + "\u4E24\u4E2A\u5B66\u671F\u4E92\u4E0D\u5F71\u54CD\u3002"));

        return content;
    }

    /**
     * Creates the semester the form describes.
     *
     * @return true when it was created, false when the form needs fixing
     */
    private boolean submitNewSemester(EditText dateInput, EditText nameInput,
            UiKit.Check carryOver, Semester source) {
        LocalDate parsed = ScheduleUtils.parseDate(dateInput.getText().toString());
        if (parsed == null) {
            dateInput.setError("\u8BF7\u6309 2027-02-22 \u7684\u683C\u5F0F\u586B\u5199\u65E5\u671F");
            dateInput.requestFocus();
            return false;
        }
        LocalDate monday = ScheduleUtils.mondayOf(parsed);
        for (Semester existing : repository.getSemesters()) {
            if (existing.start().equals(monday)) {
                dateInput.setError("\u8BE5\u5468\u5DF2\u7ECF\u662F\u201C" + existing.label()
                        + "\u201D\uFF08" + monday + "\uFF09");
                dateInput.requestFocus();
                return false;
            }
        }

        Semester created = repository.addSemester(monday,
                nameInput.getText().toString());
        if (carryOver.isChecked()) {
            repository.copyCourses(source, created);
        }
        repository.setActiveSemester(created);
        Toast.makeText(this, "\u5DF2\u65B0\u5EFA " + created.label()
                + "\uFF08\u7B2C 1 \u5468 " + monday + "\uFF09\u5E76\u5207\u6362\u8FC7\u53BB",
                Toast.LENGTH_LONG).show();
        setResult(RESULT_SWITCHED);
        finish();
        return true;
    }

    private TextView label(String text) {
        TextView label = UiKit.text(this, text, UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_L - 2);
        label.setLayoutParams(params);
        return label;
    }

    private TextView hint(String text) {
        TextView hint = UiKit.caption(this, text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_XS + 1);
        hint.setLayoutParams(params);
        return hint;
    }

    private EditText dateField(String value) {
        EditText input = textField("yyyy-MM-dd");
        input.setInputType(InputType.TYPE_CLASS_DATETIME);
        input.setText(value);
        input.setSelection(input.getText().length());
        input.setMinHeight(dp(UiTokens.FIELD_HEIGHT));
        return input;
    }

    private EditText textField(String hintText) {
        return UiKit.field(this, hintText, false);
    }

    private void confirmDelete(final Semester semester) {
        List<Semester> semesters = repository.getSemesters();
        if (semesters.size() <= 1) {
            Toast.makeText(this, "\u81F3\u5C11\u4FDD\u7559\u4E00\u4E2A\u5B66\u671F",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int courses = repository.countCoursesOf(semester);
        String message = "\u786E\u5B9A\u5220\u9664\u201C" + semester.label() + "\u201D\u5417\uFF1F";
        if (courses > 0) {
            message += "\n\u8BE5\u5B66\u671F\u7684 " + courses
                    + " \u6761\u8BFE\u7A0B\u4F1A\u4E00\u5E76\u5220\u9664\uFF0C\u65E0\u6CD5\u6062\u590D\u3002";
        } else {
            message += "\n\u8BE5\u5B66\u671F\u8FD8\u6CA1\u6709\u8BFE\u7A0B\u3002";
        }
        UiKit.dialog(this, "\u5220\u9664\u5B66\u671F", message, null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664", () -> {
                    boolean removed = repository.deleteSemester(semester);
                    if (removed) {
                        Toast.makeText(this, "\u5DF2\u5220\u9664",
                                Toast.LENGTH_SHORT).show();
                        render();
                    }
                }));
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

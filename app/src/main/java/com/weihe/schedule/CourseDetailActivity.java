package com.weihe.schedule;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.CourseDetail;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * One course, in full, before anything is changed.
 *
 * <p>The timetable cards answer "what is next"; this answers "what exactly is
 * this course". Tapping a card used to open the editor, which means the first
 * thing anyone saw about a course was a form with the cursor in it -- easy to
 * change something by accident, and no way to simply read the details.</p>
 *
 * <h2>Held by id, not by object</h2>
 *
 * <p>The screen is given a course id and looks the course up every time it
 * resumes. A {@link Course} passed through an Intent would be a snapshot: after
 * an edit it would describe the course as it used to be, and after a delete it
 * would keep describing something that no longer exists. Looking it up again
 * costs one list scan and makes all of that impossible. When the lookup finds
 * nothing the screen says so instead of rendering the stale copy.</p>
 */
public class CourseDetailActivity extends Activity {

    /** The course to show. The same extra the editor takes. */
    public static final String EXTRA_COURSE_ID = "course_id";

    private CourseRepository repository;
    private LinearLayout body;
    private TextView subtitle;
    private long courseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        courseId = getIntent().getLongExtra(EXTRA_COURSE_ID, 0L);
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The editor may have saved, deleted or renamed the course behind this
        // screen. Re-reading storage is what makes coming back from it show the
        // new state rather than the one this screen was built with.
        repository.reload();
        render();
    }

    // ------------------------------------------------------------------ layout

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.SURFACE);
        LinearLayout bar = UiKit.detailAppBar(this, "\u8BFE\u7A0B\u8BE6\u60C5", "",
                view -> finish());
        subtitle = UiKit.appBarSubtitleOf(bar);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    /** Draws whatever the course currently is, including not being there. */
    private void render() {
        Course course = courseId == 0L ? null : repository.findCourse(courseId);
        Semester semester = repository.getActiveSemester();
        List<BellSchedule> schedules = repository.getBellSchedules();
        CourseDetail detail = CourseDetail.of(course, semester, schedules,
                repository.getDefaultScheduleId());

        body.removeAllViews();
        if (!detail.isPresent()) {
            subtitle.setText("\u8BFE\u7A0B\u5DF2\u4E0D\u5B58\u5728");
            showMissing();
            return;
        }
        // The semester, not the day and periods: the hero card states those a few
        // pixels below, and the same line twice on one screen reads as a mistake.
        // The term, with its dates. The semester used to be a fact in the schedule
        // card as well, which put the same name on the screen twice under two
        // headings; it belongs here, once.
        subtitle.setText(detail.semesterRange().isEmpty()
                ? detail.semesterLabel()
                : detail.semesterLabel() + "\uFF08" + detail.semesterRange() + "\uFF09");
        body.addView(heroCard(detail));
        body.addView(spaced(factsCard(detail)));
        body.addView(spaced(whereCard(detail)));
        body.addView(spaced(actionsCard(detail)));
    }

    /**
     * The course that was asked for is gone.
     *
     * <p>Deleted from the editor, or from the timetable underneath this screen.
     * Saying so, and offering the way back, is the whole of what can be done --
     * rendering an empty detail page would look like a course with no fields.</p>
     */
    private void showMissing() {
        LinearLayout card = card();
        card.addView(UiKit.emptyState(this, R.drawable.ic_school,
                "\u8FD9\u95E8\u8BFE\u7A0B\u5DF2\u4E0D\u5B58\u5728",
                "\u5B83\u53EF\u80FD\u5DF2\u88AB\u5220\u9664\uFF0C\u6216\u8005\u5C5E\u4E8E\u53E6\u4E00\u4E2A\u5B66\u671F\u3002"));
        body.addView(card);
        LinearLayout back = card();
        back.addView(UiKit.filledButton(this, "\u8FD4\u56DE\u8BFE\u8868",
                view -> finish()));
        body.addView(spaced(back));
    }

    /**
     * The name and the two facts that matter most: when, and where.
     *
     * <p>Carries the course colour as a rail down the side, which is the same cue
     * the timetable uses, so the colour a card was recognised by is still there on
     * the page it opens.</p>
     */
    private View heroCard(CourseDetail detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View rail = new View(this);
        rail.setBackground(UiKit.fill(this, detail.color(), UiTokens.RADIUS_FULL));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(
                dp(5), ViewGroup.LayoutParams.MATCH_PARENT);
        railParams.rightMargin = dp(UiTokens.SPACE_M);
        row.addView(rail, railParams);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // No maxLines and no ellipsis anywhere in this card: a long course name is
        // the one thing on the page that must never be cut short, and the card
        // grows to fit it.
        column.addView(UiKit.text(this, detail.name(), UiTokens.Type.TITLE_LARGE,
                Palette.ON_SURFACE, Typeface.BOLD));
        column.addView(UiKit.text(this, detail.headline(), UiTokens.Type.BODY_LARGE,
                Palette.ON_SURFACE_VARIANT, false), UiKit.stack(this, UiTokens.SPACE_S));

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.HORIZONTAL);
        time.setGravity(Gravity.CENTER_VERTICAL);
        time.addView(UiKit.pill(this, detail.timeText(), UiTokens.Type.LABEL_LARGE,
                detail.timeKnown() ? UiKit.courseTint(detail.color())
                        : Palette.SURFACE_CONTAINER_HIGH,
                detail.timeKnown() ? UiKit.courseInk(detail.color())
                        : Palette.ON_SURFACE_VARIANT,
                true, UiTokens.RADIUS_S));
        column.addView(time, UiKit.stack(this, UiTokens.SPACE_M));

        if (detail.timeKnown() && !detail.scheduleName().isEmpty()) {
            column.addView(UiKit.text(this,
                    "\u6309\u300C" + detail.scheduleName() + "\u300D\u4F5C\u606F",
                    UiTokens.Type.LABEL_MEDIUM, Palette.OUTLINE, false),
                    UiKit.stack(this, UiTokens.SPACE_S));
        } else if (!detail.timeKnown() && !detail.timeNote().isEmpty()) {
            // The reason is named rather than left as a bare "unavailable": it is
            // the difference between a course whose schedule was deleted and one
            // whose period the schedule does not reach.
            column.addView(UiKit.text(this,
                    detail.timeText() + "\uFF08" + detail.timeNote() + "\uFF09",
                    UiTokens.Type.LABEL_MEDIUM, Palette.ERROR, false),
                    UiKit.stack(this, UiTokens.SPACE_S));
        }

        LinearLayout card = card();
        card.addView(row);
        return card;
    }

    /** When the course runs, field by field. */
    /**
     * When the course runs, field by field.
     *
     * <p>The term is not repeated here: its name belongs on the app bar, where it
     * already is, and a fact row saying the same thing under a second heading is
     * the kind of line that makes a page look longer than it is.</p>
     */
    private View factsCard(CourseDetail detail) {
        LinearLayout card = card();
        card.addView(section("\u4E0A\u8BFE\u5B89\u6392"));
        card.addView(fact("\u661F\u671F", detail.dayName()));
        card.addView(fact("\u8282\u6B21", detail.periodText()));
        card.addView(fact("\u5468\u6B21", detail.weekRange()));
        card.addView(fact("\u4E0A\u8BFE\u89C4\u5219", detail.parity()));
        card.addView(fact("\u4E0A\u8BFE\u65F6\u95F4", detail.timeText()));
        if (detail.timeKnown() && !detail.scheduleName().isEmpty()) {
            card.addView(fact("\u4F5C\u606F\u8868", detail.scheduleName()));
        }
        return card;
    }

    /** Who and where, with the empty case said out loud. */
    private View whereCard(CourseDetail detail) {
        LinearLayout card = card();
        card.addView(section("\u5730\u70B9\u4E0E\u6559\u5E08"));
        card.addView(fact("\u4E0A\u8BFE\u5730\u70B9", detail.location()));
        card.addView(fact("\u4EFB\u8BFE\u6559\u5E08", detail.teacher()));
        return card;
    }

    /** The one thing this screen exists to lead to. */
    /**
     * What can be done with this course: change it, make another like it, or drop
     * it.
     *
     * <p>Editing is the one this screen exists to lead to, so it is the only filled
     * button. The other two share a quieter row: copying is an ordinary thing to
     * want but does not carry the same weight, and deleting should not be the
     * second-easiest thing to hit on the page.</p>
     */
    private View actionsCard(CourseDetail detail) {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(UiTokens.BUTTON_HEIGHT));
        TextView edit = UiKit.filledButton(this, "\u7F16\u8F91\u8BFE\u7A0B",
                view -> openEditor());
        card.addView(edit, params);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(UiTokens.BUTTON_HEIGHT_COMPACT));
        rowParams.topMargin = dp(UiTokens.SPACE_S);
        row.setLayoutParams(rowParams);

        row.addView(UiKit.outlinedButton(this, "\u590D\u5236\u8BFE\u7A0B",
                view -> copyCourse()), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        removeParams.leftMargin = dp(UiTokens.SPACE_S);
        row.addView(UiKit.textButton(this, "\u5220\u9664\u8BFE\u7A0B", Palette.ERROR,
                view -> confirmDelete()), removeParams);
        card.addView(row);
        return card;
    }

    /**
     * Opens the editor on a new record carrying this course's fields.
     *
     * <p>The id travels with the intent but the copy drops it, so saving writes a
     * course that did not exist and leaves this one alone. Nothing else is changed
     * on the way: a copy the user did not edit is the course they copied.</p>
     */
    private void copyCourse() {
        Intent intent = new Intent(this, EditCourseActivity.class);
        intent.putExtra(EditCourseActivity.EXTRA_COURSE_ID, courseId);
        intent.putExtra(EditCourseActivity.EXTRA_COPY, true);
        startActivity(intent);
    }

    /**
     * Asks before deleting the course on screen.
     *
     * <p>Re-read rather than remembered: the entry can go stale between being drawn
     * and being pressed, and a dialog naming a course that is already gone would be
     * asking about nothing.</p>
     */
    private void confirmDelete() {
        Course course = repository.findCourse(courseId);
        if (course == null) {
            render();
            return;
        }
        UiKit.dialog(this, "\u5220\u9664\u8BFE\u7A0B",
                "\u786E\u5B9A\u5220\u9664\u201C" + course.name
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664\u8BFE\u7A0B", () -> {
                    repository.deleteCourse(courseId);
                    Toast.makeText(this, "\u8BFE\u7A0B\u5DF2\u5220\u9664",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    /** Opens the existing editor on this course; nothing about it changes here. */
    private void openEditor() {
        Intent intent = new Intent(this, EditCourseActivity.class);
        intent.putExtra(EditCourseActivity.EXTRA_COURSE_ID, courseId);
        startActivity(intent);
    }

    // ---------------------------------------------------------------- building

    private LinearLayout card() {
        LinearLayout card = UiKit.card(this);
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        return card;
    }

    private View spaced(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private TextView section(String text) {
        return UiKit.text(this, text, UiTokens.Type.TITLE_SMALL, Palette.ON_SURFACE, true);
    }

    /**
     * One label and one value.
     *
     * <p>The value wraps onto as many lines as it needs. A long venue or a long
     * teacher's name is information, and shrinking the type until it fits would
     * trade a layout problem for a reading problem.</p>
     */
    private View fact(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView name = UiKit.text(this, label, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE_VARIANT, false);
        row.addView(name, new LinearLayout.LayoutParams(
                dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView amount = UiKit.text(this, value, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, false);
        row.addView(amount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setLayoutParams(UiKit.stack(this, UiTokens.SPACE_M));
        return row;
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

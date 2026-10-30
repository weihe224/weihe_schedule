package com.weihe.schedule;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.CourseDetail;
import com.weihe.schedule.rule.CourseQuery;
import com.weihe.schedule.rule.CourseSearchSummary;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * Finds a course by name, teacher or venue.
 *
 * <p>A screen of its own rather than a bar wedged into the timetable. The
 * timetable already carries a week picker, a day strip, a period column and a
 * list of cards; a search field with its own states, results and keyboard would
 * be a second screen's worth of behaviour living inside the first. The entry
 * costs the home screen one app-bar icon and nothing else.</p>
 *
 * <h2>The search itself is not here</h2>
 *
 * <p>{@link CourseQuery#search} decides what matches, in what order, and what an
 * empty keyword means. This class types a keyword, hands it over, and draws what
 * comes back. The results are <em>not</em> re-sorted: the rule already orders
 * them by day, period and id, and sorting again here would be a second opinion
 * about a question that already has an answer.</p>
 *
 * <h2>Current term, read fresh</h2>
 *
 * <p>{@link CourseRepository#getCourses()} is the active semester's timetable, so
 * the search can only ever see the term the app is on. No term id is passed
 * around: one would let this screen and the repository disagree about which term
 * is current. Every resume reloads the repository and re-runs the search, so a
 * course edited, deleted or imported behind this screen is gone from the list
 * when it comes back.</p>
 */
public class CourseSearchActivity extends Activity {

    private CourseRepository repository;
    private EditText input;
    private View clearAction;
    private TextView subtitle;
    private LinearLayout results;

    /** What has been typed, kept so a resume does not lose the search. */
    private String keyword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
        search();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Another screen may have edited, deleted or re-imported courses, or
        // switched semester, while this one was stopped. Re-reading storage before
        // re-running the search is what keeps a deleted course out of the list and
        // a renamed one from showing its old name.
        repository.reload();
        search();
    }

    // ------------------------------------------------------------------ layout

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.SURFACE);
        LinearLayout bar = UiKit.detailAppBar(this, "\u641C\u7D22\u8BFE\u7A0B", "",
                view -> finish());
        subtitle = UiKit.appBarSubtitleOf(bar);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(20));

        body.addView(searchCard());
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results);

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    /** The input, and the one action that goes with it. */
    private View searchCard() {
        LinearLayout card = UiKit.card(this);
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        input = UiKit.field(this, "\u641C\u7D22\u8BFE\u7A0B\u3001\u6559\u5E08\u6216"
                + "\u6559\u5BA4", false);
        input.setText(keyword);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count,
                    int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before,
                    int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                keyword = text.toString();
                search();
            }
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            // The results already follow every keystroke, so the key's whole job
            // is to put the keyboard away.
            hideKeyboard();
            input.clearFocus();
            return true;
        });
        row.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Focus starts here, not on the app bar. The bar's back button is the
        // window's first focusable view, so without this it holds focus: typing
        // goes nowhere until the field is tapped, and worse, Enter activates the
        // button and backs out of the screen instead of searching. Opening a
        // search screen with the caret in the field and the keyboard up is also
        // simply what the screen is for.
        input.requestFocus();
        input.post(() -> showKeyboard());

        clearAction = UiKit.textButton(this, "\u6E05\u7A7A", Palette.PRIMARY,
                view -> clearKeyword());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(UiTokens.BUTTON_HEIGHT_COMPACT));
        clearParams.leftMargin = dp(UiTokens.SPACE_S);
        row.addView(clearAction, clearParams);

        card.addView(row);
        return card;
    }

    // ----------------------------------------------------------------- search

    /** Runs the search for whatever is typed and draws the outcome. */
    private void search() {
        if (results == null) {
            return;
        }
        List<Course> matches = CourseQuery.search(courses(), keyword);
        CourseSearchSummary summary = CourseSearchSummary.of(keyword, matches);

        if (clearAction != null) {
            // Offered only when there is something to clear, so the row does not
            // carry a dead control in the state the screen opens in.
            clearAction.setVisibility(
                    keyword.trim().isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (subtitle != null) {
            // Only the count goes in the bar. In the two guidance states the card
            // below already carries the same sentence as its title, and the same
            // line twice on one screen reads as a mistake -- which is exactly the
            // fault this file was written to avoid on the detail page.
            subtitle.setText(summary.showsResults() ? summary.headline() : "");
        }

        results.removeAllViews();
        if (summary.showsGuidance()) {
            results.addView(guidance(summary), UiKit.stack(this, UiTokens.SPACE_L));
            return;
        }
        for (Course course : matches) {
            results.addView(resultCard(course), UiKit.stack(this, 10));
        }
    }

    /** The active semester's courses, read fresh. Never null. */
    private List<Course> courses() {
        List<Course> courses = repository.getCourses();
        return courses == null ? Collections.<Course>emptyList() : courses;
    }

    /**
     * What the screen says before anything is typed, and when nothing matches.
     *
     * <p>Two different situations that both arrive as an empty list; the title and
     * the hint are what tell them apart.</p>
     */
    private View guidance(CourseSearchSummary summary) {
        return UiKit.emptyState(this, R.drawable.ic_search,
                summary.headline(), summary.hint());
    }

    /**
     * One course, in the order the fields are useful when scanning a list.
     *
     * <p>Everything shown comes from {@link CourseDetail}, which is what the detail
     * page renders as well: the same wording for an empty teacher or venue, the
     * same period and week phrasing, the same refusal to show a time that cannot
     * be established. A second set of formatting rules here would eventually
     * disagree with the page it leads to.</p>
     *
     * <p>Unlike the detail page, every line here is single and ellipsised. A list
     * has to stay scannable, and the full text is one tap away -- which is exactly
     * the trade the detail page declines to make.</p>
     */
    private View resultCard(Course course) {
        CourseDetail detail = detailOf(course);

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

        column.addView(clipped(UiKit.text(this, detail.name(),
                UiTokens.Type.TITLE_SMALL, Palette.ON_SURFACE, Typeface.BOLD), 2));
        column.addView(clipped(UiKit.text(this, whenOf(detail),
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE_VARIANT, false), 1),
                UiKit.stack(this, 3));
        column.addView(clipped(UiKit.text(this, detail.weekRule(),
                UiTokens.Type.LABEL_LARGE, Palette.ON_SURFACE_VARIANT, false), 1),
                UiKit.stack(this, 3));
        column.addView(clipped(UiKit.text(this, whereOf(detail),
                UiTokens.Type.LABEL_LARGE, Palette.OUTLINE, false), 1),
                UiKit.stack(this, 3));

        LinearLayout card = UiKit.card(this);
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        card.addView(row);
        UiKit.ripple(card, UiTokens.RADIUS_L, false);
        // The id travels, never the name and never a position in this list: two
        // courses really can share a name, and the list is rebuilt on every
        // keystroke, so an index would point at whatever happened to be there.
        UiKit.pressable(card, view -> openDetails(course));
        return card;
    }

    private CourseDetail detailOf(Course course) {
        Semester semester = repository.getActiveSemester();
        List<BellSchedule> schedules = repository.getBellSchedules();
        return CourseDetail.of(course, semester, schedules,
                repository.getDefaultScheduleId());
    }

    /** {@code \u5468\u4E00 \u00B7 \u7B2C 1-2 \u8282 \u00B7 08:15 - 09:35}, the time left out when unknown. */
    private static String whenOf(CourseDetail detail) {
        String head = detail.headline();
        return detail.timeKnown() ? head + " \u00B7 " + detail.timeText() : head;
    }

    /** {@code \u6559\u5B66\u697C A203 \u00B7 \u5F20\u8001\u5E08}, each empty half saying so rather than gaping. */
    private static String whereOf(CourseDetail detail) {
        return detail.location() + " \u00B7 " + detail.teacher();
    }

    /** One line at most, ellipsised, so a long field cannot push the rest away. */
    private static TextView clipped(TextView view, int maxLines) {
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private void clearKeyword() {
        // Emptying the field re-runs the search through the text watcher, which is
        // also what puts the screen back into its guidance state.
        input.setText("");
        // The button that was just tapped is clickable and therefore focusable, so
        // it now holds focus: without this, the next keystroke goes nowhere and the
        // search appears broken until the field is tapped again.
        input.requestFocus();
    }

    private void openDetails(Course course) {
        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, course.id);
        startActivity(intent);
    }

    private void showKeyboard() {
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && input != null) {
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && input != null) {
            keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

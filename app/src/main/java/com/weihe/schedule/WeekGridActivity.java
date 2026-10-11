package com.weihe.schedule;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.CourseQuery;
import com.weihe.schedule.rule.WeekGridPlan;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * The timetable as a rectangular spreadsheet-like grid.
 *
 * <p>The whole-semester view shows every course and its week rule. The
 * single-week view filters by week and odd/even parity, while keeping the same
 * table shape.</p>
 *
 * <p>Where a course goes is decided by {@link WeekGridLayout}, not here: this
 * class only turns blocks into views. Overlapping courses are therefore laid out
 * side by side, and a course that starts inside another one keeps its own start
 * period instead of being swallowed by the block above it.</p>
 */
public class WeekGridActivity extends Activity {
    public static final String EXTRA_WEEK = "week";

    /**
     * Grid metrics.
     *
     * <p>The label column holds a period number and nothing else, so it is sized
     * for "15" rather than for a clock time. The rows follow from what a block
     * has to show: a name line, and below it either the week rule or the venue.
     * School timetables leave gaps in the day, so a comfortable two-period block
     * matters more than seeing all fifteen at once.</p>
     */
    private static final int ROW_HEIGHT_DP = 54;
    private static final int HEADER_HEIGHT_DP = 50;
    private static final int LABEL_WIDTH_DP = 42;
    private static final int DAY_WIDTH_DP = 118;
    private static final int LANE_GAP_DP = 2;
    /** Inset around the grid, so the first cell does not touch the screen edge. */
    private static final int GRID_PAD_DP = 6;
    /** Room under the last row for the floating button to sit over. */
    private static final int FAB_CLEARANCE_DP = 78;

    /**
     * Rows drawn when nothing needs more.
     *
     * <p>A day with fewer periods than this still gets a full-looking grid, and
     * an empty timetable still looks like a timetable.</p>
     */
    private static final int MIN_ROWS = 10;
    /** Days in the grid, and therefore day columns in the header. */
    private static final int DAY_COUNT = 7;
    private CourseRepository repository;
    private LocalDate semesterStart;
    private int currentWeek;
    private int selectedWeek;
    private boolean wholeSemester = true;
    private HorizontalScrollView horizontal;
    private ScrollView vertical;

    private TextView subtitle;
    private TextView semesterChip;
    private TextView weekChip;
    private TextView todayChip;
    private LinearLayout weekNav;
    private GridLayout grid;
    /** Day names, pinned above the body and slid sideways with it. */
    private GridLayout headerRow;
    private FrameLayout headerClip;
    /** Period numbers, pinned to the left and scrolled down with the body. */
    private GridLayout labelColumn;

    /** Gap between two lanes, in pixels; resolved in onCreate. */
    private int laneGapPx = 2;

    /** Row height in pixels, resolved once in onCreate. */
    private int rowHeightPx = 1;

    /** Top edge of each row in pixels, so cells and blocks share the same grid. */
    private int[] rowTopPx = new int[2];

    /**
     * Width of the period column, in pixels, resolved once.
     *
     * <p>One value, used by both the body's left column and the header's spacer.
     * Computing it twice is how the two drifted apart before: the header simply
     * did not have the offset at all, so every day name sat a full column width
     * plus an inset to the left of the column it named.</p>
     */
    private int labelColumnWidthPx = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        laneGapPx = Math.max(1, UiKit.dp(this, LANE_GAP_DP));
        rowHeightPx = Math.max(1, UiKit.dp(this, ROW_HEIGHT_DP));
        labelColumnWidthPx = Math.max(1,
                UiKit.dp(this, LABEL_WIDTH_DP + GRID_PAD_DP));
        // Computed cumulatively instead of as dp(period * height): rounding each
        // row separately would let the block edges drift away from the cell
        // lines further down the day.
        int rows = ScheduleUtils.PERIODS_PER_DAY + 2;
        rowTopPx = new int[rows];
        for (int period = 1; period < rows; period++) {
            rowTopPx[period] = UiKit.dp(this, (period - 1) * (float) ROW_HEIGHT_DP);
        }
        repository = new CourseRepository(this);
        semesterStart = repository.getSemesterStart();
        currentWeek = ScheduleUtils.currentWeek(semesterStart, LocalDate.now());
        selectedWeek = Math.max(1, Math.min(ScheduleUtils.SEMESTER_WEEKS,
                getIntent().getIntExtra(EXTRA_WEEK, currentWeek)));
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (grid != null) {
            // Re-read storage: another screen may have replaced the timetable
            // while this one was stopped, and this instance's snapshot would not
            // know. See CourseRepository#reload.
            repository.reload();
            semesterStart = repository.getSemesterStart();
            render();
        }
    }

    private View buildRoot() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Palette.SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(createHeader());
        root.addView(createModeBar());

        // Three separate grids, pinned to each other by scroll listeners.
        //
        // The header used to be row 0 of the one grid, which meant it scrolled
        // away in both directions: drag the timetable sideways and the day names
        // went with it, so "which column is Wednesday" had no answer on screen at
        // all. Same for the period numbers when scrolling down. Splitting them out
        // is what makes the axes legible while moving, and it is worth the sync
        // callback it costs.
        headerRow = new GridLayout(this);
        headerRow.setColumnCount(7);
        headerRow.setRowCount(1);
        headerRow.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        headerRow.setUseDefaultMargins(false);
        headerRow.setBackgroundColor(Palette.SURFACE);
        // The grid's own inset, so day 1 starts where the body's day 1 starts.
        headerRow.setPadding(dp(GRID_PAD_DP), 0, 0, 0);

        labelColumn = new GridLayout(this);
        labelColumn.setColumnCount(1);
        labelColumn.setRowCount(MIN_ROWS + 1);
        labelColumn.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        labelColumn.setUseDefaultMargins(false);
        labelColumn.setBackgroundColor(Palette.SURFACE);
        labelColumn.setPadding(dp(GRID_PAD_DP), 0, 0, dp(GRID_PAD_DP + FAB_CLEARANCE_DP));

        // Only the body scrolls; everything else follows it.
        //
        // Overriding onScrollChanged on small subclasses rather than attaching
        // OnScrollChangeListener: that listener arrived in API 23 and does not fire
        // for every internal scroll, so a follower can miss a frame and settle a
        // column out of step. onScrollChanged is the method the framework itself
        // calls.
        //
        // Followers are moved with setTranslationX / scrollTo, never by being
        // scrollable themselves. A view the user can drag is a view that can be
        // dragged away from the thing it is supposed to track, which is precisely
        // how the header used to come loose from the body.
        horizontal = new SyncedHorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(true);
        horizontal.setClipToPadding(false);
        grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setRowCount(MIN_ROWS + 1);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        grid.setPadding(dp(GRID_PAD_DP), 0, 0, dp(GRID_PAD_DP + FAB_CLEARANCE_DP));
        grid.setBackgroundColor(Palette.SURFACE);
        horizontal.addView(grid, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        vertical = new SyncedVerticalScrollView(this);
        vertical.setFillViewport(true);
        vertical.setClipToPadding(false);
        vertical.addView(horizontal, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        headerRow.setBackgroundColor(Palette.SURFACE);

        // The header sits in a row built exactly like the body's, with a spacer
        // standing in for the period column. Anything less than this duplicates
        // the body's left offset by hand, and a duplicated offset is one that can
        // disagree -- which is precisely how the day names ended up 126px to the
        // left of the columns they name.
        //
        // Added to root BEFORE bodyRow: children of a LinearLayout are stacked in
        // the order they are added, so appending this afterwards puts the day
        // names along the bottom edge of the screen.
        //
        // Note what is NOT here: a scroll view around the header. It used to have
        // its own, kept in step by a scroll listener, and that is exactly what the
        // user could defeat -- dragging the header itself moved it and left the
        // body behind. A view that cannot scroll cannot drift, so the header is
        // now a plain grid that the body slides with setTranslationX.
        //
        // Two widths have to be right for that to work, and both are taken from the
        // body's measured viewport rather than guessed:
        //
        //  * headerClip must be exactly as wide as the body's viewport, so the
        //    header is cut off at the same place the timetable is;
        //  * headerRow must span the whole scrollable range plus that viewport, so
        //    that after sliding left there is still a day to draw at the right
        //    edge. Sized to the viewport alone, sliding left moved the later days
        //    outside the clip and the week simply ended after the third one.
        //
        // Clipping is turned off on the grid for the same reason: a child wider
        // than its own view group is otherwise cut at the grid's bounds before the
        // clip that should be doing the cutting ever runs.
        headerRow.setClipChildren(false);
        headerRow.setClipToPadding(false);

        headerClip = new FrameLayout(this);
        headerClip.setBackgroundColor(Palette.SURFACE);
        headerClip.addView(headerRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        headerClip.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int viewport = right - left;
                int wanted = viewport + maxHorizontalScroll();
                ViewGroup.LayoutParams params = headerRow.getLayoutParams();
                if (viewport > 0 && params.width != wanted) {
                    params.width = wanted;
                    headerRow.setLayoutParams(params);
                }
            }
        });

        LinearLayout headerRowContainer = new LinearLayout(this);
        headerRowContainer.setOrientation(LinearLayout.HORIZONTAL);
        headerRowContainer.setPadding(0, dp(UiTokens.SPACE_S), 0, 0);
        headerRowContainer.addView(new View(this), new LinearLayout.LayoutParams(
                labelColumnWidthPx, 1));
        headerRowContainer.addView(headerClip, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(headerRowContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bodyRow = new LinearLayout(this);
        bodyRow.setOrientation(LinearLayout.HORIZONTAL);
        bodyRow.addView(labelColumn, new LinearLayout.LayoutParams(
                labelColumnWidthPx, ViewGroup.LayoutParams.MATCH_PARENT));
        bodyRow.addView(vertical, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(bodyRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Adding a course belongs here: this is the screen where you can see
        // what is missing from the week.
        ImageButton add = UiKit.fab(this, R.drawable.ic_add,
                "\u6DFB\u52A0\u8BFE\u7A0B", view -> openEditor(null));
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                dp(UiTokens.FAB_SIZE), dp(UiTokens.FAB_SIZE),
                Gravity.END | Gravity.BOTTOM);
        fabParams.setMargins(0, 0, dp(UiTokens.SPACE_L + 2),
                dp(UiTokens.SPACE_XL - 2));
        frame.addView(add, fabParams);
        return frame;
    }

    /**
     * Opens the course editor for a new course.
     *
     * <p>The day is pre-selected from what is on screen: today when the current
     * week is showing, Monday otherwise. Guessing a day for an older week would
     * put the course somewhere the user is not looking.</p>
     */
    /**
     * Opens the course's own page rather than the editor.
     *
     * <p>The add button still goes straight to the editor, because there is
     * nothing to describe about a course that does not exist yet.</p>
     */
    private void showDetails(Course course) {
        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, course.id);
        startActivity(intent);
    }
    private void openEditor(Course course) {
        Intent intent = new Intent(this, EditCourseActivity.class);
        intent.putExtra(EditCourseActivity.EXTRA_COURSE_ID,
                course == null ? 0L : course.id);
        int day = 1;
        if (course != null) {
            day = course.dayOfWeek;
        } else if (selectedWeek == currentWeek) {
            day = LocalDate.now().getDayOfWeek().getValue();
        }
        intent.putExtra(EditCourseActivity.EXTRA_DAY, day);
        startActivity(intent);
    }

    private View createHeader() {
        LinearLayout header = UiKit.detailAppBar(this, "\u8BFE\u8868\u603B\u89C8", "",
                view -> finish());
        // The bar's own subtitle view, so the week readout can be updated in place
        // without rebuilding the header on every swipe.
        subtitle = UiKit.appBarSubtitleOf(header);

        weekNav = new LinearLayout(this);
        weekNav.setOrientation(LinearLayout.HORIZONTAL);
        weekNav.setGravity(Gravity.CENTER_VERTICAL);
        weekNav.addView(stepButton(R.drawable.ic_chevron_left, -1));
        weekNav.addView(stepButton(R.drawable.ic_chevron_right, 1));
        header.addView(weekNav);
        return header;
    }

    /**
     * The week stepper.
     *
     * <p>Material chevrons rather than the {@code U+25C0} and {@code U+25B6}
     * glyphs this used to draw: those come from whatever font the device has, at
     * whatever weight, and next to the app's own vector icons they read as
     * characters that happen to be arrow-shaped.</p>
     */
    private View stepButton(int icon, final int delta) {
        android.widget.ImageButton button = UiKit.iconButton(this, icon,
                delta < 0 ? "\u4E0A\u4E00\u5468" : "\u4E0B\u4E00\u5468",
                UiTokens.CHIP_HEIGHT, Palette.SURFACE_CONTAINER,
                Palette.ON_HEADER, Palette.HEADER_ACTION_BORDER,
                UiTokens.RADIUS_FULL, view -> stepWeek(delta));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                button.getLayoutParams();
        params.leftMargin = dp(UiTokens.SPACE_S);
        return button;
    }

    private View createModeBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(10), dp(12), dp(4));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_HIGH, UiTokens.RADIUS_M));

        semesterChip = modeChip("\u603B\u8BFE\u8868");
        weekChip = modeChip("\u5355\u5468\u8BFE\u8868");
        todayChip = modeChip("\u56DE\u5230\u672C\u5468");
        semesterChip.setOnClickListener(view -> setWholeSemester(true));
        weekChip.setOnClickListener(view -> setWholeSemester(false));
        todayChip.setOnClickListener(view -> {
            setWholeSemester(false);
            if (selectedWeek != currentWeek) {
                selectedWeek = currentWeek;
                render();
            }
        });

        bar.addView(semesterChip, new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(
                0, dp(38), 1f);
        weekParams.leftMargin = dp(4);
        bar.addView(weekChip, weekParams);
        LinearLayout.LayoutParams todayParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        todayParams.leftMargin = dp(4);
        bar.addView(todayChip, todayParams);
        wrap.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return wrap;
    }

    private TextView modeChip(String label) {
        TextView chip = UiKit.text(this, label, UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackgroundColor(Color.TRANSPARENT);
        return chip;
    }

    private void setWholeSemester(boolean whole) {
        if (wholeSemester == whole) {
            return;
        }
        wholeSemester = whole;
        render();
    }

    private void stepWeek(int delta) {
        int next = selectedWeek + delta;
        if (next < 1 || next > ScheduleUtils.SEMESTER_WEEKS) {
            return;
        }
        selectedWeek = next;
        render();
    }

    private void render() {
        styleChip(semesterChip, wholeSemester);
        styleChip(weekChip, !wholeSemester);
        styleTodayChip();
        weekNav.setVisibility(wholeSemester ? View.GONE : View.VISIBLE);
        todayChip.setVisibility(wholeSemester ? View.GONE : View.VISIBLE);

        List<Course> stored = repository.getCourses();
        List<Course> shown = wholeSemester
                ? CourseQuery.all(stored)
                : CourseQuery.inWeek(stored, selectedWeek);

        if (wholeSemester) {
            subtitle.setText(repository.getActiveSemester().label() + " \u00B7 "
                    + shown.size() + " \u6761\u5B89\u6392");
        } else {
            LocalDate monday = ScheduleUtils.weekStart(semesterStart, selectedWeek);
            String range = ScheduleUtils.formatDate(monday) + " - "
                    + ScheduleUtils.formatDate(monday.plusDays(6));
            subtitle.setText((selectedWeek == currentWeek
                    ? "\u672C\u5468" : "\u7B2C" + selectedWeek + "\u5468")
                    + " \u00B7 " + range);
        }

        // The plan decides what goes where; this method only draws it. Each day
        // is one frame that spans every row, with its clusters positioned by
        // period. That is what lets a course starting at period 2 sit beside one
        // spanning periods 1-4 in the same columns.
        List<List<WeekGridPlan.Cluster>> perDay = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            perDay.add(new ArrayList<WeekGridPlan.Cluster>());
        }
        for (WeekGridPlan.Cluster cluster : WeekGridPlan.plan(shown)) {
            perDay.get(cluster.day() - 1).add(cluster);
        }

        // Only the periods that are actually used are drawn. A timetable that
        // runs to period 15 with the day ending at 11 would otherwise be mostly
        // empty rows, which is what made the first version of this screen look
        // wrong. The grid grows as courses are added, so nothing is unreachable.
        // Order matters, and getting it wrong crashes the app. GridLayout
        // validates a new row count against the children it currently holds, so
        // shrinking the grid while last week's rows are still attached throws
        // "rowCount must be greater than or equal to the maximum of all grid
        // indices". Switching from the whole-semester view (which may reach
        // period 13) to a single week that ends at period 6 is exactly that case.
        // Clear first, then resize.
        int periods = lastPeriodInUse(shown);
        int rows = periods;
        // Each grid is cleared before it is resized: GridLayout validates a new
        // row count against the children it already holds, so shrinking while last
        // week's rows are attached throws "rowCount must be greater than or equal
        // to the maximum of all grid indices". Switching from the whole-semester
        // view (which may reach period 13) to a single week ending at period 6 is
        // exactly that case.
        grid.removeAllViews();
        grid.setRowCount(rows);
        labelColumn.removeAllViews();
        labelColumn.setRowCount(rows);
        headerRow.removeAllViews();

        addDayHeaders();
        for (int period = 1; period <= periods; period++) {
            labelColumn.addView(periodCell(period),
                    cellParamsPx(period - 1, 0, 1, dp(LABEL_WIDTH_DP), rowHeight()));
        }
        // Each day spans every row in one cell, so the column has to be as tall as
        // the rows it positions -- dayColumnRows, not rowTop, which is the top
        // *edge* of the last row.
        for (int day = 1; day <= 7; day++) {
            grid.addView(dayColumn(perDay.get(day - 1), day >= 6, periods),
                    cellParamsPx(0, day - 1, rows, dp(DAY_WIDTH_DP),
                            rowSpan(dayColumnRows(periods))));
        }
    }

    /**
     * How many period rows a day column must be tall enough for.
     *
     * <p>The column is given {@code periods} rows of {@link #rowHeight()} each, so
     * its height in rows is that same number. It used to be sized with
     * {@link #rowTop(int)}, which is the <em>top edge</em> of the last row: every
     * day column came out exactly one row short of the rows it positioned.</p>
     *
     * <p>Two things followed from that. The frame's own bounds cut the last
     * period, because the grid never had clipping turned off; and the grid's total
     * content height was one row less than the period column's, so the vertical
     * scroll range could not bring that row fully into view either. Both are gone
     * once the two numbers agree.</p>
     */
    static int dayColumnRows(int periods) {
        return Math.max(1, Math.min(ScheduleUtils.PERIODS_PER_DAY, periods));
    }

    /**
     * How many period rows this timetable needs.
     *
     * <p>Derived from the courses on screen, not from
     * {@link ScheduleUtils#PERIODS_PER_DAY}, so a school day of nine periods
     * gets nine rows. The floor of {@link #MIN_ROWS} keeps an empty week looking
     * like a timetable rather than a two-row stub.</p>
     *
     * <p>Package-private rather than private so {@code WeekGridRowsTest} can
     * exercise the real rule instead of a hand-written copy of it.</p>
     */
    static int lastPeriodInUse(List<Course> courses) {
        int last = 0;
        for (Course course : courses) {
            last = Math.max(last, course.endPeriod);
        }
        return Math.max(MIN_ROWS,
                Math.min(ScheduleUtils.PERIODS_PER_DAY, last));
    }

    /**
     * One weekday: a full-height frame of empty period cells with the cluster
     * views laid on top of it.
     */
    private View dayColumn(List<WeekGridPlan.Cluster> clusters, boolean isWeekend,
            int periods) {
        FrameLayout column = new FrameLayout(this);
        for (int period = 1; period <= periods; period++) {
            View slot = emptyCell(isWeekend);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowHeight());
            params.topMargin = rowTop(period);
            column.addView(slot, params);
        }
        if (clusters == null || clusters.isEmpty()) {
            return column;
        }
        int columnWidth = dp(DAY_WIDTH_DP);
        for (WeekGridPlan.Cluster cluster : clusters) {
            int top = rowTop(cluster.startPeriod());
            int height = rowSpan(cluster.periodSpan());
            FrameLayout.LayoutParams clusterParams = new FrameLayout.LayoutParams(
                    columnWidth, height);
            clusterParams.topMargin = top;
            column.addView(clusterView(cluster, columnWidth, height), clusterParams);
        }
        return column;
    }

    /** Height of one period row, in pixels. */
    private int rowHeight() {
        return rowHeightPx;
    }

    /** Top edge of {@code period} inside a day column, in pixels. */
    private int rowTop(int period) {
        int index = Math.max(1, Math.min(rowTopPx.length - 1, period));
        return rowTopPx[index];
    }

    /** Height of {@code periods} rows, in pixels, using the same grid as rowTop. */
    private int rowSpan(int periods) {
        int last = Math.max(1, Math.min(ScheduleUtils.PERIODS_PER_DAY, periods));
        return UiKit.dp(this, last * (float) ROW_HEIGHT_DP);
    }

    /**
     * The day names, one per column, above the body.
     *
     * <p>Each cell carries the weekday as a bold second line and the date under
     * it. In the single-week view the dates are the point; in the whole-semester
     * view there is no single date to show, so the line stays empty and the
     * weekday is left to carry the row on its own.</p>
     */
    private void addDayHeaders() {
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= 7; day++) {
            String date = "";
            boolean isToday = false;
            if (!wholeSemester) {
                LocalDate value = ScheduleUtils.dateOf(semesterStart, selectedWeek, day);
                date = ScheduleUtils.formatDate(value);
                isToday = value.equals(today);
            }
            headerRow.addView(dayHeader(day, date, isToday, day >= 6),
                    cellParamsPx(0, day - 1, 1, dp(DAY_WIDTH_DP), dp(HEADER_HEIGHT_DP)));
        }
    }

    /**
     * One column heading: the weekday, and its date when there is one.
     *
     * <p>The weekend columns are tinted so the working week is readable at a
     * glance, and today is outlined in the accent colour -- the same treatment
     * the day strip on the home screen uses, so the two screens agree about what
     * "today" looks like.</p>
     */
    private View dayHeader(int day, String date, boolean isToday, boolean isWeekend) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setContentDescription(ScheduleUtils.dayName(day)
                + (date.isEmpty() ? "" : "\uFF0C" + date)
                + (isToday ? "\uFF0C\u4ECA\u5929" : ""));

        int background = isWeekend ? Palette.SURFACE_CONTAINER_HIGH
                : Palette.SURFACE_CONTAINER_LOW;
        if (isToday) {
            UiKit.setRoundedBackground(cell, Palette.ACCENT_CONTAINER, 2,
                    Palette.ACCENT, UiTokens.STROKE_HAIRLINE);
        } else {
            UiKit.setRoundedBackground(cell, background, 2, Color.TRANSPARENT, 0f);
        }

        TextView name = UiKit.text(this, ScheduleUtils.dayName(day),
                UiTokens.Type.LABEL_LARGE,
                isToday ? Palette.ON_ACCENT_CONTAINER : Palette.ON_SURFACE,
                true);
        name.setGravity(Gravity.CENTER);
        cell.addView(name);

        if (!date.isEmpty()) {
            TextView detail = UiKit.text(this, date, UiTokens.Type.LABEL_SMALL,
                    isToday ? Palette.ON_ACCENT_CONTAINER : Palette.ON_SURFACE_VARIANT,
                    false);
            detail.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(1);
            cell.addView(detail, detailParams);
        }
        return cell;
    }

    private View tableHeader(String title, String detail, boolean isToday) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setBackground(UiKit.outline(this,
                isToday ? Palette.ACCENT_CONTAINER : Palette.SURFACE_CONTAINER_LOW, 2,
                isToday ? Palette.ACCENT : Palette.OUTLINE_VARIANT, 1));
        TextView name = UiKit.text(this, title, 12,
                isToday ? Palette.ON_ACCENT_CONTAINER : Palette.ON_SURFACE, true);
        name.setGravity(Gravity.CENTER);
        header.addView(name);
        if (!detail.isEmpty()) {
            TextView sub = UiKit.text(this, detail, 9,
                    isToday ? Palette.ON_ACCENT_CONTAINER : Palette.ON_SURFACE_VARIANT, false);
            sub.setGravity(Gravity.CENTER);
            header.addView(sub);
        }
        return header;
    }

    /**
     * The period number, and nothing else.
     *
     * <p>No clock times here on purpose. Periods do not share one timetable:
     * the lab building rings at different minutes than the main one, and the
     * overview shows courses from every venue at once. A time in this column
     * would be a claim about the wrong building, so the overview stays in period
     * numbers and each course's own times are read on the day list, where the
     * applicable schedule is known.</p>
     */
    private View periodCell(int period) {
        TextView number = UiKit.text(this, String.valueOf(period),
                UiTokens.Type.BODY_LARGE, Palette.ON_SURFACE, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER_LOW,
                2, Palette.OUTLINE_VARIANT, UiTokens.STROKE_HAIRLINE));
        return number;
    }

    /** An empty period slot; the weekend columns are tinted a shade darker. */
    private View emptyCell(boolean isWeekend) {
        View cell = new View(this);
        cell.setBackground(UiKit.outline(this,
                isWeekend ? Palette.SURFACE_CONTAINER_LOW : Palette.CARD, 2, Palette.OUTLINE_VARIANT, 1));
        return cell;
    }

    /**
     * A cluster drawn over one day column.
     *
     * <p>Each cell of the cluster is placed at its own start period inside this
     * frame, and the blocks of a cell share the cluster's lanes, so a course
     * starting later lines up in the same columns as the long course beside
     * it.</p>
     */
    private View clusterView(WeekGridPlan.Cluster cluster, int widthPx,
            int heightPx) {
        FrameLayout frame = new FrameLayout(this);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        for (WeekGridPlan.Cell cell : cluster.cells()) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    widthPx, rowSpan(cell.periodSpan()));
            params.topMargin = rowTop(cell.startPeriod())
                    - rowTop(cluster.startPeriod());
            frame.addView(cellView(cell, widthPx, cluster.laneCount()), params);
        }
        return frame;
    }

    /**
     * One start period of a cluster: its courses side by side, each at its own
     * natural height.
     *
     * <p>Positions are computed here rather than in a layout pass, because the
     * given width and lane count are all that is needed. An earlier version
     * positioned the views in a layout listener and never attached them to the
     * frame at all, which left the grid empty.</p>
     */
    private View cellView(final WeekGridPlan.Cell cell, int widthPx, int lanes) {
        FrameLayout frame = new FrameLayout(this);
        int laneWidth = Math.max(1, widthPx / Math.max(1, lanes));
        for (WeekGridPlan.Block block : cell.blocks()) {
            View blockView = courseBlock(block, cell.periodSpan(),
                    cell.blocks().size() > 1);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    laneWidth, rowSpan(block.periodSpan()) - laneGapPx);
            params.gravity = Gravity.TOP | Gravity.START;
            params.leftMargin = block.lane() * laneWidth;
            blockView.setLayoutParams(params);
            frame.addView(blockView);
        }
        return frame;
    }

    private View courseBlock(final WeekGridPlan.Block block, int span,
            boolean sharedCell) {
        final Course course = block.course();
        LinearLayout blockView = new LinearLayout(this);
        blockView.setOrientation(LinearLayout.VERTICAL);
        blockView.setPadding(dp(6), dp(5), dp(5), dp(5));
        // The same derived colours the home screen's cards use. They used to be
        // blended here with three literals of their own, which made one course
        // draw at two slightly different tints on two screens.
        blockView.setBackground(UiKit.outline(this, UiKit.courseTint(course.color), 4,
                UiKit.courseBorder(course.color), 1));

        TextView name = UiKit.text(this, course.name, 11,
                UiKit.courseInk(course.color), true);
        name.setMaxLines(sharedCell || span == 1 ? 1 : 2);
        blockView.addView(name);

        TextView rule = UiKit.text(this, cellNote(course),
                9, Palette.ON_SURFACE_VARIANT, false);
        rule.setMaxLines(span >= 2 ? 2 : 1);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ruleParams.topMargin = dp(2);
        blockView.addView(rule, ruleParams);

        if (!sharedCell && span >= 2) {
            String detailText = blockDetail(course);
            if (!detailText.isEmpty()) {
                TextView detail = UiKit.text(this, detailText, 9,
                        Palette.ON_SURFACE_VARIANT, false);
                detail.setMaxLines(1);
                LinearLayout.LayoutParams detailParams =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                detailParams.topMargin = dp(1);
                blockView.addView(detail, detailParams);
            }
        }

        blockView.setOnClickListener(view -> showDetails(course));
        return blockView;
    }

    /**
     * The line under the block's second line, chosen per view.
     *
     * <p>The two views put different things on the second line, so this cannot be
     * a fixed "venue and teacher" string: the whole-semester view has the week
     * rule up there, while the single-week view has the venue. Repeating the
     * venue in both lines is what a shared implementation produced, and it made
     * every block on the single-week grid read the venue twice.</p>
     */
    private String blockDetail(Course course) {
        if (wholeSemester) {
            if (course.location.isEmpty()) {
                return course.teacher;
            }
            return course.teacher.isEmpty() ? course.location
                    : course.location + " \u00B7 " + course.teacher;
        }
        return course.teacher;
    }

    /**
     * The second line of a block.
     *
     * <p>Which one it is depends on the view, and the two must not overlap: the
     * whole-semester view has no week anywhere else on screen, so it shows the
     * week rule; the single-week view knows the week already and shows the venue
     * instead, because the venue is what tells the user which building's bells
     * apply. Neither shows clock times -- see {@link #periodCell} for why.</p>
     */
    private String cellNote(Course course) {
        if (wholeSemester) {
            return ScheduleUtils.weekRule(course);
        }
        if (!course.location.isEmpty()) {
            return course.location;
        }
        if (!course.teacher.isEmpty()) {
            return course.teacher;
        }
        return "\u7B2C " + course.startPeriod + "-" + course.endPeriod + " \u8282";
    }

    private GridLayout.LayoutParams cellParams(int row, int column, int rowSpan,
            int widthDp, int heightDp) {
        return cellParamsPx(row, column, rowSpan, dp(widthDp), dp(heightDp));
    }

    /**
     * Grid cell geometry in pixels.
     *
     * <p>The pixel form exists because the day frames and their blocks are
     * positioned from the same {@link #rowTop} table; converting the total
     * height from dp a second time here could disagree with it by a pixel and
     * leave the last row clipped.</p>
     */
    private GridLayout.LayoutParams cellParamsPx(int row, int column, int rowSpan,
            int widthPx, int heightPx) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row, rowSpan), GridLayout.spec(column));
        params.width = widthPx;
        params.height = heightPx;
        return params;
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? Palette.PRIMARY : Palette.ON_SURFACE_VARIANT);
        chip.setBackground(UiKit.fill(this,
                selected ? Palette.CARD : Color.TRANSPARENT, 8));
        chip.setElevation(selected ? dp(1) : 0);
    }

    private void styleTodayChip() {
        boolean current = selectedWeek == currentWeek;
        todayChip.setTextColor(current ? Palette.ON_SURFACE_VARIANT : Palette.ON_ACCENT_CONTAINER);
        todayChip.setBackground(UiKit.fill(this,
                current ? Color.TRANSPARENT : Palette.ACCENT_CONTAINER, 8));
        todayChip.setEnabled(!current);
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }

    /**
     * How far the body can be dragged sideways, in pixels.
     *
     * <p>The grid's own width is not known until it has been measured, so this
     * works from what is known: seven day columns plus the grid's inset, minus the
     * viewport the grid sits in. Clamped at zero so a narrow timetable cannot ask
     * for a negative width.</p>
     */
    private int maxHorizontalScroll() {
        if (grid == null || horizontal == null) {
            return 0;
        }
        int content = dp(GRID_PAD_DP) + DAY_COUNT * dp(DAY_WIDTH_DP);
        int viewport = horizontal.getWidth();
        return Math.max(0, content - viewport);
    }

    /**
     * The body's horizontal scroll, which slides the header with it.
     *
     * <p>A subclass overriding {@code onScrollChanged} rather than a plain
     * {@code HorizontalScrollView} with an {@code OnScrollChangeListener}: that
     * listener arrived in API 23 and does not fire for every internal scroll.
     * This is the method the framework itself calls, so the header cannot miss a
     * frame and end up a column out of step.</p>
     *
     * <p>The header is moved with {@code setTranslationX}, not by scrolling a view
     * of its own. Translating a view that has nothing to scroll needs no
     * synchronisation in the other direction, so there is no feedback loop and no
     * way for the two halves to be dragged apart.</p>
     */
    private final class SyncedHorizontalScrollView extends HorizontalScrollView {
        SyncedHorizontalScrollView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onScrollChanged(int x, int y, int oldX, int oldY) {
            super.onScrollChanged(x, y, oldX, oldY);
            if (headerRow != null && headerRow.getTranslationX() != -x) {
                headerRow.setTranslationX(-x);
            }
        }
    }

    /** The body's vertical scroll, which drives the period label column's. */
    private final class SyncedVerticalScrollView extends ScrollView {
        SyncedVerticalScrollView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onScrollChanged(int x, int y, int oldX, int oldY) {
            super.onScrollChanged(x, y, oldX, oldY);
            if (labelColumn != null && labelColumn.getScrollY() != y) {
                labelColumn.scrollTo(0, y);
            }
        }
    }
}

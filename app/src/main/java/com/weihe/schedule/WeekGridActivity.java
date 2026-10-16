package com.weihe.schedule;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
import com.weihe.schedule.rule.GridZoom;
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
 *
 * <h2>The week fits the screen, and the user can change their mind</h2>
 *
 * <p>At {@link GridZoom#FIT} the seven day columns divide the width the body
 * actually has, so opening the screen shows the whole week without any sideways
 * dragging. That is a default, not a rule: a pinch or the zoom buttons multiply
 * the column width and the row height, which is how a name that is too cramped
 * at seven-across is read. Everything is derived from the viewport and the
 * scale, so a rotation re-fits the week and then re-applies the scale -- see
 * {@link GridZoom} for the arithmetic, which is kept out of this class so it can
 * be tested without a device.</p>
 *
 * <p>Zooming is a real layout change rather than a transform on the finished
 * picture: the cells and the blocks are rebuilt at the new sizes, so the text is
 * drawn at its final size instead of being stretched, and a tap lands on the
 * block it looks like it lands on. The cost is that the grid is rebuilt during
 * the gesture, which {@link #queueRender} keeps to once a frame.</p>
 */
public class WeekGridActivity extends Activity {
    public static final String EXTRA_WEEK = "week";

    /**
     * Grid metrics.
     *
     * <p>Only two sizes are still fixed: the height a period row has at the
     * default scale, and the height of the weekday heading. The column widths
     * come from the viewport, because a fixed day width is exactly what used to
     * force the week off the side of the screen.</p>
     *
     * <p>The rows follow from what a block has to show: a name line, and below it
     * either the week rule or the venue. School timetables leave gaps in the day,
     * so a comfortable two-period block matters more than seeing all fifteen at
     * once -- which is what the "whole day" control is for.</p>
     */
    private static final int BASE_ROW_HEIGHT_DP = 54;
    private static final int HEADER_HEIGHT_DP = 50;

    /**
     * The period column, sized from the screen rather than fixed.
     *
     * <p>It holds a period number and nothing else, so it only has to be as wide
     * as "15" -- that is what the floor is for. Above that it takes a tenth of
     * the width, which on a normal phone lands within a dp of the 42dp this used
     * to be a constant, and on a narrow one hands the seven day columns a few
     * more dp each. The ceiling stops a tablet spending a wide strip on two
     * digits.</p>
     */
    private static final int MIN_LABEL_WIDTH_DP = 26;
    private static final int MAX_LABEL_WIDTH_DP = 42;
    private static final float LABEL_WIDTH_FRACTION = 0.10f;

    private static final int LANE_GAP_DP = 2;
    /** Inset around the grid, so the first cell does not touch the screen edge. */
    private static final int GRID_PAD_DP = 6;
    /** Room under the last row for the floating button to sit over. */
    private static final int FAB_CLEARANCE_DP = 78;

    /**
     * The course block's own metrics.
     *
     * <p>Corner radius and the two text sizes, named rather than repeated: the
     * block is the only place in the app where a course is drawn at this size,
     * and a literal in each of the three call sites is how the name and the week
     * rule end up on different scales.</p>
     */
    private static final int BLOCK_RADIUS_DP = 4;
    private static final float BLOCK_NAME_SP = 11f;
    private static final float BLOCK_META_SP = 9f;
    /** The "+N" that says how many courses a stack is hiding. */
    private static final float BADGE_TEXT_SP = 10f;

    /**
     * Rows drawn when nothing needs more.
     *
     * <p>A day with fewer periods than this still gets a full-looking grid, and
     * an empty timetable still looks like a timetable.</p>
     */
    private static final int MIN_ROWS = 10;
    /** Days in the grid, and therefore day columns in the header. */
    private static final int DAY_COUNT = 7;
    /** No scroll position waiting to be applied. */
    private static final int NO_SCROLL = Integer.MIN_VALUE;

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

    /** Period row height in pixels, at the current zoom. */
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

    /** Width of a period cell, in pixels; the column adds the grid's inset. */
    private int labelWidthPx = 1;

    /** The scale the user is at, as a multiple of {@link GridZoom#FIT}. */
    private float zoom = GridZoom.FIT;

    /**
     * One day column's width at {@link GridZoom#FIT}, in pixels.
     *
     * <p>The baseline everything else is a multiple of. It is recomputed from the
     * viewport whenever that changes, which is what makes "fit the screen" mean
     * the screen in front of the user rather than a number baked in at build
     * time.</p>
     */
    private int baseDayWidthPx = 1;

    /** One day column's width at the current zoom, in pixels. */
    private int dayWidthPx = 1;

    /** The body's measured size, or 0 before the first layout. */
    private int viewportWidthPx;
    private int viewportHeightPx;

    /** Set by a pinch so the rebuild can restore the point under the fingers. */
    private int pendingScrollX = NO_SCROLL;

    /** True while a rebuild is already queued for this frame. */
    private boolean renderQueued;

    /** True from the second finger of a pinch until the gesture ends. */
    private boolean pinching;

    private ScaleGestureDetector scaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        laneGapPx = Math.max(1, UiKit.dp(this, LANE_GAP_DP));
        updateLabelWidth();
        recomputeGeometry();
        repository = new CourseRepository(this);
        semesterStart = repository.getSemesterStart();
        currentWeek = ScheduleUtils.currentWeek(semesterStart, LocalDate.now());
        selectedWeek = Math.max(1, Math.min(ScheduleUtils.SEMESTER_WEEKS,
                getIntent().getIntExtra(EXTRA_WEEK, currentWeek)));
        // A pinch scales around the point between the fingers, so the day being
        // looked at stays under them instead of sliding off the edge.
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        setZoomAround(zoom * detector.getScaleFactor(),
                                detector.getFocusX() - horizontalLeftOnScreen());
                        return true;
                    }
                });
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
        render();
    }

    /**
     * Watches every touch for a pinch before anything else sees it.
     *
     * <p>On the Activity rather than on the grid, because the grid is a tree of
     * clickable blocks and scroll views and whichever of them consumed the down
     * event would be the only one to see the rest -- a {@code ScaleGestureDetector}
     * attached lower down would simply never receive the second finger. Here it
     * sees the whole stream whatever happens to it afterwards.</p>
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (scaleDetector != null) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pinching = false;
            }
            if (event.getPointerCount() > 1) {
                // A pinch is not a drag. While a second finger is down, neither
                // scroll view is allowed to claim the gesture; the framework
                // clears the flag on the next finger-down, so an ordinary
                // one-finger drag afterwards still scrolls as it always did.
                pinching = true;
                disallowScrollIntercept(true);
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                // The last of the two fingers has gone: hand the grid back to the
                // scroll views without waiting for the finger to be lifted.
                disallowScrollIntercept(false);
            }
            scaleDetector.onTouchEvent(event);
            if (pinching && (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL)) {
                // A pinch is not a tap either. The finger that landed last would
                // otherwise be read as a click on whatever block it happened to
                // be over, and open a course the user was only zooming around.
                pinching = false;
                MotionEvent cancelled = MotionEvent.obtain(event);
                cancelled.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancelled);
                cancelled.recycle();
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void disallowScrollIntercept(boolean disallow) {
        if (horizontal != null) {
            horizontal.requestDisallowInterceptTouchEvent(disallow);
        }
        if (vertical != null) {
            vertical.requestDisallowInterceptTouchEvent(disallow);
        }
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
        // The body's size is what the day width and the row height are worked out
        // from, and it is not known until the first layout. Recomputing here is
        // also what keeps the week fitting the screen through a rotation or a
        // window resize. Guarded on the size actually changing, so it settles in
        // one extra pass rather than looping.
        vertical.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int width = right - left;
                int height = bottom - top;
                if (width <= 0 || height <= 0) {
                    return;
                }
                if (width == viewportWidthPx && height == viewportHeightPx) {
                    return;
                }
                viewportWidthPx = width;
                viewportHeightPx = height;
                recomputeGeometry();
                render();
            }
        });

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
                syncHeaderWidth();
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

        // The zoom controls share the band the grid already reserves under its
        // last row -- the floating button sits at the other end of it -- so they
        // cover no lesson and cost the timetable no height.
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.BOTTOM);
        zoomParams.setMargins(dp(UiTokens.SPACE_L), 0, 0, dp(UiTokens.SPACE_L));
        frame.addView(zoomControls(), zoomParams);
        return frame;
    }

    /**
     * The zoom stepper and the fit control, as one compact pill.
     *
     * <p>Kept off the app bar on purpose: that bar is already carrying the week
     * stepper, and a second row of controls above the grid would come straight
     * off the number of periods that fit. Down here it shares the band the
     * floating button already reserves.</p>
     *
     * <p>There is no "whole day" control: the zoom floor is already the
     * fit-the-week scale, so a button that shrank the grid further would be a
     * second way to do the one thing {@code MIN} refuses.</p>
     */
    private View zoomControls() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(4), dp(4), dp(4), dp(4));
        UiKit.setRoundedBackground(pill, Palette.SURFACE_CONTAINER,
                UiTokens.RADIUS_FULL, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE);
        UiKit.elevate(pill, UiTokens.RADIUS_FULL, UiTokens.ELEVATION_MEDIUM);

        pill.addView(zoomIconButton(R.drawable.ic_remove,
                "\u7F29\u5C0F\u8BFE\u8868", this::zoomOut));
        pill.addView(zoomTextButton("\u9002\u5E94",
                "\u9002\u5E94\u5C4F\u5E55\uFF0C\u4E03\u5929\u5B8C\u6574\u663E\u793A",
                this::fitWidth));
        pill.addView(zoomIconButton(R.drawable.ic_add,
                "\u653E\u5927\u8BFE\u8868", this::zoomIn));
        return pill;
    }

    /** One square icon in the zoom pill. */
    private View zoomIconButton(int icon, String description, Runnable action) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setColorFilter(Palette.ON_SURFACE);
        button.setBackground(UiKit.fill(this, Color.TRANSPARENT, UiTokens.RADIUS_FULL));
        button.setPadding(dp(7), dp(7), dp(7), dp(7));
        button.setContentDescription(description);
        button.setOnClickListener(view -> action.run());
        button.setLayoutParams(zoomButtonParams());
        return button;
    }

    /** One labelled control in the zoom pill. */
    private View zoomTextButton(String label, String description, Runnable action) {
        TextView button = UiKit.text(this, label, UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setContentDescription(description);
        button.setBackground(UiKit.fill(this, Color.TRANSPARENT, UiTokens.RADIUS_FULL));
        button.setOnClickListener(view -> action.run());
        button.setLayoutParams(zoomButtonParams());
        return button;
    }

    private LinearLayout.LayoutParams zoomButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(38), dp(38));
        params.leftMargin = dp(2);
        return params;
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

    // ------------------------------------------------------------------ zoom

    /**
     * Sizes the period column from the screen.
     *
     * <p>Worked out from the display rather than from the measured layout,
     * deliberately: the column's width decides how much is left for the body, so
     * deriving it from the body would be a circle. The display width does not
     * depend on either, which is what makes the measurement converge in one
     * pass.</p>
     */
    private void updateLabelWidth() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int wanted = Math.round(screen * LABEL_WIDTH_FRACTION);
        labelWidthPx = Math.min(dp(MAX_LABEL_WIDTH_DP),
                Math.max(dp(MIN_LABEL_WIDTH_DP), wanted));
        labelColumnWidthPx = Math.max(1, labelWidthPx + dp(GRID_PAD_DP));
    }

    /**
     * Recomputes the fit-the-week baseline for the current viewport.
     *
     * <p>Called at startup and whenever the body is resized. The zoom the user
     * chose is deliberately kept: it is a multiple of this baseline, so a
     * rotation re-fits the week and then re-applies how far in or out they
     * were.</p>
     */
    private void recomputeGeometry() {
        // Before the first layout the body has no measured width, so it is worked
        // out from the display instead: the root fills the window and the period
        // column is a known slice of it, which makes this the same number the
        // first layout pass will report. Guessing low would show a mis-sized week
        // for one frame; guessing high would show one that does not fit.
        int viewport = viewportWidthPx > 0
                ? viewportWidthPx
                : Math.max(1, getResources().getDisplayMetrics().widthPixels
                        - labelColumnWidthPx);
        baseDayWidthPx = GridZoom.fitWidthDayPx(viewport, dp(GRID_PAD_DP), DAY_COUNT);
        applyZoomPixels();
    }

    /** Turns {@link #zoom} into the pixel sizes every part of the grid is built from. */
    private void applyZoomPixels() {
        dayWidthPx = GridZoom.dayWidthPx(baseDayWidthPx, zoom);
        rowHeightPx = GridZoom.rowHeightPx(dp(BASE_ROW_HEIGHT_DP), zoom);
        rebuildRowTops();
    }

    /**
     * Rebuilds the row offset table from the current row height.
     *
     * <p>Computed cumulatively instead of as {@code dp(period * height)}: rounding
     * each row separately would let the block edges drift away from the cell
     * lines further down the day. Rebuilt whenever the zoom changes, because the
     * cells and the blocks both read this one table and a stale one would place
     * them on two different grids.</p>
     *
     * <p>Sized from {@link ScheduleUtils#MAX_PERIODS}, not from the default
     * fifteen: the table is indexed by period, and a twenty-period day whose
     * table stopped at seventeen would clamp its last three rows onto row
     * seventeen.</p>
     */
    private void rebuildRowTops() {
        int rows = ScheduleUtils.MAX_PERIODS + 2;
        rowTopPx = new int[rows];
        for (int period = 1; period < rows; period++) {
            rowTopPx[period] = (period - 1) * rowHeightPx;
        }
    }

    private void zoomIn() {
        setZoom(GridZoom.zoomIn(zoom));
    }

    private void zoomOut() {
        setZoom(GridZoom.zoomOut(zoom));
    }

    /**
     * Back to the default: seven days across the screen.
     *
     * <p>Recomputes the baseline rather than assigning a number of dp, which is
     * what makes this "fit <em>this</em> screen" instead of "reset to a constant
     * that happened to be right on the phone it was written on". It is also the
     * far end of the range, so this and zooming all the way out agree.</p>
     */
    private void fitWidth() {
        recomputeGeometry();
        setZoom(GridZoom.FIT);
        render();
    }

    /** Applies a scale, rebuilding the grid at the new sizes. */
    private void setZoom(float wanted) {
        float next = GridZoom.clamp(wanted);
        if (next == zoom) {
            return;
        }
        zoom = next;
        applyZoomPixels();
        queueRender();
    }

    /**
     * Applies a scale while keeping the point under the fingers where it is.
     *
     * <p>Without this, pinching in from the right-hand side of the week drags the
     * day being looked at off the screen, which reads as the grid jumping rather
     * than as the grid growing.</p>
     *
     * @param focusInViewport the pinch centre, measured from the body's left edge
     */
    private void setZoomAround(float wanted, float focusInViewport) {
        float next = GridZoom.clamp(wanted);
        if (next == zoom) {
            return;
        }
        int previousDayWidth = dayWidthPx;
        int previousScroll = horizontal == null ? 0 : horizontal.getScrollX();
        zoom = next;
        applyZoomPixels();
        if (previousDayWidth > 0 && horizontal != null) {
            float grew = dayWidthPx / (float) previousDayWidth;
            pendingScrollX = Math.max(0,
                    Math.round((previousScroll + focusInViewport) * grew
                            - focusInViewport));
        }
        queueRender();
    }

    /**
     * Rebuilds the grid once per frame at most.
     *
     * <p>A pinch delivers several events per frame, and rebuilding is not free --
     * it re-creates every block. Coalescing them changes nothing that is drawn,
     * because the last scale of the frame is the one that wins; it only stops the
     * same work being done three times before the frame it belongs to.</p>
     */
    private void queueRender() {
        if (renderQueued) {
            return;
        }
        renderQueued = true;
        // On the decor view rather than on the grid: this is a "next frame" hook,
        // and the decor view is the one that always exists.
        getWindow().getDecorView().postOnAnimation(new Runnable() {
            @Override
            public void run() {
                renderQueued = false;
                render();
            }
        });
    }

    /** Where the body's left edge sits on screen, for mapping a pinch centre. */
    private float horizontalLeftOnScreen() {
        if (horizontal == null) {
            return 0f;
        }
        int[] position = new int[2];
        horizontal.getLocationOnScreen(position);
        return position[0];
    }

    /**
     * Gives the day-name row the full scrollable width.
     *
     * <p>It has to span the viewport plus everything the body can be dragged
     * past, or sliding left moves the later days outside the clip and the week
     * simply ends. Recomputed after every rebuild, because zooming in widens
     * what there is to scroll through.</p>
     */
    private void syncHeaderWidth() {
        if (headerClip == null || headerRow == null) {
            return;
        }
        int viewport = headerClip.getWidth();
        if (viewport <= 0) {
            return;
        }
        int wanted = viewport + maxHorizontalScroll();
        ViewGroup.LayoutParams params = headerRow.getLayoutParams();
        if (params != null && params.width != wanted) {
            params.width = wanted;
            headerRow.setLayoutParams(params);
        }
    }

    /**
     * The courses the current mode shows.
     *
     * <p>Split out because the zoom controls need the same answer the grid is
     * drawn from: "show the whole day" has to count the periods of the week on
     * screen, not of every course in the term.</p>
     */
    private List<Course> shownCourses() {
        List<Course> stored = repository.getCourses();
        return wholeSemester
                ? CourseQuery.all(stored)
                : CourseQuery.inWeek(stored, selectedWeek);
    }

    private void render() {
        styleChip(semesterChip, wholeSemester);
        styleChip(weekChip, !wholeSemester);
        styleTodayChip();
        weekNav.setVisibility(wholeSemester ? View.GONE : View.VISIBLE);
        todayChip.setVisibility(wholeSemester ? View.GONE : View.VISIBLE);

        List<Course> shown = shownCourses();

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
                    cellParamsPx(period - 1, 0, 1, labelWidthPx, rowHeight()));
        }
        // Each day spans every row in one cell, so the column has to be as tall as
        // the rows it positions -- dayColumnRows, not rowTop, which is the top
        // *edge* of the last row.
        for (int day = 1; day <= 7; day++) {
            grid.addView(dayColumn(perDay.get(day - 1), day >= 6, periods),
                    cellParamsPx(0, day - 1, rows, dayWidthPx,
                            rowSpan(dayColumnRows(periods))));
        }

        // The day names span whatever there now is to scroll through, and the
        // pending scroll of a pinch is applied only once the new widths have been
        // measured -- scrolling before that would be clamped against the old
        // range and land in the wrong place.
        syncHeaderWidth();
        applyPendingScroll();
    }

    /** Applies a scroll a pinch asked for, after the rebuild it belongs to. */
    private void applyPendingScroll() {
        final int target = pendingScrollX;
        pendingScrollX = NO_SCROLL;
        if (target == NO_SCROLL || horizontal == null) {
            return;
        }
        horizontal.post(new Runnable() {
            @Override
            public void run() {
                horizontal.scrollTo(target, 0);
            }
        });
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
        return Math.max(1, Math.min(ScheduleUtils.MAX_PERIODS, periods));
    }

    /**
     * How many period rows this timetable needs.
     *
     * <p>Derived from the courses on screen, so a school day of nine periods
     * gets nine rows and a day of twenty gets twenty. The floor of
     * {@link #MIN_ROWS} keeps an empty week looking like a timetable rather than a
     * two-row stub.</p>
     *
     * <p>The ceiling is {@link ScheduleUtils#MAX_PERIODS} rather than the default
     * fifteen: a course may legitimately sit on period 18 of a twenty-period table,
     * and clamping the grid at fifteen used to leave that lesson with no row to be
     * drawn in. The grid allocates its row offsets from the same constant, so the
     * two stay in step.</p>
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
                Math.min(ScheduleUtils.MAX_PERIODS, last));
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
        int columnWidth = dayWidthPx;
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

    /**
     * Height of {@code periods} rows, in pixels, using the same grid as rowTop.
     *
     * <p>Multiplied out rather than converted from dp a second time: {@link #rowTop}
     * already carries the rounding of the current row height, and converting the
     * total separately is how a block edge drifts a pixel away from the cell line
     * further down the day.</p>
     */
    private int rowSpan(int periods) {
        int last = Math.max(1, Math.min(ScheduleUtils.MAX_PERIODS, periods));
        return last * rowHeightPx;
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
                    cellParamsPx(0, day - 1, 1, dayWidthPx, dp(HEADER_HEIGHT_DP)));
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

    /**
     * One block: the course drawn on top, and -- when the block stands for
     * several -- an edge of the cards behind it and a count of what is hidden.
     *
     * <p>A stack is drawn inside its own cell, inset on the left and top, so it
     * never reaches over a neighbouring day column or the next period row. It
     * takes the width its lane was already given, which is what keeps the seven
     * day columns the same size however many alternatives a slot holds.</p>
     */
    private View courseBlock(final WeekGridPlan.Block block, int span,
            boolean sharedCell) {
        final Course course = block.course();
        boolean stacked = block.isStack();
        LinearLayout blockView = new LinearLayout(this);
        blockView.setOrientation(LinearLayout.VERTICAL);
        blockView.setPadding(dp(6), dp(5), dp(5), dp(5));
        // The same derived colours the home screen's cards use. They used to be
        // blended here with three literals of their own, which made one course
        // draw at two slightly different tints on two screens.
        blockView.setBackground(UiKit.outline(this, UiKit.courseTint(course.color),
                BLOCK_RADIUS_DP, UiKit.courseBorder(course.color), 1));

        TextView name = UiKit.text(this, course.name, BLOCK_NAME_SP,
                UiKit.courseInk(course.color), true);
        name.setMaxLines(sharedCell || span == 1 ? 1 : 2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        blockView.addView(name);

        TextView rule = UiKit.text(this, cellNote(course), BLOCK_META_SP,
                Palette.ON_SURFACE_VARIANT, false);
        rule.setMaxLines(span >= 2 ? 2 : 1);
        rule.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ruleParams.topMargin = dp(2);
        blockView.addView(rule, ruleParams);

        if (stacked) {
            // The count stands in for the courses behind the card, and it takes
            // the place of the venue line: a slot holding several courses has no
            // room for it at seven columns, and knowing there are three more
            // courses there matters more than whose classroom the first one is in.
            blockView.addView(countBadge(block.courseCount() - 1));
            blockView.setOnClickListener(view -> showStack(block));
            return stackedBlock(block, blockView);
        }

        if (!sharedCell && span >= 2) {
            String detailText = blockDetail(course);
            if (!detailText.isEmpty()) {
                TextView detail = UiKit.text(this, detailText, BLOCK_META_SP,
                        Palette.ON_SURFACE_VARIANT, false);
                detail.setMaxLines(1);
                detail.setEllipsize(TextUtils.TruncateAt.END);
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
     * The edge of the cards a stack stands for.
     *
     * <p>Inset on the left and top rather than the right and bottom: an edge
     * drawn to the right would reach over the next day column and one drawn below
     * would reach over the next period, and both are the kind of overlap that
     * makes a slot unreadable. One edge, not one per hidden course -- two offset
     * strips read as a stack, and a dozen would read as a comb; the badge carries
     * the number.</p>
     */
    private View stackedBlock(final WeekGridPlan.Block block, View card) {
        final Course course = block.course();
        FrameLayout stack = new FrameLayout(this);
        stack.setClipChildren(true);

        View behind = new View(this);
        behind.setBackground(UiKit.outline(this, UiKit.courseTint(course.color),
                BLOCK_RADIUS_DP, UiKit.courseBorder(course.color), 1));
        stack.addView(behind, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams front = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        int peek = stackPeekPx();
        front.leftMargin = peek;
        front.topMargin = peek;
        stack.addView(card, front);
        return stack;
    }

    /**
     * How far the card behind shows at the edge, in pixels.
     *
     * <p>Scaled with the row height rather than fixed, so the stack is still
     * visible when the grid is zoomed out and does not swallow the card when it
     * is zoomed in.</p>
     */
    private int stackPeekPx() {
        return Math.max(dp(2), Math.min(dp(8), rowHeightPx / 12));
    }

    /** The "+N" that says how many courses a stack is hiding. */
    private View countBadge(int hidden) {
        TextView badge = UiKit.text(this, "+" + hidden, BADGE_TEXT_SP,
                Palette.ON_SURFACE_VARIANT, true);
        badge.setIncludeFontPadding(false);
        badge.setGravity(Gravity.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        params.topMargin = dp(1);
        badge.setLayoutParams(params);
        return badge;
    }

    /**
     * Lists every course behind a stacked card.
     *
     * <p>A sheet rather than a jump straight into one course: the card stands for
     * several, and which one the user wants is their decision, not the
     * renderer's.</p>
     */
    private void showStack(WeekGridPlan.Block block) {
        List<Course> courses = block.courses();
        Course lead = block.course();
        String title = ScheduleUtils.dayName(lead.dayOfWeek)
                + " · " + periodRangeLabel(lead);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_L), 0);

        TextView caption = UiKit.text(this,
                courses.size() + " \u95E8\u8BFE\u7A0B\uFF0C\u5206\u5C5E\u4E0D\u540C\u5468\u6B21",
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.bottomMargin = dp(UiTokens.SPACE_XS);
        list.addView(caption, captionParams);

        // The dialog does not exist yet while the rows are built, so it is handed
        // to them through a holder that is filled in before any of them can be
        // tapped.
        final android.app.Dialog[] sheet = new android.app.Dialog[1];
        boolean first = true;
        for (Course course : courses) {
            list.addView(stackRow(course, first, sheet),
                    stackRowParams(first));
            first = false;
        }

        sheet[0] = UiKit.bottomSheet(this, title, list);
    }

    /** "第 3—4 节", or "第 5 节" for a single period. */
    private static String periodRangeLabel(Course course) {
        return course.startPeriod == course.endPeriod
                ? "\u7B2C " + course.startPeriod + " \u8282"
                : "\u7B2C " + course.startPeriod + "\u2014" + course.endPeriod
                        + " \u8282";
    }

    private LinearLayout.LayoutParams stackRowParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(first ? UiTokens.SPACE_XS : 2);
        return params;
    }

    /**
     * One course in a stack's list: its name and its week rule.
     *
     * <p>Nothing else. Teacher and venue belong on the course's own page, and a
     * list that repeated them for every alternative would be wider than the sheet
     * it is in.</p>
     */
    private View stackRow(final Course course, boolean isLead,
            final android.app.Dialog[] sheet) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(UiTokens.SPACE_M), dp(UiTokens.SPACE_S),
                dp(UiTokens.SPACE_M), dp(UiTokens.SPACE_S));
        row.setBackground(UiKit.outline(this, isLead
                ? UiKit.courseTint(course.color) : Palette.SURFACE_CONTAINER,
                UiTokens.RADIUS_S, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));

        TextView name = UiKit.text(this, course.name, UiTokens.Type.BODY_LARGE,
                UiKit.courseInk(course.color), true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name);

        TextView weeks = UiKit.text(this, ScheduleUtils.weekRule(course),
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, false);
        weeks.setMaxLines(2);
        weeks.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams weeksParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        weeksParams.topMargin = dp(2);
        row.addView(weeks, weeksParams);

        row.setOnClickListener(view -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            showDetails(course);
        });
        return row;
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
        int content = dp(GRID_PAD_DP) + DAY_COUNT * dayWidthPx;
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

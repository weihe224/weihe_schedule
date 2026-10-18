package com.weihe.schedule;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.CourseConflicts;
import com.weihe.schedule.rule.CourseQuery;
import com.weihe.schedule.rule.SemesterWeeks;
import com.weihe.schedule.rule.TodayTarget;
import com.weihe.schedule.data.RestoreReport;
import com.weihe.schedule.reminder.ReminderNotifications;
import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.importer.ExcelTimetableReader;
import com.weihe.schedule.importer.TimetableImportException;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.Pickers;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * Today's courses, and the way into everything else.
 *
 * <p>The screen answers one question first -- "what do I have on this day of
 * this week?" -- and keeps the rest (import, the semester-wide grid, the bell
 * schedules, the semester start date) one tap away. Logic is delegated: the day
 * list comes from {@link CourseQuery}, conflicts from {@link CourseConflicts},
 * times from {@link CourseRepository#timeRangeOf}, and week arithmetic from
 * {@link ScheduleUtils}.</p>
 */
public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_TIMETABLE = 1001;

    /** Space between the two floating buttons. */
    private static final int FAB_GAP_DP = 10;

    /**
     * Room under the last card for the floating pair to sit over.
     *
     * <p>Both of them, stacked: the bottom margin, the overview button, the gap and
     * the "back to today" button come to
     * {@code SPACE_L + FAB_SIZE + FAB_GAP_DP + FAB_SIZE}
     * = 16 + 56 + 10 + 56 = 138dp, and this is that plus a small margin. It is
     * padding on the scrolling content, so the last card stays reachable -- no card
     * is hidden and nothing else on the screen gives up room for it.</p>
     */
    private static final int FAB_CLEARANCE_DP = 150;

    private CourseRepository repository;
    private LocalDate semesterStart;
    private int currentWeek;
    private int selectedWeek;
    private int selectedDay;

    /**
     * Which way the last day or week change moved, so the new list can slide in
     * from that side. Zero means "no direction", for the first render.
     */
    private int motionDirection;

    private LinearLayout dayStrip;
    private LinearLayout courseContainer;
    private TextView weekDates;
    private TextView weekLabel;
    private TextView weekParity;
    private TextView parityPill;
    private TextView semesterSubtitle;
    private TextView semesterChip;
    private TextView dayTitle;
    private TextView dayCountPill;
    /** The floating "back to today" button, above the overview button. */
    private android.widget.ImageButton todayFab;

    @Override
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        semesterStart = repository.getSemesterStart();
        LocalDate today = LocalDate.now();
        currentWeek = ScheduleUtils.currentWeek(semesterStart, today);
        selectedWeek = currentWeek;
        selectedDay = today.getDayOfWeek().getValue();
        // The app bar is the page surface, so the status bar takes the same colour
        // and keeps dark icons: the two read as one surface with no seam.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
        refreshAll();
        warnAboutUnsettledRestore();
        // The channel has to exist before anything is posted, and the launcher is
        // the one screen every launch goes through. Creating it again is a no-op,
        // so this costs nothing on the second and later starts.
        ReminderNotifications.ensureChannel(this);
    }

    /**
     * Tells the user when a restore could not be settled.
     *
     * <p>The repository puts the previous data back whenever it can, and does it
     * silently. The one case it cannot fix is when neither the restored data nor
     * the rollback could be confirmed -- then the only honest thing to do is say
     * so on the first screen the app opens, rather than let someone keep editing
     * a timetable whose state nobody can vouch for.</p>
     */
    private void warnAboutUnsettledRestore() {
        RestoreReport report = repository.recoveryReport();
        if (report == null || !report.needsAttention()) {
            return;
        }
        UiKit.message(this, "\u4E0A\u4E00\u6B21\u6062\u590D\u672A\u5B8C\u6210",
                report.message() + (report.detail().isEmpty() ? "" : "\n\n" + report.detail())
                        + "\n\n\u5F53\u524D\u6570\u636E\u72B6\u6001\u65E0\u6CD5\u786E\u8BA4"
                        + "\uFF0C\u8BF7\u5148\u4E0D\u8981\u7EE7\u7EED\u4FEE\u6539\u8BFE\u8868"
                        + "\uFF0C\u5E76\u4FDD\u7559\u539F\u5907\u4EFD\u6587\u4EF6\u3002");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (courseContainer != null) {
            // Another screen may have changed the timetable while this one was
            // stopped -- an import from settings, most often. The repository's
            // snapshot is only invalidated by writes through this instance, so it
            // has to be dropped or this screen keeps showing what it loaded at
            // startup: that is how the overview and the home screen ended up
            // disagreeing after an import.
            repository.reload();
            // The repository reads whichever semester is active now, which may
            // have changed on the semester screen while this one was stopped.
            semesterStart = repository.getSemesterStart();
            refreshAll();
        }
        // The timetable may have changed on any screen since the alarms were last
        // planned -- an import, an edit, a semester switch, a restored backup. The
        // app's own start is the one place that sees all of them, so it is where
        // the plan is brought up to date. This is idempotent and bounded: it
        // cancels what it registered last time and registers at most a week's
        // worth again, so resuming repeatedly cannot pile alarms up.
        ReminderScheduler.sync(this);
    }

    // ---------------------------------------------------------------- layout

    /**
     * Builds the page.
     *
     * <p>The swipe listener is an addition to the visible controls -- the arrows,
     * the spinner and the "back to this week" chip all move weeks too -- so the
     * container deliberately does not report clicks of its own, which is what
     * lint's ClickableViewAccessibility warning is about.</p>
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private View buildRoot() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Palette.SURFACE);
        frame.setOnTouchListener(weekSwipeListener());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        frame.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(createHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        // The bottom padding clears the floating pair, so the last course card can
        // always be scrolled out from under them.
        body.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L),
                dp(UiTokens.SPACE_L), dp(FAB_CLEARANCE_DP));

        body.addView(createWeekCard());
        body.addView(createDayBar());
        body.addView(createDayHeader());

        courseContainer = new LinearLayout(this);
        courseContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(courseContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        frame.addView(createFloatingActions());
        return frame;
    }

    /**
     * The floating pair: back to today, and the timetable overview.
     *
     * <p>The calendar button was once removed from here, for three reasons -- its
     * glyph was the same calendar as the brand mark above it, it sat over the
     * bottom of the course list, and it duplicated the control the date header
     * carried. It is back, because a return action belongs with the other floating
     * action rather than inside the header it is undoing, and the reasons it left
     * are each answered rather than ignored: it is one of a matched pair now
     * instead of a lone circle, the list reserves room for both of them, and the
     * header no longer carries a second copy of it.</p>
     *
     * <p>Both are plain {@link UiKit#fab} calls, so the circle, the size, the
     * colour and the shadow come from one piece of code and the only difference
     * between them is the icon. They sit in one column, right-aligned, so neither
     * can drift into the other however wide the screen is.</p>
     */
    private View createFloatingActions() {
        todayFab = UiKit.fab(this, R.drawable.ic_today,
                "\u56DE\u5230\u4ECA\u5929", view -> goToToday());
        ImageButton overview = UiKit.fab(this, R.drawable.ic_grid,
                "\u8BFE\u8868\u603B\u89C8", view -> openOverview());
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                dp(UiTokens.FAB_SIZE), dp(UiTokens.FAB_SIZE));
        overviewParams.topMargin = dp(FAB_GAP_DP);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.END);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        actions.addView(todayFab, new LinearLayout.LayoutParams(
                dp(UiTokens.FAB_SIZE), dp(UiTokens.FAB_SIZE)));
        actions.addView(overview, overviewParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        params.setMargins(0, 0, dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L));
        actions.setLayoutParams(params);
        return actions;
    }

    /**
     * Back to the current week and today's weekday.
     *
     * <p>Both, not just the day: from week 12 the button means "show me where I
     * am now", which is the week as well as the date.</p>
     */
    private void goToToday() {
        LocalDate today = LocalDate.now();
        // Worked out here rather than read from currentWeek, so the target cannot
        // depend on that field having been refreshed since the date last changed.
        int targetWeek = TodayTarget.week(semesterStart, today);
        int targetDay = TodayTarget.day(today);
        motionDirection = selectedWeek != targetWeek
                ? Integer.signum(targetWeek - selectedWeek)
                : Integer.signum(targetDay - selectedDay);
        selectedWeek = targetWeek;
        selectedDay = targetDay;
        refreshAll();
        UiKit.swapIn(courseContainer, motionDirection);
    }

    /**
     * Whether the screen is already showing this week and today.
     *
     * <p>Note the two halves: the week <em>and</em> the weekday. A screen showing
     * the current week but a different day is not "today", and it is exactly the
     * state the return button is for.</p>
     */
    private boolean isShowingToday() {
        return TodayTarget.isShowing(selectedWeek, selectedDay, semesterStart,
                LocalDate.now());
    }

    /**
     * Keeps the "get back" controls in step with what is on screen.
     *
     * <p>Called from every path that can change the selected day or week, which is
     * the point of having it in one place: when it lived inline in
     * {@link #refreshAll()} the day-change path silently skipped it.</p>
     */
    private void syncReturnControls() {
        if (todayFab != null) {
            // Hidden while the screen already shows today: a control that would do
            // nothing is noise, and the date header is already reading "today".
            // The container is bottom-aligned, so hiding this one does not move the
            // overview button below it.
            todayFab.setVisibility(isShowingToday() ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * The top app bar: the app's mark, the page title, the semester it is
     * showing, and settings.
     *
     * <p>This used to be the one dark band in the app, with the week selector as a
     * second dark block under it. Three things came out of that: the lightest
     * screen in the app opened with its heaviest element, the two dark blocks read
     * as two competing headers, and the status bar had to be tinted to match or a
     * seam showed above it.</p>
     *
     * <p>Now the bar is the page surface. The title carries the hierarchy, the
     * semester row is a tinted panel, and the status bar is simply painted the
     * same colour as the bar with dark icons on it.</p>
     */
    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Palette.HEADER_SURFACE);
        header.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_S));

        LinearLayout titleRow = UiKit.appBarRow(this);
        titleRow.addView(createBrandMark());

        TextView title = UiKit.appBarTitle(this, "\u6211\u7684\u8BFE\u8868");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(UiTokens.SPACE_M);
        titleRow.addView(title, titleParams);

        // The search entry sits beside the settings icon: one tap from the home
        // screen, and no new row, chip or bar for the timetable to make room for.
        titleRow.addView(headerAction(R.drawable.ic_search,
                "\u641C\u7D22\u8BFE\u7A0B",
                view -> startActivity(new Intent(this, CourseSearchActivity.class))));
        titleRow.addView(headerAction(R.drawable.ic_settings, "\u8BBE\u7F6E",
                view -> startActivity(new Intent(this, SettingsActivity.class))));
        header.addView(titleRow);

        header.addView(createSemesterControl(),
                UiKit.stack(this, UiTokens.SPACE_M));
        return header;
    }

    /**
     * The app's own mark: the launcher icon's calendar reduced to a 34dp tile.
     *
     * <p>One element, and the only decoration in the app bar. Without it the bar
     * is a title floating in white with no sign of which app this is, and with
     * anything more it competes with the title.</p>
     */
    private View createBrandMark() {
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_today);
        mark.setColorFilter(Palette.ON_PRIMARY);
        mark.setBackground(UiKit.fill(this, Palette.PRIMARY, UiTokens.RADIUS_M));
        int size = dp(34);
        mark.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        int padding = dp(UiTokens.SPACE_S);
        mark.setPadding(padding, padding, padding, padding);
        mark.setContentDescription("\u62FE\u8BFE");
        return mark;
    }

    /**
     * The semester, as one wide control under the title.
     *
     * <p>Switching term is the second thing anyone does on this screen, so it is
     * a full-width row with its own icon and a chevron, rather than a small chip
     * tucked beside the subtitle. A chevron is the platform's way of saying "this
     * leads somewhere", which the old chip did not say at all.</p>
     *
     * <p>The row is a brand tint rather than a translucent white: on a dark bar
     * "lighter than the background" was the way to lift a control off it, and on a
     * light bar the same trick draws nothing at all.</p>
     */
    private View createSemesterControl() {
        LinearLayout row = new UiKit.PressFeedback(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(UiTokens.SPACE_M), dp(UiTokens.SPACE_S),
                dp(UiTokens.SPACE_S), dp(UiTokens.SPACE_S));
        row.setMinimumHeight(dp(UiTokens.TOUCH_TARGET));
        UiKit.setRoundedBackground(row, Palette.HEADER_PANEL, UiTokens.RADIUS_M,
                Color.TRANSPARENT, 0f);
        row.setContentDescription("\u5207\u6362\u5B66\u671F");

        // A graduation cap rather than a calendar: the calendar glyph is on the
        // brand mark beside the title and on the button that jumps back to today,
        // and a third one would make three different actions look alike.
        row.addView(UiKit.inlineIcon(this, R.drawable.ic_school,
                Palette.ON_HEADER_PANEL));

        semesterSubtitle = UiKit.text(this, "", UiTokens.Type.BODY_MEDIUM,
                Palette.ON_HEADER_PANEL, Typeface.NORMAL);
        semesterSubtitle.setMaxLines(1);
        semesterSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(UiTokens.SPACE_S + 2);
        row.addView(semesterSubtitle, textParams);

        // The word says what tapping does; the chip beside it stays for the one
        // case where the app is showing a term the calendar is not in, because
        // then "switch" is not what the user needs to read -- "get back" is.
        semesterChip = UiKit.pill(this, "", UiTokens.Type.LABEL_MEDIUM,
                Palette.PRIMARY, Palette.ON_PRIMARY, true, UiTokens.RADIUS_FULL);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.rightMargin = dp(UiTokens.SPACE_S);
        row.addView(semesterChip, chipParams);

        row.addView(UiKit.inlineIcon(this, R.drawable.ic_chevron_right,
                Palette.ON_HEADER_PANEL));

        UiKit.ripple(row, UiTokens.RADIUS_M, false);
        UiKit.pressable(row, view -> openSemesters());
        return row;
    }

    private View headerAction(int icon, String description,
            View.OnClickListener listener) {
        ImageButton button = UiKit.headerIcon(this, icon, description, listener);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                button.getLayoutParams();
        params.leftMargin = dp(UiTokens.SPACE_S);
        return button;
    }

    /**
     * The card's headline: the week number, with a chevron, and it opens a sheet.
     *
     * <p>Replaces a {@code Spinner}. The spinner worked, but a native dropdown was
     * the last piece of platform chrome on this screen: a list of twenty rows with
     * no dates, no marker for the week you are in, and a {@code â¾} glyph drawn
     * from the font. Tapping this opens {@link #showWeekPicker} instead.</p>
     */
    private View createWeekControl() {
        LinearLayout row = new UiKit.PressFeedback(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setContentDescription("\u9009\u62E9\u5468\u6B21");
        UiKit.ripple(row, UiTokens.RADIUS_S, false);

        weekLabel = UiKit.text(this, "", UiTokens.Type.HEADLINE_SMALL,
                Palette.ON_SURFACE, Typeface.BOLD);
        row.addView(weekLabel);
        weekParity = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        LinearLayout.LayoutParams parityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        parityParams.leftMargin = dp(UiTokens.SPACE_XS);
        row.addView(weekParity, parityParams);
        android.widget.ImageView chevron = UiKit.inlineIcon(this,
                R.drawable.ic_chevron_right, Palette.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                dp(UiTokens.ICON_SIZE - 2), dp(UiTokens.ICON_SIZE - 2));
        chevronParams.leftMargin = dp(UiTokens.SPACE_XS + 2);
        row.addView(chevron, chevronParams);

        UiKit.pressable(row, view -> showWeekPicker());
        return row;
    }

    /**
     * Every week of the term, as a grid in a bottom sheet.
     *
     * <p>A grid rather than a list because the choice is spatial: a term is a
     * sequence, and seeing all of it at once is how you find "the week before the
     * exam". Each cell carries its dates, because week numbers alone do not tell
     * you where you are, and the week the calendar is in is marked so that "åå°
     * æ¬å¨" is visible without reading.</p>
     */
    private void showWeekPicker() {
        int columns = ScheduleUtils.weekPickerColumns(ScheduleUtils.SEMESTER_WEEKS);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_S));

        // The dialog does not exist yet while the cells are built, so it is
        // handed to them through a holder that is filled in before any of them
        // can be tapped.
        final android.app.Dialog[] sheet = new android.app.Dialog[1];

        LinearLayout line = null;
        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            if ((week - 1) % columns == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lineParams.topMargin = week == 1 ? 0 : dp(UiTokens.SPACE_S);
                grid.addView(line, lineParams);
            }
            line.addView(weekCell(week, sheet), weightedCell());
        }

        sheet[0] = UiKit.bottomSheet(this, "\u9009\u62E9\u5468\u6B21", grid);
    }

    /** Layout params for one cell of the week grid. */
    private LinearLayout.LayoutParams weightedCell() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = dp(UiTokens.SPACE_XS + 2);
        return params;
    }

    /**
     * One week in the picker: the number, its dates, and where it stands.
     *
     * <p>Picking a week closes the sheet. Making the user tap the backdrop after
     * they have already decided is a step that exists only because the code forgot
     * to take it, and the refresh runs after the dismissal so the screen behind is
     * already up to date.</p>
     */
    private View weekCell(final int week, final android.app.Dialog[] sheet) {
        boolean isSelected = week == selectedWeek;
        boolean isCurrent = week == currentWeek;

        LinearLayout cell = new UiKit.PressFeedback(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(UiTokens.SPACE_XS), dp(UiTokens.SPACE_S),
                dp(UiTokens.SPACE_XS), dp(UiTokens.SPACE_S));
        cell.setMinimumHeight(dp(UiTokens.TOUCH_TARGET + UiTokens.SPACE_S));
        cell.setContentDescription("\u7B2C " + week + " \u5468\uFF0C"
                + ScheduleUtils.weekRange(semesterStart, week)
                + (isCurrent ? "\uFF0C\u672C\u5468" : ""));

        if (isSelected) {
            UiKit.setRoundedBackground(cell, Palette.PRIMARY, UiTokens.RADIUS_M,
                    Color.TRANSPARENT, 0f);
        } else if (isCurrent) {
            UiKit.setRoundedBackground(cell, Palette.ACCENT_CONTAINER,
                    UiTokens.RADIUS_M, Palette.ACCENT, UiTokens.STROKE_HAIRLINE);
        } else {
            UiKit.setRoundedBackground(cell, Palette.SURFACE_CONTAINER_LOW,
                    UiTokens.RADIUS_M, Color.TRANSPARENT, 0f);
        }

        TextView number = UiKit.text(this, "\u7B2C" + week + "\u5468",
                UiTokens.Type.BODY_MEDIUM,
                isSelected ? Palette.ON_PRIMARY
                        : isCurrent ? Palette.ON_ACCENT_CONTAINER
                                : Palette.ON_SURFACE,
                isSelected || isCurrent ? Typeface.BOLD : Typeface.NORMAL);
        number.setGravity(Gravity.CENTER);
        cell.addView(number);

        // The dates are what make the grid readable; on the small tile they are
        // the quiet second line, on the filled tile they step back to the
        // container tone so the number stays the loudest thing.
        TextView dates = UiKit.text(this,
                ScheduleUtils.weekRange(semesterStart, week),
                UiTokens.Type.LABEL_SMALL,
                isSelected ? Palette.PRIMARY_CONTAINER
                        : isCurrent ? Palette.ON_ACCENT_CONTAINER
                                : Palette.ON_SURFACE_VARIANT,
                Typeface.NORMAL);
        dates.setGravity(Gravity.CENTER);
        dates.setMaxLines(1);
        LinearLayout.LayoutParams datesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        datesParams.topMargin = dp(2);
        cell.addView(dates, datesParams);

        UiKit.ripple(cell, UiTokens.RADIUS_M, isSelected);
        UiKit.pressable(cell, view -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            selectWeek(week);
        });
        return cell;
    }

    /** Switches week, keeping the visible weekday where that makes sense. */
    private void selectWeek(int week) {
        if (week == selectedWeek) {
            return;
        }
        motionDirection = week > selectedWeek ? 1 : -1;
        selectedWeek = week;
        if (week == currentWeek) {
            selectedDay = LocalDate.now().getDayOfWeek().getValue();
        }
        refreshAll();
        UiKit.swapIn(courseContainer, motionDirection);
    }

    /**
     * The week on screen, as one compact row.
     *
     * <p>It used to be a card of four elements: the week and its chevron, a parity
     * pill, a divider, and a row carrying today's full date beside the arrows. Two
     * facts came out of it twice over -- the parity was in the date line and again
     * in the pill, and today's date appeared here, in the day strip, and once more
     * in the day header -- and a card plus a divider was spent saying them. Each
     * fact is now in one place: the week and its parity in the headline, the dates
     * under it, and the arrows on the same line as the week they move.</p>
     *
     * <p>The slot on the right carries the week's state and never both halves of
     * it: "this is the week the calendar is in", or the way back to it. The way
     * back to <em>today</em> is not here -- it belongs beside the date it returns
     * to, which is the day header.</p>
     */
    private View createWeekCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M));
        UiKit.styleAsCard(this, card, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(createWeekControl());
        weekDates = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        LinearLayout.LayoutParams datesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        datesParams.topMargin = dp(UiTokens.SPACE_XS);
        block.addView(weekDates, datesParams);
        card.addView(block, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        parityPill = UiKit.pill(this, "\u672C\u5468", UiTokens.Type.LABEL_MEDIUM,
                Palette.ACCENT_CONTAINER, Palette.ON_ACCENT_CONTAINER, true,
                UiTokens.RADIUS_FULL);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.leftMargin = dp(UiTokens.SPACE_S);
        card.addView(parityPill, pillParams);

        // There was a "back to this week" chip here, between the week label and the
        // steppers. It is gone: the card now reads as one row -- which week, its
        // dates, then the two arrows that move through them -- instead of three
        // controls competing for the same line. The way back to the current week is
        // the floating calendar button in the corner, which returns to this week and
        // today's weekday together; the old chip deliberately kept the weekday where
        // it was, and that distinction is the one thing this removal gives up.
        card.addView(stepButton(R.drawable.ic_chevron_left, "\u4E0A\u4E00\u5468",
                view -> stepWeek(-1)));
        card.addView(stepButton(R.drawable.ic_chevron_right, "\u4E0B\u4E00\u5468",
                view -> stepWeek(1)));
        return card;
    }

    /** A round icon button for stepping one week, matching the chip height. */
    private View stepButton(int icon, String description,
            View.OnClickListener listener) {
        ImageButton button = UiKit.iconButton(this, icon, description,
                UiTokens.CHIP_HEIGHT, Palette.SURFACE_CONTAINER_LOW,
                Palette.ON_SURFACE, Palette.OUTLINE_VARIANT,
                UiTokens.RADIUS_S + 2, listener);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                button.getLayoutParams();
        params.leftMargin = dp(UiTokens.SPACE_S);
        return button;
    }

    private View createDayBar() {
        LinearLayout dayBar = new LinearLayout(this);
        dayBar.setOrientation(LinearLayout.HORIZONTAL);
        dayBar.setPadding(dp(UiTokens.SPACE_XS), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_XS), dp(UiTokens.SPACE_XS));
        UiKit.styleAsCard(this, dayBar, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);
        dayStrip = new LinearLayout(this);
        dayStrip.setOrientation(LinearLayout.HORIZONTAL);
        dayBar.addView(dayStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams dayBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dayBarParams.topMargin = dp(UiTokens.SPACE_M);
        dayBar.setLayoutParams(dayBarParams);
        return dayBar;
    }

    private View createDayHeader() {
        LinearLayout dayHeader = new LinearLayout(this);
        dayHeader.setOrientation(LinearLayout.HORIZONTAL);
        dayHeader.setGravity(Gravity.CENTER_VERTICAL);
        dayTitle = UiKit.text(this, "", UiTokens.Type.TITLE_MEDIUM,
                Palette.ON_SURFACE, Typeface.BOLD);
        dayTitle.setMaxLines(1);
        dayTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dayHeader.addView(dayTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dayCountPill = UiKit.pill(this, "", UiTokens.Type.LABEL_MEDIUM,
                Palette.SURFACE_CONTAINER_HIGH, Palette.ON_SURFACE_VARIANT, true,
                UiTokens.RADIUS_FULL);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.leftMargin = dp(UiTokens.SPACE_S);
        dayHeader.addView(dayCountPill, pillParams);
        // The way back to today is a floating button now, not a chip here. It used
        // to sit beside the date it returns to, which read well but crowded the
        // header and gave the same action two homes on one screen.
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(UiTokens.SPACE_L);
        headerParams.bottomMargin = dp(UiTokens.SPACE_S);
        dayHeader.setLayoutParams(headerParams);
        return dayHeader;
    }

    // ----------------------------------------------------------- interaction

    /**
     * Left/right swipe over the day list moves one week at a time.
     *
     * <p>Suppressed for the same reason as in {@link #buildRoot()}: the gesture
     * replaces no click, and everything it does is also reachable from a
     * button.</p>
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private View.OnTouchListener weekSwipeListener() {
        final GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent from, MotionEvent to,
                            float velocityX, float velocityY) {
                        if (from == null || to == null) {
                            return false;
                        }
                        float travel = to.getX() - from.getX();
                        if (Math.abs(travel) < Math.abs(to.getY() - from.getY())
                                || Math.abs(travel) < dp(60)
                                || Math.abs(velocityX) < 320f) {
                            return false;
                        }
                        stepWeek(travel < 0 ? 1 : -1);
                        return true;
                    }
                });
        return (view, event) -> detector.onTouchEvent(event);
    }

    private void stepWeek(int delta) {
        int next = Math.max(1, Math.min(ScheduleUtils.SEMESTER_WEEKS,
                selectedWeek + delta));
        if (next == selectedWeek) {
            return;
        }
        motionDirection = delta;
        selectedWeek = next;
        if (selectedWeek == currentWeek) {
            selectedDay = LocalDate.now().getDayOfWeek().getValue();
        }
        refreshAll();
        UiKit.swapIn(courseContainer, motionDirection);
    }

    private void openOverview() {
        Intent intent = new Intent(this, WeekGridActivity.class);
        intent.putExtra(WeekGridActivity.EXTRA_WEEK, selectedWeek);
        startActivity(intent);
    }

    /** The stored term whose dates contain {@code date}, or null when none does. */
    private Semester semesterHolding(LocalDate date) {
        for (Semester semester : repository.getSemesters()) {
            if (!date.isBefore(semester.start()) && !date.isAfter(semester.lastDay())) {
                return semester;
            }
        }
        return null;
    }

    /** Makes {@code semester} the term every screen shows. */
    private void switchTo(Semester semester) {
        repository.setActiveSemester(semester);
        // Every label on this page is derived from the active term, so the whole
        // page is rebuilt rather than patched.
        refreshAll();
        Toast.makeText(this, "\u5DF2\u5207\u6362\u5230 " + semester.label(),
                Toast.LENGTH_SHORT).show();
    }
    /** Picks which term every screen shows; see {@link SemesterListActivity}. */
    private void openSemesters() {
        startActivity(new Intent(this, SemesterListActivity.class));
    }

    // -------------------------------------------------------------- rendering

    private void refreshAll() {
        Semester active = repository.getActiveSemester();
        semesterStart = repository.getSemesterStart();
        LocalDate today = LocalDate.now();
        currentWeek = ScheduleUtils.currentWeek(semesterStart, today);
        selectedWeek = Math.max(1, Math.min(ScheduleUtils.SEMESTER_WEEKS, selectedWeek));
        selectedDay = Math.max(1, Math.min(7, selectedDay));

        // One slot, one fact. On the current week it says so; otherwise the chip
        // beside it offers the way back. Showing both would be two names for the
        // same thing, which is what the old card did.
        boolean showingCurrentWeek = selectedWeek == currentWeek;
        parityPill.setText("\u672C\u5468");
        parityPill.setVisibility(showingCurrentWeek ? View.VISIBLE : View.GONE);
        // The accent is the one highlighted tile in the app's own icon, which is
        // why "this is the week you are in" is the only thing allowed to use it.
        parityPill.setTextColor(Palette.ON_ACCENT_CONTAINER);
        parityPill.setBackground(UiKit.fill(this, Palette.ACCENT_CONTAINER,
                UiTokens.RADIUS_FULL));

        // The term's own facts belong on the term's row: whether the calendar is
        // inside this term at all, and the way to the term it is inside. Both were
        // scattered before -- "finished" sat in the week card, where it read as a
        // remark about the week on screen.
        boolean inSession = !today.isBefore(active.start())
                && !today.isAfter(active.lastDay());
        // Only a term the calendar has already left has finished. A term that has
        // not begun yet keeps its start date, which is the useful fact about it --
        // calling that one "finished" would be plainly wrong.
        boolean finished = today.isAfter(active.lastDay());
        semesterSubtitle.setText(active.label() + " \u00B7 "
                + (finished ? "\u5DF2\u7ED3\u675F"
                        : ScheduleUtils.formatDate(semesterStart) + " \u5F00\u5B66"));
        if (semesterChip != null) {
            // The chip says "view the current term", so that is what it does. It
            // used to be a label inside a row whose only action was opening the term
            // list: the one control promising to take you back to the term the
            // calendar is in instead asked you to go and find it. When no stored
            // term holds today there is nothing to go back to, and it stays hidden
            // rather than promising something it cannot do.
            Semester currentTerm = semesterHolding(today);
            // Named for the term the calendar is in, not for the one on screen: the
            // list marks the term being viewed, and two different things called
            // "current" is how a badge and a button came to look like the same
            // control saying two things.
            semesterChip.setText("\u56DE\u5230\u672C\u5B66\u671F");
            semesterChip.setVisibility(currentTerm != null && !inSession
                    ? View.VISIBLE : View.GONE);
            if (currentTerm != null) {
                UiKit.pressable(semesterChip, view -> switchTo(currentTerm));
            }
        }
        syncReturnControls();

        renderWeekPicker();
        renderDayStrip();
        renderCourses();
    }

    /** Keeps the week headline in step with the selection. */
    private void renderWeekPicker() {
        if (weekLabel == null) {
            return;
        }
        weekLabel.setText("\u7B2C" + selectedWeek + "\u5468");
        // The dates are the dates. The parity is a property of the week, so it sits
        // with the week number instead of being repeated on this line.
        weekDates.setText(ScheduleUtils.weekRange(semesterStart, selectedWeek));
        weekParity.setText("\u00B7 " + ScheduleUtils.parityName(selectedWeek));
        weekLabel.setContentDescription("\u9009\u62E9\u5468\u6B21\uFF0C\u5F53\u524D\u7B2C"
                + selectedWeek + "\u5468");
    }

    /**
     * The seven days of the week on screen, Monday first.
     *
     * <p>Three states, three treatments, and the hierarchy inside a tile is the
     * same in all of them: the number is what people look for, so it is the larger
     * line, and the weekday above it is the label that says which column this is.
     * An earlier version drew both at 10sp, which made a row of seven tiles that
     * had to be read rather than glanced at.</p>
     *
     * <p>The large line is the day of the month, not {@code 10/12}. Seven columns
     * on a 320dp phone leave about 44dp each, and at a 130% font scale
     * {@code 10/12} is already 84px against 84px of space: it clipped the last
     * digit. Auto-sizing cannot rescue it either, because at a 200% scale even the
     * smallest size on the type scale is too wide, and a control whose whole job is
     * showing dates must not have a size at which it stops doing that. One or two
     * digits fit at every scale, and the month is not lost: the week card directly
     * above reads {@code 10/5 - 10/11}, and the day header reads
     * {@code 周四 · 今天 · 10/8}.</p>
     */
    private void renderDayStrip() {
        dayStrip.removeAllViews();
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= 7; day++) {
            boolean selected = day == selectedDay;
            LocalDate dateValue = ScheduleUtils.dateOf(semesterStart, selectedWeek, day);
            boolean isToday = dateValue.equals(today);
            LinearLayout item = new UiKit.PressFeedback(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(UiTokens.SPACE_S), 0, dp(UiTokens.SPACE_S));
            // A minimum, not a fixed height. Every cell carries the same two lines
            // so they stay the same height as each other, and at a large font scale
            // the strip grows instead of cutting the numbers in half.
            item.setMinimumHeight(dp(UiTokens.DAY_CELL_HEIGHT));
            item.setContentDescription(ScheduleUtils.dayName(day)
                    + ScheduleUtils.formatFullDate(dateValue)
                    + (isToday ? "\uFF0C\u4ECA\u5929" : ""));
            // Three states, three treatments. The selected day is the loudest
            // thing in the strip: a filled brand tile with white text. Today is
            // marked but not louder than the selection, and the rest stay quiet.
            if (selected) {
                item.setBackground(UiKit.fill(this, Palette.PRIMARY,
                        UiTokens.RADIUS_M));
            } else if (isToday) {
                item.setBackground(UiKit.outline(this, Palette.ACCENT_CONTAINER,
                        UiTokens.RADIUS_M, Palette.ACCENT,
                        UiTokens.STROKE_HAIRLINE));
            } else {
                item.setBackgroundColor(Color.TRANSPARENT);
            }
            TextView name = UiKit.text(this, ScheduleUtils.dayName(day),
                    UiTokens.Type.LABEL_SMALL,
                    selected ? Palette.PRIMARY_CONTAINER
                            : isToday ? Palette.ON_ACCENT_CONTAINER
                                    : Palette.ON_SURFACE_VARIANT,
                    selected || isToday);
            TextView number = UiKit.text(this, String.valueOf(dateValue.getDayOfMonth()),
                    UiTokens.Type.BODY_MEDIUM,
                    selected ? Palette.ON_PRIMARY
                            : isToday ? Palette.ON_ACCENT_CONTAINER
                                    : Palette.ON_SURFACE,
                    true);
            // On the filled tile the weekday steps back to the container tone
            // rather than competing with the number below it.
            addDayLine(item, name, 9f, UiTokens.Type.LABEL_SMALL);
            addDayLine(item, number, 10f, UiTokens.Type.BODY_MEDIUM);
            final int chosenDay = day;
            UiKit.ripple(item, UiTokens.RADIUS_M, selected);
            UiKit.pressable(item, view -> selectDay(chosenDay));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.rightMargin = day < 7 ? dp(2) : 0;
            dayStrip.addView(item, params);
        }
    }

    /** Adds one centred, non-clipping line to a day tile. */
    private void addDayLine(LinearLayout cell, TextView line, float minSizeSp,
            float maxSizeSp) {
        line.setGravity(Gravity.CENTER);
        line.setSingleLine(true);
        line.setAutoSizeTextTypeUniformWithConfiguration(Math.round(minSizeSp),
                Math.round(maxSizeSp), 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        cell.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Selects a day and lets the course list arrive from the side it moved to. */
    private void selectDay(int day) {
        if (day == selectedDay) {
            return;
        }
        motionDirection = day > selectedDay ? 1 : -1;
        selectedDay = day;
        renderDayStrip();
        renderCourses();
        // The "back to today" button answers "is this screen already showing
        // today?", and the day is half of that question. Without this the button
        // stayed hidden after tapping any other day of the current week, so the
        // one case it exists for -- you moved to Wednesday and want Thursday
        // back -- had no way back at all.
        syncReturnControls();
        UiKit.swapIn(courseContainer, motionDirection);
    }

    private void renderCourses() {
        LocalDate date = ScheduleUtils.dateOf(semesterStart, selectedWeek, selectedDay);
        boolean isToday = date.equals(LocalDate.now());
        // Today leads with the fact that it is today, because that is the state the
        // user needs to recognise; any other date leads with its own name, because
        // the state is "not today" and the name is what was asked for.
        dayTitle.setText(isToday
                ? "\u4ECA\u5929 \u00B7 " + ScheduleUtils.formatDate(date) + " "
                        + ScheduleUtils.dayName(selectedDay)
                : ScheduleUtils.dayName(selectedDay) + " \u00B7 "
                        + ScheduleUtils.formatDate(date));
        courseContainer.removeAllViews();

        // Read once: the same list answers "what is on today" and "does this term
        // have anything at all", and the empty state needs both.
        List<Course> all = repository.getCourses();
        List<Course> visible = CourseQuery.onDay(all, selectedWeek, selectedDay);
        dayCountPill.setText(visible.isEmpty() ? "\u65E0\u8BFE"
                : visible.size() + " \u95E8\u8BFE");

        if (visible.isEmpty()) {
            View empty = createEmptyState(all.isEmpty());
            courseContainer.addView(empty);
            UiKit.swapIn(empty, motionDirection);
            return;
        }
        Set<Long> conflicting = CourseConflicts.conflictingIds(visible);
        int index = 0;
        for (Course course : visible) {
            View card = createCourseCard(course, conflicting.contains(course.id));
            courseContainer.addView(card);
            // A short stagger so the list arrives as one gesture rather than
            // snapping in, capped so a long day never feels slow.
            UiKit.enter(card, Math.min(index, 5) * 35L);
            index++;
        }
    }

    /**
     * One course, as a card.
     *
     * <p>Three columns, in the order the eye wants them: a colour rail that
     * identifies the course, a tinted tile holding the periods and their clock
     * times, and the content. The name is the largest thing on the card, the
     * venue and teacher are one quiet block under it, and the week rule is a small
     * pill at the bottom, so the card can be read top-down in one pass instead of
     * being scanned for the part you wanted.</p>
     *
     * <p>The vertical hairline that used to separate the time column from the
     * content is gone. It divided two things that the tint already separates, and
     * a full-height line down every card is a lot of ink for a boundary nobody
     * needed drawn.</p>
     */
    private View createCourseCard(Course course, boolean hasConflict) {
        LinearLayout card = new UiKit.PressFeedback(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        // The card surface carries a wash of the course colour, so a course is
        // recognisable by its whole card and not only by the rail on its edge.
        UiKit.styleAsCourseCard(this, card, UiTokens.RADIUS_L,
                UiTokens.ELEVATION_LOW, course.color);
        card.setMinimumHeight(dp(88));

        // The rail is the course's own colour at nearly full strength now that it
        // sits on a wash of the same colour; it reads as the edge of the course
        // rather than as a stripe applied to a white card.
        View rail = new View(this);
        rail.setBackground(UiKit.fill(this, UiKit.courseRail(course.color), 2));
        card.addView(rail, new LinearLayout.LayoutParams(
                dp(UiTokens.COURSE_RAIL_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        card.addView(createTimeBlock(course));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(UiTokens.SPACE_M), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M));
        card.addView(content, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = UiKit.text(this, course.name, UiTokens.Type.TITLE_MEDIUM,
                Palette.ON_SURFACE, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (hasConflict) {
            titleRow.addView(createConflictBadge());
        }
        content.addView(titleRow);

        // Venue and teacher are one secondary block, each line led by a small
        // icon. The icons do the separating, so no bullets or middle dots are
        // needed and the two facts stay scannable on their own.
        if (!course.location.isEmpty()) {
            content.addView(createMetaLine(R.drawable.ic_location, course.location));
        }
        if (!course.teacher.isEmpty()) {
            content.addView(createMetaLine(R.drawable.ic_person, course.teacher));
        }

        String rule = ScheduleUtils.weekRule(course);
        BellSchedule schedule = repository.scheduleFor(course);
        String tag = rule;
        // Named only when it is not what the user considers normal. That used to be
        // read off the standard schedule's name; it is now read off the stored
        // choice, so moving the default to another building changes which rows stay
        // unlabelled -- which is what the label is for.
        String defaultId = repository.getDefaultScheduleId();
        boolean isDefault = schedule.id() != null
                && schedule.id().equals(defaultId);
        if (!isDefault) {
            tag += " \u00B7 " + schedule.name();
        }
        content.addView(createTag(tag, course.color));

        UiKit.ripple(card, UiTokens.RADIUS_L, false);
        UiKit.pressable(card, view -> showDetails(course));
        card.setOnLongClickListener(view -> {
            showDeleteDialog(course);
            return true;
        });
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(UiTokens.SPACE_M);
        card.setLayoutParams(cardParams);
        return card;
    }

    /** A secondary fact: a small icon, then the text, in the muted role. */
    private View createMetaLine(int iconRes, String value) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(UiKit.inlineIcon(this, iconRes, Palette.OUTLINE));
        TextView text = UiKit.text(this, value, UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(UiTokens.SPACE_S);
        line.addView(text, textParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_XS + 2);
        line.setLayoutParams(params);
        return line;
    }

    /** The week rule as a low-saturation pill in the course's own colour. */
    private View createTag(String text, int courseColor) {
        TextView tag = UiKit.text(this, text, UiTokens.Type.LABEL_SMALL,
                UiKit.courseInk(courseColor), Typeface.BOLD);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(UiTokens.SPACE_S + 1), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_S + 1), dp(UiTokens.SPACE_XS));
        UiKit.setRoundedBackground(tag, UiKit.courseTint(courseColor),
                UiTokens.RADIUS_XS + 2, Color.TRANSPARENT, 0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_S);
        tag.setLayoutParams(params);
        return tag;
    }

    /**
     * Periods on top, the clock times from the course's own schedule below, on a
     * tinted tile rather than in a column behind a divider.
     *
     * <p>When the schedule's time for those periods has never been confirmed,
     * the block says so instead of printing a number nobody can vouch for. The
     * app shipped placeholder afternoon times once, and a wrong time on screen
     * is worse than an admitted gap: it sends someone to the wrong place at the
     * wrong hour.</p>
     */
    private View createTimeBlock(Course course) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER);
        block.setPadding(dp(UiTokens.SPACE_XS), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_XS), dp(UiTokens.SPACE_M));
        // The tile used to be an opaque grey. On a card that now carries the
        // course's own wash, a grey tile reads as a hole punched in a coloured
        // card, so it is tinted from the same colour instead -- slightly deeper
        // than the card, which is what separates it without introducing a second
        // hue into the card.
        block.setBackground(UiKit.fill(this, UiKit.courseTint(course.color),
                UiTokens.RADIUS_M));

        TextView periods = UiKit.text(this,
                course.startPeriod + "-" + course.endPeriod + "\u8282",
                UiTokens.Type.LABEL_LARGE, UiKit.courseInk(course.color),
                Typeface.BOLD);
        periods.setGravity(Gravity.CENTER);
        periods.setSingleLine(true);
        block.addView(periods);

        String[] clock = clockOf(course).split(" - ");
        if (clock.length > 0) {
            block.addView(timeLine(clock[0], dp(UiTokens.SPACE_XS + 2)));
        }
        if (clock.length > 1) {
            block.addView(timeLine(clock[1], dp(2)));
        }
        // A minimum width rather than a fixed one. A fixed 56dp column starts the
        // content at the same x on every card, which is worth having, but it also
        // clips "1-2节" in half at a large font scale. Asking for 56dp and allowing
        // more keeps the alignment on every card that fits and lets the rare wide
        // one push the content instead of hiding the period.
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(UiTokens.SPACE_M);
        params.topMargin = dp(UiTokens.SPACE_S);
        params.bottomMargin = dp(UiTokens.SPACE_S);
        block.setMinimumWidth(dp(56));
        block.setLayoutParams(params);
        return block;
    }

    /** The stored span when there is one, otherwise resolved on the spot. */
    private String clockOf(Course course) {
        return course.timeRange == null || course.timeRange.isEmpty()
                ? repository.timeRangeOf(course) : course.timeRange;
    }

    private TextView timeLine(String value, int topMarginPx) {
        TextView line = UiKit.text(this, value, UiTokens.Type.LABEL_SMALL,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        line.setGravity(Gravity.CENTER);
        line.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        line.setLayoutParams(params);
        return line;
    }

    /** Small red pill marking a course that shares a slot with another one. */
    private TextView createConflictBadge() {
        TextView badge = UiKit.badge(this, "\u51B2\u7A81", Palette.ERROR_CONTAINER,
                Palette.ON_ERROR_CONTAINER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(UiTokens.SPACE_S);
        badge.setLayoutParams(params);
        return badge;
    }

    /**
     * Nothing to show for the selected day.
     *
     * <p>Two different situations arrive here as the same empty list: a term with
     * courses on other days, and a term with no courses at all. "Nothing on today"
     * told to someone who has never imported anything describes the wrong problem
     * and points at the wrong next step, so the two say different things and offer
     * different first moves.</p>
     */
    private View createEmptyState(boolean termIsEmpty) {
        if (termIsEmpty) {
            return UiKit.emptyState(this, R.drawable.ic_add,
                    "\u8FD9\u4E2A\u5B66\u671F\u8FD8\u6CA1\u6709\u8BFE\u7A0B",
                    "\u5728\u8BBE\u7F6E\u91CC\u5BFC\u5165\u8BFE\u8868\uFF0C"
                            + "\u6216\u5230\u8BFE\u8868\u603B\u89C8\u91CC\u70B9 + \u6DFB\u52A0");
        }
        return UiKit.emptyState(this, R.drawable.ic_today,
                "\u8FD9\u5929\u6CA1\u6709\u5B89\u6392",
                "\u5230\u8BFE\u8868\u603B\u89C8\u91CC\u70B9\u53F3\u4E0B\u89D2 + \u6DFB\u52A0"
                        + "\uFF0C\u6216\u5728\u8BBE\u7F6E\u91CC\u6279\u91CF\u5BFC\u5165");
    }

    // ------------------------------------------------------------------ import

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
        Set<String> names = new HashSet<>();
        for (Course course : courses) {
            names.add(course.name);
        }
        // The term the file lands in, and the date its week 1 already starts on.
        // The file's week 1 means the term's week 1, so the only choice left is
        // which calendar Monday that is.
        Semester target = repository.getActiveSemester();
        LocalDate storedStart = repository.getSemesterStart();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(4), dp(22), 0);
        String summary = "\u8BC6\u522B\u5230 " + names.size() + " \u95E8\u8BFE\u7A0B\uFF0C"
                + "\u5171 " + courses.size() + " \u6761\u5B89\u6392";
        content.addView(UiKit.title(this, summary));
        TextView warning = UiKit.caption(this,
                "\u5BFC\u5165\u5C06\u8986\u76D6\u5F53\u524D\u8BFE\u8868");
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        warningParams.topMargin = dp(UiTokens.SPACE_XS + 1);
        content.addView(warning, warningParams);

        TextView targetLine = UiKit.caption(this,
                "\u5BFC\u5165\u5230\uFF1A" + target.label()
                        + " \u00B7 \u6587\u4EF6\u7B2C 1 \u5468\u5BF9\u5E94"
                        + "\u672C\u5B66\u671F\u7B2C 1 \u5468");
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        targetParams.topMargin = dp(UiTokens.SPACE_M);
        content.addView(targetLine, targetParams);

        TextView label = UiKit.text(this, "\u672C\u5B66\u671F\u5F00\u5B66\u65E5\u671F",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(UiTokens.SPACE_M);
        content.addView(label, labelParams);

        // Held here, written nowhere until 覆盖导入 is pressed. What the calendar
        // returns is normalised to a Monday as it arrives, so what the line below
        // shows is the date the term will actually use.
        final LocalDate[] chosen = {ScheduleUtils.mondayOf(storedStart)};
        // The line is refreshed from inside this callback, not from a watcher on the
        // field: the picker writes the tapped day into the field before it hands the
        // value back, so a watcher would run against the previous Monday and the two
        // would contradict each other until the next tap.
        final TextView[] effective = new TextView[1];
        TextView dateField = Pickers.dateField(this, chosen[0], date -> {
            chosen[0] = ScheduleUtils.mondayOf(date);
            if (effective[0] != null) {
                effective[0].setText(effectiveDateText(chosen[0]));
            }
        });
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(UiTokens.TOUCH_TARGET));
        fieldParams.topMargin = dp(UiTokens.SPACE_XS);
        content.addView(dateField, fieldParams);

        effective[0] = UiKit.caption(this, effectiveDateText(chosen[0]));
        LinearLayout.LayoutParams effectiveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        effectiveParams.topMargin = dp(UiTokens.SPACE_XS + 1);
        content.addView(effective[0], effectiveParams);

        // How long the term runs, held here and written nowhere until 覆盖导入 is
        // pressed. Read from the target term so the dialog opens on the length this
        // term already has, and kept apart from the date: the date says which
        // Monday week 1 is, the count says how many weeks follow it.
        final int[] weeks = {SemesterWeeks.of(target)};
        final int storedWeeks = SemesterWeeks.of(target);
        TextView weeksLabel = UiKit.text(this, "\u5B66\u671F\u603B\u5468\u6570",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams weeksLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        weeksLabelParams.topMargin = dp(UiTokens.SPACE_M);
        content.addView(weeksLabel, weeksLabelParams);

        final TextView[] weeksValue = new TextView[1];
        content.addView(weekStepper(weeks, weeksValue), UiKit.stack(this,
                UiTokens.SPACE_XS));
        final TextView[] range = new TextView[1];
        range[0] = UiKit.caption(this, semesterRangeText(chosen[0], weeks[0]));
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = dp(UiTokens.SPACE_XS + 1);
        content.addView(range[0], rangeParams);
        weeksValue[0].addTextChangedListener(new Pickers.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                range[0].setText(semesterRangeText(chosen[0], weeks[0]));
            }
        });
        dateField.addTextChangedListener(new Pickers.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                range[0].setText(semesterRangeText(chosen[0], weeks[0]));
            }
        });

        UiKit.dialog(this, "\u5BFC\u5165\u8BFE\u8868", null, content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u8986\u76D6\u5BFC\u5165", () -> {
                    LocalDate weekOne = chosen[0];
                    int requestedWeeks = weeks[0];
                    boolean dateMoved = !weekOne.equals(
                            ScheduleUtils.mondayOf(storedStart));
                    boolean weeksShrank = requestedWeeks < storedWeeks;
                    List<Course> existing = repository.getCourses();

                    // Shortening the term can strand a course outside it. Refused
                    // rather than trimmed: a length setting may not rewrite the
                    // weeks a course was given, nor drop the courses it catches.
                    if (weeksShrank
                            && SemesterWeeks.wouldStrandCourses(existing, requestedWeeks)) {
                        int stranded = SemesterWeeks.countBeyond(existing, requestedWeeks);
                        UiKit.dialog(this, "\u5468\u6570\u4E0D\u8DB3",
                                "\u672C\u5B66\u671F\u5DF2\u6709 " + stranded
                                        + " \u95E8\u8BFE\u7A0B\u6392\u5728\u7B2C "
                                        + requestedWeeks + " \u5468\u4E4B\u540E\uFF0C"
                                        + "\u6539\u6210 " + requestedWeeks
                                        + " \u5468\u4F1A\u8BA9\u5B83\u4EEC\u843D\u5728"
                                        + "\u5B66\u671F\u4E4B\u5916\u3002\n\n"
                                        + "\u8BF7\u5148\u51CF\u5C11\u6216\u8C03\u6574"
                                        + "\u8FD9\u4E9B\u8BFE\u7A0B\uFF0C\u6216\u6539"
                                        + "\u56DE " + storedWeeks + " \u5468\u3002",
                                null, null,
                                UiKit.Action.of("\u77E5\u9053\u4E86", null));
                        return;
                    }
                    if ((dateMoved || requestedWeeks != storedWeeks)
                            && !existing.isEmpty()) {
                        UiKit.dialog(this, "\u4FEE\u6539\u5B66\u671F\u8BBE\u7F6E",
                                semesterChangeWarning(weekOne, requestedWeeks,
                                        storedWeeks, storedStart),
                                null, null,
                                UiKit.Action.of("\u53D6\u6D88", null),
                                UiKit.Action.of("\u7EE7\u7EED\u5BFC\u5165",
                                        () -> runImport(courses, target, weekOne,
                                                requestedWeeks)));
                        return;
                    }
                    runImport(courses, target, weekOne, requestedWeeks);
                }));
    }

    private String effectiveDateText(LocalDate monday) {
        return "\u751F\u6548\u65E5\u671F\uFF1A" + monday
                + "\uFF0C\u5373\u672C\u5B66\u671F\u7B2C 1 \u5468\u5468\u4E00";
    }

    /**
     * Builds the {@code − 18 周 +} row for the import dialog.
     *
     * <p>The value array is passed in rather than returned, because the confirm
     * path reads the same cell the buttons write; a copy would let the readout and
     * the import disagree about the count.</p>
     */
    private View weekStepper(final int[] weeks, final TextView[] value) {
        TextView minus = UiKit.pill(this, "\u2212", UiTokens.Type.TITLE_MEDIUM,
                Palette.SURFACE_CONTAINER_HIGH, Palette.PRIMARY, true,
                UiTokens.RADIUS_S);
        minus.setContentDescription("\u51CF\u5C11\u4E00\u5468");
        TextView plus = UiKit.pill(this, "+", UiTokens.Type.TITLE_MEDIUM,
                Palette.SURFACE_CONTAINER_HIGH, Palette.PRIMARY, true,
                UiTokens.RADIUS_S);
        plus.setContentDescription("\u589E\u52A0\u4E00\u5468");

        value[0] = UiKit.text(this, weeksText(weeks[0]),
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        value[0].setGravity(Gravity.CENTER);

        minus.setOnClickListener(view -> {
            if (weeks[0] > Semester.MIN_WEEKS) {
                weeks[0] = weeks[0] - 1;
                value[0].setText(weeksText(weeks[0]));
            }
        });
        plus.setOnClickListener(view -> {
            if (weeks[0] < Semester.MAX_WEEKS) {
                weeks[0] = weeks[0] + 1;
                value[0].setText(weeksText(weeks[0]));
            }
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                dp(UiTokens.TOUCH_TARGET), dp(UiTokens.BUTTON_HEIGHT_COMPACT));
        row.addView(minus, buttonParams);
        row.addView(value[0], new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(plus, buttonParams);
        return row;
    }

    /** The stepper's readout, for example {@code 18 周}. */
    private String weeksText(int weeks) {
        return weeks + " \u5468";
    }

    /**
     * The range a term would have, given a start date and a length.
     *
     * <p>Shown under the stepper so a count change reads as a range change rather
     * than as a number whose effect the user has to imagine.</p>
     */
    private String semesterRangeText(LocalDate start, int weeks) {
        LocalDate monday = ScheduleUtils.mondayOf(start);
        return "\u8BFE\u7A0B\u53EF\u6392\u8303\u56F4\uFF1A"
                + ScheduleUtils.formatDate(monday) + " - "
                + ScheduleUtils.formatDate(SemesterWeeks.lastDay(monday, weeks))
                + "\uFF0C\u5171 " + weeks + " \u5468";
    }

    /**
     * The sentence shown before a date or length change is written.
     *
     * <p>Names only the parts that actually changed, so a warning about the date
     * does not appear when the user only touched the length.</p>
     */
    private String semesterChangeWarning(LocalDate weekOne, int weeks,
            int storedWeeks, LocalDate storedStart) {
        StringBuilder message = new StringBuilder();
        if (!weekOne.equals(ScheduleUtils.mondayOf(storedStart))) {
            message.append("\u4FEE\u6539\u5F00\u5B66\u65E5\u671F\u4F1A\u6539\u53D8"
                    + "\u672C\u5B66\u671F\u8BFE\u7A0B\u5BF9\u5E94\u7684\u5B9E\u9645"
                    + "\u65E5\u671F\u53CA\u76F8\u5173\u63D0\u9192\u65F6\u95F4\u3002\n\n"
                    + "\u65B0\u7684\u7B2C 1 \u5468\u5468\u4E00\uFF1A" + weekOne);
        }
        if (weeks != storedWeeks) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append("\u5B66\u671F\u603B\u5468\u6570\uFF1A")
                    .append(storedWeeks).append(" \u5468 \u2192 ")
                    .append(weeks).append(" \u5468");
        }
        return message.toString();
    }

    /**
     * Writes the file, the chosen week-1 date and the term length, or says why
     * nothing landed.
     *
     * <p>One place for both paths, so the confirmed and unconfirmed cases cannot
     * differ in what they save: the date, the length and the courses are a single
     * commit in the repository, and none is written here on its own.</p>
     */
    private void runImport(List<Course> courses, Semester target, LocalDate weekOne,
            int weeks) {
        if (repository.replaceCoursesAt(courses, target, weekOne, weeks)) {
            // The import replaced the term the alarms were planned from.
            ReminderScheduler.sync(this);
            refreshAll();
            Toast.makeText(this, "\u8BFE\u8868\u5DF2\u5BFC\u5165",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        refreshAll();
        UiKit.message(this, "\u5BFC\u5165\u672A\u5B8C\u6210",
                "\u8BFE\u8868\u5199\u5165\u5931\u8D25\uFF0C\u5BFC\u5165\u53EF\u80FD"
                        + "\u6CA1\u6709\u751F\u6548\uFF0C\u8BF7\u91CD\u65B0\u5BFC\u5165\u3002");
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

    // ----------------------------------------------------------------- actions

    /**
     * Opens the course's own page.
     *
     * <p>A tap used to open the editor, which put a form -- and the keyboard --
     * between the user and simply reading the course. Editing is one tap further
     * on, from the detail page, and nothing here changes what the editor does.</p>
     */
    private void showDetails(Course course) {
        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, course.id);
        startActivity(intent);
    }

    private void showDeleteDialog(Course course) {
        UiKit.dialog(this, "\u5220\u9664\u8BFE\u7A0B",
                "\u786E\u5B9A\u5220\u9664\u201C" + course.name
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664\u8BFE\u7A0B", () -> {
                    repository.deleteCourse(course.id);
                    refreshAll();
                    Toast.makeText(this, "\u8BFE\u7A0B\u5DF2\u5220\u9664",
                            Toast.LENGTH_SHORT).show();
                }));
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

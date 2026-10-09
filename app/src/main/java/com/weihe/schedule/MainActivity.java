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
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import com.weihe.schedule.data.RestoreReport;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.importer.ExcelTimetableReader;
import com.weihe.schedule.importer.TimetableImportException;
import com.weihe.schedule.importer.TimetableImportParser;
import com.weihe.schedule.ui.Insets;
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
    private TextView parityPill;
    private View todayButton;
    private TextView semesterSubtitle;
    private TextView semesterChip;
    private TextView dayTitle;
    private TextView dayCountPill;
    private TextView todayLabel;
    private View backToThisWeek;

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
        // The bottom padding clears the floating buttons, so the last course card
        // can always be scrolled out from under them.
        body.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L),
                dp(UiTokens.SPACE_L),
                dp(UiTokens.FAB_SIZE + UiTokens.SPACE_XXL));

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
     * Two buttons in the bottom right corner: the timetable overview, and a way
     * back to today.
     *
     * <p>Both used to live in the header. They are here because they act on the
     * page rather than on the app -- and because the header is now the title, the
     * semester and settings, with nothing else competing.</p>
     *
     * <p>The overview keeps the filled brand button and the today button is drawn
     * on a plain surface. Stacking two filled buttons in one corner makes both of
     * them claim to be the main action, and two teal discs are more brand colour
     * than the timetable underneath them.</p>
     *
     * <p>The today button is hidden while the screen is already showing today:
     * a control that would do nothing is noise, and hiding it also makes the
     * corner quieter on the common case.</p>
     */
    private View createFloatingActions() {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.END);

        todayButton = UiKit.fabTonal(this, R.drawable.ic_today,
                "\u56DE\u5230\u4ECA\u5929", view -> goToToday());
        todayButton.setContentDescription("\u56DE\u5230\u4ECA\u5929");
        stack.addView(todayButton, new LinearLayout.LayoutParams(
                dp(UiTokens.FAB_SIZE), dp(UiTokens.FAB_SIZE)));

        ImageButton overview = UiKit.fab(this, R.drawable.ic_grid,
                "\u8BFE\u8868\u603B\u89C8", view -> openOverview());
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                dp(UiTokens.FAB_SIZE), dp(UiTokens.FAB_SIZE));
        overviewParams.topMargin = dp(UiTokens.SPACE_M);
        stack.addView(overview, overviewParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        params.setMargins(0, 0, dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L));
        stack.setLayoutParams(params);
        return stack;
    }

    /**
     * Back to the current week and today's weekday.
     *
     * <p>Both, not just the day: from week 12 the button means "show me where I
     * am now", which is the week as well as the date.</p>
     */
    private void goToToday() {
        LocalDate today = LocalDate.now();
        motionDirection = selectedWeek != currentWeek ? Integer.signum(currentWeek - selectedWeek)
                : Integer.signum(today.getDayOfWeek().getValue() - selectedDay);
        selectedWeek = currentWeek;
        selectedDay = today.getDayOfWeek().getValue();
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
        return selectedWeek == currentWeek
                && selectedDay == LocalDate.now().getDayOfWeek().getValue();
    }

    /**
     * Keeps the two "get back" controls in step with what is on screen.
     *
     * <p>Called from every path that can change the selected day or week, which is
     * the point of having it in one place: when it lived inline in
     * {@link #refreshAll()} the day-change path silently skipped it.</p>
     */
    private void syncReturnControls() {
        if (todayButton != null) {
            // Hidden while the screen already shows today: a button that would do
            // nothing is noise.
            todayButton.setVisibility(isShowingToday() ? View.GONE : View.VISIBLE);
        }
        if (backToThisWeek != null) {
            backToThisWeek.setVisibility(
                    selectedWeek == currentWeek ? View.GONE : View.VISIBLE);
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
     * One surface card holding everything about the week on screen: which week,
     * which dates, its parity, and the arrows and "back to this week" control
     * that move between weeks.
     */
    private View createWeekCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M));
        UiKit.styleAsCard(this, card, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout weekBlock = new LinearLayout(this);
        weekBlock.setOrientation(LinearLayout.VERTICAL);
        weekBlock.addView(createWeekControl(),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        weekDates = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        LinearLayout.LayoutParams datesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        datesParams.topMargin = dp(UiTokens.SPACE_XS);
        weekBlock.addView(weekDates, datesParams);
        topRow.addView(weekBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        parityPill = UiKit.pill(this, "", UiTokens.Type.LABEL_MEDIUM,
                Palette.SURFACE_CONTAINER_LOW, Palette.ON_SURFACE_VARIANT, true,
                UiTokens.RADIUS_FULL);
        topRow.addView(parityPill);
        card.addView(topRow);

        card.addView(UiKit.divider(this, UiTokens.SPACE_M, UiTokens.SPACE_XS));
        card.addView(createWeekNavRow());
        return card;
    }

    /** Previous / next week, plus a way back to the current one. */
    private View createWeekNavRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        todayLabel = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.BOLD);
        todayLabel.setMaxLines(1);
        todayLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(todayLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        backToThisWeek = navChip("\u56DE\u5230\u672C\u5468", view -> {
            motionDirection = Integer.signum(currentWeek - selectedWeek);
            selectedWeek = currentWeek;
            selectedDay = LocalDate.now().getDayOfWeek().getValue();
            refreshAll();
            UiKit.swapIn(courseContainer, motionDirection);
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(UiTokens.CHIP_HEIGHT));
        backParams.rightMargin = dp(UiTokens.SPACE_S);
        row.addView(backToThisWeek, backParams);

        row.addView(stepButton(R.drawable.ic_chevron_left, "\u4E0A\u4E00\u5468",
                view -> stepWeek(-1)));
        row.addView(stepButton(R.drawable.ic_chevron_right, "\u4E0B\u4E00\u5468",
                view -> stepWeek(1)));
        return row;
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

    private TextView navChip(String label, View.OnClickListener listener) {
        TextView chip = UiKit.text(this, label, UiTokens.Type.LABEL_LARGE,
                Palette.PRIMARY, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(UiTokens.SPACE_M), 0, dp(UiTokens.SPACE_M), 0);
        UiKit.setRoundedBackground(chip, Palette.PRIMARY_CONTAINER,
                UiTokens.RADIUS_FULL, Color.TRANSPARENT, 0f);
        chip.setOnClickListener(listener);
        // The chip's own feedback, so a text control does not feel inert next to
        // the icon buttons either side of it.
        UiKit.ripple(chip, UiTokens.RADIUS_FULL, false);
        return chip;
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

        boolean showingCurrentWeek = selectedWeek == currentWeek;
        parityPill.setText(showingCurrentWeek
                ? "\u672C\u5468" : ScheduleUtils.parityName(selectedWeek));
        // The pill sits on a light card, so its two states are the light-surface
        // pair: the accent container when this is the week the calendar is in, a
        // quiet grey when it is not. The accent is the same orange as the icon's
        // one highlighted tile, which is why "this is the week you are in" is the
        // only thing in the app allowed to use it.
        parityPill.setTextColor(showingCurrentWeek
                ? Palette.ON_ACCENT_CONTAINER : Palette.ON_SURFACE_VARIANT);
        parityPill.setBackground(showingCurrentWeek
                ? UiKit.fill(this, Palette.ACCENT_CONTAINER, UiTokens.RADIUS_FULL)
                : UiKit.fill(this, Palette.SURFACE_CONTAINER_LOW,
                        UiTokens.RADIUS_FULL));
        semesterSubtitle.setText(active.label() + " \u00B7 "
                + ScheduleUtils.formatDate(semesterStart) + " \u5F00\u5B66");
        if (semesterChip != null) {
            // Only a term that is not the one the calendar is in right now can be
            // mistaken for the current timetable, so only then does the way out
            // have to be visible. The surrounding row already says "switch"; this
            // chip is the louder, "get back" case.
            boolean inSession = !today.isBefore(active.start())
                    && !today.isAfter(active.lastDay());
            semesterChip.setText("\u67E5\u770B\u5F53\u524D\u5B66\u671F");
            semesterChip.setVisibility(inSession ? View.GONE : View.VISIBLE);
        }
        syncReturnControls();
        if (todayLabel != null) {
            boolean inSession = !today.isBefore(active.start())
                    && !today.isAfter(active.lastDay());
            todayLabel.setText(inSession
                    ? "\u4ECA\u5929 " + ScheduleUtils.formatFullDate(today)
                            + " \u00B7 "
                            + ScheduleUtils.dayName(today.getDayOfWeek().getValue())
                    : active.label() + " \u5DF2\u7ED3\u675F");
        }

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
        weekDates.setText(ScheduleUtils.weekRange(semesterStart, selectedWeek)
                + "  \u00B7  " + ScheduleUtils.parityName(selectedWeek));
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
        dayTitle.setText(ScheduleUtils.dayName(selectedDay)
                + (isToday ? " \u00B7 \u4ECA\u5929" : "")
                + " \u00B7 " + ScheduleUtils.formatDate(date));
        courseContainer.removeAllViews();

        List<Course> visible = CourseQuery.onDay(
                repository.getCourses(), selectedWeek, selectedDay);
        dayCountPill.setText(visible.isEmpty() ? "\u65E0\u8BFE"
                : visible.size() + " \u95E8\u8BFE");

        if (visible.isEmpty()) {
            View empty = createEmptyState();
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
        UiKit.styleAsCard(this, card, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);
        card.setMinimumHeight(dp(88));

        // The colour rail keeps a course identifiable at a glance, at the card's
        // own width rather than a stripe bolted on: 4dp of the course's colour,
        // softened towards the card so a column of them stays quiet, and rounded
        // with the card by the clip.
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
        if (!schedule.name().equals(BellSchedules.STANDARD_NAME)) {
            tag += " \u00B7 " + schedule.name();
        }
        content.addView(createTag(tag, course.color));

        UiKit.ripple(card, UiTokens.RADIUS_L, false);
        UiKit.pressable(card, view -> openEditor(course));
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
        block.setBackground(UiKit.fill(this, Palette.SURFACE_CONTAINER_LOW,
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

    private View createEmptyState() {
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

        TextView label = UiKit.text(this,
                "\u8BFE\u8868\u7B2C 1 \u5468\u5BF9\u5E94\u672C\u5B66\u671F\uFF1A",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(UiTokens.SPACE_L);
        content.addView(label, labelParams);

        Spinner startWeekSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, starts);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        startWeekSpinner.setAdapter(adapter);
        startWeekSpinner.setSelection(0);
        startWeekSpinner.setContentDescription("\u9009\u62E9\u5BFC\u5165\u8D77\u59CB\u5468");
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(UiTokens.TOUCH_TARGET));
        spinnerParams.topMargin = dp(UiTokens.SPACE_XS);
        content.addView(startWeekSpinner, spinnerParams);

        TextView hint = UiKit.caption(this,
                "\u6587\u4EF6\u4E2D\u6700\u540E\u4E00\u5468\u4E3A\u7B2C"
                        + lastSourceWeek + "\u5468\uFF0C\u5DF2\u8FC7\u6EE4\u4F1A\u8D85\u51FA"
                        + ScheduleUtils.SEMESTER_WEEKS + "\u5468\u7684\u9009\u9879");
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(UiTokens.SPACE_XS + 1);
        content.addView(hint, hintParams);

        UiKit.dialog(this, "\u5BFC\u5165\u8BFE\u8868", null, content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u8986\u76D6\u5BFC\u5165", () -> {
                    int sourceFirstWeek = startWeekSpinner
                            .getSelectedItemPosition() + 1;
                    try {
                        List<Course> shifted = TimetableImportParser.shiftWeeks(
                                courses, sourceFirstWeek);
                        repository.replaceCourses(shifted);
                        refreshAll();
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

    // ----------------------------------------------------------------- actions

    private void openEditor(Course course) {
        Intent intent = new Intent(this, EditCourseActivity.class);
        intent.putExtra(EditCourseActivity.EXTRA_COURSE_ID,
                course == null ? 0L : course.id);
        intent.putExtra(EditCourseActivity.EXTRA_DAY, selectedDay);
        startActivity(intent);
    }

    private void showDeleteDialog(Course course) {
        UiKit.dialog(this, "\u5220\u9664\u8BFE\u7A0B",
                "\u786E\u5B9A\u5220\u9664\u201C" + course.name
                        + "\u201D\u5417\uFF1F\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664", () -> {
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

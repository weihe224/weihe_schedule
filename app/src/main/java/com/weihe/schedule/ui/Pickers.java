package com.weihe.schedule.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.weihe.schedule.design.Palette;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * The ways this app asks for a date and for a time.
 *
 * <p>Both used to be keyboard fields, and then both were the platform's own
 * dialogs -- which is why one of them said {@code CANCEL} and {@code OK} in a
 * Chinese app and opened on a clock face twice the height of anything else on
 * screen. Neither is styled by the app, so neither could be made to match it.</p>
 *
 * <h2>One frame, two contents</h2>
 *
 * <p>These are built on {@link UiKit#dialog}, the same panel every confirmation in
 * the app uses. So the width, the corner radius, the title's weight, the button
 * height and the order of the buttons are not kept in step by hand -- they are the
 * same code. Each picker supplies only what it is for: a list of hours and minutes,
 * or a month of days.</p>
 *
 * <h2>Reading is kept, writing is replaced</h2>
 *
 * <p>Everything already stored still goes through the same parsers
 * ({@link ScheduleUtils#parseDate} and {@link BellSchedule#parseLoose}), so a term
 * saved as {@code 2026-08-31} and a period saved as {@code 8.00} read exactly as
 * they did. What changes is only how a new value is chosen.</p>
 *
 * <h2>What these are not</h2>
 *
 * <p>They pick a calendar date and a clock time, and nothing else. A period number,
 * a week number and a reminder lead time are all numbers about the timetable rather
 * than moments in a day, and none of them belongs in a date or time picker.</p>
 */
public final class Pickers {

    private Pickers() {
    }

    // ================================================================ formatting

    /** {@code yyyy-MM-dd}, the one spelling of a date this app writes. */
    public static String formatDate(LocalDate date) {
        return date.toString();
    }

    /** {@code HH:mm}, zero-padded, the one spelling of a time this app writes. */
    public static String formatTime(int hour, int minute) {
        return two(hour) + ":" + two(minute);
    }

    /**
     * The day {@code text} names, or {@code fallback} when it names none.
     *
     * <p>Used to decide where a calendar opens. A field that is empty, or holds
     * something unreadable, opens on the fallback rather than refusing to open.</p>
     */
    public static LocalDate dateOr(String text, LocalDate fallback) {
        LocalDate parsed = ScheduleUtils.parseDate(text);
        return parsed == null ? fallback : parsed;
    }

    /** The hour of {@code text} (0-23), or {@code fallback} when it has none. */
    public static int hourOr(String text, int fallback) {
        Integer minutes = BellSchedule.parseLoose(text);
        return minutes == null ? fallback : minutes / 60;
    }

    /** The minute of {@code text} (0-59), or {@code fallback} when it has none. */
    public static int minuteOr(String text, int fallback) {
        Integer minutes = BellSchedule.parseLoose(text);
        return minutes == null ? fallback : minutes % 60;
    }

    // ==================================================================== fields

    /**
     * A read-only field showing a date, which opens a calendar when tapped.
     *
     * <p>The field is not a text box: it displays the value and nothing else, so
     * there is no state in which it holds something that is neither a date nor
     * empty. {@code onPicked} is called only on a confirmed choice -- dismissing the
     * panel leaves the field and the value alone.</p>
     */
    public static TextView dateField(Context context, LocalDate initial,
            Consumer<LocalDate> onPicked) {
        final LocalDate[] held = {initial};
        final TextView field = pickable(context, formatDate(initial));
        field.setOnClickListener(view -> showDatePicker(context,
                held[0] == null ? LocalDate.now() : held[0], chosen -> {
                    held[0] = chosen;
                    field.setText(formatDate(chosen));
                    onPicked.accept(chosen);
                }));
        return field;
    }

    /**
     * A read-only field showing a time, which opens the picker when tapped.
     *
     * <p>Shows whatever text it was given, so a stored {@code 8.00} stays legible
     * until the user picks something, at which point it becomes {@code HH:mm}. The
     * picker opens on the time the field already means, or on {@code fallbackHour}
     * when it means nothing yet.</p>
     */
    public static TextView timeField(Context context, String initialText,
            int fallbackHour, Consumer<String> onPicked) {
        final TextView field = pickable(context, initialText);
        field.setOnClickListener(view -> showTimePicker(context,
                hourOr(field.getText().toString(), fallbackHour),
                minuteOr(field.getText().toString(), 0), value -> {
                    field.setText(value);
                    onPicked.accept(value);
                }));
        return field;
    }

    /**
     * A time that is shown but not chosen.
     *
     * <p>Styled to say so: a flat, muted box with no ripple and no press state, next
     * to the filled, tappable field it is calculated from. The difference has to be
     * visible, not just absent -- a value that looks like the one beside it and does
     * not respond reads as broken rather than as read-only.</p>
     */
    public static TextView readOnlyField(Context context, String text) {
        TextView field = pickable(context, text);
        field.setClickable(false);
        field.setTextColor(Palette.ON_SURFACE_VARIANT);
        field.setBackground(UiKit.fill(context, Palette.SURFACE_CONTAINER_HIGH,
                UiTokens.RADIUS_S));
        return field;
    }
    /** Updates a picker field's text without opening it, for a restored draft. */
    public static void show(TextView field, String text) {
        if (field != null) {
            field.setText(text);
        }
    }

    // ============================================================== time picker

    /**
     * Asks for a time: an hour column, a minute column, and the choice so far.
     *
     * <p>Two lists rather than a clock face. A dial is large, and it hides the
     * minutes behind a second gesture; a column of hours and a column of minutes
     * shows every value the app can store at once, in a panel the height of a
     * confirmation. Both columns scroll to whatever the field already holds, so the
     * picker opens on the current time rather than at midnight.</p>
     */
    public static void showTimePicker(Context context, int hour, int minute,
            Consumer<String> onPicked) {
        final int[] chosen = {hour, minute};
        final TextView readout = readout(context, formatTime(hour, minute));
        readout.setContentDescription("\u9009\u62E9\u65F6\u95F4");

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(readout);
        content.addView(hint(context, "24 \u5C0F\u65F6\u5236"));

        ScrollView hours = column(context, 0, 23, hour, value -> {
            chosen[0] = value;
            readout.setText(formatTime(chosen[0], chosen[1]));
        });
        ScrollView minutes = column(context, 0, 59, minute, value -> {
            chosen[1] = value;
            readout.setText(formatTime(chosen[0], chosen[1]));
        });

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                0, UiKit.dp(context, COLUMN_HEIGHT_DP), 1f);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                0, UiKit.dp(context, COLUMN_HEIGHT_DP), 1f);
        secondParams.leftMargin = UiKit.dp(context, UiTokens.SPACE_M);
        columns.addView(hours, columnParams);
        columns.addView(minutes, secondParams);
        LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnsParams.topMargin = UiKit.dp(context, UiTokens.SPACE_M);
        content.addView(columns, columnsParams);

        UiKit.dialog(context, "\u9009\u62E9\u65F6\u95F4", null, content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u786E\u5B9A", () -> onPicked.accept(
                        formatTime(chosen[0], chosen[1])))).show();
    }

    // ============================================================== date picker

    /**
     * Asks for a date: a month, with the day marked.
     *
     * <p>Laid out from Monday, the way the timetable itself is: a week here reads in
     * the same order as a week on the home screen. Days outside the month are left
     * blank rather than greyed in, so the only things that can be tapped are days
     * that belong to the month on screen.</p>
     */
    public static void showDatePicker(Context context, LocalDate initial,
            Consumer<LocalDate> onPicked) {
        final LocalDate[] chosen = {initial};
        final LocalDate[] month = {initial.withDayOfMonth(1)};
        final TextView readout = readout(context, formatDate(initial));
        readout.setContentDescription("\u9009\u62E9\u65E5\u671F");
        final TextView monthLabel = UiKit.text(context, monthName(month[0]),
                UiTokens.Type.TITLE_SMALL, Palette.ON_SURFACE, Typeface.BOLD);
        monthLabel.setGravity(Gravity.CENTER);
        final LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(readout);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(monthStep(context, "\u2039", () -> {
            month[0] = month[0].minusMonths(1);
            monthLabel.setText(monthName(month[0]));
            fillMonth(context, grid, month[0], chosen, readout);
        }), new LinearLayout.LayoutParams(
                UiKit.dp(context, UiTokens.TOUCH_TARGET),
                UiKit.dp(context, UiTokens.TOUCH_TARGET)));
        header.addView(monthLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(monthStep(context, "\u203A", () -> {
            month[0] = month[0].plusMonths(1);
            monthLabel.setText(monthName(month[0]));
            fillMonth(context, grid, month[0], chosen, readout);
        }), new LinearLayout.LayoutParams(
                UiKit.dp(context, UiTokens.TOUCH_TARGET),
                UiKit.dp(context, UiTokens.TOUCH_TARGET)));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = UiKit.dp(context, UiTokens.SPACE_S);
        content.addView(header, headerParams);

        content.addView(weekdayHeader(context), headerParams);
        content.addView(grid);
        fillMonth(context, grid, month[0], chosen, readout);

        UiKit.dialog(context, "\u9009\u62E9\u65E5\u671F", null, content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u786E\u5B9A", () -> onPicked.accept(chosen[0]))).show();
    }

    /**
     * The days of {@code month}, padded to whole weeks from Monday.
     *
     * <p>Pure so it can be checked without a screen: a month laid out one day out
     * puts every date under the wrong weekday, which is the kind of mistake that
     * looks plausible in a screenshot. Nulls are the blanks before the first and
     * after the last.</p>
     */
    public static List<LocalDate> monthCells(LocalDate month) {
        LocalDate first = month.withDayOfMonth(1);
        int lead = first.getDayOfWeek().getValue() - 1;
        int length = first.lengthOfMonth();
        List<LocalDate> cells = new ArrayList<>();
        for (int i = 0; i < lead; i++) {
            cells.add(null);
        }
        for (int day = 1; day <= length; day++) {
            cells.add(first.withDayOfMonth(day));
        }
        while (cells.size() % 7 != 0) {
            cells.add(null);
        }
        return cells;
    }

    // ================================================================ building

    private static void fillMonth(Context context, LinearLayout grid, LocalDate month,
            LocalDate[] chosen, TextView readout) {
        grid.removeAllViews();
        List<LocalDate> cells = monthCells(month);
        LinearLayout week = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % 7 == 0) {
                week = new LinearLayout(context);
                week.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(week, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            week.addView(dayCell(context, cells.get(i), chosen, readout, grid, month),
                    new LinearLayout.LayoutParams(0, UiKit.dp(context, DAY_CELL_DP), 1f));
        }
    }

    private static View dayCell(Context context, LocalDate day, LocalDate[] chosen,
            TextView readout, LinearLayout grid, LocalDate month) {
        TextView cell = UiKit.text(context, day == null ? "" : String.valueOf(
                day.getDayOfMonth()), UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, Typeface.NORMAL);
        cell.setGravity(Gravity.CENTER);
        if (day == null) {
            return cell;
        }
        boolean selected = day.equals(chosen[0]);
        if (selected) {
            cell.setTextColor(Palette.ON_PRIMARY);
            cell.setBackground(UiKit.fill(context, Palette.PRIMARY, UiTokens.RADIUS_FULL));
        } else if (day.equals(LocalDate.now())) {
            cell.setTextColor(Palette.PRIMARY);
            cell.setBackground(UiKit.outline(context, Palette.SURFACE,
                    UiTokens.RADIUS_FULL, Palette.PRIMARY, UiTokens.STROKE_HAIRLINE));
        }
        UiKit.ripple(cell, UiTokens.RADIUS_FULL, false);
        cell.setOnClickListener(view -> {
            chosen[0] = day;
            readout.setText(formatDate(day));
            // Redrawn rather than patched: one cell gains the mark and another
            // loses it, and repainting the month keeps the two from drifting.
            fillMonth(context, grid, month, chosen, readout);
        });
        return cell;
    }

    private static View weekdayHeader(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"\u4E00", "\u4E8C", "\u4E09", "\u56DB",
                "\u4E94", "\u516D", "\u65E5"};
        for (String name : names) {
            TextView label = UiKit.text(context, name, UiTokens.Type.LABEL_MEDIUM,
                    Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private static TextView monthStep(Context context, String glyph, Runnable work) {
        TextView step = UiKit.text(context, glyph, UiTokens.Type.TITLE_LARGE,
                Palette.PRIMARY, Typeface.BOLD);
        step.setGravity(Gravity.CENTER);
        UiKit.ripple(step, UiTokens.RADIUS_FULL, true);
        step.setOnClickListener(view -> work.run());
        return step;
    }

    private static String monthName(LocalDate month) {
        return month.getYear() + " \u5E74 " + month.getMonthValue() + " \u6708";
    }

    /**
     * One scrolling column of numbers, the chosen one marked.
     *
     * <p>Scrolled to the current value when it opens, so the picker starts where the
     * field already is. The mark is a filled pill rather than a colour change, so it
     * stays readable against both the panel and the rows around it.</p>
     */
    private static ScrollView column(Context context, int from, int to, int current,
            Consumer<Integer> onPick) {
        final ScrollView scroll = new ScrollView(context);
        scroll.setBackground(UiKit.fill(context, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_S));
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(UiKit.dp(context, UiTokens.SPACE_XS),
                UiKit.dp(context, UiTokens.SPACE_XS),
                UiKit.dp(context, UiTokens.SPACE_XS),
                UiKit.dp(context, UiTokens.SPACE_XS));
        final int[] selected = {current};
        final List<TextView> rows = new ArrayList<>();
        for (int value = from; value <= to; value++) {
            final int held = value;
            TextView row = UiKit.text(context, two(value), UiTokens.Type.BODY_LARGE,
                    value == current ? Palette.ON_PRIMARY : Palette.ON_SURFACE,
                    value == current ? Typeface.BOLD : Typeface.NORMAL);
            row.setGravity(Gravity.CENTER);
            row.setMinimumHeight(UiKit.dp(context, ROW_HEIGHT_DP));
            if (value == current) {
                row.setBackground(UiKit.fill(context, Palette.PRIMARY, UiTokens.RADIUS_S));
            }
            UiKit.ripple(row, UiTokens.RADIUS_S, false);
            row.setOnClickListener(view -> {
                for (int i = 0; i < rows.size(); i++) {
                    TextView other = rows.get(i);
                    boolean on = (from + i) == held;
                    other.setTextColor(on ? Palette.ON_PRIMARY : Palette.ON_SURFACE);
                    other.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
                    other.setBackground(on
                            ? UiKit.fill(context, Palette.PRIMARY, UiTokens.RADIUS_S)
                            : null);
                }
                selected[0] = held;
                onPick.accept(held);
            });
            rows.add(row);
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(list);
        scroll.post(() -> {
            View target = rows.get(Math.max(0, Math.min(rows.size() - 1,
                    selected[0] - from)));
            // A third down the column rather than at its edge, so the values on
            // either side of the current one are visible: the column reads as a
            // list with a position, not as a single number.
            scroll.scrollTo(0, Math.max(0,
                    target.getTop() - UiKit.dp(context, COLUMN_HEIGHT_DP) / 3));
        });
        return scroll;
    }

    /** The value being chosen, shown large and updated as the columns move. */
    private static TextView readout(Context context, String text) {
        TextView readout = UiKit.text(context, text, UiTokens.Type.HEADLINE_SMALL,
                Palette.PRIMARY, Typeface.BOLD);
        readout.setGravity(Gravity.CENTER);
        return readout;
    }

    private static TextView hint(Context context, String text) {
        TextView hint = UiKit.text(context, text, UiTokens.Type.LABEL_MEDIUM,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        return hint;
    }

    // ------------------------------------------------------------- appearance

    /**
     * The shared look: a filled, centred, read-only box that says it can be tapped.
     *
     * <p>Read-only rather than a disabled field: a disabled control cannot be
     * tapped, and the whole point is that this one is the control.</p>
     */
    private static TextView pickable(Context context, String text) {
        TextView field = UiKit.text(context, text, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, Typeface.NORMAL);
        field.setGravity(Gravity.CENTER);
        field.setSingleLine(true);
        field.setMinHeight(UiKit.dp(context, FIELD_HEIGHT_DP));
        int pad = UiKit.dp(context, UiTokens.SPACE_S);
        field.setPadding(pad, pad, pad, pad);
        field.setBackground(UiKit.outline(context, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_S, Palette.OUTLINE_VARIANT, UiTokens.STROKE_HAIRLINE));
        UiKit.ripple(field, UiTokens.RADIUS_S, false);
        field.setClickable(true);
        field.setFocusable(false);
        return field;
    }

    /** Tall enough to be an easy target without towering over the row. */
    private static final int FIELD_HEIGHT_DP = 42;
    /** How much of a number column is on screen at once. */
    private static final int COLUMN_HEIGHT_DP = 168;
    private static final int ROW_HEIGHT_DP = 40;
    /** A day cell is as wide as the panel allows and this tall. */
    private static final int DAY_CELL_DP = 44;

    /** Lays a picker field out at its natural width inside a weighted row. */
    public static LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}

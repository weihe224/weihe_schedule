package com.weihe.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;

import com.weihe.schedule.design.Palette;

/**
 * One column of a wheel: a cyclic run of integers that is dragged up and down, with
 * the value under the finger landing dead centre when the finger lifts.
 *
 * <h2>Why this is not a {@code ScrollView} of {@code TextView}s</h2>
 *
 * <p>The picker used to be exactly that, and it could not be made to centre. A
 * {@code ScrollView} knows nothing about which child matters, so "the current
 * value" was expressed by scrolling it <em>roughly</em> into view -- two thirds
 * of the way down, because that is where a list can put it -- and by giving it a
 * background. The selected row was therefore only ever near the middle, and on a
 * short column it was sometimes clipped at the top. There is no amount of styling
 * that fixes a layout that has no centre.</p>
 *
 * <p>So this view owns its own scroll offset instead. {@link #scrollPx} is the
 * single source of truth: the value is the nearest entry to the offset, every row's
 * y is derived from it, and drawing a row is nothing but drawing text. Centring is
 * then not a behaviour to preserve -- it is what the coordinate system means.</p>
 *
 * <h2>Why the index space is unbounded</h2>
 *
 * <p>A clock is a cycle: {@code 23} is followed by {@code 00}, and {@code 59} by
 * {@code 00}. The first version of this class modelled each column as a finite list
 * of {@code 24} or {@code 60} rows and clamped the scroll offset to
 * {@code 0..count-1}. That made the two ends dead ends -- scrolling up from
 * {@code 00} simply stopped, leaving the rows above the centre line blank, so the
 * wheel could not show that {@code 23} precedes {@code 00}. No amount of drawing
 * can fix a list that has no other side.</p>
 *
 * <p>This version therefore keeps a <em>virtual index</em> that may be any integer
 * at all, positive or negative, and maps it to a value with
 * {@link #valueAt(int)}. The mapping is a normalised modulo -- see
 * {@link #mod(int, int)} -- because Java's {@code %} keeps the sign of the dividend,
 * so {@code -1 % 24} is {@code -1} and not {@code 23}. Normalising is what lets the
 * wheel be dragged arbitrarily far in either direction and still land on a legal
 * value; without it, scrolling up past the start would produce negative hours.</p>
 *
 * <p>Only the index is unbounded. What the column <em>is</em> -- a value in
 * {@code [from, to]} -- stays exactly as before, and so does the value handed to
 * the owner. The virtual index is private, and the two things a wheel must never do
 * (show a value that is not the one it returns, and forget where it is) stay
 * unrepresentable.</p>
 *
 * <p>It does not name hours or minutes. The owner formats the preview; this class
 * only knows it is showing integers in a range, which is what makes it testable
 * off a device.</p>
 */
public class WheelColumnView extends View {

    /** Lowest legal value, inclusive. */
    private final int from;
    /** Highest legal value, inclusive. */
    private final int to;
    /** How many distinct values there are: {@code to - from + 1}. */
    private final int count;

    /** One row's height. Matches the highlight pill drawn behind the centre row. */
    private static final int ROW_HEIGHT_DP = 44;
    /** Rows on screen at once. Odd, so one of them is the centre one. */
    private static final int VISIBLE_ROWS = 5;

    /**
     * How far outside the visible band rows are still drawn.
     *
     * <p>Rows are clipped to the view, so two rows beyond the visible five is
     * enough to avoid a gap at the edges while a fling has the wheel between
     * positions, without laying out text that can never be seen.</p>
     */
    private static final int OVERDRAW_ROWS = 2;

    /**
     * How many rows a fling's bounds reach either side of the current offset.
     *
     * <p>OverScroller requires bounds, but a cycle has none, so the bounds are set
     * wide enough that no real flick can reach them: ten thousand rows is about 450
     * full turns of an hour column. The bounds exist only to satisfy the scroller's
     * range check -- they must contain the current offset, or the fling silently
     * degrades to a slow creep towards the boundary.</p>
     */
    private static final int FLING_RANGE_ROWS = 10_000;

    /**
     * Offset of the virtual index origin from the middle of the usable range.
     *
     * <p>Starting away from zero is what makes the unbounded index safe in practice:
     * a user would have to drag through hundreds of thousands of rows to reach index
     * zero, so an overflow cannot be reached by hand. The offset is applied by
     * {@link #indexForValue} so that the wheel still opens on the value it was
     * given.</p>
     *
     * <p>One hundred thousand rows is about 4,500 turns of an hour column and 1,700
     * of a minute column -- unreachable by dragging, yet small enough that the pixel
     * offset stays around ten million, leaving a wide margin before an {@code int}
     * could overflow when the fling bounds are added to it.</p>
     */
    private static final int INDEX_ORIGIN = 100_000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DecelerateInterpolator flingEasing = new DecelerateInterpolator(1.6f);
    private final int touchSlop;
    private final int rowHeightPx;
    private final float pillRadiusPx;

    /**
     * Scroll offset in pixels: how far the run has been scrolled past the centre.
     *
     * <p>Measured in rows from {@link #INDEX_ORIGIN}, and deliberately allowed to
     * go negative and to exceed the end of the range -- that is the whole point of
     * a cycle.</p>
     */
    private float scrollPx;

    /** The virtual index currently under the centre line. */
    private int index;

    /** The legal value at {@link #index}, kept in step with it at all times. */
    private int value;

    private boolean dragging;
    private float dragStartY;
    private float dragStartScroll;
    private float flingVelocity;      // px per second, from the velocity tracker
    private long lastMoveTime;
    private float lastTouchY;
    /** Row a tap landed on, handed to {@link #performClick()}; -1 when there was none. */
    private int pendingTapIndex = -1;
    private android.widget.OverScroller flingScroller;

    private OnValueChanged onValueChanged;

    /** Called when the value changes, whether by drag, fling or {@link #setValue}. */
    public interface OnValueChanged {
        void onValueChanged(int value);
    }

    public WheelColumnView(Context context, int from, int to, int initial) {
        super(context);
        if (to < from) {
            throw new IllegalArgumentException("empty range: " + from + ".." + to);
        }
        this.from = from;
        this.to = to;
        this.count = to - from + 1;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.rowHeightPx = UiKit.dp(context, ROW_HEIGHT_DP);
        this.pillRadiusPx = UiKit.dp(context, UiTokens.RADIUS_M);
        this.highlight.setColor(Palette.PRIMARY);
        this.value = clamp(initial);
        this.index = indexForValue(this.value);
        this.scrollPx = offsetForIndex(this.index);
        setClickable(true);
        setFocusable(true);
        setContentDescription("\u6EDA\u8F6E");
    }

    /**
     * The attribute-set form, which exists only so layout tooling can see a
     * constructor it recognises.
     *
     * <p>This app builds every view in Java and has no {@code res/layout}, so this
     * is never called at runtime. It is here because lint's {@code ViewConstructor}
     * check -- and the layout editor -- look for the standard signatures, and a
     * view that cannot be instantiated from XML has to say so rather than fail the
     * check silently.</p>
     */
    public WheelColumnView(Context context, android.util.AttributeSet attrs) {
        this(context, 0, 23, 0);
    }

    // ------------------------------------------------------------------ value

    public int getValue() {
        return value;
    }

    /** Moves to {@code newValue} and scrolls it to the centre. Never animates. */
    public void setValue(int newValue) {
        int clamped = clamp(newValue);
        boolean changed = clamped != value;
        value = clamped;
        // Re-derive the index from the value rather than nudging the old one: the
        // wheel has to be able to jump to an arbitrary value, which means the index
        // follows the value here and not the other way round.
        index = indexForValue(clamped);
        scrollPx = offsetForIndex(index);
        invalidate();
        if (changed) {
            announce();
        }
    }

    public void setOnValueChanged(OnValueChanged listener) {
        this.onValueChanged = listener;
    }

    // -------------------------------------------------------------- coordinates

    /**
     * The legal value that a virtual index shows, wrapping in both directions.
     *
     * <p>This is the cycle itself. The index may be negative and may exceed the
     * range; what comes out is always a value in {@code [from, to]}. The wrapping
     * is done by {@link #mod}, not by {@code %}, because the index goes negative
     * whenever the wheel is dragged upwards and Java's remainder would carry that
     * sign straight through into a negative hour.</p>
     */
    public int valueAt(int virtualIndex) {
        return from + mod(virtualIndex, count);
    }

    /**
     * The virtual index that shows {@code v} for a column starting at {@code from}.
     *
     * <p>Static and public so the origin can be checked without a screen. There are
     * many indices showing any given value, since the mapping is a cycle; this picks
     * one that is a whole number of turns away from the origin, which is what makes
     * {@link #valueAt} of the result equal to {@code v} for every column.</p>
     *
     * <p>The multiple-of-{@code count} term is the part that matters. The origin
     * itself is a fixed row number, and a row number is only meaningful relative to a
     * column's cycle: index {@code 100000} shows {@code 16} in an hour column, not
     * {@code 0}, because {@code 100000} is not a multiple of {@code 24}. Rounding the
     * origin down to a whole number of turns first makes the origin show {@code from},
     * so opening the picker lands on the value it was given rather than on whatever
     * the arithmetic happened to leave under the centre line.</p>
     */
    public static int indexForValue(int v, int from, int count) {
        if (count <= 0) {
            return INDEX_ORIGIN;
        }
        int turns = INDEX_ORIGIN / count;
        return turns * count + (v - from);
    }

    /**
     * The origin of the virtual index space, in rows.
     *
     * <p>Exposed so a test can assert it leaves room for thousands of turns of the
     * wheel before the index could approach an {@code int} limit.</p>
     */
    public static int indexOrigin() {
        return INDEX_ORIGIN;
    }

    /** The origin-nearest index showing {@code v} for this column's range. */
    private int indexForValue(int v) {
        return indexForValue(clamp(v), from, count);
    }

    /**
     * A Euclidean modulo: the result is always in {@code [0, modulus)}.
     *
     * <p>Java's {@code %} returns a value with the sign of the dividend, so
     * {@code -1 % 24 == -1}. Every wrap-around in this class needs the mathematical
     * modulo instead -- the one where {@code -1} maps to {@code modulus - 1} -- and
     * this is the single place that guarantees it. {@code modulus} must be
     * positive, which is true for every column this picker builds.</p>
     */
    static int mod(int index, int modulus) {
        if (modulus <= 0) {
            return 0;
        }
        int reduced = index % modulus;
        return reduced < 0 ? reduced + modulus : reduced;
    }

    /**
     * The offset that puts a virtual index exactly under the centre line.
     *
     * <p>Public and static so the arithmetic can be checked without a screen: an
     * off-by-one-row here would show one value and return another, which is the
     * failure this whole class exists to prevent. Unlike the bounded version this
     * replaces, it accepts and returns values outside the visible range.</p>
     */
    public static float offsetForIndex(int virtualIndex, int rowHeightPx) {
        return virtualIndex * (float) rowHeightPx;
    }

    /**
     * The virtual index nearest a scroll offset, never clamped to the run.
     *
     * <p>Nearest rather than truncated: a wheel resting half a row past a value
     * must land on the value it is closest to, not the one behind it. There is no
     * upper or lower clamp, because the index space is the cycle and has no ends.</p>
     */
    public static int nearestIndex(float offsetPx, int rowHeightPx) {
        if (rowHeightPx <= 0) {
            return 0;
        }
        return Math.round(offsetPx / rowHeightPx);
    }

    private float offsetForIndex(int virtualIndex) {
        return offsetForIndex(virtualIndex, rowHeightPx);
    }

    private int clamp(int candidate) {
        return Math.max(from, Math.min(to, candidate));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(UiKit.dp(getContext(), 72), widthMeasureSpec);
        int wanted = rowHeightPx * VISIBLE_ROWS;
        int height = resolveSize(wanted, heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (flingScroller == null) {
            flingScroller = new android.widget.OverScroller(getContext(), flingEasing);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int centre = getHeight() / 2;
        int row = rowHeightPx;

        // The pill first: it is the one thing that must be behind everything else,
        // and drawing it from the centre outwards is what makes it line up with the
        // selected number instead of with the scroll position.
        float half = row / 2f;
        canvas.drawRoundRect(0f, centre - half, getWidth(), centre + half,
                pillRadiusPx, pillRadiusPx, highlight);

        // The rows are addressed by virtual index, so the visible band is simply the
        // band of indices around the current one. There is no first or last row to
        // stop at, and no clamping here -- every index in the band has a legal value
        // through valueAt().
        int centreIndex = nearestIndex(scrollPx, row);
        int firstVisible = centreIndex - (VISIBLE_ROWS / 2 + OVERDRAW_ROWS);
        int lastVisible = centreIndex + (VISIBLE_ROWS / 2 + OVERDRAW_ROWS);
        float textSizeSelected = sp(22f);
        float textSizePlain = sp(18f);

        for (int i = firstVisible; i <= lastVisible; i++) {
            // Every row's y is derived from the same offset. There is no second
            // notion of "where the selection is" that could drift from this one.
            int y = centre + Math.round(i * row - scrollPx);
            boolean selected = i == index;
            paint.setTypeface(Typeface.create("sans-serif",
                    selected ? Typeface.BOLD : Typeface.NORMAL));
            paint.setColor(selected ? Palette.ON_PRIMARY : Palette.ON_SURFACE_VARIANT);
            paint.setTextSize(selected ? textSizeSelected : textSizePlain);
            paint.setTextAlign(Paint.Align.CENTER);

            // Fades the outermost rows so the column reads as a wheel rather than
            // as a list that happens to be cut off. Never applied to the selected
            // row, which stays full strength on its pill.
            int distance = Math.abs(y - centre);
            if (selected) {
                paint.setAlpha(255);
            } else if (distance > row * 2) {
                paint.setAlpha(70);
            } else if (distance > row) {
                paint.setAlpha(150);
            } else {
                paint.setAlpha(255);
            }
            canvas.drawText(label(valueAt(i)), getWidth() / 2f, y - centreTextOffset(),
                    paint);
        }
    }

    private float centreTextOffset() {
        // Centres the glyph box on the row rather than the baseline, so a row's
        // numbers sit on the same line whether or not it is the bold one.
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return (metrics.ascent + metrics.descent) / 2f;
    }

    private float sp(float sizeSp) {
        return android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp,
                getResources().getDisplayMetrics());
    }

    private String label(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    // ------------------------------------------------------------ accessibility

    /**
     * Reads out the value the wheel is sitting on.
     *
     * <p>The label is the padded two-digit form, so assistive technology says
     * "08" rather than "8" for a column where the two must not be confused. The
     * separator that turns an hour and a minute into a time belongs to the owner,
     * which is the only place that knows both halves.</p>
     */
    @Override
    public CharSequence getAccessibilityClassName() {
        return WheelColumnView.class.getName();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setContentDescription(label(value));
        event.setScrollable(true);
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Claiming the gesture on the way down is what stops the panel
                // from treating a drag as a scroll and the row under the finger
                // from also being clicked.
                getParent().requestDisallowInterceptTouchEvent(true);
                if (flingScroller != null && !flingScroller.isFinished()) {
                    flingScroller.abortAnimation();
                }
                dragging = true;
                dragStartY = event.getY();
                dragStartScroll = scrollPx;
                lastTouchY = event.getY();
                lastMoveTime = event.getEventTime();
                flingVelocity = 0f;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float delta = dragStartY - event.getY();
                setScroll(dragStartScroll + delta);
                long now = event.getEventTime();
                long elapsed = now - lastMoveTime;
                if (elapsed > 0) {
                    flingVelocity = (lastTouchY - event.getY()) * 1000f / elapsed;
                    lastMoveTime = now;
                    lastTouchY = event.getY();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                dragging = false;
                if (Math.abs(event.getY() - dragStartY) > touchSlop) {
                    fling();
                } else {
                    // A tap: the row under the finger is chosen and drawn to the
                    // centre. This is a shortcut, never the only way in -- dragging
                    // works on its own.
                    pendingTapIndex = nearestIndex(
                            scrollPx + event.getY() - getHeight() / 2f, rowHeightPx);
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                // The gesture was taken away mid-drag. Put the wheel back on the
                // value it is currently showing, immediately: a cancel that left
                // the column between two rows would show a number that is not the
                // one getValue() returns.
                dragging = false;
                if (flingScroller != null) {
                    flingScroller.abortAnimation();
                }
                snapToIndex(index);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * Moves to a scroll offset, and adopts whatever value that offset now shows.
     *
     * <p>Not clamped: the offset is a position in the cycle, and the cycle has no
     * ends. The value is not "capped at the last one" either -- it is whatever
     * {@link #valueAt} says the centre index means, which is how {@code 00} is
     * reached by scrolling up past {@code 23}.</p>
     */
    private void setScroll(float px) {
        scrollPx = px;
        int nowIndex = nearestIndex(scrollPx, rowHeightPx);
        if (nowIndex != index) {
            index = nowIndex;
            int nowValue = valueAt(nowIndex);
            if (nowValue != value) {
                value = nowValue;
                announce();
            }
        }
        invalidate();
    }

    private void fling() {
        if (flingScroller == null) {
            snapToIndex(index);
            return;
        }
        // A fling has to be given *some* bounds, because OverScroller has no
        // "unbounded" overload -- the four-argument form does not exist, and omitting
        // the range is not expressible. A cycle has no ends, so the bounds have to be
        // wide enough that no real flick can reach them while still containing the
        // current offset.
        //
        // That last clause is the one that matters, and getting it wrong is what made
        // an earlier attempt creep instead of glide: OverScroller checks
        // `start > max || start < min` first, and a start outside the range is not an
        // error -- it silently switches to "start after edge", which animates slowly
        // towards the boundary for as long as the animation runs. The range below is
        // therefore centred on the current offset and spans FLING_RANGE_ROWS either
        // side of it, far beyond any gesture, so the check always finds the start
        // inside it.
        int here = Math.round(scrollPx);
        int reach = rowHeightPx * FLING_RANGE_ROWS;
        flingScroller.fling(0, here, 0, Math.round(-flingVelocity),
                0, 0, here - reach, here + reach);
        dragging = false;
        // postInvalidateOnAnimation, not invalidate: this is the call that starts
        // the frame loop that computeScroll() rides. A plain invalidate() would
        // draw the frame we are already on and then stop, leaving the glide with
        // no frames to advance through.
        postInvalidateOnAnimation();
    }

    /**
     * Puts a virtual index under the centre line with no animation at all.
     *
     * <p>Used wherever the wheel must be provably on a value the instant the call
     * returns -- a cancelled gesture, or a fling with no scroller to animate. It
     * sets the index, the offset and the value together, so the number drawn and
     * the number returned cannot disagree even for one frame.</p>
     */
    private void snapToIndex(int targetIndex) {
        if (flingScroller != null) {
            flingScroller.abortAnimation();
        }
        index = targetIndex;
        scrollPx = offsetForIndex(targetIndex);
        int snapped = valueAt(targetIndex);
        boolean changed = snapped != value;
        value = snapped;
        invalidate();
        if (changed) {
            announce();
        }
    }

    /** Scrolls the wheel so a virtual index is under the centre, with easing. */
    private void settleToIndex(int targetIndex) {
        int destination = Math.round(offsetForIndex(targetIndex));
        if (flingScroller == null) {
            snapToIndex(targetIndex);
            return;
        }
        flingScroller.startScroll(0, Math.round(scrollPx), 0,
                destination - Math.round(scrollPx), 220);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (flingScroller == null) {
            return;
        }
        boolean animating = flingScroller.computeScrollOffset();
        if (animating) {
            setScroll(flingScroller.getCurrY());
            // Keep the frame loop alive for as long as the scroller has frames
            // left. Without this the animation advances exactly once per unrelated
            // invalidation and can stop anywhere, which is what "snap to centre"
            // must never be allowed to mean.
            postInvalidateOnAnimation();
            return;
        }
        // The scroller has run out. Land on a whole row by arithmetic rather than
        // trusting the animation to have finished on one: an offset a few pixels
        // out would leave the selected number visibly off centre.
        //
        // This is written as a direct assignment, not another startScroll(). A
        // second animation here would need its own frame pump, and any failure to
        // pump it would leave the wheel parked between two rows -- precisely the
        // stuck-off-centre state this class exists to make impossible. The final
        // position is arithmetic, so set it and be done.
        int settledIndex = nearestIndex(scrollPx, rowHeightPx);
        float target = offsetForIndex(settledIndex);
        if (scrollPx != target) {
            scrollPx = target;
            invalidate();
        }
        if (settledIndex != index) {
            index = settledIndex;
        }
        int settledValue = valueAt(settledIndex);
        if (settledValue != value) {
            value = settledValue;
            announce();
        }
    }

    /**
     * Called when a tap picks the row under the finger, or when an accessibility
     * service asks for a click.
     *
     * <p>Overriding {@code onTouchEvent} without this is what the platform's
     * accessibility guidance warns about: a service that activates the view sends
     * the click here, and a view that only listens for raw touches never answers.
     * The tap path and this one do the same thing -- choose the row under the
     * pointer, or leave the value alone when the call comes from a service.</p>
     */
    @Override
    public boolean performClick() {
        super.performClick();
        if (pendingTapIndex >= 0) {
            settleToIndex(pendingTapIndex);
            pendingTapIndex = -1;
        }
        return true;
    }

    private void announce() {
        if (onValueChanged != null) {
            onValueChanged.onValueChanged(value);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }
}

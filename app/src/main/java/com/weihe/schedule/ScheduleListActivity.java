package com.weihe.schedule;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * The list of bell schedules.
 *
 * <p>This is where "different buildings ring at different times" becomes
 * visible: each row shows the schedule, its first and last period, and how many
 * courses point at it by name. Rows open the editor for that single schedule;
 * the floating button adds a new one.</p>
 */
public class ScheduleListActivity extends Activity {
    private static final int REQUEST_EDIT = 2001;

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
        render();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK) {
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
        body.setPadding(dp(14), dp(12), dp(14), dp(96));

        summary = UiKit.text(this, "", UiTokens.Type.LABEL_LARGE, Palette.ON_SURFACE_VARIANT, false);
        body.addView(summary);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(10);
        body.addView(list, listParams);

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ImageButton add = UiKit.fab(this, R.drawable.ic_add,
                "\u65B0\u5EFA\u4F5C\u606F\u8868", view -> openEditor(null));
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
        return UiKit.detailAppBar(this, "\u4F5C\u606F\u65F6\u95F4",
                "\u4E0D\u540C\u6559\u5B66\u697C\u53EF\u4EE5\u4E0A\u4E0B\u8BFE\u65F6\u95F4\u4E0D\u540C",
                view -> finish());
    }

    private void render() {
        List<BellSchedule> schedules = repository.getBellSchedules();
        List<Course> courses = repository.getCourses();
        list.removeAllViews();
        summary.setText("\u5171 " + schedules.size()
                + " \u4E2A\u4F5C\u606F\u8868 \u00B7 \u70B9\u51FB\u4FEE\u6539\uFF0C\u957F\u6309\u5220\u9664");

        for (BellSchedule schedule : schedules) {
            list.addView(scheduleCard(schedule, usersOf(schedule, courses)));
        }
    }

    private int usersOf(BellSchedule schedule, List<Course> courses) {
        int users = 0;
        for (Course course : courses) {
            if (schedule.name().equals(course.scheduleName)) {
                users++;
            }
        }
        return users;
    }

    private View scheduleCard(final BellSchedule schedule, int users) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(14));
        card.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L,
                Palette.OUTLINE_VARIANT, 1));
        card.setElevation(dp(1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(UiKit.text(this, schedule.name(), UiTokens.Type.TITLE_MEDIUM, Palette.ON_SURFACE, true),
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // "Default" is the schedule the app falls back to, not whichever happens to
        // be stored first: the badge moved with the array before, so any future
        // reordering would have re-labelled the wrong row.
        if (BellSchedules.STANDARD_NAME.equals(schedule.name())) {
            titleRow.addView(badge("\u9ED8\u8BA4", Palette.PRIMARY));
        }
        card.addView(titleRow);

        TextView span = UiKit.text(this,
                "\u7B2C1\u8282 " + spanOf(schedule, 1)
                        + "   \u00B7   \u5171 " + schedule.periodCount() + " \u8282",
                12, Palette.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams spanParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        spanParams.topMargin = dp(6);
        card.addView(span, spanParams);

        TextView last = UiKit.text(this,
                "\u672B\u8282 " + spanOf(schedule, schedule.periodCount()),
                12, Palette.ON_SURFACE_VARIANT, false);
        LinearLayout.LayoutParams lastParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lastParams.topMargin = dp(3);
        card.addView(last, lastParams);

        String venue = schedule.locationKeywords().isEmpty()
                ? "\u4E0D\u6309\u5730\u70B9\u5339\u914D\uFF0C\u9700\u8981\u5728\u8BFE\u7A0B\u91CC\u624B\u52A8\u9009\u62E9"
                : "\u9002\u7528\u4E8E\uFF1A" + String.join("\u3001",
                        schedule.locationKeywords());
        TextView keywords = UiKit.text(this, venue + "   \u00B7   "
                + users + " \u95E8\u8BFE\u6307\u5B9A", 11,
                schedule.locationKeywords().isEmpty() ? Palette.OUTLINE : Palette.ON_SURFACE_VARIANT,
                false);
        LinearLayout.LayoutParams keywordParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        keywordParams.topMargin = dp(8);
        card.addView(keywords, keywordParams);

        card.setOnClickListener(view -> openEditor(schedule.name()));
        card.setOnLongClickListener(view -> {
            confirmDelete(schedule);
            return true;
        });
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        return card;
    }

    private String spanOf(BellSchedule schedule, int period) {
        return schedule.timeRange(period, period);
    }

    private TextView badge(String label, int color) {
        return UiKit.pill(this, label, UiTokens.Type.LABEL_SMALL, color,
                Palette.ON_PRIMARY, true, UiTokens.RADIUS_S);
    }

    private void openEditor(String scheduleName) {
        Intent intent = new Intent(this, EditScheduleActivity.class);
        intent.putExtra(EditScheduleActivity.EXTRA_NAME, scheduleName);
        startActivityForResult(intent, REQUEST_EDIT);
    }

    private void confirmDelete(final BellSchedule schedule) {
        // Checked here and in the repository: this tells the user why nothing
        // happened, the repository is what actually guarantees it.
        if (repository.getBellSchedules().size() <= 1) {
            Toast.makeText(this, "\u81F3\u5C11\u4FDD\u7559\u4E00\u4E2A\u4F5C\u606F\u8868",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int users = usersOf(schedule, repository.getCourses());
        String message = "\u786E\u5B9A\u5220\u9664\u201C" + schedule.name() + "\u201D\u5417\uFF1F";
        if (users > 0) {
            message += "\n\u6709 " + users
                    + " \u95E8\u8BFE\u7A0B\u6307\u5B9A\u4E86\u5B83\uFF0C\u5220\u9664\u540E\u8FD9\u4E9B\u8BFE\u7A0B\u5C06\u6539\u4E3A"
                    + "\u6309\u4E0A\u8BFE\u5730\u70B9\u81EA\u52A8\u5339\u914D\u3002";
        }
        UiKit.dialog(this, "\u5220\u9664\u4F5C\u606F\u8868", message, null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u5220\u9664\u4F5C\u606F\u8868", () -> {
                    if (repository.deleteBellSchedule(schedule.name())) {
                        render();
                        Toast.makeText(this, "\u4F5C\u606F\u8868\u5DF2\u5220\u9664", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

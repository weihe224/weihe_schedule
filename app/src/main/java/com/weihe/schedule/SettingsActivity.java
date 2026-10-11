package com.weihe.schedule;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.data.BackupCodec;
import com.weihe.schedule.data.BackupPreview;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.importer.ExcelTimetableReader;
import com.weihe.schedule.importer.TimetableImportException;
import com.weihe.schedule.importer.TimetableImportParser;
import com.weihe.schedule.reminder.ReminderNotifications;
import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.reminder.ReminderSettings;
import com.weihe.schedule.rule.ReminderPlan;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * Everything that is not the timetable itself.
 *
 * <p>This replaced a three-item {@code AlertDialog}: importing, the bell
 * schedules and the semester start are each a different kind of decision, and
 * squashing them into a menu of verbs hid what the current state actually is.
 * Here every row states its value first -- how many courses were imported, how
 * many schedules there are, which Monday the term starts on -- and only then
 * offers the action.</p>
 */
public class SettingsActivity extends Activity {
    private static final int REQUEST_IMPORT_TIMETABLE = 3001;
    private static final int REQUEST_EXPORT_BACKUP = 3002;
    private static final int REQUEST_PREVIEW_BACKUP = 3003;

    /** Asking for POST_NOTIFICATIONS, which only exists from Android 13. */
    private static final int REQUEST_NOTIFICATIONS = 3004;

    /**
     * The backup checked and waiting for the user to choose a destination.
     *
     * <p>Built before the picker opens so that the slow part -- writing the file
     * -- is the only thing left to do afterwards, and so a cancelled picker costs
     * nothing. Cleared as soon as the result arrives either way.</p>
     */
    private BackupCodec.Document pendingBackupDocument;
    private TextView backupValue;

    private CourseRepository repository;
    private TextView semesterValue;
    private TextView semesterDetail;
    private TextView semesterStartValue;
    private TextView semesterStartDetail;
    private TextView schedulesValue;
    private TextView coursesValue;
    private TextView weekValue;

    private UiKit.Check reminderCheck;
    private TextView reminderStatus;
    private TextView reminderPermissionAction;
    private List<TextView> reminderLeadChips;

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
        if (semesterValue != null) {
            // Re-read storage; another screen may have changed it. See
            // CourseRepository#reload.
            repository.reload();
            refresh();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_TIMETABLE
                && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importTimetable(uri);
            }
        }
        if (requestCode == REQUEST_EXPORT_BACKUP) {
            // The payload was built before the picker opened; whichever way this
            // came back, it is not needed any more.
            BackupCodec.Document document = pendingBackupDocument;
            pendingBackupDocument = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                // Cancelled: nothing was written anywhere, so say nothing.
                return;
            }
            if (document != null) {
                writeBackup(data.getData(), document);
            }
        }
        if (requestCode == REQUEST_PREVIEW_BACKUP
                && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                previewBackup(uri);
            }
        }
    }

    // ---------------------------------------------------------------- layout

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.SURFACE);
        root.addView(createHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(20));

        body.addView(sectionLabel("\u8BFE\u8868\u6570\u636E"));
        body.addView(createImportCard());
        body.addView(createBackupCard(), margins(dp(10)));

        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.topMargin = dp(16);
        body.addView(sectionLabel("\u5B66\u671F"), gap);
        body.addView(createSemesterCard());

        body.addView(sectionLabel("\u5F53\u524D\u5B66\u671F\u7684\u7B2C\u4E00\u5468"),
                margins(dp(16)));
        body.addView(createSemesterStartCard());

        body.addView(sectionLabel("\u4E0A\u4E0B\u8BFE\u65F6\u95F4"),
                margins(dp(16)));
        body.addView(createScheduleCard());

        body.addView(sectionLabel("\u4E0A\u8BFE\u63D0\u9192"), margins(dp(16)));
        body.addView(createReminderCard());

        body.addView(createAboutCard(), margins(dp(20)));

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View createHeader() {
        return UiKit.detailAppBar(this, "\u8BBE\u7F6E",
                "\u8BFE\u8868\u6570\u636E\u3001\u5B66\u671F\u4E0E\u4F5C\u606F\u65F6\u95F4",
                view -> finish());
    }

    private TextView sectionLabel(String text) {
        return UiKit.sectionLabel(this, text);
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        return params;
    }

    /** A card with a title, a value and an optional action row. */
    private LinearLayout card() {
        return UiKit.card(this);
    }

    private void addTitle(LinearLayout card, int icon, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.ImageView image = new android.widget.ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(Palette.PRIMARY);
        row.addView(image, new LinearLayout.LayoutParams(
                dp(UiTokens.ICON_SIZE), dp(UiTokens.ICON_SIZE)));

        TextView label = UiKit.text(this, title, UiTokens.Type.TITLE_SMALL,
                Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(UiTokens.SPACE_S + 2);
        row.addView(label, labelParams);
        card.addView(row);
    }

    private TextView valueLine(String text, int topMargin) {
        TextView value = UiKit.text(this, text, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, Typeface.NORMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        value.setLayoutParams(params);
        return value;
    }

    private TextView hintLine(String text, int topMargin) {
        TextView hint = UiKit.caption(this, text);
        hint.setTextColor(Palette.OUTLINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        hint.setLayoutParams(params);
        return hint;
    }

    private View createImportCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_add, "\u6279\u91CF\u5BFC\u5165\u8BFE\u8868");
        coursesValue = valueLine("", 11);
        card.addView(coursesValue);
        card.addView(hintLine("\u8BFB\u53D6\u6559\u52A1\u7CFB\u7EDF\u5BFC\u51FA\u7684 .xls \u8BFE\u8868\uFF0C"
                + "\u5BFC\u5165\u4F1A\u66FF\u6362\u5F53\u524D\u5168\u90E8\u8BFE\u7A0B\u3002", 6));
        card.addView(actionRow(
                action("\u9009\u62E9\u6587\u4EF6\u5BFC\u5165", Palette.PRIMARY, true,
                        view -> openImportPicker())));
        return card;
    }

    private View createBackupCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_semester, "\u5907\u4EFD\u4E0E\u6062\u590D");
        backupValue = valueLine("", 11);
        card.addView(backupValue);
        card.addView(hintLine("\u5BFC\u51FA\u4E00\u4EFD JSON \u5907\u4EFD\u6587\u4EF6\uFF0C"
                + "\u5305\u542B\u5168\u90E8\u5B66\u671F\u7684\u8BFE\u7A0B\u3001\u4F5C\u606F"
                + "\u8868\u4E0E\u8BBE\u7F6E\u3002", 6));
        card.addView(hintLine("\u5907\u4EFD\u662F\u660E\u6587\uFF0C\u542B\u6559\u5E08\u59D3"
                + "\u540D\u4E0E\u4E0A\u8BFE\u5730\u70B9\uFF0C\u8BF7\u5B58\u653E\u5728\u81EA"
                + "\u5DF1\u4FE1\u4EFB\u7684\u4F4D\u7F6E\u3002", 2));
        card.addView(actionRow(
                action("\u5BFC\u51FA\u5907\u4EFD", Palette.PRIMARY, true,
                        view -> exportBackup()),
                action("\u4ECE\u5907\u4EFD\u9884\u89C8", Palette.PRIMARY, false,
                        view -> openBackupPicker())));
        return card;
    }

    // ----------------------------------------------------------------- backup

    /**
     * Builds the backup, checks it, then asks the user where to put it.
     *
     * <p>The payload is produced and re-parsed <em>before</em> the picker opens,
     * so a cancelled picker costs nothing and a payload that would not survive a
     * round trip is never written to a file. Everything here is a read: no
     * storage key is touched.</p>
     */
    private void exportBackup() {
        BackupCodec.Document document;
        try {
            document = buildBackup();
        } catch (RuntimeException error) {
            UiKit.message(this, "\u5BFC\u51FA\u5931\u8D25",
                    "\u8BFB\u53D6\u5F53\u524D\u8BFE\u8868\u65F6\u51FA\u9519\uFF1A" + error);
            return;
        }
        // Self-check: the very bytes that will be written are parsed back and
        // validated before the user is asked where to put them.
        BackupCodec.Result check = BackupCodec.parse(BackupCodec.write(document));
        if (!check.isOk()) {
            UiKit.message(this, "\u5BFC\u51FA\u5931\u8D25",
                    "\u751F\u6210\u7684\u5907\u4EFD\u672A\u901A\u8FC7\u81EA\u68C0\uFF1A"
                            + check.problem());
            return;
        }
        pendingBackupDocument = document;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, backupFileName());
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
    }

    /** Reads everything worth keeping out of the current timetable. */
    private BackupCodec.Document buildBackup() {
        List<Semester> semesters = repository.getSemesters();
        Map<Long, List<Course>> coursesBySemester = new LinkedHashMap<>();
        for (Semester semester : semesters) {
            coursesBySemester.put(Long.valueOf(semester.id()),
                    repository.coursesOf(semester));
        }
        // getBellSchedules hands out its cached list rather than a copy, so take
        // one before the document is built.
        List<BellSchedule> schedules =
                BellSchedules.copyOf(repository.getBellSchedules());
        return BackupCodec.of(semesters, coursesBySemester, schedules,
                repository.getActiveSemester().id(),
                OffsetDateTime.now().toString(), appVersionName());
    }

    /** Writes the already-checked backup to wherever the user pointed. */
    private void writeBackup(Uri uri, BackupCodec.Document document) {
        final String name = displayNameOf(uri);
        new Thread(() -> {
            String failure = null;
            try (OutputStream output =
                         getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    failure = "\u65E0\u6CD5\u5199\u5165\u6240\u9009\u4F4D\u7F6E";
                } else {
                    BackupCodec.writeTo(document, output);
                }
            } catch (IOException error) {
                failure = writeFailureReason(error);
            } catch (SecurityException denied) {
                failure = "\u6CA1\u6709\u5199\u5165\u8BE5\u4F4D\u7F6E\u7684\u6743\u9650";
            }
            final String problem = failure;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (problem != null) {
                    UiKit.message(this, "\u5BFC\u51FA\u5931\u8D25", problem);
                    return;
                }
                Toast.makeText(this, "\u5DF2\u5BFC\u51FA "
                        + BackupPreview.of(name, document).summary()
                        + (name.isEmpty() ? "" : "\n" + name),
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** Picks a backup file and opens the read-only preview for it. */
    private void openBackupPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/json", "text/plain", "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_PREVIEW_BACKUP);
    }

    /**
     * Opens the preview screen for the picked file.
     *
     * <p>Nothing is read, parsed or written here: the screen is handed the
     * document and does the rest itself, so there is exactly one place that reads
     * a backup file and exactly one place that decides what it means.</p>
     */
    private void previewBackup(Uri uri) {
        Intent intent = new Intent(this, BackupPreviewActivity.class);
        intent.putExtra(BackupPreviewActivity.EXTRA_URI, uri.toString());
        intent.putExtra(BackupPreviewActivity.EXTRA_FILE_NAME, displayNameOf(uri));
        startActivity(intent);
    }

    private String backupFileName() {
        return "weihe-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                .format(LocalDateTime.now()) + ".json";
    }

    private String appVersionName() {
        try {
            String name = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (android.content.pm.PackageManager.NameNotFoundException absent) {
            // Only reachable if the package is being uninstalled underneath us.
            return "";
        }
    }

    /** The file name the picker showed, for the confirmation message. */
    private static String displayNameOf(Uri uri) {
        String segment = uri == null ? null : uri.getLastPathSegment();
        if (segment == null) {
            return "";
        }
        int slash = segment.lastIndexOf('/');
        return slash >= 0 ? segment.substring(slash + 1) : segment;
    }

    private static String writeFailureReason(IOException error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "\u5199\u5165\u6587\u4EF6\u5931\u8D25\uFF0C\u8BF7\u6362\u4E00\u4E2A"
                    + "\u4F4D\u7F6E\u518D\u8BD5";
        }
        return "\u5199\u5165\u6587\u4EF6\u5931\u8D25\uFF1A" + message
                + "\n\u82E5\u662F\u5B58\u50A8\u7A7A\u95F4\u4E0D\u8DB3\uFF0C\u8BF7\u6E05"
                + "\u7406\u540E\u91CD\u8BD5\u3002";
    }

    /** Which term is on screen, and the way to look at another one. */
    private View createSemesterCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_semester, "\u5B66\u671F");
        semesterValue = valueLine("", 11);
        card.addView(semesterValue);
        semesterDetail = hintLine("", 6);
        card.addView(semesterDetail);
        card.addView(actionRow(
                action("\u67E5\u770B\u4E0E\u5207\u6362\u5B66\u671F", Palette.PRIMARY, true,
                        view -> startActivity(new Intent(this,
                                SemesterListActivity.class)))));
        return card;
    }

    private View createSemesterStartCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_semester, "\u5F00\u5B66\u7B2C\u4E00\u5468\u5468\u4E00");
        semesterStartValue = valueLine("", 11);
        card.addView(semesterStartValue);
        semesterStartDetail = hintLine("", 6);
        card.addView(semesterStartDetail);
        card.addView(actionRow(
                action("\u524D\u79FB\u4E00\u5468", Palette.INK, false,
                        view -> shiftSemesterStart(-1)),
                action("\u540E\u79FB\u4E00\u5468", Palette.INK, false,
                        view -> shiftSemesterStart(1)),
                action("\u8F93\u5165\u65E5\u671F", Palette.PRIMARY, true,
                        view -> editSemesterStart())));
        return card;
    }

    private View createScheduleCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_settings, "\u4F5C\u606F\u65F6\u95F4\u4E0E\u8282\u6B21");
        schedulesValue = valueLine("", 11);
        card.addView(schedulesValue);
        card.addView(hintLine("\u4E0D\u540C\u6559\u5B66\u697C\u53EF\u4EE5\u4E0A\u4E0B\u8BFE\u65F6\u95F4\u4E0D\u540C\uFF0C"
                + "\u8BFE\u7A0B\u53EF\u6309\u5730\u70B9\u81EA\u52A8\u5339\u914D\u3002", 6));
        card.addView(actionRow(
                action("\u7BA1\u7406\u4F5C\u606F\u8868", Palette.PRIMARY, true,
                        view -> startActivity(new Intent(this,
                                ScheduleListActivity.class)))));
        return card;
    }

    /**
     * The reminder settings: on or off, how early, and whether it can work.
     *
     * <p>The card always says which of three states it is in -- off, on, or on but
     * unable to show anything -- rather than a switch that looks on while nothing
     * will arrive. That third state is the one worth the space: on Android 13 and
     * later the permission can be refused, and a reminder feature that quietly
     * does nothing is worse than one that says why.</p>
     */
    private View createReminderCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_today, "\u4E0A\u8BFE\u63D0\u9192");

        reminderCheck = UiKit.checkbox(this, "\u5F00\u542F\u4E0A\u8BFE\u63D0\u9192",
                ReminderSettings.isEnabled(this));
        reminderCheck.onChanged(this::onReminderEnabledChanged);
        card.addView(reminderCheck.view(), margins(11));

        reminderStatus = valueLine("", 6);
        card.addView(reminderStatus);

        LinearLayout leadRow = new LinearLayout(this);
        leadRow.setOrientation(LinearLayout.HORIZONTAL);
        leadRow.setGravity(Gravity.CENTER_VERTICAL);
        reminderLeadChips = new ArrayList<>();
        int current = ReminderSettings.leadMinutes(this);
        int[] choices = leadChoices();
        for (int index = 0; index < choices.length; index++) {
            final int minutes = choices[index];
            TextView chip = UiKit.chip(this, minutes + " \u5206\u949F", minutes == current);
            chip.setOnClickListener(view -> onLeadMinutesChosen(minutes));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(UiTokens.CHIP_HEIGHT));
            if (index > 0) {
                params.leftMargin = dp(UiTokens.SPACE_S);
            }
            leadRow.addView(chip, params);
            reminderLeadChips.add(chip);
        }
        LinearLayout.LayoutParams leadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        leadParams.topMargin = dp(UiTokens.SPACE_L);
        card.addView(leadRow, leadParams);

        // Said plainly, because it is the price of not asking for the exact-alarm
        // permission: a reminder is not late by design, but it can be late.
        card.addView(hintLine("\u63D0\u9192\u4E0D\u65E9\u4E8E\u8BBE\u5B9A\u65F6\u95F4\uFF1B\u8BBE\u5907\u957F\u65F6\u95F4\u5F85\u673A\u65F6\u53EF\u80FD\u665A\u51E0\u5206\u949F\u3002", 8));

        reminderPermissionAction = action("\u53BB\u5F00\u542F\u901A\u77E5\u6743\u9650", Palette.PRIMARY, true,
                view -> openNotificationSettings());
        card.addView(actionRow(reminderPermissionAction));
        return card;
    }

    /** The three lead times the feature offers, taken from the planner's own range. */
    private static int[] leadChoices() {
        return new int[] {
                ReminderPlan.MIN_LEAD_MINUTES,
                ReminderPlan.DEFAULT_LEAD_MINUTES,
                ReminderPlan.MAX_LEAD_MINUTES
        };
    }

    private void onReminderEnabledChanged() {
        boolean wanted = reminderCheck.isChecked();
        ReminderSettings.setEnabled(this, wanted);
        if (wanted) {
            // Asked for here, from a screen the user is looking at. A broadcast
            // receiver must never raise a permission prompt, and neither should a
            // launch the user did not connect with reminders.
            requestNotificationPermissionIfNeeded();
        } else {
            ReminderScheduler.cancelAll(this);
        }
        ReminderScheduler.sync(this);
        refreshReminderCard();
    }

    private void onLeadMinutesChosen(int minutes) {
        if (ReminderSettings.leadMinutes(this) == minutes) {
            return;
        }
        ReminderSettings.setLeadMinutes(this, minutes);
        // sync() cancels every registered alarm before planning again, so the old
        // lead time cannot survive beside the new one.
        ReminderScheduler.sync(this);
        refreshReminderCard();
    }

    private void refreshReminderCard() {
        if (reminderStatus == null) {
            return;
        }
        boolean enabled = ReminderSettings.isEnabled(this);
        boolean allowed = ReminderNotifications.canPost(this);
        int minutes = ReminderSettings.leadMinutes(this);
        int[] choices = leadChoices();
        for (int index = 0; index < reminderLeadChips.size(); index++) {
            UiKit.styleChip(reminderLeadChips.get(index), choices[index] == minutes);
        }
        if (!enabled) {
            reminderStatus.setText("\u5DF2\u5173\u95ED");
            reminderStatus.setTextColor(Palette.ON_SURFACE_VARIANT);
        } else if (!allowed) {
            reminderStatus.setText("\u901A\u77E5\u6743\u9650\u672A\u5F00\u542F\uFF0C\u63D0\u9192\u4E0D\u4F1A\u663E\u793A");
            reminderStatus.setTextColor(Palette.ERROR);
        } else {
            reminderStatus.setText("\u5DF2\u5F00\u542F \u00B7 \u63D0\u524D " + minutes + " \u5206\u949F \u00B7 \u5DF2\u5B89\u6392 "
                    + ReminderScheduler.registeredCount(this) + " \u6761\u63D0\u9192");
            reminderStatus.setTextColor(Palette.ON_SURFACE);
        }
        reminderPermissionAction.setVisibility(
                enabled && !allowed ? View.VISIBLE : View.GONE);
    }

    /** Whether the app may post at all. The permission only exists from Android 13. */
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Before Android 13 there is no such permission to ask for, so the
            // check is written here and not only inside hasNotificationPermission:
            // the guard belongs beside the call it protects.
            return;
        }
        if (hasNotificationPermission()) {
            return;
        }
        requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                REQUEST_NOTIFICATIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        // Either way the timetable is unaffected: the switch stays where the user
        // put it, sync() registers nothing while posting is impossible, and the
        // status line says so.
        ReminderScheduler.sync(this);
        refreshReminderCard();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (RuntimeException unavailable) {
            // No settings screen to open on this device; the status line already
            // said what is wrong.
            Toast.makeText(this, "\u65E0\u6CD5\u6253\u5F00\u7CFB\u7EDF\u901A\u77E5\u8BBE\u7F6E", Toast.LENGTH_SHORT).show();
        }
    }

    private View createAboutCard() {
        LinearLayout card = card();
        card.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        addTitle(card, R.drawable.ic_grid, "\u5173\u4E8E");
        weekValue = valueLine("", 11);
        card.addView(weekValue);
        card.addView(hintLine("\u6570\u636E\u53EA\u5B58\u5728\u672C\u673A\uFF08SharedPreferences\uFF09\uFF0C"
                + "\u5378\u8F7D\u5E94\u7528\u4F1A\u4E00\u5E76\u5220\u9664\u3002", 6));
        return card;
    }

    /** Buttons in a card, side by side, sharing the row equally. */
    private View actionRow(View... actions) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(UiTokens.SPACE_M);
        row.setLayoutParams(rowParams);
        for (int i = 0; i < actions.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(UiTokens.BUTTON_HEIGHT_COMPACT), 1f);
            if (i > 0) {
                params.leftMargin = dp(UiTokens.SPACE_S);
            }
            row.addView(actions[i], params);
        }
        return row;
    }

    /**
     * A card action.
     *
     * @param filled true for the one action the card is about, false for the
     *               alternatives beside it
     */
    private TextView action(String label, int color, boolean filled,
            View.OnClickListener listener) {
        if (filled) {
            return UiKit.filledButton(this, label, listener);
        }
        return UiKit.outlinedButton(this, label, listener);
    }

    // ----------------------------------------------------------------- state

    private void refresh() {
        refreshReminderCard();
        Semester active = repository.getActiveSemester();
        List<Semester> semesters = repository.getSemesters();
        List<Course> courses = repository.getCourses();
        List<BellSchedule> schedules = repository.getBellSchedules();
        LocalDate start = repository.getSemesterStart();
        int currentWeek = ScheduleUtils.currentWeek(start, LocalDate.now());
        boolean inSession = !LocalDate.now().isBefore(active.start())
                && !LocalDate.now().isAfter(active.lastDay());

        coursesValue.setText(courses.isEmpty()
                ? "\u5F53\u524D\u6CA1\u6709\u8BFE\u7A0B"
                : "\u5F53\u524D\u5171 " + courses.size() + " \u6761\u5B89\u6392");

        semesterValue.setText("\u5F53\u524D\uFF1A" + active.label());
        semesterDetail.setText("\u5171 " + semesters.size() + " \u4E2A\u5B66\u671F \u00B7 "
                + ScheduleUtils.formatDate(active.start()) + " - "
                + ScheduleUtils.formatDate(active.lastDay())
                + (inSession ? " \u00B7 \u8FDB\u884C\u4E2D" : " \u00B7 \u5DF2\u7ED3\u675F"));

        semesterStartValue.setText(ScheduleUtils.formatFullDate(start));
        semesterStartDetail.setText("\u7B2C 1 \u5468\uFF1A" + start
                + " \u00B7 \u5171 " + ScheduleUtils.SEMESTER_WEEKS
                + " \u5468 \u00B7 \u4ECA\u5929\u662F\u7B2C " + Math.min(currentWeek,
                        ScheduleUtils.SEMESTER_WEEKS) + " \u5468");

        schedulesValue.setText(schedules.size() + " \u4E2A\u4F5C\u606F\u8868 \u00B7 "
                + "\u6BCF\u8282 " + BellSchedule.PERIODS + " \u8282");
        backupValue.setText("\u5F85\u5BFC\u51FA\uFF1A" + semesters.size() + " \u4E2A\u5B66\u671F"
                + " \u00B7 " + courses.size() + " \u95E8\u8BFE\u7A0B \u00B7 "
                + schedules.size() + " \u5F20\u4F5C\u606F\u8868");
        weekValue.setText("WeiheSchedule 1.0 \u00B7 minSdk 26");
    }

    /**
     * Moves week 1 of the semester currently on screen.
     *
     * <p>Rejected when it would move the active semester past today, because
     * "the term has not started yet" is not a useful state to edit into; a
     * future semester is created from the semester screen instead.</p>
     */
    private void shiftSemesterStart(int weeks) {
        LocalDate start = repository.getSemesterStart();
        if (weeks < 0 && ScheduleUtils.currentWeek(start, LocalDate.now()) <= 1) {
            Toast.makeText(this, "\u5DF2\u7ECF\u662F\u7B2C 1 \u5468\u4E86",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        applySemesterStart(start.plusWeeks(weeks));
    }

    private void editSemesterStart() {
        final LocalDate start = repository.getSemesterStart();
        final EditText input = UiKit.field(this, "yyyy-MM-dd", false);
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        input.setText(start.toString());
        input.setSelection(input.getText().length());
        input.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M));

        UiKit.dialog(this, "\u5F00\u5B66\u7B2C\u4E00\u5468\u5468\u4E00",
                "\u683C\u5F0F\uFF1A2026-02-23\u3002\u4E0D\u662F\u5468\u4E00\u4F1A\u81EA\u52A8"
                        + "\u5F52\u5230\u5F53\u5468\u5468\u4E00\u3002",
                input, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.validated("\u4FDD\u5B58", () -> {
                    LocalDate parsed = ScheduleUtils.parseDate(
                            input.getText().toString());
                    if (parsed == null) {
                        input.setError("\u65E5\u671F\u683C\u5F0F\u5E94\u4E3A 2026-02-23");
                        input.requestFocus();
                        return false;
                    }
                    applySemesterStart(parsed);
                    return true;
                }));
    }

    private void applySemesterStart(LocalDate date) {
        LocalDate monday = ScheduleUtils.mondayOf(date);
        repository.setSemesterStart(monday);
        refresh();
        Toast.makeText(this, "\u5F00\u5B66\u65E5\u5DF2\u8BBE\u4E3A "
                + ScheduleUtils.formatFullDate(monday), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- import

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
        java.util.Set<String> names = new java.util.HashSet<>();
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
        content.setPadding(dp(UiTokens.SPACE_XL - 2), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_XL - 2), 0);
        String summary = "\u8BC6\u522B\u5230 " + names.size() + " \u95E8\u8BFE\u7A0B\uFF0C"
                + "\u5171 " + courses.size() + " \u6761\u5B89\u6392";
        content.addView(UiKit.title(this, summary));
        content.addView(hintLine(
                "\u5BFC\u5165\u5C06\u8986\u76D6\u5F53\u524D\u8BFE\u8868", UiTokens.SPACE_XS + 1));

        TextView label = UiKit.text(this,
                "\u8BFE\u8868\u7B2C 1 \u5468\u5BF9\u5E94\u672C\u5B66\u671F\uFF1A",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(UiTokens.SPACE_L);
        content.addView(label, labelParams);

        android.widget.Spinner startWeekSpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, starts);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        startWeekSpinner.setAdapter(adapter);
        startWeekSpinner.setSelection(0);
        startWeekSpinner.setContentDescription("\u9009\u62E9\u5BFC\u5165\u8D77\u59CB\u5468");
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        spinnerParams.topMargin = dp(4);
        content.addView(startWeekSpinner, spinnerParams);

        content.addView(hintLine("\u6587\u4EF6\u4E2D\u6700\u540E\u4E00\u5468\u4E3A\u7B2C"
                + lastSourceWeek + "\u5468\uFF0C\u5DF2\u8FC7\u6EE4\u4F1A\u8D85\u51FA"
                + ScheduleUtils.SEMESTER_WEEKS + "\u5468\u7684\u9009\u9879", 5));

        UiKit.dialog(this, "\u5BFC\u5165\u8BFE\u8868", null, content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u8986\u76D6\u5BFC\u5165", () -> {
                    int sourceFirstWeek = startWeekSpinner
                            .getSelectedItemPosition() + 1;
                    try {
                        List<Course> shifted = TimetableImportParser.shiftWeeks(
                                courses, sourceFirstWeek);
                        repository.replaceCourses(shifted);
                        refresh();
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

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

package com.weihe.schedule;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.view.Gravity;
import android.text.InputType;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.data.BackupCodec;
import com.weihe.schedule.data.BackupService;
import com.weihe.schedule.data.BackupPreview;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.data.DamagedCourseCopy;
import com.weihe.schedule.importer.ExcelTimetableReader;
import com.weihe.schedule.importer.TimetableImportException;
import com.weihe.schedule.importer.TimetableImportParser;
import com.weihe.schedule.reminder.ReminderNotifications;
import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.reminder.ReminderSettings;
import com.weihe.schedule.rule.ReminderPlan;
import com.weihe.schedule.rule.SemesterWeeks;
import com.weihe.schedule.rule.SemesterSummary;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.Pickers;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;
import com.weihe.schedule.widget.CourseWidgetUpdater;

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

    /** Where to put the copy of a course payload that could not be read. */
    private static final int REQUEST_EXPORT_DAMAGED = 3005;

    /**
     * The backup checked and waiting for the user to choose a destination.
     *
     * <p>Built before the picker opens so that the slow part -- writing the file
     * -- is the only thing left to do afterwards, and so a cancelled picker costs
     * nothing. Cleared as soon as the result arrives either way.</p>
     */
    private BackupCodec.Document pendingBackupDocument;
    private TextView backupValue;

    /**
     * The damaged copy read and waiting for a destination.
     *
     * <p>Held the same way the backup is, and for the same reason: the text is
     * taken before the picker opens, so cancelling writes nothing and a payload
     * that changed underneath cannot be exported by halves.</p>
     */
    private DamagedCourseCopy pendingDamagedCopy;
    private LinearLayout damagedCopyCard;
    private TextView damagedCopyValue;
    private View damagedCopyAction;

    private CourseRepository repository;
    private BackupService backupService;
    private TextView semesterValue;
    private TextView semesterDetail;
    private TextView semesterStartValue;
    private TextView semesterStartDetail;
    private TextView schedulesValue;
    private TextView coursesValue;

    /** The undo entry in the import card; shown only when there is one. */
    private LinearLayout undoImportSection;

    private UiKit.Check reminderCheck;
    private TextView reminderStatus;
    private TextView reminderPermissionAction;
    private View reminderPermissionRow;
    private List<TextView> reminderLeadChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CourseRepository(this);
        backupService = new BackupService(this::runOnUiThread);
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
            // Rebuild the alarms before drawing them. The plan can have gone stale
            // for reasons this screen cannot observe -- a course edited on another
            // screen, notifications granted in system settings, a backup restored --
            // and the card below reports how many alarms exist. Reading a count
            // without re-planning is how a screen ends up saying "0 条提醒" next to a
            // switch that is on.
            ReminderScheduler.sync(this);
            // The widget reads the same timetable and goes stale on the same events;
            // returning to the app is the moment anything done elsewhere becomes
            // visible on the home screen.
            CourseWidgetUpdater.refreshAll(this);
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
        if (requestCode == REQUEST_EXPORT_DAMAGED) {
            DamagedCourseCopy copy = pendingDamagedCopy;
            pendingDamagedCopy = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                // Cancelled: the copy stays where it is and nothing is written.
                return;
            }
            if (copy != null) {
                writeDamagedCopy(data.getData(), copy);
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

        // Ordered by how often each item is reached for. The term on screen and the
        // date its week 1 begins are one subject, so they sit together; then the
        // reminder, which is set once and lived with; then the periods every course
        // is laid out against; and last the data management -- importing and
        // backing up are done rarely and deliberately, and they belong at the end
        // rather than between the settings people change.
        body.addView(sectionLabel("\u5B66\u671F\u7BA1\u7406"));
        body.addView(createSemesterGroup());

        // Named for what it changes rather than for where it sits: this is the
        // date week 1 begins, and moving it re-dates every course in the term.
        body.addView(sectionLabel("\u5B66\u671F\u8D77\u59CB\u65E5\u671F"),
                margins(dp(16)));
        body.addView(createSemesterStartCard());

        body.addView(sectionLabel("\u4E0A\u8BFE\u63D0\u9192"), margins(dp(16)));
        body.addView(createReminderCard());

        body.addView(sectionLabel("\u4E0A\u4E0B\u8BFE\u65F6\u95F4"), margins(dp(16)));
        body.addView(createScheduleGroup());

        body.addView(sectionLabel("\u8BFE\u8868\u6570\u636E"), margins(dp(16)));
        body.addView(createDataGroup());
        // Only ever visible when a course payload actually failed to read, so the
        // page does not carry a diagnostic row for a situation most installs never
        // reach. refresh() decides.
        damagedCopyCard = createDamagedCopyCard();
        body.addView(damagedCopyCard, margins(dp(10)));

        body.addView(createAboutCard(), margins(dp(20)));

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    /**
     * One row of a settings group: an icon, a title, a line of context, a chevron.
     *
     * <p>The page used to give every entry its own card, a bold heading, a value
     * line and one or two paragraphs of explanation. That is a lot of height and a
     * lot of reading for entries that are all the same kind of thing -- a place to
     * go. A row says the same in one line, and the group card around it says which
     * entries belong together without a heading repeating what the rows already
     * say.</p>
     */
    private static final class Row {
        final LinearLayout view;
        final TextView title;
        final TextView subtitle;

        Row(LinearLayout view, TextView title, TextView subtitle) {
            this.view = view;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    /** A tappable row: icon, title over a line of context, and a chevron. */
    private Row settingRow(int icon, String title, String subtitle,
            View.OnClickListener listener) {
        LinearLayout row = new UiKit.PressFeedback(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M));
        // Tall enough to be an easy target, and the same height whatever the
        // subtitle says, so a column of rows lines up.
        row.setMinimumHeight(dp(UiTokens.TOUCH_TARGET + 12));

        android.widget.ImageView image = new android.widget.ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(Palette.PRIMARY);
        row.addView(image, new LinearLayout.LayoutParams(
                dp(UiTokens.ICON_SIZE), dp(UiTokens.ICON_SIZE)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = UiKit.text(this, title, UiTokens.Type.TITLE_SMALL,
                Palette.ON_SURFACE, Typeface.BOLD);
        text.addView(titleView);
        TextView subtitleView = UiKit.text(this, subtitle, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(2);
        text.addView(subtitleView, subParams);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(UiTokens.SPACE_M);
        row.addView(text, textParams);

        row.addView(UiKit.inlineIcon(this, R.drawable.ic_chevron_right,
                Palette.OUTLINE), new LinearLayout.LayoutParams(
                        dp(UiTokens.ICON_SIZE - 4), dp(UiTokens.ICON_SIZE - 4)));

        UiKit.ripple(row, UiTokens.RADIUS_L, false);
        UiKit.pressable(row, listener);
        return new Row(row, titleView, subtitleView);
    }

    /** A card of rows, with no padding of its own so the rows run edge to edge. */
    private LinearLayout groupCard() {
        LinearLayout group = UiKit.card(this);
        group.setPadding(0, dp(UiTokens.SPACE_XS), 0, dp(UiTokens.SPACE_XS));
        return group;
    }

    /** The hairline between two rows of a group. */
    private View rowDivider() {
        return UiKit.divider(this, 0, 0);
    }

    /**
     * The timetable's data entries: import, export, preview, and the undo that is
     * only there when there is something to undo.
     *
     * <p>Import and export are separate rows rather than one card with two buttons,
     * because they are separate decisions: one replaces the term's courses, the
     * other writes a file and changes nothing. The counts each row needs are the
     * row's own second line, which is where a number about an action belongs.</p>
     */
    private View createDataGroup() {
        LinearLayout group = groupCard();

        Row importRow = settingRow(R.drawable.ic_add, "\u5BFC\u5165\u8BFE\u8868", "",
                view -> openImportPicker());
        coursesValue = importRow.subtitle;
        group.addView(importRow.view);

        group.addView(rowDivider());
        Row backupRow = settingRow(R.drawable.ic_download, "\u5BFC\u51FA\u5907\u4EFD", "",
                view -> exportBackup());
        backupValue = backupRow.subtitle;
        group.addView(backupRow.view);
        // The warning is the one thing here worth the lines: the file is readable
        // text and the decision to write it is the user's.
        group.addView(hintLine("\u5907\u4EFD\u662F\u660E\u6587\uFF0C\u542B\u6559\u5E08"
                + "\u59D3\u540D\u4E0E\u4E0A\u8BFE\u5730\u70B9\u3002", 0), indented());

        group.addView(rowDivider());
        Row previewRow = settingRow(R.drawable.ic_search, "\u4ECE\u5907\u4EFD\u9884\u89C8",
                "\u5148\u68C0\u67E5\u5907\u4EFD\u5185\u5BB9\uFF0C\u786E\u8BA4\u540E\u518D\u6062\u590D",
                view -> openBackupPicker());
        group.addView(previewRow.view);

        // Offered only while there is something to go back to. A row that never
        // becomes usable is worse than no row: it says the feature exists and is
        // broken, when in fact the term has simply not imported.
        undoImportSection = new LinearLayout(this);
        undoImportSection.setOrientation(LinearLayout.VERTICAL);
        undoImportSection.addView(rowDivider());
        undoImportSection.addView(settingRow(R.drawable.ic_back,
                "\u64A4\u9500\u4E0A\u6B21\u5BFC\u5165",
                "\u628A\u672C\u5B66\u671F\u8BFE\u8868\u6062\u590D\u4E3A\u5BFC\u5165\u524D"
                        + "\u7684\u5185\u5BB9",
                view -> confirmUndoImport()).view);
        undoImportSection.setVisibility(View.GONE);
        group.addView(undoImportSection);
        return group;
    }

    /** A caption under a row, aligned with the row's title rather than its icon. */
    private LinearLayout.LayoutParams indented() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(UiTokens.SPACE_L + UiTokens.ICON_SIZE + UiTokens.SPACE_M);
        params.rightMargin = dp(UiTokens.SPACE_L + UiTokens.ICON_SIZE);
        params.bottomMargin = dp(UiTokens.SPACE_S);
        return params;
    }

    /** The term on screen, with its dates and length, opening the term list. */
    private View createSemesterGroup() {
        LinearLayout group = groupCard();
        Row row = settingRow(R.drawable.ic_semester, "", "",
                view -> startActivity(new Intent(this, SemesterListActivity.class)));
        semesterValue = row.title;
        semesterDetail = row.subtitle;
        group.addView(row.view);
        return group;
    }

    /** The bell schedules, opening the list that manages them. */
    private View createScheduleGroup() {
        LinearLayout group = groupCard();
        Row row = settingRow(R.drawable.ic_settings, "\u4F5C\u606F\u65F6\u95F4\u8868", "",
                view -> startActivity(new Intent(this, ScheduleListActivity.class)));
        schedulesValue = row.subtitle;
        group.addView(row.view);
        return group;
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

    /**
     * The one sentence that says which calendar day the import will actually use.
     *
     * <p>Built in one place because it is rendered twice: once when the dialog
     * opens and again on every confirmed pick. Two copies of the wording could
     * drift, and this is the line the user reads to decide whether the date is
     * right -- the tapped day and the effective day are different when the tap
     * lands midweek.</p>
     */
    private String effectiveDateText(LocalDate monday) {
        return "\u751F\u6548\u65E5\u671F\uFF1A" + monday
                + "\uFF0C\u5373\u672C\u5B66\u671F\u7B2C 1 \u5468\u5468\u4E00";
    }

    // ------------------------------------------------------------- undo import

    /**
     * Asks once, in words that say what is about to change, then puts the term
     * back the way it was before the last import.
     *
     * <p>The entry can go stale between being drawn and being pressed -- another
     * screen may have imported, undone, or switched semester in between. When
     * that has happened there is nothing to undo, so the press refreshes instead
     * of opening a dialog that would fail.</p>
     */
    private void confirmUndoImport() {
        if (!repository.hasPreviousCourses()) {
            refresh();
            return;
        }
        UiKit.dialog(this, "\u64A4\u9500\u4E0A\u6B21\u5BFC\u5165",
                "\u5C06\u628A\u5F53\u524D\u5B66\u671F\u7684\u8BFE\u8868\u6062\u590D\u4E3A\u4E0A"
                        + "\u6B21\u5BFC\u5165\u4E4B\u524D\u7684\u5185\u5BB9\u3002\n\n"
                        + "\u5F53\u524D\u8BFE\u8868\u4F1A\u88AB\u66FF\u6362\uFF0C\u5BFC\u5165\u540E"
                        + "\u65B0\u589E\u7684\u8BFE\u7A0B\u5C06\u6D88\u5931\uFF1B\u6BCF\u6B21"
                        + "\u5BFC\u5165\u53EA\u80FD\u64A4\u9500\u4E00\u6B21\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u64A4\u9500\u5BFC\u5165", this::undoImport));
    }

    private void undoImport() {
        boolean restored = repository.restorePreviousCourses();
        // A failed disk write may still have changed the process's preferences.
        ReminderScheduler.sync(this);
        CourseWidgetUpdater.refreshAll(this);
        if (!restored) {
            // Deliberately not "数据未改变": a refused commit is not proof of
            // that, so the wording leaves the real state to be confirmed on screen.
            refresh();
            UiKit.message(this, "\u64A4\u9500\u5931\u8D25",
                    "\u8BFE\u8868\u53EF\u80FD\u672A\u6062\u590D\u4E3A\u5BFC\u5165\u524D"
                            + "\u7684\u5185\u5BB9\uFF0C\u8BF7\u8FD4\u56DE\u9996\u9875\u786E\u8BA4"
                            + "\u540E\u518D\u8BD5\u3002");
            return;
        }
        refresh();
        Toast.makeText(this, "\u5DF2\u6062\u590D\u4E3A\u5BFC\u5165\u524D\u7684\u8BFE\u8868",
                Toast.LENGTH_SHORT).show();
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
        BackupService.Result<BackupCodec.Document> prepared =
                backupService.prepare(repository, appVersionName());
        if (!prepared.isOk()) {
            UiKit.message(this, "\u5BFC\u51FA\u5931\u8D25", prepared.problem());
            return;
        }
        BackupCodec.Document document = prepared.value();
        pendingBackupDocument = document;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, backupFileName());
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
    }

    /** Writes the already-checked backup to wherever the user pointed. */
    private void writeBackup(Uri uri, BackupCodec.Document document) {
        final String name = displayNameOf(uri);
        final ContentResolver resolver = getApplicationContext().getContentResolver();
        backupService.write(() -> resolver.openOutputStream(uri, "wt"), document, result -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (!result.isOk()) {
                UiKit.message(this, "\u5BFC\u51FA\u5931\u8D25", result.problem());
                return;
            }
            Toast.makeText(this, "\u5DF2\u5BFC\u51FA "
                    + BackupPreview.of(name, document).summary()
                    + (name.isEmpty() ? "" : "\n" + name),
                    Toast.LENGTH_LONG).show();
        });
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
        startActivity(intent);
    }

    private String backupFileName() {
        return "weihe-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                .format(LocalDateTime.now()) + ".json";
    }

    // --------------------------------------------------------- damaged copy

    /**
     * The card for a course payload the reader could not make sense of.
     *
     * <p>Built once and shown only when there is something to say. The copy is not
     * a backup and not a repair: it is the evidence, kept so that a file that
     * failed to read can still be looked at. The wording says so, because a user
     * who thinks this is a restore will be disappointed by it.</p>
     */
    private LinearLayout createDamagedCopyCard() {
        LinearLayout card = card();
        card.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        addTitle(card, R.drawable.ic_settings, "\u65E0\u6CD5\u8BC6\u522B\u7684\u8BFE\u7A0B\u6570\u636E");
        damagedCopyValue = valueLine("", 11);
        card.addView(damagedCopyValue);
        card.addView(hintLine("\u8FD9\u662F\u8BFB\u53D6\u5931\u8D25\u65F6\u539F\u6837\u4FDD\u7559"
                + "\u7684\u539F\u59CB\u5185\u5BB9\uFF0C\u53EF\u80FD\u542B\u8BFE\u7A0B\u540D\u3001"
                + "\u6559\u5E08\u4E0E\u6559\u5BA4\u3002", 6));
        card.addView(hintLine("\u4EC5\u4F9B\u6280\u672F\u6392\u67E5\uFF0C\u4E0D\u4FDD\u8BC1"
                + "\u80FD\u76F4\u63A5\u6062\u590D\u6210\u8BFE\u8868\uFF1B\u672C\u5E94\u7528"
                + "\u4E0D\u4F1A\u81EA\u52A8\u4FEE\u590D\u6216\u56DE\u5199\u3002", 2));
        damagedCopyAction = actionRow(
                action("\u5BFC\u51FA\u635F\u574F\u6570\u636E\u526F\u672C", Palette.PRIMARY, true,
                        view -> confirmExportDamagedCopy()));
        card.addView(damagedCopyAction);
        return card;
    }

    /**
     * Shows the card only when there is something to say about it.
     *
     * <p>Absent is the ordinary state, and a row saying "no damaged data" on every
     * visit would be noise on a page opened for other reasons. Empty and unreadable
     * are not ordinary: the slot exists, so the card appears and says which of the
     * two it is rather than offering an export that cannot happen.</p>
     */
    private void refreshDamagedCopyCard() {
        if (damagedCopyCard == null) {
            return;
        }
        DamagedCourseCopy copy = repository.damagedCopy();
        if (!copy.exists()) {
            damagedCopyCard.setVisibility(View.GONE);
            return;
        }
        damagedCopyCard.setVisibility(View.VISIBLE);
        damagedCopyAction.setVisibility(copy.isExportable() ? View.VISIBLE : View.GONE);
        switch (copy.state()) {
            case PRESENT:
                damagedCopyValue.setText("\u5DF2\u4FDD\u7559\u4E00\u4EFD\u539F\u59CB\u526F"
                        + "\u672C\uFF08" + copy.length() + " \u4E2A\u5B57\u7B26\uFF09");
                break;
            case EMPTY:
                damagedCopyValue.setText("\u526F\u672C\u4E3A\u7A7A\uFF0C\u6CA1\u6709\u53EF"
                        + "\u5BFC\u51FA\u7684\u5185\u5BB9\u3002");
                break;
            default:
                damagedCopyValue.setText("\u526F\u672C\u65E0\u6CD5\u8BFB\u53D6\uFF0C"
                        + "\u65E0\u6CD5\u5BFC\u51FA\u3002");
                break;
        }
    }

    /**
     * Says what the file is before the picker opens.
     *
     * <p>Exported because the user asked for it, never as a side effect: this
     * dialog is the only way in, and cancelling it leaves every key alone. The
     * wording promises nothing about recovery -- the payload is precisely what the
     * reader could not use.</p>
     */
    private void confirmExportDamagedCopy() {
        if (!repository.damagedCopy().isExportable()) {
            // The card can go stale between being drawn and being pressed.
            refresh();
            return;
        }
        UiKit.dialog(this, "\u5BFC\u51FA\u635F\u574F\u6570\u636E\u526F\u672C",
                "\u8FD9\u4EFD\u526F\u672C\u662F\u8BFE\u7A0B\u6570\u636E\u8BFB\u53D6\u5931"
                        + "\u8D25\u65F6\u539F\u6837\u4FDD\u7559\u7684\u539F\u59CB\u5185\u5BB9"
                        + "\uFF0C\u4E3B\u8981\u7528\u4E8E\u6280\u672F\u6392\u67E5\uFF0C"
                        + "\u4E0D\u4FDD\u8BC1\u80FD\u591F\u76F4\u63A5\u6062\u590D\u3002\n\n"
                        + "\u6587\u4EF6\u662F\u660E\u6587\uFF0C\u53EF\u80FD\u542B\u8BFE\u7A0B"
                        + "\u540D\u3001\u6559\u5E08\u4E0E\u6559\u5BA4\uFF0C\u8BF7\u59A5\u5584"
                        + "\u4FDD\u7BA1\u3002",
                null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.of("\u9009\u62E9\u5BFC\u51FA\u4F4D\u7F6E",
                        this::exportDamagedCopy));
    }

    /** Opens the destination picker with the copy already read. */
    private void exportDamagedCopy() {
        DamagedCourseCopy copy = repository.damagedCopy();
        if (!copy.isExportable()) {
            refresh();
            return;
        }
        pendingDamagedCopy = copy;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Plain text, not the backup's JSON: the payload is whatever was stored,
        // and the app does not know what that is. Claiming a format it cannot
        // verify would be a guess, and the extension keeps the two files apart.
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, damagedCopyFileName());
        startActivityForResult(intent, REQUEST_EXPORT_DAMAGED);
    }

    /** Writes the copy out exactly as it was read. */
    private void writeDamagedCopy(Uri uri, DamagedCourseCopy copy) {
        final String name = displayNameOf(uri);
        new Thread(() -> {
            String failure = null;
            try (OutputStream output =
                         getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    failure = "\u65E0\u6CD5\u5199\u5165\u6240\u9009\u4F4D\u7F6E";
                } else {
                    copy.writeTo(output);
                }
            } catch (IOException error) {
                failure = BackupService.writeFailureReason(error);
            } catch (SecurityException denied) {
                failure = "\u6CA1\u6709\u5199\u5165\u8BE5\u4F4D\u7F6E\u7684\u6743\u9650";
            }
            final String problem = failure;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (problem != null) {
                    // The internal copy is left where it is: a failed export is a
                    // reason to keep the evidence, not to throw it away.
                    UiKit.message(this, "\u5BFC\u51FA\u5931\u8D25", problem);
                    return;
                }
                Toast.makeText(this, "\u5DF2\u5BFC\u51FA\u635F\u574F\u6570\u636E\u526F\u672C"
                        + (name.isEmpty() ? "" : "\n" + name),
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /**
     * A name that cannot be mistaken for a normal backup.
     *
     * <p>{@code weihe-backup-….json} is the app's own format and can be restored;
     * this is neither, so it gets its own stem and its own extension.</p>
     */
    private String damagedCopyFileName() {
        return "weihe-damaged-courses-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                .format(LocalDateTime.now()) + ".txt";
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

    /** Which term is on screen, and the way to look at another one. */
    /**
     * Week 1 of the term on screen, and the three ways to move it.
     *
     * <p>These controls change stored data: they move the Monday that week 1 begins
     * on, so every course's weeks line up with different real dates afterwards. The
     * card says so in one line, because it is the one entry on this page whose
     * buttons are not navigation -- without that, "previous week" next to a date
     * reads like browsing a calendar.</p>
     */
    private View createSemesterStartCard() {
        LinearLayout card = card();
        addTitle(card, R.drawable.ic_today, "\u7B2C 1 \u5468\u5468\u4E00");
        semesterStartValue = valueLine("", 10);
        card.addView(semesterStartValue);
        semesterStartDetail = hintLine("", 4);
        card.addView(semesterStartDetail);
        card.addView(hintLine("\u6539\u7684\u662F\u672C\u5B66\u671F\u7B2C 1 \u5468\u7684"
                + "\u5468\u4E00\uFF0C\u8BFE\u7A0B\u5468\u6B21\u5BF9\u5E94\u7684\u5B9E"
                + "\u9645\u65E5\u671F\u4F1A\u8DDF\u7740\u53D8\u3002", 4));
        card.addView(actionRow(
                action("\u524D\u79FB\u4E00\u5468", Palette.INK, false,
                        view -> shiftSemesterStart(-1)),
                action("\u540E\u79FB\u4E00\u5468", Palette.INK, false,
                        view -> shiftSemesterStart(1)),
                action("\u8F93\u5165\u65E5\u671F", Palette.PRIMARY, true,
                        view -> editSemesterStart())));
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
        boolean custom = !isQuickChoice(choices, current);
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
        // The fourth entry, beside the three shortcuts rather than instead of them.
        // It is a chip like the others so the row stays one control, and it carries
        // the saved number when that number is not one of the three -- otherwise
        // reopening the screen would show three unselected chips and the value only
        // in the status line below.
        TextView customChip = UiKit.chip(this, customLeadLabel(custom, current), custom);
        customChip.setOnClickListener(view -> showCustomLeadDialog());
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(UiTokens.CHIP_HEIGHT));
        customParams.leftMargin = dp(UiTokens.SPACE_S);
        leadRow.addView(customChip, customParams);
        reminderLeadChips.add(customChip);
        LinearLayout.LayoutParams leadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        leadParams.topMargin = dp(UiTokens.SPACE_L);
        card.addView(leadRow, leadParams);

        card.addView(hintLine("\u63D0\u9192\u5728\u8BFE\u7A0B\u5F00\u59CB\u524D\u53D1\u51FA"
                + "\uFF1B\u672A\u6388\u4E88\u7CBE\u786E\u95F9\u949F\u6743\u9650\u65F6"
                + "\u53EF\u80FD\u665A\u51E0\u5206\u949F\u3002", 8));

        // One action row, carrying whichever permission is the one standing in the
        // way. A missing notification permission stops reminders altogether, a
        // missing exact-alarm permission only costs punctuality, and asking for both
        // at once would put two prompts on a card where one answer already fixes the
        // thing the user came here for.
        reminderPermissionAction = action("\u53BB\u5F00\u542F\u901A\u77E5\u6743\u9650", Palette.PRIMARY, true,
                view -> openNotificationSettings());
        reminderPermissionRow = actionRow(reminderPermissionAction);
        card.addView(reminderPermissionRow);
        return card;
    }

    /**
     * The one-tap lead times, taken from the planner's own list.
     *
     * <p>Not the ends of the supported band. Those were the same three numbers
     * only while the band was five-to-fifteen; the band now reaches from one
     * minute to three hours for typed values, and deriving the shortcuts from it
     * would have offered "1 分钟" and "180 分钟" instead of the three a student
     * actually picks.</p>
     */
    private static int[] leadChoices() {
        return ReminderPlan.QUICK_LEAD_MINUTES;
    }

    /** Whether {@code minutes} is one of the shortcuts, and so needs no fourth chip. */
    private static boolean isQuickChoice(int[] choices, int minutes) {
        for (int choice : choices) {
            if (choice == minutes) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the fourth chip reads: the saved number, or the word for the entry.
     *
     * <p>Showing the number is the point of the chip. A custom value has no
     * shortcut to sit on, so without this the screen would open on three
     * unselected chips and leave the user to find the number in the status line.</p>
     */
    private static String customLeadLabel(boolean custom, int minutes) {
        return custom ? minutes + " \u5206\u949F" : "\u81EA\u5B9A\u4E49";
    }

    /**
     * The dialog for typing a lead time.
     *
     * <p>A plain number field rather than a stepper: the shortcuts cover the
     * common answers, and someone who wants twenty-five should not have to press a
     * button twenty times to say so. The keyboard is asked for digits only, so the
     * letter keys are not on screen to be hit by accident.</p>
     *
     * <p>Nothing is written until 保存 is pressed, and 保存 refuses rather than
     * repairs: an empty field, a word, a fraction or a number outside the band
     * leaves the stored value exactly as it was and says what is wrong. That is
     * what makes 取消 and a failed 保存 the same thing as far as the timetable and
     * the alarms are concerned.</p>
     */
    private void showCustomLeadDialog() {
        final EditText input = UiKit.field(this,
                "\u4F8B\u5982 25", true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(ReminderSettings.leadMinutes(this)));
        input.setSelection(input.getText().length());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(UiTokens.SPACE_XL - 2), dp(UiTokens.SPACE_S),
                dp(UiTokens.SPACE_XL - 2), 0);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(UiTokens.FIELD_HEIGHT)));

        UiKit.dialog(this, "\u81EA\u5B9A\u4E49\u63D0\u524D\u65F6\u95F4",
                "\u63D0\u524D\u591A\u5C11\u5206\u949F\u63D0\u9192\uFF08"
                        + ReminderPlan.MIN_LEAD_MINUTES + "\u2013"
                        + ReminderPlan.MAX_LEAD_MINUTES + " \u5206\u949F\uFF09",
                content, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.validated("\u4FDD\u5B58",
                        () -> applyCustomLeadMinutes(input)));
    }

    /**
     * Saves a typed lead time, or refuses and keeps the dialog open.
     *
     * @return whether the value was accepted, which is what decides if the dialog
     *         closes
     */
    private boolean applyCustomLeadMinutes(EditText input) {
        String typed = input.getText().toString().trim();
        if (typed.isEmpty()) {
            input.setError("\u8BF7\u586B\u5199\u63D0\u524D\u63D0\u9192\u7684\u5206\u949F\u6570");
            input.requestFocus();
            return false;
        }
        Integer minutes = ReminderPlan.parseLeadMinutes(typed);
        if (minutes == null) {
            input.setError("\u8BF7\u586B\u5199 " + ReminderPlan.MIN_LEAD_MINUTES + "\u2013"
                    + ReminderPlan.MAX_LEAD_MINUTES + " \u4E4B\u95F4\u7684\u6574\u6570");
            input.requestFocus();
            return false;
        }
        onLeadMinutesChosen(minutes.intValue());
        return true;
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
        for (int index = 0; index < choices.length; index++) {
            UiKit.styleChip(reminderLeadChips.get(index), choices[index] == minutes);
        }
        // The fourth chip is styled from the same number as the other three, so
        // exactly one of the four is ever selected and it is always the value the
        // alarms were built from.
        boolean custom = !isQuickChoice(choices, minutes);
        TextView customChip = reminderLeadChips.get(choices.length);
        customChip.setText(customLeadLabel(custom, minutes));
        UiKit.styleChip(customChip, custom);
        boolean exact = ReminderScheduler.canScheduleExact(this);
        if (!enabled) {
            reminderStatus.setText("\u5DF2\u5173\u95ED");
            reminderStatus.setTextColor(Palette.ON_SURFACE_VARIANT);
            reminderPermissionRow.setVisibility(View.GONE);
        } else if (!allowed) {
            reminderStatus.setText("\u901A\u77E5\u6743\u9650\u672A\u5F00\u542F\uFF0C\u63D0\u9192\u4E0D\u4F1A\u663E\u793A");
            reminderStatus.setTextColor(Palette.ERROR);
            setPermissionAction("\u53BB\u5F00\u542F\u901A\u77E5\u6743\u9650",
                    view -> openNotificationSettings());
        } else {
            reminderStatus.setText("\u5DF2\u5F00\u542F \u00B7 \u63D0\u524D " + minutes + " \u5206\u949F \u00B7 \u5DF2\u5B89\u6392 "
                    + ReminderScheduler.registeredCount(this) + " \u6761\u63D0\u9192");
            reminderStatus.setTextColor(Palette.ON_SURFACE);
            if (exact) {
                reminderPermissionRow.setVisibility(View.GONE);
            } else {
                // The feature works without it, only less punctually; the entry is
                // therefore a quiet second priority rather than a warning.
                setPermissionAction("\u5141\u8BB8\u7CBE\u786E\u95F9\u949F",
                        view -> openExactAlarmSettings());
            }
        }
    }

    /** Points the card's single permission row at whatever is blocking punctuality. */
    private void setPermissionAction(String label, View.OnClickListener listener) {
        reminderPermissionAction.setText(label);
        reminderPermissionAction.setOnClickListener(listener);
        reminderPermissionRow.setVisibility(View.VISIBLE);
    }

    /**
     * The system's own page for granting exact alarms.
     *
     * <p>There is no runtime dialog for this permission: Android only offers the
     * settings screen, which is why the app explains what it is for and then opens
     * that screen rather than trying to ask.</p>
     */
    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, "\u65E0\u6CD5\u6253\u5F00\u7CBE\u786E\u95F9\u949F\u8BBE\u7F6E",
                    Toast.LENGTH_SHORT).show();
        }
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

    /**
     * The way into the about page.
     *
     * <p>One row, where two paragraphs used to sit. The version and the note about
     * where the data lives are worth reading once, and this is a page people open to
     * change a setting -- the height is better spent on the settings. Neither line
     * has been dropped: both moved to the page this opens, where the version is read
     * from the installed package instead of typed into the source.</p>
     */
    private View createAboutCard() {
        LinearLayout card = groupCard();
        Row row = settingRow(R.drawable.ic_grid, "\u5173\u4E8E\u62FE\u8BFE",
                "\u7248\u672C\u3001\u6570\u636E\u4E0E\u9690\u79C1\u8BF4\u660E",
                view -> startActivity(new Intent(this, AboutActivity.class)));
        card.addView(row.view);
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
        LocalDate today = LocalDate.now();

        // Read from the store rather than remembered, so switching semester,
        // importing or undoing on another screen is reflected the moment this one
        // comes back.
        if (undoImportSection != null) {
            undoImportSection.setVisibility(
                    repository.hasPreviousCourses() ? View.VISIBLE : View.GONE);
        }
        refreshDamagedCopyCard();

        // The count and the file type, which is what decides whether to tap. How
        // the import behaves is said in the import flow, where it is acted on.
        coursesValue.setText((courses.isEmpty()
                ? "\u5F53\u524D\u6CA1\u6709\u8BFE\u7A0B"
                : "\u5F53\u524D\u5171 " + courses.size() + " \u6761\u5B89\u6392")
                + " \u00B7 \u4ECE Excel \u6587\u4EF6\u5BFC\u5165");

        // The row's title is the term itself; the line under it is the summary the
        // section exists to give. The number of terms is not repeated here -- the
        // row opens the list, where that is the first thing on screen.
        semesterValue.setText(active.label());
        semesterDetail.setText(ScheduleUtils.formatDate(active.start()) + " - "
                + ScheduleUtils.formatDate(active.lastDay())
                + " \u00B7 \u5171 " + active.weeks() + " \u5468"
                + " \u00B7 " + SemesterSummary.status(active, today));

        semesterStartValue.setText(ScheduleUtils.formatFullDate(start));
        semesterStartDetail.setText("\u7B2C 1 \u5468\uFF1A" + start
                + " \u00B7 \u5171 " + active.weeks()
                + " \u5468 \u00B7 " + SemesterSummary.todayLabel(active, today));

        // Read from the tables rather than from the default constant, because the
        // day is settable per schedule now and "每节 15 节" would be wrong the moment
        // anybody changed one. The range covers the usual case where every table
        // agrees; when they differ it says so instead of picking one.
        schedulesValue.setText(schedules.size() + " \u4E2A\u4F5C\u606F\u8868 \u00B7 "
                + periodRange(schedules));
        backupValue.setText("\u5F85\u5BFC\u51FA\uFF1A" + semesters.size() + " \u4E2A\u5B66\u671F"
                + " \u00B7 " + repository.totalCourseCount()
                + " \u95E8\u8BFE\u7A0B \u00B7 "
                + schedules.size() + " \u5F20\u4F5C\u606F\u8868");
    }

    /**
     * The day length across the stored tables, for the summary line.
     *
     * <p>One number when every table agrees, and a range when they do not. A single
     * number would have to pick one table and would be a lie about the others;
     * listing every table's count would be a wall of numbers where one figure
     * belongs.</p>
     */
    private static String periodRange(List<BellSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return "\u6BCF\u5929 " + ScheduleUtils.PERIODS_PER_DAY + " \u8282";
        }
        int low = Integer.MAX_VALUE;
        int high = 0;
        for (BellSchedule schedule : schedules) {
            low = Math.min(low, schedule.periodCount());
            high = Math.max(high, schedule.periodCount());
        }
        if (low == high) {
            return "\u6BCF\u5929 " + low + " \u8282";
        }
        return "\u6BCF\u5929 " + low + "-" + high + " \u8282";
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

    /**
     * Moves week 1 of the term on screen, by calendar.
     *
     * <p>A calendar rather than a text field: the only thing this asks for is a
     * date, and a date picker cannot produce the thirtieth of February. The term's
     * own rule still applies afterwards -- any day is accepted and rounded to its
     * Monday -- and the confirmation names the day that actually took effect, so a
     * Tuesday typed in does not silently become a Monday.</p>
     */
    private void editSemesterStart() {
        Pickers.showDatePicker(this, repository.getSemesterStart(),
                this::applySemesterStart);
    }

    private void applySemesterStart(LocalDate date) {
        LocalDate monday = ScheduleUtils.mondayOf(date);
        repository.setSemesterStart(monday);
        // Every date in the term just moved, so every alarm instant moved with it.
        // Re-planning now is what keeps a reminder from firing on the old calendar.
        ReminderScheduler.sync(this);
        // Today's date is read against the term start, so the widget's answer moved
        // too -- possibly to a different week, or out of the term altogether.
        CourseWidgetUpdater.refreshAll(this);
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
                TimetableImportParser.Report report =
                        ExcelTimetableReader.readWithReport(input);
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (!isFinishing()) {
                        confirmImport(report);
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

    /**
     * Shows what the file turned into, before anything is replaced.
     *
     * <p>The counts come from the report, so the sentence describes the file rather
     * than the app's hopes for it. When something could not be recognised the
     * screen says so with a few located examples, and the user decides whether an
     * incomplete timetable is still worth having -- which is a decision only they
     * can make. Nothing here writes: cancelling leaves the timetable, and any
     * snapshot an earlier import left behind, exactly as they were.</p>
     */
    private void confirmImport(TimetableImportParser.Report report) {
        if (!report.isUsable()) {
            showNothingRecognised(report);
            return;
        }
        List<Course> courses = report.courses();
        // The term the file lands in. Its stored week-1 date is what the picker
        // opens on, so the file's week 1 keeps meaning the week 1 the term
        // already has rather than a date the import invents.
        Semester target = repository.getActiveSemester();
        LocalDate storedStart = repository.getSemesterStart();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(UiTokens.SPACE_XL - 2), dp(UiTokens.SPACE_XS),
                dp(UiTokens.SPACE_XL - 2), 0);
        String summary = "\u8BC6\u522B\u5230 " + report.courseCount() + " \u95E8\u8BFE\u7A0B\uFF0C"
                + "\u5171 " + report.scheduleCount() + " \u6761\u5B89\u6392";
        content.addView(UiKit.title(this, summary));
        content.addView(hintLine(
                "\u5BFC\u5165\u5C06\u8986\u76D6\u5F53\u524D\u8BFE\u8868", UiTokens.SPACE_XS + 1));
        content.addView(issueSummary(report));

        content.addView(hintLine("\u5BFC\u5165\u5230\uFF1A" + target.label()
                + " \u00B7 \u6587\u4EF6\u7B2C 1 \u5468\u5BF9\u5E94\u672C\u5B66\u671F\u7B2C 1 \u5468",
                UiTokens.SPACE_L));

        TextView label = UiKit.text(this,
                "\u672C\u5B66\u671F\u5F00\u5B66\u65E5\u671F",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(UiTokens.SPACE_M);
        content.addView(label, labelParams);

        // The chosen date, kept here and written to nothing until 覆盖导入 is
        // pressed. The picker hands back whatever the calendar says, including a
        // midweek day, and the field shows the Monday the week maths will use --
        // the preview must describe the date that takes effect, not the one that
        // was tapped.
        final LocalDate[] chosen = {ScheduleUtils.mondayOf(storedStart)};
        // The line below the field is updated from inside this callback rather than
        // from a TextWatcher on the field: the picker writes the tapped day into the
        // field first and only then hands the value back, so a watcher would read
        // the previous Monday and the two would disagree on screen.
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

        effective[0] = hintLine(effectiveDateText(chosen[0]), 5);
        content.addView(effective[0]);

        // How long the term runs, held here and written nowhere until 覆盖导入 is
        // pressed. Read from the target term rather than from the default, so the
        // dialog opens on the length this term already has.
        final int[] weeks = {SemesterWeeks.of(target)};
        final int storedWeeks = SemesterWeeks.of(target);
        TextView weeksLabel = UiKit.text(this,
                "\u5B66\u671F\u603B\u5468\u6570",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams weeksLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        weeksLabelParams.topMargin = dp(UiTokens.SPACE_M);
        content.addView(weeksLabel, weeksLabelParams);

        final TextView[] weeksValue = new TextView[1];
        content.addView(weekStepper(weeks, weeksValue), UiKit.stack(this,
                UiTokens.SPACE_XS));
        // What the count will mean once it lands: the term's own range, recomputed
        // from the start date the dialog is currently showing and the count the
        // user has chosen. Nothing here re-dates a course; it only says how far the
        // term reaches.
        final TextView[] range = new TextView[1];
        range[0] = hintLine(semesterRangeText(chosen[0], weeks[0]), 5);
        content.addView(range[0]);
        weeksValue[0].addTextChangedListener(new Pickers.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                range[0].setText(semesterRangeText(chosen[0], weeks[0]));
            }
        });
        // The date and the length are independent: moving week 1 shifts the range,
        // but it must not change how many weeks the term runs.
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

                    // Shortening the term can leave a course outside it. That is a
                    // refusal, not a quiet trim: the weeks on a course are the
                    // user's data, and a length setting is not allowed to rewrite
                    // them or drop the courses it strands.
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
                    // Moving the date re-dates every course in the term, and moving
                    // the length changes the term's range, so when the file would
                    // land on a term that already has courses the user is told
                    // before the write, not after.
                    if ((dateMoved || requestedWeeks != storedWeeks)
                            && !existing.isEmpty()) {
                        UiKit.dialog(this, "\u4FEE\u6539\u5B66\u671F\u8BBE\u7F6E",
                                semesterChangeWarning(weekOne, requestedWeeks, storedWeeks),
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

    /**
     * Builds the {@code − 18 周 +} row for the import dialog.
     *
     * <p>Handed the value array rather than returning it, because the row has to
     * read and write the same cell the confirm path reads; a copy would let the
     * buttons and the import disagree about the count.</p>
     *
     * @param weeks holds the current count; updated in place by the buttons
     * @param value receives the readout, so callers can hang a listener on it
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
     * <p>Shown under the stepper so a count change is visible as a range change
     * rather than as a number that may or may not mean anything.</p>
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
     * <p>Names only the parts that actually changed. Warning about the date when
     * the user only touched the length would train them to dismiss the dialog.</p>
     */
    private String semesterChangeWarning(LocalDate weekOne, int weeks,
            int storedWeeks) {
        StringBuilder message = new StringBuilder();
        if (!weekOne.equals(ScheduleUtils.mondayOf(repository.getSemesterStart()))) {
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
     * Writes the file, the chosen week-1 date and the term length, or reports why
     * it did not.
     *
     * <p>Separated from the dialog so the confirmation path and the plain path
     * land in exactly one place: the date, the length and the courses are one
     * commit inside the repository, so there is no order here in which the length
     * could be saved while the courses failed.</p>
     */
    private void runImport(List<Course> courses, Semester target, LocalDate weekOne,
            int weeks) {
        boolean imported = repository.replaceCoursesAt(courses, target, weekOne, weeks);
        // Reconcile with the state actually read back, even after a disk failure.
        ReminderScheduler.sync(this);
        CourseWidgetUpdater.refreshAll(this);
        if (imported) {
            refresh();
            Toast.makeText(this, "\u8BFE\u8868\u5DF2\u5BFC\u5165",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // The store refused the write. "课表已导入" is a claim the app cannot back
        // up. The in-memory state may have changed despite the disk failure.
        refresh();
        UiKit.message(this, "\u5BFC\u5165\u672A\u5B8C\u6210",
                "\u8BFE\u8868\u5199\u5165\u5931\u8D25\uFF0C\u5BFC\u5165\u53EF\u80FD"
                        + "\u6CA1\u6709\u751F\u6548\uFF0C\u8BF7\u91CD\u65B0\u5BFC\u5165\u3002");
    }

    /**
     * The line, or the block, that says whether the file was read in full.
     *
     * <p>Reasons and positions only, never the cell text: the point is to help the
     * user find the spot in their spreadsheet, and a warning that quoted the cell
     * would put a teacher's name on a screen that did not ask for it.</p>
     */
    private View issueSummary(TimetableImportParser.Report report) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        if (!report.hasIssues()) {
            block.addView(hintLine("\u6587\u4EF6\u5185\u5BB9\u5DF2\u5168\u90E8\u8BC6\u522B\u3002",
                    UiTokens.SPACE_XS + 1));
            return block;
        }
        block.addView(hintLine("\u53E6\u6709 " + report.issueCount()
                + " \u5904\u5185\u5BB9\u672A\u80FD\u8BC6\u522B\uFF0C\u672C\u6B21\u5BFC\u5165"
                + "\u53EF\u80FD\u4E0D\u5B8C\u6574\u3002", UiTokens.SPACE_XS + 1));
        for (TimetableImportParser.Issue issue : report.issues()) {
            block.addView(hintLine("\u00B7 " + issue.describe(), UiTokens.SPACE_XS));
        }
        return block;
    }

    /**
     * The file produced no course at all.
     *
     * <p>A dialog rather than the toast this used to be: "没有识别到课程" on its own
     * leaves the user with nothing to act on, and the reasons are the only thing
     * that can tell them whether the file is the wrong kind, the wrong format, or
     * damaged. Nothing is replaced, and no undo entry is written, because no
     * import happened.</p>
     */
    private void showNothingRecognised(TimetableImportParser.Report report) {
        StringBuilder message = new StringBuilder(
                "\u8FD9\u4EFD\u6587\u4EF6\u91CC\u6CA1\u6709\u8BC6\u522B\u5230\u4EFB\u4F55"
                        + "\u8BFE\u7A0B\uFF0C\u5F53\u524D\u8BFE\u8868\u672A\u505A\u4EFB\u4F55"
                        + "\u6539\u52A8\u3002");
        if (report.hasIssues()) {
            message.append("\n\n\u672A\u80FD\u8BC6\u522B\u7684\u5185\u5BB9\u5171 ")
                    .append(report.issueCount()).append(" \u5904\uFF1A");
            for (TimetableImportParser.Issue issue : report.issues()) {
                message.append("\n\u00B7 ").append(issue.describe());
            }
        } else {
            message.append("\n\n\u6587\u4EF6\u91CC\u6CA1\u6709\u8BFE\u7A0B\u5185\u5BB9\uFF0C"
                    + "\u8BF7\u786E\u8BA4\u5BFC\u51FA\u7684\u662F\u8BFE\u8868\u800C\u4E0D"
                    + "\u662F\u5176\u4ED6\u8868\u683C\u3002");
        }
        UiKit.message(this, "\u6CA1\u6709\u8BC6\u522B\u5230\u8BFE\u7A0B", message.toString());
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

package com.weihe.schedule;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


import com.weihe.schedule.data.BackupCodec;
import com.weihe.schedule.data.BackupService;
import com.weihe.schedule.data.BackupFileName;
import com.weihe.schedule.data.BackupPreview;
import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.data.RestoreReport;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;
import com.weihe.schedule.widget.CourseWidgetUpdater;

/**
 * Shows what a backup file contains, without applying any of it.
 *
 * <p>Previewing only reads the selected file. After explicit confirmation,
 * {@link BackupService} reopens and validates it before asking
 * {@link CourseRepository} to apply it. Keeping file work out of the screen
 * makes the same failure handling testable without Android UI.</p>
 */
public class BackupPreviewActivity extends Activity {
    /** The picked document, as a string so the extra stays small. */
    public static final String EXTRA_URI = "backup_uri";
    /** The name the picker showed, for display only. */
    public static final String EXTRA_FILE_NAME = "backup_file_name";

    private LinearLayout body;
    private TextView subtitle;
    private Uri uri;
    private String fileName = "";

    /** The checked document the restore would write, or null when unusable. */
    private BackupCodec.Document document;
    private BackupService backupService;

    /**
     * True from the moment the user confirms until the outcome is on screen.
     *
     * <p>The confirm dialog closes on its first tap, so the button cannot be
     * pressed twice; this guards the other way in as well -- a queued tap, a
     * second tap during the write -- because two restores at once would each
     * snapshot the other's half-finished state.</p>
     */
    private boolean restoring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backupService = new BackupService(this::runOnUiThread);
        uri = parse(getIntent().getStringExtra(EXTRA_URI));
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());

        if (uri == null) {
            showFailure("\u6CA1\u6709\u6536\u5230\u8981\u9884\u89C8\u7684\u5907\u4EFD\u6587\u4EF6");
            return;
        }
        showLoading();
        readInBackground();
    }

    private static Uri parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(text);
        } catch (RuntimeException notAUri) {
            return null;
        }
    }

    // ----------------------------------------------------------------- reading

    /**
     * Reads and checks the file off the main thread.
     *
     * <p>Only the read is off the main thread: {@link BackupCodec} and
     * {@link BackupPreview} are plain Java and hold nothing that another thread
     * could see half-finished, so the parsing itself needs no such care.</p>
     */
    private void readInBackground() {
        final Uri target = uri;
        final ContentResolver resolver = getApplicationContext().getContentResolver();
        backupService.load(() -> resolver.openInputStream(target),
                () -> BackupFileName.read(resolver, target), result -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (!result.isOk()) {
                        showFailure(result.problem());
                    } else {
                        document = result.value().document();
                        BackupPreview preview = result.value().preview();
                        fileName = preview.fileName();
                        showPreview(preview);
                    }
                });
    }

    // ------------------------------------------------------------------ layout

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Palette.SURFACE);
        LinearLayout bar = UiKit.detailAppBar(this, "\u6062\u590D\u9884\u89C8", "\u8BFB\u53D6\u4E2D\u2026",
                view -> finish());
        subtitle = UiKit.appBarSubtitleOf(bar);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void showLoading() {
        body.removeAllViews();
        body.addView(UiKit.caption(this, "\u6B63\u5728\u8BFB\u53D6\u5E76\u6821\u9A8C\u5907\u4EFD\u6587\u4EF6\u2026"));
    }

    /**
     * The only thing shown for a file that cannot be used.
     *
     * <p>No counts, no preview and no restore entry: a screen that showed an empty
     * summary beside a failure would read like a backup of nothing rather than a
     * file that was refused.</p>
     */
    private void showFailure(String reason) {
        body.removeAllViews();
        subtitle.setText("\u65E0\u6CD5\u9884\u89C8");
        LinearLayout card = card();
        card.addView(UiKit.text(this, "\u65E0\u6CD5\u9884\u89C8\u8FD9\u4EFD\u5907\u4EFD",
                UiTokens.Type.TITLE_SMALL, Palette.ERROR, true));
        card.addView(line(reason, Palette.ON_SURFACE, UiTokens.SPACE_S));
        card.addView(line("\u5F53\u524D\u8BFE\u8868\u6CA1\u6709\u4EFB\u4F55\u6539\u52A8\u3002", Palette.ON_SURFACE_VARIANT,
                UiTokens.SPACE_S));
        body.addView(card);
    }

    private void showPreview(BackupPreview preview) {
        body.removeAllViews();
        subtitle.setText(preview.summary());

        LinearLayout banner = card();
        banner.addView(UiKit.text(this, "\u8FD9\u662F\u5907\u4EFD\u6587\u4EF6\u7684\u5185\u5BB9\u9884\u89C8\uFF0C\u5C1A\u672A\u5E94\u7528\u5230\u5F53\u524D\u8BFE\u8868\u3002",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, false));
        body.addView(banner);

        LinearLayout file = card();
        file.addView(section("\u5907\u4EFD\u6587\u4EF6"));
        addRow(file, "\u6587\u4EF6\u540D", preview.fileName());
        addRow(file, "\u683C\u5F0F\u7248\u672C", String.valueOf(preview.formatVersion()));
        addRow(file, "\u5BFC\u51FA\u65F6\u95F4", preview.hasExportedAt() ? preview.exportedAt().replace('T', ' ') : "\u672A\u77E5");
        addRow(file, "\u5BFC\u51FA\u5E94\u7528", preview.hasAppVersionName()
                ? preview.appVersionName() : "\u672A\u77E5");
        body.addView(spaced(file));

        LinearLayout counts = card();
        counts.addView(section("\u5305\u542B\u7684\u6570\u636E"));
        addRow(counts, "\u5B66\u671F", preview.semesterCount() + " \u4E2A");
        addRow(counts, "\u8BFE\u7A0B", preview.courseCount() + " \u95E8");
        addRow(counts, "\u4F5C\u606F\u8868", preview.scheduleCount() + " \u5F20");
        addRow(counts, "\u5176\u4E2D\u5355/\u53CC\u5468\u8BFE\u7A0B", preview.parityCourseCount() + " \u95E8");
        addRow(counts, "\u81EA\u5B9A\u4E49\u547D\u540D\u7684\u5B66\u671F", preview.namedSemesterCount() + " \u4E2A");
        addRow(counts, "\u5730\u70B9\u5173\u952E\u8BCD", preview.keywordCount() + " \u4E2A");
        body.addView(spaced(counts));

        LinearLayout detail = card();
        detail.addView(section("\u5B66\u671F\u660E\u7EC6"));
        if (preview.semesters().isEmpty()) {
            detail.addView(line("\u5907\u4EFD\u91CC\u6CA1\u6709\u5B66\u671F\u3002", Palette.ON_SURFACE_VARIANT,
                    UiTokens.SPACE_S));
        } else {
            for (BackupPreview.SemesterSummary row : preview.semesters()) {
                detail.addView(semesterRow(row));
            }
        }
        body.addView(spaced(detail));

        body.addView(spaced(checkCard(preview)));
        body.addView(spaced(restoreCard()));
    }

    private LinearLayout checkCard(BackupPreview preview) {
        LinearLayout card = card();
        card.addView(section("\u6587\u4EF6\u6821\u9A8C"));
        if (preview.needsRepair()) {
            card.addView(UiKit.text(this, "\u6587\u4EF6\u53EF\u4EE5\u8BFB\u53D6\uFF0C\u4F46\u6062\u590D\u524D\u9700\u8981\u4FEE\u6B63\u4EE5\u4E0B\u51E0\u5904\uFF1A",
                    UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, false));
            for (String note : preview.notes()) {
                card.addView(line("\u00B7 " + note, Palette.ON_SURFACE_VARIANT,
                        UiTokens.SPACE_XS));
            }
        } else {
            card.addView(UiKit.text(this, "\u6821\u9A8C\u901A\u8FC7\uFF0C\u6587\u4EF6\u5185\u5BB9\u5B8C\u6574\u3002",
                    UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE, false));
        }
        return card;
    }

    /**
     * The restore entry.
     *
     * <p>Nothing here writes. The button only opens a confirmation; the write
     * happens after the user has read what it will replace, and never as a side
     * effect of opening this screen.</p>
     */
    private LinearLayout restoreCard() {
        LinearLayout card = card();
        card.addView(section("\u6062\u590D"));
        card.addView(line("\u6062\u590D\u91C7\u7528\u5B8C\u6574\u8986\u76D6\uFF1A\u5F53\u524D\u5168\u90E8\u5B66\u671F\u3001\u8BFE\u7A0B\u4E0E\u4F5C\u606F\u8868\u90FD\u4F1A\u88AB\u8FD9\u4EFD\u5907\u4EFD\u66FF\u6362\uFF0C"
                + "\u5907\u4EFD\u91CC\u6CA1\u6709\u7684\u8BFE\u7A0B\u548C\u5B66\u671F\u4E0D\u4F1A\u4FDD\u7559\u3002",
                Palette.ON_SURFACE_VARIANT, UiTokens.SPACE_S));
        card.addView(line("\u6062\u590D\u524D\u4F1A\u81EA\u52A8\u4FDD\u7559\u4E00\u4EFD\u5F53\u524D\u6570\u636E\uFF0C\u6062\u590D\u672A\u901A\u8FC7\u6821\u9A8C\u65F6\u4F1A\u81EA\u52A8\u8FD8\u539F\u3002",
                Palette.ON_SURFACE_VARIANT, UiTokens.SPACE_XS));
        TextView button = UiKit.filledButton(this, "\u786E\u8BA4\u6062\u590D", view -> confirmRestore());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(UiTokens.BUTTON_HEIGHT_COMPACT));
        params.topMargin = dp(UiTokens.SPACE_M);
        card.addView(button, params);
        return card;
    }

    // -------------------------------------------------------------- restoring

    /**
     * Asks once, in words that say what is about to be replaced.
     *
     * <p>The counts come from the file, so the sentence names the very numbers
     * the user has just been reading rather than a vague warning.</p>
     */
    private void confirmRestore() {
        if (restoring || document == null) {
            return;
        }
        BackupPreview preview = BackupPreview.of(fileName, document);
        String message = "\u5C06\u7528\u8FD9\u4EFD\u5907\u4EFD\u5B8C\u6574\u8986\u76D6\u5F53\u524D\u8BFE\u8868\uFF1A\n"
                + preview.summary() + "\n\n"
                + "\u5F53\u524D\u5168\u90E8\u5B66\u671F\u3001\u8BFE\u7A0B\u4E0E\u4F5C\u606F\u8868\u90FD\u4F1A\u88AB\u66FF\u6362\uFF0C\u5907\u4EFD\u91CC\u6CA1\u6709\u7684\u5185\u5BB9\u4E0D\u4F1A\u4FDD\u7559\u3002\n\n"
                + "\u6062\u590D\u524D\u4F1A\u81EA\u52A8\u4FDD\u7559\u4E00\u4EFD\u5F53\u524D\u6570\u636E\uFF1B\u6062\u590D\u672A\u901A\u8FC7\u6821\u9A8C\u65F6\u4F1A\u81EA\u52A8\u8FD8\u539F\u3002";
        UiKit.dialog(this, "\u786E\u8BA4\u6062\u590D", message, null, null,
                UiKit.Action.of("\u53D6\u6D88", null),
                UiKit.Action.destructive("\u8986\u76D6\u6062\u590D", this::startRestore));
    }

    /**
     * Re-reads the file, then hands it to the repository.
     *
     * <p>The file is read again rather than reusing what the preview parsed: a
     * document picked minutes ago may have been edited or replaced since, and the
     * restore must agree with what is on disk at the moment it runs. The whole
     * restore -- read, check, snapshot, commit, read-back -- happens on one
     * background thread over its own repository, so nothing here is shared with
     * the screens behind this one.</p>
     */
    private void startRestore() {
        if (restoring || document == null) {
            return;
        }
        restoring = true;
        subtitle.setText("\u6B63\u5728\u6062\u590D\u2026");
        body.removeAllViews();
        body.addView(UiKit.caption(this, "\u6B63\u5728\u8BFB\u53D6\u5E76\u6062\u590D\u5907\u4EFD\u6587\u4EF6\u2026"));

        final Uri target = uri;
        final Context app = getApplicationContext();
        final ContentResolver resolver = app.getContentResolver();
        backupService.restore(() -> resolver.openInputStream(target),
                () -> new CourseRepository(app), result -> {
                    restoring = false;
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (!result.isOk()) {
                        showRestoreFailure("\u6062\u590D\u672A\u5F00\u59CB", result.problem(),
                                "\u5F53\u524D\u8BFE\u8868\u6CA1\u6709\u4EFB\u4F55\u6539\u52A8\u3002");
                    } else {
                        showRestoreOutcome(result.value());
                    }
                });
    }

    /** Reports what the repository did, in the four states it can be in. */
    private void showRestoreOutcome(RestoreReport report) {
        switch (report.status()) {
            case RESTORED:
                subtitle.setText("\u5DF2\u6062\u590D");
                // Every course the phone held a minute ago has just been replaced,
                // so every registered alarm points at something that may no longer
                // exist. Rebuilding here is the difference between "restored" and
                // "restored, but still reminding me about last term".
                ReminderScheduler.sync(this);
                // A restore replaces the whole term, so the widget's answer changes
                // wholesale with it.
                CourseWidgetUpdater.refreshAll(this);
                body.removeAllViews();
                LinearLayout done = card();
                done.addView(UiKit.text(this, "\u6062\u590D\u5B8C\u6210",
                        UiTokens.Type.TITLE_SMALL, Palette.PRIMARY, true));
                done.addView(line(report.message(), Palette.ON_SURFACE,
                        UiTokens.SPACE_S));
                done.addView(line("\u8FD4\u56DE\u9996\u9875\u5373\u53EF\u770B\u5230\u6062\u590D\u540E\u7684\u8BFE\u8868\u3002", Palette.ON_SURFACE_VARIANT,
                        UiTokens.SPACE_S));
                body.addView(done);
                break;
            case ROLLED_BACK:
                showRestoreFailure("\u6062\u590D\u5DF2\u64A4\u9500", report.message(),
                        "\u5F53\u524D\u8BFE\u8868\u5DF2\u8FD8\u539F\u4E3A\u6062\u590D\u524D\u7684\u6570\u636E\u3002");
                break;
            case UNCHANGED:
                showRestoreFailure("\u6062\u590D\u672A\u751F\u6548", report.message(),
                        "\u5F53\u524D\u8BFE\u8868\u6CA1\u6709\u4EFB\u4F55\u6539\u52A8\u3002");
                break;
            default:
                showRestoreFailure("\u6062\u590D\u72B6\u6001\u9700\u8981\u786E\u8BA4", report.message(),
                        "\u65E0\u6CD5\u786E\u8BA4\u5F53\u524D\u6570\u636E\u5904\u4E8E\u54EA\u4E00\u4EFD\u72B6\u6001\u3002\u8BF7\u5148\u4E0D\u8981\u7EE7\u7EED\u4FEE\u6539\u8BFE\u8868\uFF0C"
                                + "\u5E76\u4FDD\u7559\u8FD9\u4EFD\u5907\u4EFD\u6587\u4EF6\uFF0C\u4EE5\u4FBF\u518D\u6B21\u6062\u590D\u3002");
                break;
        }
    }

    private void showRestoreFailure(String title, String reason, String state) {
        subtitle.setText(title);
        body.removeAllViews();
        LinearLayout card = card();
        card.addView(UiKit.text(this, title, UiTokens.Type.TITLE_SMALL,
                Palette.ERROR, true));
        card.addView(line(reason, Palette.ON_SURFACE, UiTokens.SPACE_S));
        if (state != null && !state.isEmpty()) {
            card.addView(line(state, Palette.ON_SURFACE_VARIANT, UiTokens.SPACE_S));
        }
        body.addView(card);
    }

    // ---------------------------------------------------------------- building

    private LinearLayout card() {
        LinearLayout card = UiKit.card(this);
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        return card;
    }

    /** A card as a body child, with a gap above it. */
    private View spaced(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private TextView section(String text) {
        return UiKit.text(this, text, UiTokens.Type.TITLE_SMALL, Palette.ON_SURFACE, true);
    }

    private TextView line(String text, int color, int topMarginDp) {
        TextView view = UiKit.text(this, text, UiTokens.Type.BODY_MEDIUM, color, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        view.setLayoutParams(params);
        return view;
    }

    /** One label-and-value line inside a card. */
    private void addRow(LinearLayout card, String label, String value) {
        LinearLayout row = UiKit.infoRow(this, label, value);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_S);
        card.addView(row, params);
    }

    /** One semester: its label, its dates and how many courses it holds. */
    private View semesterRow(BackupPreview.SemesterSummary row) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(UiKit.text(this, row.label(), UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, true));
        block.addView(UiKit.text(this, row.dateRange() + " \u00B7 " + row.courseCount() + " \u95E8\u8BFE\u7A0B",
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_SURFACE_VARIANT, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_M);
        block.setLayoutParams(params);
        return block;
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

package com.weihe.schedule;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.weihe.schedule.design.Palette;
import com.weihe.schedule.ui.Insets;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * What this app is, and what it does with your data.
 *
 * <p>Split out of the settings page, where it was two lines at the bottom: the
 * version, and a sentence about where the timetable lives. Both are worth saying
 * properly, and neither belongs in the middle of a list of things to configure.</p>
 *
 * <h2>Nothing here is written down twice</h2>
 *
 * <p>The version is read from the installed package rather than typed into the
 * source, so a release that bumps {@code versionName} cannot leave this page
 * claiming the old one. The minimum supported version is read from the package too,
 * and the network claim is not a promise typed into a string -- it is what the
 * manifest already says, which is that the app requests no network permission at
 * all.</p>
 *
 * <p>There is no feedback entry and no website: the project has no channel
 * configured, and inventing an address here would send people to a mailbox nobody
 * reads.</p>
 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The app bar is the page surface, so both system bars take that colour and
        // keep dark icons on it; see Insets for why this cannot live in the theme.
        Insets.applyBrandedSystemBars(this);
        setContentView(buildRoot());
    }

    private View buildRoot() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Palette.SURFACE);

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
        // Bottom padding clears the system navigation bar, so the last paragraph is
        // never half under it.
        body.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_M),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L + UiTokens.SPACE_XL));

        body.addView(brandCard());
        body.addView(spaced(infoCard(), UiTokens.SPACE_M));
        body.addView(spaced(privacyCard(), UiTokens.SPACE_M));

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return frame;
    }

    private View createHeader() {
        return UiKit.detailAppBar(this, "\u5173\u4E8E\u62FE\u8BFE",
                "WeiheSchedule", view -> finish());
    }

    // ------------------------------------------------------------------- brand

    /**
     * The icon, the name, and what the app is for.
     *
     * <p>Centred, because this is the one page in the app that is about the app
     * rather than about a timetable; everything else is left-aligned and reads as a
     * list. The icon is the launcher icon, so the picture here is the picture the
     * user tapped to get in -- a second drawing of the same idea would only be a
     * slightly different one.</p>
     */
    private View brandCard() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(this);
        // The brand artwork, not the launcher icon. R.mipmap.ic_launcher is an
        // <adaptive-icon>: the platform draws it on a 108dp canvas whose outer ring
        // exists only so launcher masks have something to cut, so the illustration
        // arrives already inset to the central safe area -- about three quarters of
        // the box. That inset is right on a home screen and wrong here, where there
        // is no mask and the drawing simply looked smaller than it should. This
        // drawable is the same artwork cropped to its own ink and centred, with no
        // canvas around it.
        icon.setImageResource(R.drawable.ic_brand_logo);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = dp(72);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                iconSize, iconSize);
        iconParams.topMargin = dp(UiTokens.SPACE_S);
        card.addView(icon, iconParams);

        TextView name = UiKit.text(this, "\u62FE\u8BFE", UiTokens.Type.HEADLINE_SMALL,
                Palette.ON_SURFACE, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(UiTokens.SPACE_M);
        card.addView(name, nameParams);

        TextView tagline = UiKit.text(this,
                "\u8BA9\u8BFE\u7A0B\u5B89\u6392\u66F4\u6E05\u6670\uFF0C"
                        + "\u8BA9\u6821\u56ED\u751F\u6D3B\u66F4\u6709\u5E8F",
                UiTokens.Type.BODY_MEDIUM, Palette.ON_SURFACE_VARIANT,
                Typeface.NORMAL);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        taglineParams.topMargin = dp(UiTokens.SPACE_S);
        taglineParams.bottomMargin = dp(UiTokens.SPACE_S);
        card.addView(tagline, taglineParams);
        return card;
    }

    // -------------------------------------------------------------- app details

    /** Name, version and the oldest Android this build runs on. */
    private View infoCard() {
        LinearLayout card = card();
        card.addView(UiKit.sectionLabel(this, "\u5E94\u7528\u4FE1\u606F"));
        card.addView(fact("\u5E94\u7528\u540D\u79F0", appName()));
        card.addView(fact("\u7248\u672C", versionName()));
        card.addView(fact("\u6700\u4F4E\u652F\u6301\u7CFB\u7EDF", minimumAndroid()));
        return card;
    }

    // ------------------------------------------------------------ data privacy

    /**
     * Where the timetable lives, and what would destroy it.
     *
     * <p>Every claim here is one the code already makes: the app stores its data in
     * its own private storage, requests no network permission, needs the
     * notification permission for reminders, and writes backups as plain files the
     * user keeps. Nothing is promised that a future change could quietly break
     * without this text having to change too -- the network sentence in particular
     * is checkable against the manifest in one command.</p>
     */
    private View privacyCard() {
        LinearLayout card = card();
        card.addView(UiKit.sectionLabel(this, "\u6570\u636E\u4E0E\u9690\u79C1"));
        card.addView(paragraph("\u8BFE\u8868\u3001\u5B66\u671F\u4E0E\u4F5C\u606F\u65F6\u95F4"
                + "\u90FD\u4FDD\u5B58\u5728\u672C\u673A\uFF0C\u4E0D\u4F1A\u4E0A\u4F20\uFF0C"
                + "\u4E5F\u4E0D\u4F1A\u81EA\u52A8\u540C\u6B65\u5230\u5176\u4ED6\u8BBE\u5907\u3002"));
        card.addView(paragraph("\u5E94\u7528\u672A\u7533\u8BF7\u8054\u7F51\u6743\u9650\uFF1B"
                + "\u4E0A\u8BFE\u63D0\u9192\u53EA\u5728\u672C\u673A\u8BA1\u7B97\u4E0E\u53D1\u9001"
                + "\uFF0C\u9700\u8981\u4F60\u6388\u4E88\u901A\u77E5\u6743\u9650\u3002"));
        card.addView(paragraph("\u5378\u8F7D\u5E94\u7528\u6216\u5728\u7CFB\u7EDF\u8BBE\u7F6E"
                + "\u4E2D\u6E05\u9664\u5E94\u7528\u6570\u636E\uFF0C\u90FD\u4F1A\u4E00\u5E76"
                + "\u5220\u9664\u8FD9\u4E9B\u5185\u5BB9\u4E14\u65E0\u6CD5\u6062\u590D\u3002"));
        card.addView(paragraph("\u9700\u8981\u4FDD\u7559\u65F6\uFF0C\u8BF7\u5148\u5728"
                + "\u300C\u8BBE\u7F6E \u2192 \u8BFE\u8868\u6570\u636E\u300D\u4E2D\u5BFC\u51FA"
                + "\u5907\u4EFD\u3002\u5907\u4EFD\u662F\u660E\u6587\u6587\u4EF6\uFF0C\u5305\u542B"
                + "\u6559\u5E08\u59D3\u540D\u4E0E\u4E0A\u8BFE\u5730\u70B9\uFF0C\u8BF7\u59A5\u5584"
                + "\u4FDD\u7BA1\u3002"));
        return card;
    }

    // ------------------------------------------------------------------ pieces

    /** One labelled line: the name on the left, what it is on the right. */
    private View fact(String label, String value) {
        LinearLayout row = UiKit.infoRow(this, label, value);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(UiTokens.SPACE_M);
        row.setLayoutParams(rowParams);
        return row;
    }

    /** A block of prose: full width, wrapped, comfortably spaced. */
    private View paragraph(String text) {
        TextView view = UiKit.text(this, text, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        view.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(UiTokens.SPACE_M);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L),
                dp(UiTokens.SPACE_L), dp(UiTokens.SPACE_L));
        card.setBackground(UiKit.outline(this, Palette.SURFACE_CONTAINER,
                UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        UiKit.elevate(card, UiTokens.RADIUS_L, UiTokens.ELEVATION_NONE);
        return card;
    }

    private View spaced(View view, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        view.setLayoutParams(params);
        return view;
    }

    // ------------------------------------------------------------- app metadata

    /** The name the launcher shows, so this page and the icon cannot disagree. */
    private String appName() {
        try {
            PackageManager manager = getPackageManager();
            CharSequence label = manager.getApplicationLabel(
                    manager.getApplicationInfo(getPackageName(), 0));
            return label == null ? "" : label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * The version of the running build.
     *
     * <p>Read rather than written down: a hard-coded {@code 1.0} here would keep
     * saying 1.0 on the release after next, and the one page that exists to state
     * the version is the last place that should be wrong about it.</p>
     */
    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String name = info.versionName;
            return name == null || name.isEmpty() ? "" : name;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * The oldest Android this build installs on, said the way a person would.
     *
     * <p>The API level is what the build actually declares; the release name is a
     * second, well-known mapping on top of it. Both are shown, so a reader who knows
     * only one of the two is served, and an API level this app does not have a name
     * for is still reported rather than guessed at.</p>
     */
    private String minimumAndroid() {
        int level = 0;
        try {
            level = getApplicationInfo().minSdkVersion;
        } catch (RuntimeException e) {
            level = 0;
        }
        if (level <= 0) {
            return "";
        }
        String release = androidReleaseName(level);
        if (release.isEmpty()) {
            return "API " + level;
        }
        return release + "\uFF08API " + level + "\uFF09\u53CA\u4EE5\u4E0A";
    }

    /** Android 8.0 for 26, and so on; empty for a level without a public name. */
    private static String androidReleaseName(int level) {
        switch (level) {
            case 26: return "Android 8.0";
            case 27: return "Android 8.1";
            case 28: return "Android 9";
            case 29: return "Android 10";
            case 30: return "Android 11";
            case 31: return "Android 12";
            case 32: return "Android 12L";
            case 33: return "Android 13";
            case 34: return "Android 14";
            case 35: return "Android 15";
            case 36: return "Android 16";
            default: return "";
        }
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }
}

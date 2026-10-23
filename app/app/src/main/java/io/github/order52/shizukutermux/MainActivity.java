package io.github.order52.shizukutermux;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * One screen that asks Termux to run the shizuku-termux command and shows the result.
 *
 * Termux runs the command through its RUN_COMMAND service and reports stdout, stderr
 * and the exit code back through a PendingIntent, received by {@link #resultReceiver}.
 */
public class MainActivity extends Activity {

    static final String ACTION_START = "io.github.order52.shizukutermux.START";
    static final String ACTION_STOP = "io.github.order52.shizukutermux.STOP";

    private static final String TERMUX = "com.termux";
    private static final String SHIZUKU = "moe.shizuku.privileged.api";
    private static final String PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_BIN = "/data/data/com.termux/files/usr/bin/";
    private static final String ACTION_RESULT = "io.github.order52.shizukutermux.RESULT";
    private static final String EXTRA_COMMAND = "command";
    private static final String INSTALL_COMMAND =
            "curl -fsSL https://raw.githubusercontent.com/Order52/shizuku-termux/main/install.sh | bash";
    private static final String TERMUX_DOWNLOAD = "https://github.com/termux/termux-app/releases/latest";
    private static final String SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/";
    private static final int REQUEST_PERMISSION = 1;
    private static final long RESULT_TIMEOUT_MS = 60_000L;
    // Termux added result reporting for RUN_COMMAND in v0.109.
    private static final int TERMUX_MIN_VERSION_CODE = 109;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView setupView;
    private TextView outputView;
    private Button startButton;
    private Button stopButton;
    private Button refreshButton;
    private String pendingCommand;
    private boolean busy;
    private int requestCounter;

    private final Runnable timeout = () -> {
        setBusy(false);
        showOutput("No answer from Termux.\n\n"
                + "Open Termux once, then try again. If it keeps happening, your Termux is "
                + "probably the old Google Play version; install Termux from GitHub or F-Droid.");
    };

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(timeout);
            setBusy(false);
            onResult(intent.getStringExtra(EXTRA_COMMAND), intent.getBundleExtra("result"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());

        IntentFilter filter = new IntentFilter(ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(resultReceiver, filter);
        }
        handleShortcut(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShortcut(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (refreshSetup() && !busy && pendingCommand == null && statusView.getTag() == null) {
            statusView.setTag("checked");
            run("status");
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        unregisterReceiver(resultReceiver);
        super.onDestroy();
    }

    private void handleShortcut(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (ACTION_START.equals(intent.getAction())) {
            statusView.setTag("checked");
            run("start");
        } else if (ACTION_STOP.equals(intent.getAction())) {
            statusView.setTag("checked");
            run("stop");
        }
    }

    // ---- Setup checks -------------------------------------------------------------

    /** Updates the checklist. Returns true when commands can be sent to Termux. */
    private boolean refreshSetup() {
        StringBuilder sb = new StringBuilder();
        PackageInfo termux = packageInfo(TERMUX);
        boolean termuxOk = termux != null;
        boolean termuxNewEnough = termuxOk && versionCode(termux) >= TERMUX_MIN_VERSION_CODE;
        boolean permissionOk = termuxOk && hasRunCommandPermission();
        boolean shizukuOk = packageInfo(SHIZUKU) != null;

        line(sb, termuxOk, termuxOk
                ? "Termux installed (" + termux.versionName + ")"
                : "Termux is not installed");
        if (termuxOk && !termuxNewEnough) {
            line(sb, false, "Termux is too old (Google Play version). Install it from GitHub or F-Droid.");
        }
        line(sb, shizukuOk, shizukuOk ? "Shizuku installed" : "Shizuku is not installed");
        line(sb, permissionOk, permissionOk
                ? "Allowed to run commands in Termux"
                : "Permission to run commands in Termux is needed");
        sb.append("\nIn Termux, run this once:\n");
        setupView.setText(sb.toString());

        boolean ready = termuxOk && termuxNewEnough && permissionOk;
        startButton.setEnabled(ready && !busy);
        stopButton.setEnabled(ready && !busy);
        refreshButton.setEnabled(ready && !busy);
        if (!termuxOk || !termuxNewEnough) {
            statusView.setText("Install Termux first");
        } else if (!permissionOk) {
            statusView.setText("Tap Start to allow running commands");
            startButton.setEnabled(!busy);
        }
        return ready;
    }

    private void line(StringBuilder sb, boolean ok, String text) {
        sb.append(ok ? "✓  " : "✗  ").append(text).append('\n');
    }

    private boolean hasRunCommandPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED;
    }

    private PackageInfo packageInfo(String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            refreshSetup();
            if (pendingCommand != null) {
                String command = pendingCommand;
                pendingCommand = null;
                run(command);
            }
        } else {
            pendingCommand = null;
            refreshSetup();
            showOutput("Permission denied. Allow \"Run commands in Termux environment\" in "
                    + "Settings > Apps > Shizuku Termux > Permissions.");
            openAppSettings();
        }
    }

    // ---- Running commands ---------------------------------------------------------

    private void run(String command) {
        if (busy) return;
        if (packageInfo(TERMUX) == null) {
            showOutput("Termux is not installed.");
            return;
        }
        if (!hasRunCommandPermission()) {
            pendingCommand = command;
            requestPermissions(new String[]{PERMISSION_RUN_COMMAND}, REQUEST_PERMISSION);
            return;
        }

        Intent result = new Intent(ACTION_RESULT)
                .setPackage(getPackageName())
                .putExtra(EXTRA_COMMAND, command);
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(this, ++requestCounter, result, flags);

        String script = "[ -x " + TERMUX_BIN + "shizuku ] || exit 127; exec "
                + TERMUX_BIN + "shizuku " + command + " </dev/null";
        Intent intent = new Intent("com.termux.RUN_COMMAND")
                .setClassName(TERMUX, "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BIN + "bash")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", script})
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
        try {
            startService(intent);
        } catch (RuntimeException e) {
            showOutput("Could not reach Termux: " + e.getMessage()
                    + "\n\nOpen Termux once so Android lets it start, then try again.");
            return;
        }
        setBusy(true);
        showOutput("");
        statusView.setText(label(command));
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, RESULT_TIMEOUT_MS);
    }

    private void onResult(String command, Bundle result) {
        if (result == null) {
            showOutput("Termux returned no result.");
            return;
        }
        String stdout = result.getString("stdout", "");
        String stderr = result.getString("stderr", "");
        int exitCode = result.getInt("exitCode", -1);
        int err = result.getInt("err", 0);
        String errmsg = result.getString("errmsg", "");

        // Termux refused to run anything (usually allow-external-apps is off).
        if (err > 0 && exitCode == -1) {
            statusView.setText("Termux refused the command");
            showOutput(errmsg.contains("allow-external-apps")
                    ? "Termux does not allow other apps to run commands yet.\n\n"
                        + "Run the install command in Termux (it turns this on), then try again."
                    : errmsg);
            return;
        }
        if (exitCode == 127) {
            statusView.setText("shizuku-termux is not installed in Termux");
            showOutput("Copy the install command below, paste it into Termux and press Enter.");
            return;
        }

        String text = (stdout + "\n" + stderr).trim();
        if (text.contains("is running") || text.contains("already running")) {
            statusView.setText("Shizuku is running");
        } else if (text.contains("not running") || text.contains("stopped")) {
            statusView.setText("Shizuku is not running");
        } else if (exitCode != 0) {
            statusView.setText("Could not " + ("status".equals(command) ? "check" : command) + " Shizuku");
        }
        showOutput(text.isEmpty() ? "Done." : text);
    }

    private String label(String command) {
        switch (command) {
            case "start": return "Starting Shizuku…";
            case "stop": return "Stopping Shizuku…";
            default: return "Checking…";
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        refreshSetup();
    }

    private void showOutput(String text) {
        outputView.setText(text);
        outputView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    // ---- UI -----------------------------------------------------------------------

    private View buildLayout() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        statusView = text("", 20, true);
        statusView.setPadding(0, 0, 0, dp(12));
        root.addView(statusView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = button("Start", v -> run("start"));
        stopButton = button("Stop", v -> run("stop"));
        refreshButton = button("Status", v -> run("status"));
        buttons.addView(startButton, weight());
        buttons.addView(stopButton, weight());
        buttons.addView(refreshButton, weight());
        root.addView(buttons);

        outputView = text("", 14, false);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(0, dp(16), 0, 0);
        outputView.setVisibility(View.GONE);
        root.addView(outputView);

        TextView setupTitle = text("Setup", 18, true);
        setupTitle.setPadding(0, dp(24), 0, dp(4));
        root.addView(setupTitle);

        setupView = text("", 14, false);
        root.addView(setupView);

        TextView install = text(INSTALL_COMMAND, 13, false);
        install.setTypeface(Typeface.MONOSPACE);
        install.setTextIsSelectable(true);
        install.setPadding(dp(8), dp(8), dp(8), dp(8));
        install.setBackgroundColor(0x22808080);
        root.addView(install);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.addView(button("Copy", v -> copyInstallCommand()), weight());
        links.addView(button("Open Termux", v -> openTermux()), weight());
        root.addView(links);

        LinearLayout downloads = new LinearLayout(this);
        downloads.setOrientation(LinearLayout.HORIZONTAL);
        downloads.addView(button("Get Termux", v -> openUrl(TERMUX_DOWNLOAD)), weight());
        downloads.addView(button("Get Shizuku", v -> openUrl(SHIZUKU_DOWNLOAD)), weight());
        root.addView(downloads);

        TextView help = text(getString(R.string.help), 13, false);
        help.setPadding(0, dp(16), 0, 0);
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        return scroll;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.START);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void copyInstallCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("install", INSTALL_COMMAND));
        Toast.makeText(this, "Copied. Paste it in Termux.", Toast.LENGTH_SHORT).show();
    }

    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(TERMUX);
        if (launch == null) {
            openUrl(TERMUX_DOWNLOAD);
        } else {
            startActivity(launch);
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (RuntimeException ignored) {
        }
    }
}

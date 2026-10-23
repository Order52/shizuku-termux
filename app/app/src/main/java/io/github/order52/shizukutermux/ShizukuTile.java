package io.github.order52.shizukutermux;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/**
 * Quick Settings tile that starts or stops Shizuku by asking Termux to run the shizuku-termux
 * command. Android binds it only while the quick panel is open; Termux reports back to
 * {@link Result}, which saves whether Shizuku is running and refreshes the tile.
 */
public class ShizukuTile extends TileService {

    private static final String ACTION_RESULT = "io.github.order52.shizukutermux.TILE_RESULT";

    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        if (checkSelfPermission(MainActivity.PERMISSION_RUN_COMMAND) != PackageManager.PERMISSION_GRANTED) {
            // The permission prompt needs a screen, so open the app once.
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(open);
            }
            return;
        }
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent result = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_RESULT).setClass(this, Result.class), flags);
        try {
            startService(MainActivity.termuxIntent("toggle", result));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not reach Termux. Open Termux once, then try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        // Flip right away so the tap feels instant; Termux's result corrects it if needed.
        boolean running = !prefs(this).getBoolean("running", false);
        prefs(this).edit().putBoolean("running", running).apply();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(running ? "Starting…" : "Stopping…");
            tile.updateTile();
        }
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean running = prefs(this).getBoolean("running", false);
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(running ? "On" : "Off");
        tile.updateTile();
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("tile", MODE_PRIVATE);
    }

    /** Saves what the last command did; also used by the app so the tile stays in sync. */
    static void remember(Context context, String output) {
        Boolean running = MainActivity.runningFrom(output);
        if (running == null) return;
        prefs(context).edit().putBoolean("running", running).apply();
        TileService.requestListeningState(context, new ComponentName(context, ShizukuTile.class));
    }

    /** Receives Termux's result for a tile tap. Not exported; only Termux's reply reaches it. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle result = intent.getBundleExtra("result");
            String output = result == null ? ""
                    : (result.getString("stdout", "") + "\n" + result.getString("stderr", "")).trim();
            remember(context, output);
            if (result == null || result.getInt("exitCode", -1) != 0) {
                Toast.makeText(context, output.isEmpty() ? "Shizuku didn't respond. Open the app to see why."
                        : output, Toast.LENGTH_LONG).show();
            }
        }
    }
}

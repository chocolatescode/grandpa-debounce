package pl.dziadek.debounce;

import android.accessibilityservice.AccessibilityService;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseLongArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Drops "ghost" key presses from a remote whose IR signal briefly drops out:
 * if a key goes DOWN again within DEBOUNCE ms of its own UP, that second
 * press (down, any repeats, up) is swallowed.
 *
 * Window is tunable without rebuilding:
 *   adb shell settings put global dziadek_debounce_ms 200
 */
public class DebounceService extends AccessibilityService {

    private static final String TAG = "DziadekDebounce";
    private static final String SETTING_KEY = "dziadek_debounce_ms";
    private static final long DEFAULT_DEBOUNCE_MS = 150;

    private final SparseLongArray lastUpTime = new SparseLongArray();
    private final SparseBooleanArray suppressing = new SparseBooleanArray();

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "connected, window=" + debounceMs() + "ms");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                long gap = event.getEventTime() - lastUpTime.get(code, Long.MIN_VALUE / 2);
                if (gap >= 0 && gap < debounceMs()) {
                    suppressing.put(code, true);
                    Log.i(TAG, "dropped " + KeyEvent.keyCodeToString(code) + " gap=" + gap + "ms");
                    return true;
                }
                suppressing.put(code, false);
            }
            return suppressing.get(code, false);
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            // Chain from every UP, so a 3x stutter is collapsed to one press too.
            lastUpTime.put(code, event.getEventTime());
            if (suppressing.get(code, false)) {
                suppressing.put(code, false);
                return true;
            }
        }
        return false;
    }

    private long debounceMs() {
        try {
            String v = Settings.Global.getString(getContentResolver(), SETTING_KEY);
            if (v != null) return Long.parseLong(v.trim());
        } catch (RuntimeException ignored) {
        }
        return DEFAULT_DEBOUNCE_MS;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}

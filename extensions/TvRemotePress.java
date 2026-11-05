package io.freemytv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

/**
 * TV remote OK: short tap opens the item; long hold invokes a separate action.
 * Consumes only SELECT keys, never DPAD navigation. Touch/click still works.
 */
final class TvRemotePress {
    private static final long HOLD_MS = 625L;

    private TvRemotePress() {}

    static void bind(View view, Runnable tap, Runnable hold) {
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] pressed = {false};
        final boolean[] fired = {false};
        Runnable delayed = () -> {
            if (pressed[0] && view.isAttachedToWindow() && view.hasFocus()) {
                fired[0] = true;
                hold.run();
            }
        };
        view.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                    && keyCode != KeyEvent.KEYCODE_ENTER
                    && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER
                    && keyCode != KeyEvent.KEYCODE_BUTTON_A) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (!pressed[0]) {
                    pressed[0] = true;
                    fired[0] = false;
                    handler.postDelayed(delayed, HOLD_MS);
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean wasPressed = pressed[0];
                pressed[0] = false;
                handler.removeCallbacks(delayed);
                if (wasPressed && !fired[0] && view.isAttachedToWindow()) tap.run();
                fired[0] = false;
                return true;
            }
            return false;
        });
        view.setOnClickListener(v -> tap.run());
        view.setOnLongClickListener(v -> {
            if (!fired[0]) {
                fired[0] = true;
                handler.removeCallbacks(delayed);
                hold.run();
            }
            return true;
        });
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                pressed[0] = false;
                fired[0] = false;
                handler.removeCallbacks(delayed);
            }
        });
    }
}

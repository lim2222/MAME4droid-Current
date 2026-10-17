/*
 * This file is part of MAME4droid.
 *
 * Copyright (C) 2026 David Valdeita (Seleuco)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Linking MAME4droid statically or dynamically with other modules is
 * making a combined work based on MAME4droid. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * In addition, as a special exception, the copyright holders of MAME4droid
 * give you permission to combine MAME4droid with free software programs
 * or libraries that are released under the GNU LGPL and with code included
 * in the standard release of MAME under the MAME License (or modified
 * versions of such code, with unchanged license). You may copy and
 * distribute such a system following the terms of the GNU GPL for MAME4droid
 * and the licenses of the other code concerned, provided that you include
 * the source code of that other code when and as the GNU GPL requires
 * distribution of source code.
 *
 * Note that people who make modified versions of MAME4idroid are not
 * obligated to grant this special exception for their modified versions; it
 * is their choice whether to do so. The GNU General Public License
 * gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version
 * which carries forward this exception.
 *
 * MAME4droid is dual-licensed: Alternatively, you can license MAME4droid
 * under a MAME license, as set out in http://mamedev.org/
 */

package com.seleuco.mame4droid.input;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.R;

import java.util.ArrayList;

/**
 * On screen mahjong panel.
 *
 * Every button is an independent control (its own {@link InputValue} of type
 * {@link TouchController#TYPE_MAHJONG_IMG}) so that it can be moved, faded and
 * hidden individually in the "customize layout" screen, exactly like the
 * regular arcade buttons.
 *
 * All buttons send plain Android key codes. MAME maps its mahjong inputs
 * (IPT_MAHJONG_*) to keyboard keys by default, and MAME4droid's OSD translates
 * the Android key codes into MAME ones
 * (see src/osd/myosd/droid/myosd_droid.cpp, droid_map_keyboard()), so a tile
 * button only has to press the key MAME already listens to. With the default
 * mapping that is simply the letter printed on the button:
 *
 *   Mahjong A..N, O, Q -> A..N, O, Q
 *   Mahjong P          -> ';'   (MAME binds Mahjong P to KEYCODE_COLON)
 *   Kan / Pon / Chi / Reach / Ron -> LCTRL / LALT / SPACE / LSHIFT / Z
 *   Bet / Take Score / Double Up / Big / Small / Last Chance / Bookkeeping /
 *   Credit / Payout / Service / Left / Right / D-Play / H-Play / S-Play
 *                      -> 3 / RCTRL / RSHIFT / ENTER / BACKSPACE / RALT / 0 /
 *                         9 / 4 / F2 / K / L / A / E / C
 *
 * (There is no "Flip Flop" button: no artwork was provided for it.)
 *
 * The image naming follows {@code mj_<name>_<key>.png}; tile buttons are just
 * {@code mj_<letter>.png}.
 *
 * Note on the press duration: the native keyboard state is *polled*, so a down
 * and an up landing in the same frame would be dropped. A short minimum hold is
 * therefore enforced (mirroring Keyboard.handleKeyboard()).
 */
public class TouchMahjong implements IController {

    /* ---- button table ---------------------------------------------------- */

    /** Number of mahjong buttons. */
    public static final int BUTTON_COUNT = 37;

    /** Preset layout groups: row 1 = tiles A..Q, row 2 = actions, row 3 = rest. */
    private static final int TILES_COUNT = 17;
    private static final int ACTIONS_COUNT = 5;
    private static final int EXTRAS_COUNT = BUTTON_COUNT - TILES_COUNT - ACTIONS_COUNT;

    /** Drawable of every button, indexed by the layout value. */
    private static final int[] IMAGE_RES = {
        // 0..16 - tiles A..Q (Mahjong A..Q)
        R.drawable.mj_a, R.drawable.mj_b, R.drawable.mj_c, R.drawable.mj_d,
        R.drawable.mj_e, R.drawable.mj_f, R.drawable.mj_g, R.drawable.mj_h,
        R.drawable.mj_i, R.drawable.mj_j, R.drawable.mj_k, R.drawable.mj_l,
        R.drawable.mj_m, R.drawable.mj_n, R.drawable.mj_o, R.drawable.mj_p,
        R.drawable.mj_q,
        // 17..21 - actions
        R.drawable.mj_chi_space, R.drawable.mj_pon_lalt, R.drawable.mj_kan_lcontrol,
        R.drawable.mj_reach_lshift, R.drawable.mj_ron_z,
        // 22..36 - everything else
        R.drawable.mj_bet_3, R.drawable.mj_take_rcontrol, R.drawable.mj_doubleup_rshift,
        R.drawable.mj_big_enter, R.drawable.mj_small_backspace, R.drawable.mj_lastchance_ralt,
        R.drawable.mj_bookkeeping_0, R.drawable.mj_credit_9, R.drawable.mj_payout_4,
        R.drawable.mj_service_f2, R.drawable.mj_left_k, R.drawable.mj_right_m,
        R.drawable.mj_dplay_a, R.drawable.mj_hplay_e, R.drawable.mj_splay_c
    };

    /** Android key code sent by every button, indexed by the layout value. */
    private static final int[] KEY_CODE = {
        // 0..16 - tiles A..Q. Mahjong P is the ';' key in MAME, not 'P'.
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
        KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
        KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_SEMICOLON,
        KeyEvent.KEYCODE_Q,
        // 17..21 - chi, pon, kan, reach, ron
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_Z,
        // 22..36 - bet, take, double up, big, small, last chance, bookkeeping,
        // credit, payout, service, left, right, d-play, h-play, s-play
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_4,
        KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_M,
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_C
    };

    /**
     * Minimum time a key stays down. MAME's keyboard state is polled once per
     * frame, so a tap shorter than a frame would never be seen.
     */
    private static final int MIN_HOLD_MS = 45;

    /** Alpha used to draw a hidden button while the customizer is open. */
    public static final int HIDDEN_ALPHA = 55;

    /* ---- state ----------------------------------------------------------- */

    protected MAME4droid mm = null;

    private TouchController touchController = null;

    /** Visual properties pushed by InputView.updateImages(). */
    private final BitmapDrawable[] images = new BitmapDrawable[BUTTON_COUNT];
    private final Rect[] rects = new Rect[BUTTON_COUNT];
    private final int[] alphas = new int[BUTTON_COUNT];

    /** Scratch rect reused while drawing a pressed (shrunk) button. */
    private final Rect tmpRect = new Rect();

    /** Per button key state. */
    private final boolean[] pressed = new boolean[BUTTON_COUNT];
    private final long[] pressTime = new long[BUTTON_COUNT];
    private final boolean[] releaseScheduled = new boolean[BUTTON_COUNT];

    private Handler handler = null;

    public void setMAME4droid(MAME4droid value) {
        mm = value;
    }

    public boolean isEnabled() {
        return mm != null && mm.getPrefsHelper().isMahjong();
    }

    public static int getKeyCode(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return 0;
        return KEY_CODE[index];
    }

    public static int getImageRes(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return 0;
        return IMAGE_RES[index];
    }

    private TouchController touchController() {
        if (touchController == null && mm != null && mm.getInputHandler() != null)
            touchController = mm.getInputHandler().getTouchController();
        return touchController;
    }

    private Handler handler() {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        return handler;
    }

    /* ---- key state ------------------------------------------------------- */

    /**
     * Releases every held key at once. Called when the controller is hidden or
     * the app is paused so no key is left stuck high in MAME.
     */
    public void reset() {
        clearPointerOwnership();
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (pressed[i] || releaseScheduled[i]) {
                pressed[i] = false;
                releaseScheduled[i] = false;
                sendKey(i, false);
            }
        }
    }

    /** A finger went down on a button. */
    public void press(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;
        if (pressed[index]) return;

        pressed[index] = true;
        pressTime[index] = SystemClock.uptimeMillis();
        releaseScheduled[index] = false;

        sendKey(index, true);

        TouchController tc = touchController();
        if (tc != null) tc.fireHaptic();
    }

    /** The finger that owned a button lifted. */
    public void release(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;
        if (!pressed[index]) return;

        long elapsed = SystemClock.uptimeMillis() - pressTime[index];
        if (elapsed >= MIN_HOLD_MS) {
            pressed[index] = false;
            sendKey(index, false);
            return;
        }

        // Too short for MAME to see it: keep the key down for the remaining
        // time and release it from the UI thread afterwards.
        final int idx = index;
        releaseScheduled[idx] = true;
        handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                releaseScheduled[idx] = false;
                // A new press may have started in the meantime.
                if (!pressed[idx]) return;
                pressed[idx] = false;
                sendKey(idx, false);
            }
        }, MIN_HOLD_MS - elapsed);
    }

    public boolean isPressed(int index) {
        return index >= 0 && index < BUTTON_COUNT && pressed[index];
    }

    /** Forgets which pointer held which button (used when inputs are reset). */
    public void clearPointerOwnership() {
        TouchController tc = touchController();
        if (tc != null) tc.clearMahjongTouches();
    }

    private void sendKey(int index, boolean down) {
        Emulator.setKeyData(KEY_CODE[index], down ? Emulator.KEY_DOWN : Emulator.KEY_UP, (char) 0);
    }

    /* ---- drawing --------------------------------------------------------- */

    /**
     * Stores the resolved geometry / opacity of a mahjong control. Called from
     * InputView.updateImages() for every TYPE_MAHJONG_IMG element.
     */
    public void updateImage(InputValue v) {
        int index = v.getValue();
        if (index < 0 || index >= BUTTON_COUNT) return;

        Rect r = v.getRect();
        if (rects[index] == null) rects[index] = new Rect();
        rects[index].set(r);

        int alpha = v.getAlpha() >= 0 ? v.getAlpha() : mm.getMainHelper().getControllerAlpha();
        if (v.isHidden()) {
            // Hidden controls stay visible (dimmed) only while the customizer is
            // open, so they can be brought back.
            alpha = ControlCustomizer.isEnabled() ? Math.min(alpha, HIDDEN_ALPHA) : 0;
        }
        alphas[index] = alpha;

        BitmapDrawable img = images[index];
        if (img == null) {
            img = (BitmapDrawable) mm.getResources().getDrawable(getImageRes(index), null);
            images[index] = img;
        }
    }

    /**
     * Draws one button. The artwork is the same in both states; a press simply
     * shrinks it slightly around its centre.
     */
    public void draw(Canvas canvas, int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;

        BitmapDrawable img = images[index];
        Rect r = rects[index];
        if (img == null || r == null) return;

        if (pressed[index]) {
            Rect shrunk = tmpRect;
            shrunk.set(r);
            int dx = (int) (r.width() * 0.06f);
            int dy = (int) (r.height() * 0.06f);
            shrunk.inset(dx, dy);
            img.setBounds(shrunk);
        } else {
            img.setBounds(r);
        }
        img.setAlpha(alphas[index]);
        img.draw(canvas);
    }

    /** Discards the cached drawables when the layout changed. */
    public void invalidateImages() {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            images[i] = null;
            rects[i] = null;
        }
    }

    /* ---- default layout -------------------------------------------------- */

    /** Smallest comfortable cell width (layout units). */
    private static final int MIN_CELL_W = 40;

    /**
     * Appends the mahjong buttons to the freshly parsed controller layout.
     *
     * Preset order: the 17 tiles (A..Q) first, then the five actions (chi, pon,
     * kan, reach, ron), then everything else. Each of those groups starts on a
     * new row and wraps so the cells stay big enough to tap: in landscape the
     * tiles fit on one row (exactly as expected), in portrait they need two. All
     * rows share one cell size, are centred and bottom aligned. The user is free
     * to rearrange everything in the "customize layout" screen.
     */
    public void buildDefaultLayout(ArrayList<InputValue> values) {
        if (values == null) return;

        int mw = 0, mh = 0;
        for (InputValue v : values) {
            if (v.getType() == TouchController.TYPE_MAIN_RECT) {
                Rect r = v.getOrigRect();
                mw = r.width();
                mh = r.height();
                break;
            }
        }
        if (mw <= 0 || mh <= 0) return;

        int maxCols = Math.max(5, Math.min(TILES_COUNT, mw / MIN_CELL_W));
        float cellW = (float) mw / maxCols;
        float cellH = cellW * 128f / 124f;   // keep the artwork aspect ratio

        int[] groupCount = { TILES_COUNT, ACTIONS_COUNT, EXTRAS_COUNT };

        int rows = 0;
        for (int g = 0; g < 3; g++)
            rows += (groupCount[g] + maxCols - 1) / maxCols;

        // Never let the panel eat more than a bit less than half the height.
        float maxTotalH = mh * 0.45f;
        if (rows * cellH > maxTotalH) {
            cellH = maxTotalH / rows;
            cellW = cellH * 124f / 128f;
        }

        float gap = Math.max(1f, cellW * 0.06f);
        float top = mh - (rows * cellH) - (mh * 0.01f);

        int index = 0;
        int row = 0;
        for (int g = 0; g < 3; g++) {
            int count = groupCount[g];
            for (int c = 0; c < count; c += maxCols) {
                int inRow = Math.min(maxCols, count - c);
                float x0 = (mw - (inRow * cellW)) / 2f;
                float y = top + (row * cellH);

                for (int k = 0; k < inRow; k++) {
                    int left = Math.round(x0 + (k * cellW) + gap);
                    int right = Math.round(x0 + ((k + 1) * cellW) - gap);
                    int t = Math.round(y + gap);
                    int b = Math.round(y + cellH - gap);
                    int[] data = { TouchController.TYPE_MAHJONG_IMG, index, left, t,
                        Math.max(1, right - left), Math.max(1, b - t) };
                    values.add(new InputValue(data, mm));
                    index++;
                }
                row++;
            }
        }
    }
}

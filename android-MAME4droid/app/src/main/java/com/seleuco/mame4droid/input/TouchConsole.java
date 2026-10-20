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

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.KeyEvent;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.R;

import java.util.ArrayList;

/**
 * Console controller panel (gamepad).
 *
 * Twin analog sticks, one d-pad and the face / shoulder / start / select
 * buttons, generated from a RetroArch overlay configuration (the
 * {@code console_portrait} / {@code console_landscape} overlays of
 * control-test.cfg). Like the other panels it replaces the normal stick and
 * action buttons while it is the selected controller, and it owns copies of
 * the exit / option buttons (the cfg's own {@code menu_toggle} /
 * {@code overlay_next} keys are not part of this panel).
 *
 * RetroArch desc semantics: x / y is the CENTRE of an element and w / h its
 * half extents (radius), normalized to the cfg's full screen space. The
 * generated tables store exactly that and {@link #buildDefaultLayout} maps
 * them onto the main rect of the current orientation, so the default layout
 * matches the RetroArch original.
 *
 * Controls, and what the "customize layout" screen can do with them:
 * <ul>
 *   <li>every button is one {@link TouchController#TYPE_CONSOLE_IMG} entry
 *       (value = PAD_*): moved, resized, faded and hidden on its own;</li>
 *   <li>the d-pad is ONE single {@link TouchController#TYPE_CONSOLE_DPAD}
 *       entry covering the union of the cfg's 4 arm + 4 diagonal zones: the
 *       8 directions can never be split, a move / resize / hide / opacity
 *       change always applies to the whole pad. The direction comes from the
 *       touch angle inside that rect and the way restriction of the settings
 *       still applies, exactly like on the normal stick;</li>
 *   <li>each analog stick is one {@link TouchController#TYPE_CONSOLE_STICK}
 *       entry (value = 0 left, 1 right) covering the cfg's background
 *       circle: it captures the whole pointer while held, sends true analog
 *       data to MAME (LEFT / RIGHT_STICK_DATA, dead zone from the analog
 *       dead zone setting) and its pad follows the finger. It is deliberately
 *       NOT constrained by the way restriction - the d-pad is the digital
 *       input of this panel.</li>
 * </ul>
 *
 * Button inputs: A -&gt; button 1, B -&gt; 2, Y -&gt; 3, X -&gt; 4, L1 -&gt; 5,
 * R1 -&gt; 6, L2 -&gt; 7, R2 -&gt; 8, start -&gt; start, select -&gt; coin. L3 / R3
 * send no MAME bit: they press the keyboard keys b / n (MAME's default
 * P1 buttons 9 / 10) through {@link TouchKeyboard}. The artwork
 * is the Nintendo diamond (A bottom, B right, Y top, X left). Buttons send
 * their bits through the shared digital state like the normal action
 * buttons, so pressed visuals, haptics, exit / option routing and the
 * cross-talk guards of the system buttons all keep working unchanged.
 */
public class TouchConsole implements IController {

    /* ---- table area (generated from the cfg) ---------------------------- */

    /** Normalized button rects of the cfg portrait overlay ("console_portrait"), canonical order A, B, Y, X, L1, R1, L2, R2, L3, R3, start, select, as { centreX, centreY, halfW, halfH } (RetroArch desc semantics: x / y is the centre, w / h the radius). */
    private static final float[][] PORTRAIT_BUTTONS = {
        { 0.840000f, 0.790000f, 0.060000f, 0.036000f },
        { 0.930000f, 0.740000f, 0.060000f, 0.036000f },
        { 0.840000f, 0.690000f, 0.060000f, 0.036000f },
        { 0.750000f, 0.740000f, 0.060000f, 0.036000f },
        { 0.100000f, 0.455000f, 0.087040f, 0.030210f },
        { 0.900000f, 0.455000f, 0.087040f, 0.030210f },
        { 0.100000f, 0.515000f, 0.087040f, 0.030210f },
        { 0.900000f, 0.515000f, 0.087040f, 0.030210f },
        { 0.100000f, 0.575000f, 0.087040f, 0.030210f },
        { 0.900000f, 0.575000f, 0.087040f, 0.030210f },
        { 0.600000f, 0.550000f, 0.060000f, 0.020000f },
        { 0.400000f, 0.550000f, 0.060000f, 0.020000f },
    };

    /** Same buttons of the landscape overlay ("console_landscape"). */
    private static final float[][] LANDSCAPE_BUTTONS = {
        { 0.899380f, 0.623700f, 0.041670f, 0.074070f },
        { 0.961880f, 0.512590f, 0.041670f, 0.074070f },
        { 0.899380f, 0.401480f, 0.041670f, 0.074070f },
        { 0.836880f, 0.512590f, 0.041670f, 0.074070f },
        { 0.050000f, 0.050000f, 0.048960f, 0.033700f },
        { 0.950000f, 0.050000f, 0.048960f, 0.033700f },
        { 0.050000f, 0.150000f, 0.048960f, 0.033700f },
        { 0.950000f, 0.150000f, 0.048960f, 0.033700f },
        { 0.050000f, 0.250000f, 0.048960f, 0.033700f },
        { 0.950000f, 0.250000f, 0.048960f, 0.033700f },
        { 0.960000f, 0.950000f, 0.030000f, 0.032590f },
        { 0.890000f, 0.950000f, 0.030000f, 0.033590f },
    };

    /** The 8 d-pad zones of the portrait overlay (4 arms, 4 invisible diagonal corners), same { cx, cy, rx, ry } format, in order left, right, up, down, up-left, up-right, down-left, down-right. Only their UNION is used: the d-pad is always one single control. */
    private static final float[][] PORTRAIT_DPAD = {
        { 0.080000f, 0.740000f, 0.079000f, 0.033000f },
        { 0.240000f, 0.740000f, 0.079000f, 0.033000f },
        { 0.160000f, 0.695000f, 0.061000f, 0.044000f },
        { 0.160000f, 0.785000f, 0.061000f, 0.044000f },
        { 0.065000f, 0.685000f, 0.053700f, 0.030210f },
        { 0.260000f, 0.685000f, 0.053700f, 0.030210f },
        { 0.065000f, 0.795000f, 0.053700f, 0.030210f },
        { 0.260000f, 0.795000f, 0.053700f, 0.030210f },
    };

    /** Same zones of the landscape overlay. */
    private static final float[][] LANDSCAPE_DPAD = {
        { 0.050000f, 0.512590f, 0.044790f, 0.068520f },
        { 0.150000f, 0.512590f, 0.044790f, 0.068520f },
        { 0.100000f, 0.418150f, 0.038540f, 0.079630f },
        { 0.100000f, 0.607040f, 0.038540f, 0.079630f },
        { 0.040000f, 0.405000f, 0.030210f, 0.053700f },
        { 0.160000f, 0.405000f, 0.030210f, 0.053700f },
        { 0.040000f, 0.620000f, 0.030210f, 0.053700f },
        { 0.160000f, 0.620000f, 0.030210f, 0.053700f },
    };

    /** The analog sticks of the portrait overlay: background circle { cx, cy, rx, ry } plus the pad artwork size as a fraction of the background (RetroArch pad radius / background radius). */
    private static final float[][] PORTRAIT_STICKS = {
        { 0.320000f, 0.920000f, 0.121850f, 0.070000f, 0.762905f, 0.764857f },
        { 0.680000f, 0.920000f, 0.121850f, 0.070000f, 0.762905f, 0.764857f },
    };

    /** Same for the landscape overlay. */
    private static final float[][] LANDSCAPE_STICKS = {
        { 0.255000f, 0.825560f, 0.075420f, 0.141850f, 0.709891f, 0.725837f },
        { 0.755560f, 0.825560f, 0.075420f, 0.141850f, 0.709891f, 0.725837f },
    };

    /** Artwork of every button, same order as the tables. */
    private static final int[] PAD_IMAGE = { R.drawable.co_a, R.drawable.co_b, R.drawable.co_y, R.drawable.co_x, R.drawable.co_l1, R.drawable.co_r1, R.drawable.co_l2, R.drawable.co_r2, R.drawable.co_l3, R.drawable.co_r3, R.drawable.co_start, R.drawable.co_select };


    /* ---- identities ------------------------------------------------------ */

    /** Number of buttons ({@link TouchController#TYPE_CONSOLE_IMG} entries). */
    public static final int PAD_COUNT = 12;

    /** Number of analog sticks ({@link TouchController#TYPE_CONSOLE_STICK}). */
    public static final int STICK_COUNT = 2;
    /** Value of the left analog stick. */
    public static final int STICK_LEFT = 0;
    /** Value of the right analog stick. */
    public static final int STICK_RIGHT = 1;

    /**
     * Button indices, named after the artwork label (Nintendo diamond: A
     * bottom, B right, Y top, X left). The cfg names those descs after
     * RetroArch's own a / b / x / y, which carry the opposite labels, hence
     * the mapping between cfg name and index looks scrambled - the tables
     * and the artwork arrays follow the labels.
     */
    public static final int PAD_A = 0;
    public static final int PAD_B = 1;
    public static final int PAD_Y = 2;
    public static final int PAD_X = 3;
    public static final int PAD_L1 = 4;
    public static final int PAD_R1 = 5;
    public static final int PAD_L2 = 6;
    public static final int PAD_R2 = 7;
    public static final int PAD_L3 = 8;
    public static final int PAD_R3 = 9;
    public static final int PAD_START = 10;
    public static final int PAD_SELECT = 11;

    /**
     * MAME input bit every button sends. L3 / R3 send no bit at all: they
     * trigger the keyboard keys b / n (MAME's default P1 button 9 / 10),
     * see {@link #getKeyButton(int)}.
     */
    private static final int[] PAD_BITS = {
        BTN1_VALUE, BTN2_VALUE, BTN3_VALUE, BTN4_VALUE,
        BTN5_VALUE, BTN6_VALUE, BTN7_VALUE, BTN8_VALUE,
        0, 0, START_VALUE, COIN_VALUE,
    };

    /**
     * The 9 d-pad artworks, indexed by the shared stick state
     * ({@link IController} STICK_NONE ... STICK_DOWN_RIGHT): the pad draws
     * exactly like the normal d-pad, the artwork swap is the press feedback.
     */
    private static final int[] DPAD_IMAGE = {
        R.drawable.co_dpad_none, R.drawable.co_dpad_up_left,
        R.drawable.co_dpad_up, R.drawable.co_dpad_up_right,
        R.drawable.co_dpad_left, R.drawable.co_dpad_right,
        R.drawable.co_dpad_down_left, R.drawable.co_dpad_down,
        R.drawable.co_dpad_down_right,
    };

    /** Alpha used to draw a hidden control while the customizer is open. */
    public static final int HIDDEN_ALPHA = 55;

    /**
     * The thumbstick pad is drawn at 80% of the cfg's pad/background ratio.
     * The cfg ratio leaves the pad only ~24% of the background radius of
     * travel; the smaller artwork gives it ~40%, so the movement reads much
     * better while the pad's edge still reaches (never crosses) the
     * background circle's rim at full deflection.
     */
    private static final float PAD_SIZE_SCALE = 0.8f;

    /**
     * How far above the system-button slot of the full-screen portrait layout
     * the panel's own start / select are drawn (see PORTRAIT_SYS_ROW_TOP).
     */
    private static final int PORTRAIT_START_SELECT_RAISE = 25;

    /**
     * The row of system buttons of the full-screen portrait console layout:
     * exit / option / coin / start are all drawn at y = 210, well above the
     * L1 / R1 shoulder keys (which start around y = 245), so the taller exit
     * / option artwork never touches the shoulder keys.
     */
    static final int PORTRAIT_SYS_ROW_TOP = 210;

    /* ---- state ----------------------------------------------------------- */

    protected MAME4droid mm = null;

    private TouchController touchController = null;

    /** Artwork / geometry / opacity pushed by InputView.updateImages(). */
    private final BitmapDrawable[] padImages = new BitmapDrawable[PAD_COUNT];
    private final BitmapDrawable[] dpadImages = new BitmapDrawable[DPAD_IMAGE.length];
    private BitmapDrawable bgImage = null;
    private BitmapDrawable stickPadImage = null;

    private final Rect[] padRects = new Rect[PAD_COUNT];
    private final int[] padAlphas = new int[PAD_COUNT];
    private final Rect dpadRect = new Rect();
    private int dpadAlpha = 255;
    private final Rect[] stickRects = { new Rect(), new Rect() };
    private final int[] stickAlphas = { 255, 255 };
    /** Pad artwork size as a fraction of the stick area, per stick. */
    private final float[][] stickPadRatio = {
        { 0f, 0f }, { 0f, 0f }
    };
    /** The 8 cfg touch zones as fractions of the union rect ({cx,cy,rx,ry}). */
    private final float[][] activeZones = {
        new float[4], new float[4], new float[4], new float[4],
        new float[4], new float[4], new float[4], new float[4]
    };
    /** Zone order of the cfg tables to getStickValue sectors (L,R,U,D,UL,UR,DL,DR). */
    private static final int[] DPAD_ZONE_SECTORS = { 4, 5, 2, 7, 1, 3, 6, 8 };

    /** Cached TouchKeyboard key indices of the key buttons, -1 = none yet. */
    private int l3Key = -1;
    private int r3Key = -1;

    /** Pointer ownership of the analog sticks, -1 = free. */
    private final int[] stickOwner = { -1, -1 };
    /** Pad offset of each stick in -1..1 (visual + analog output). */
    private final float[] stickOX = { 0f, 0f };
    private final float[] stickOY = { 0f, 0f };
    /**
     * Axis values at the last redraw of each stick. The redraw threshold is
     * measured against these (not against the previous touch event), so a
     * slow drag still accumulates and the pad never freezes at a partial
     * deflection.
     */
    private final float[] stickDrawX = { 0f, 0f };
    private final float[] stickDrawY = { 0f, 0f };

    /** Scratch rect reused while drawing a pressed (shrunk) control. */
    private final Rect tmpRect = new Rect();

    public TouchConsole() {
        for (int i = 0; i < PAD_COUNT; i++) padAlphas[i] = 255;
    }

    public void setMAME4droid(MAME4droid value) {
        mm = value;
    }

    public boolean isEnabled() {
        return mm != null && mm.getPrefsHelper().isConsolePanel();
    }

    /* ---- input ----------------------------------------------------------- */

    /**
     * MAME input bits a button sends. The B + X macro of the settings is
     * honoured the same way the normal action buttons honour it (their
     * button 3 gets the combined output; here that is the button carrying
     * BTN3_VALUE, the artwork label Y).
     */
    public int getButtonBits(int padIndex) {
        if (padIndex < 0 || padIndex >= PAD_COUNT) return 0;
        int bits = PAD_BITS[padIndex];
        if (bits == BTN3_VALUE && mm != null && mm.getPrefsHelper().isBplusX())
            bits |= BTN2_VALUE | BTN1_VALUE;
        return bits;
    }

    /**
     * TouchKeyboard key index a pad button triggers instead of a digital
     * bit: L3 / R3 send the keyboard keys b / n, MAME's default P1 button
     * 9 / 10. -1 for the plain digital buttons.
     */
    public int getKeyButton(int padIndex) {
        if (padIndex != PAD_L3 && padIndex != PAD_R3) return -1;
        if (l3Key < 0) {
            l3Key = TouchKeyboard.indexOfKeyCode(KeyEvent.KEYCODE_B);
            r3Key = TouchKeyboard.indexOfKeyCode(KeyEvent.KEYCODE_N);
        }
        return padIndex == PAD_L3 ? l3Key : r3Key;
    }

    /**
     * Repaints the pad button that sends the given key so its pressed
     * (shrunk) state follows the keyboard key it triggers. No-op for every
     * key that is not one of this panel's (L3 / R3).
     */
    public void invalidateKeyButton(int kbd) {
        if (kbd < 0 || mm == null || mm.getInputView() == null) return;
        int pad = kbd == getKeyButton(PAD_L3) ? PAD_L3
            : kbd == getKeyButton(PAD_R3) ? PAD_R3 : -1;
        if (pad >= 0 && padRects[pad] != null) mm.getInputView().invalidate(padRects[pad]);
    }

    /**
     * Direction bits for a touch inside the d-pad's rect. The 8 cfg zones
     * are hit-tested as fractions of the control's rect (so a moved /
     * resized pad keeps its touch shape): the zones hug the artwork, a
     * touch in the blank corners of the bounding box - outside every zone -
     * is no direction at all, so the pad no longer lights an arm from
     * touches in the empty space around it. The way restriction of the
     * settings is applied by TouchController.getStickValue(), so a 2 /
     * 4-way machine or the menu never sees diagonals.
     */
    public int getDpadBits(int x, int y, Rect r) {
        if (r == null || r.width() <= 0 || r.height() <= 0) return 0;

        float fx = (x - r.left) / (float) r.width();
        float fy = (y - r.top) / (float) r.height();
        for (int i = 0; i < activeZones.length; i++) {
            float[] z = activeZones[i];
            if (Math.abs(fx - z[0]) <= z[2] && Math.abs(fy - z[1]) <= z[3]) {
                TouchController tc = touchController();
                return tc == null ? 0 : tc.getStickValue(DPAD_ZONE_SECTORS[i]);
            }
        }
        return 0;
    }

    /* ---- analog sticks ---------------------------------------------------- */

    /** True while the given pointer is kept by one of the analog sticks. */
    public boolean handlesPointer(int pid) {
        if (pid == -1) return false;
        return stickOwner[0] == pid || stickOwner[1] == pid;
    }

    /**
     * Attempts to take the stick of this input value for the pointer (first
     * finger wins, and it has to start on the stick area) and updates the
     * analog output. Called from the touch loop for TYPE_CONSOLE_STICK.
     */
    public void updateStick(InputValue iv, int pid, int x, int y) {
        int idx = iv.getValue();
        if (idx < 0 || idx >= STICK_COUNT) return;
        Rect r = iv.getRect();
        if (r == null || r.width() <= 0 || r.height() <= 0) return;

        if (stickOwner[idx] != pid) {
            if (stickOwner[idx] != -1 || !r.contains(x, y)) return;
            stickOwner[idx] = pid;
            TouchController tc = touchController();
            if (tc != null && mm.getPrefsHelper().isVibrate()) tc.fireHaptic();
        }
        applyStick(idx, r, x, y);
    }

    /** Keeps feeding the analog axes of a stick its pointer already owns. */
    public void moveOwnedStick(int pid, int x, int y) {
        for (int i = 0; i < STICK_COUNT; i++) {
            if (stickOwner[i] == pid) applyStick(i, stickRects[i], x, y);
        }
    }

    /** Releases the stick a lifted / cancelled pointer was holding. */
    public void releasePointer(int pid) {
        if (pid == -1) return;
        for (int i = 0; i < STICK_COUNT; i++) {
            if (stickOwner[i] != pid) continue;
            stickOwner[i] = -1;
            stickOX[i] = stickOY[i] = 0f;
            stickDrawX[i] = stickDrawY[i] = 0f;
            Emulator.setAnalogData(i == STICK_LEFT
                ? Emulator.LEFT_STICK_DATA : Emulator.RIGHT_STICK_DATA, 0, 0.0f, 0.0f);
            if (mm.getInputView() != null) mm.getInputView().invalidate(stickRects[i]);
        }
    }

    /** Drops all ownership and zeroes the axes (inputs were reset). */
    public void reset() {
        for (int i = 0; i < STICK_COUNT; i++) {
            if (stickOwner[i] != -1) {
                Emulator.setAnalogData(i == STICK_LEFT
                    ? Emulator.LEFT_STICK_DATA : Emulator.RIGHT_STICK_DATA, 0, 0.0f, 0.0f);
            }
            stickOwner[i] = -1;
            stickOX[i] = stickOY[i] = 0f;
            stickDrawX[i] = stickDrawY[i] = 0f;
        }
    }

    private void applyStick(int idx, Rect r, int x, int y) {
        if (r == null || r.width() <= 0 || r.height() <= 0) return;

        // Touch position relative to the area, clamped to the unit disc.
        float rx = (x - r.centerX()) / (r.width() / 2f);
        float ry = (y - r.centerY()) / (r.height() / 2f);
        float mag = (float) Math.sqrt(rx * rx + ry * ry);
        if (mag > 1f) {
            rx /= mag;
            ry /= mag;
            mag = 1f;
        }

        float deadZone = 0.1f;
        switch (mm.getPrefsHelper().getAnalogDZ()) {
            case 1: deadZone = 0.01f; break;
            case 2: deadZone = 0.1f; break;
            case 3: deadZone = 0.15f; break;
            case 4: deadZone = 0.2f; break;
            case 5: deadZone = 0.3f; break;
        }

        float ox = 0f, oy = 0f;
        if (mag >= deadZone) {
            ox = rx;
            oy = ry;
        }

        // MAME's Y axis points down, like on the normal touch stick.
        Emulator.setAnalogData(idx == STICK_LEFT
            ? Emulator.LEFT_STICK_DATA : Emulator.RIGHT_STICK_DATA, 0, ox, oy * -1.0f);

        float dx = ox - stickDrawX[idx];
        float dy = oy - stickDrawY[idx];
        stickOX[idx] = ox;
        stickOY[idx] = oy;
        // Redraw threshold like the normal analog stick (~8% of travel),
        // measured against the last drawn position so slow drags accumulate.
        if (dx * dx + dy * dy >= 0.0064f
            && mm.getPrefsHelper().isAnimatedInput() && mm.getInputView() != null) {
            stickDrawX[idx] = ox;
            stickDrawY[idx] = oy;
            mm.getInputView().invalidate(stickRects[idx]);
        }
    }

    /* ---- drawing ---------------------------------------------------------- */

    private TouchController touchController() {
        if (touchController == null && mm != null && mm.getInputHandler() != null)
            touchController = mm.getInputHandler().getTouchController();
        return touchController;
    }

    private BitmapDrawable imageFor(int index) {
        if (index < 0 || index >= PAD_COUNT || mm == null) return null;
        BitmapDrawable img = padImages[index];
        if (img == null) {
            img = (BitmapDrawable) mm.getResources().getDrawable(PAD_IMAGE[index], null);
            padImages[index] = img;
        }
        return img;
    }

    private BitmapDrawable dpadImageFor(int state) {
        if (state < 0 || state >= dpadImages.length || mm == null) return null;
        BitmapDrawable img = dpadImages[state];
        if (img == null) {
            img = (BitmapDrawable) mm.getResources().getDrawable(DPAD_IMAGE[state], null);
            dpadImages[state] = img;
        }
        return img;
    }

    private boolean portrait() {
        return mm != null && mm.getMainHelper().getscrOrientation()
            == Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * Stores the resolved geometry / opacity of a console control. Called
     * from InputView.updateImages() for every entry of the three console
     * types; the d-pad also picks up the arm fractions of the current
     * orientation, a stick the pad artwork ratio of its area.
     */
    public void updateImage(InputValue v) {
        if (mm == null) return;
        int type = v.getType();

        int alpha = v.getAlpha() >= 0 ? v.getAlpha() : mm.getMainHelper().getControllerAlpha();
        if (v.isHidden()) {
            // Hidden controls stay visible (dimmed) only while the customizer
            // is open, so they can be brought back.
            alpha = ControlCustomizer.isEnabled() ? Math.min(alpha, HIDDEN_ALPHA) : 0;
        }

        if (type == TouchController.TYPE_CONSOLE_IMG) {
            int i = v.getValue();
            if (i < 0 || i >= PAD_COUNT) return;
            if (padRects[i] == null) padRects[i] = new Rect();
            padRects[i].set(v.getRect());
            padAlphas[i] = alpha;
            imageFor(i);
        } else if (type == TouchController.TYPE_CONSOLE_DPAD) {
            dpadRect.set(v.getRect());
            dpadAlpha = alpha;
            boolean por = portrait();
            // The 8 cfg zones as fractions of the zone union (this entry's
            // rect), so the touch shape follows a moved / resized pad.
            // The zones hug the artwork: the blank corners of the bounding
            // box stay dead instead of lighting an arm by themselves.
            float[][] zones = por ? PORTRAIT_DPAD : LANDSCAPE_DPAD;
            float ux1 = Float.MAX_VALUE, uy1 = Float.MAX_VALUE;
            float ux2 = -Float.MAX_VALUE, uy2 = -Float.MAX_VALUE;
            for (float[] z : zones) {
                ux1 = Math.min(ux1, z[0] - z[2]);
                uy1 = Math.min(uy1, z[1] - z[3]);
                ux2 = Math.max(ux2, z[0] + z[2]);
                uy2 = Math.max(uy2, z[1] + z[3]);
            }
            float uw = Math.max(1e-6f, ux2 - ux1);
            float uh = Math.max(1e-6f, uy2 - uy1);
            for (int i = 0; i < activeZones.length && i < zones.length; i++) {
                activeZones[i][0] = (zones[i][0] - ux1) / uw;
                activeZones[i][1] = (zones[i][1] - uy1) / uh;
                activeZones[i][2] = zones[i][2] / uw;
                activeZones[i][3] = zones[i][3] / uh;
            }
            for (int i = 0; i < dpadImages.length; i++) dpadImageFor(i);
        } else if (type == TouchController.TYPE_CONSOLE_STICK) {
            int i = v.getValue();
            if (i < 0 || i >= STICK_COUNT) return;
            stickRects[i].set(v.getRect());
            stickAlphas[i] = alpha;
            float[][] sticks = portrait() ? PORTRAIT_STICKS : LANDSCAPE_STICKS;
            if (i < sticks.length) {
                stickPadRatio[i][0] = sticks[i][4];
                stickPadRatio[i][1] = sticks[i][5];
            }
            if (bgImage == null)
                bgImage = (BitmapDrawable) mm.getResources().getDrawable(
                    R.drawable.co_thumbstick_background, null);
            if (stickPadImage == null)
                stickPadImage = (BitmapDrawable) mm.getResources().getDrawable(
                    R.drawable.co_thumbstick_pad, null);
        }
    }

    /**
     * Draws one button. A press shrinks it slightly around its centre; the
     * pressed state comes from the shared digital button states, exactly
     * like for the normal action buttons. L3 / R3 send keyboard keys
     * instead of bits, so they read their state from the keyboard.
     */
    public void drawButton(Canvas canvas, int value) {
        if (value < 0 || value >= PAD_COUNT) return;
        Rect r = padRects[value];
        if (r == null) return;
        BitmapDrawable img = imageFor(value);
        if (img == null) return;

        int kbd = getKeyButton(value);
        boolean pressed;
        if (kbd >= 0) {
            pressed = mm.getInputHandler().getTouchKeyboard().isPressed(kbd);
        } else {
            TouchController tc = touchController();
            pressed = tc != null
                && tc.getBtnStates()[value] == BTN_PRESS_STATE;
        }
        if (pressed) {
            tmpRect.set(r);
            tmpRect.inset((int) (r.width() * 0.06f), (int) (r.height() * 0.06f));
            img.setBounds(tmpRect);
        } else {
            img.setBounds(r);
        }
        img.setAlpha(padAlphas[value]);
        img.draw(canvas);
    }

    /**
     * Draws the d-pad exactly like the normal d-pad: one of the nine
     * direction artworks (chosen by the shared stick state) at the
     * control's rect with its opacity - the artwork swap itself is the
     * press feedback, no per-arm shrinking or dimming.
     */
    public void drawDpad(Canvas canvas) {
        if (dpadRect.width() <= 0 || dpadRect.height() <= 0) return;

        int state = touchController() == null
            ? STICK_NONE : touchController().getStick_state();
        BitmapDrawable img = dpadImageFor(state);
        if (img == null) return;

        img.setBounds(dpadRect);
        img.setAlpha(dpadAlpha);
        img.draw(canvas);
    }

    /**
     * Draws one analog stick exactly like the normal analog stick: the
     * background circle at the control's rect and the pad artwork centred
     * under the finger, clamped so it never leaves the background. The axes
     * are already the unit-disc-clamped values the emulator gets, so the
     * pad sits exactly on the (clamped) touch point and is centred while
     * the finger stays inside the dead zone.
     */
    public void drawStick(Canvas canvas, int value) {
        if (value < 0 || value >= STICK_COUNT) return;
        Rect r = stickRects[value];
        if (r == null || r.width() <= 0 || r.height() <= 0) return;
        if (bgImage == null || stickPadImage == null) return;

        int alpha = stickAlphas[value];

        bgImage.setBounds(r);
        bgImage.setAlpha(alpha);
        bgImage.draw(canvas);

        float ratioX = (stickPadRatio[value][0] > 0f ? stickPadRatio[value][0] : 0.75f) * PAD_SIZE_SCALE;
        float ratioY = (stickPadRatio[value][1] > 0f ? stickPadRatio[value][1] : 0.75f) * PAD_SIZE_SCALE;
        int pw = Math.max(1, Math.round(r.width() * ratioX));
        int ph = Math.max(1, Math.round(r.height() * ratioY));
        int cx = r.centerX() + Math.round(stickOX[value] * r.width() / 2f);
        int cy = r.centerY() + Math.round(stickOY[value] * r.height() / 2f);
        int l = Math.max(r.left, Math.min(cx - pw / 2, r.right - pw));
        int t = Math.max(r.top, Math.min(cy - ph / 2, r.bottom - ph));

        stickPadImage.setBounds(l, t, l + pw, t + ph);
        stickPadImage.setAlpha(alpha);
        stickPadImage.draw(canvas);
    }

    /** Discards the cached drawables when the layout changed. */
    public void invalidateImages() {
        for (int i = 0; i < PAD_COUNT; i++) {
            padImages[i] = null;
            padRects[i] = null;
        }
        for (int i = 0; i < dpadImages.length; i++) dpadImages[i] = null;
        bgImage = null;
        stickPadImage = null;
    }

    /* ---- default layout ---------------------------------------------------- */

    /**
     * Appends the console panel to the freshly parsed controller layout,
     * using the RetroArch cfg's own geometry: sticks first (so a finger in
     * the shared d-pad / stick corner of the landscape overlay grabs the
     * stick), then the single d-pad entry (union of the cfg's 8 zones), then
     * the buttons. Portrait screens use the portrait table, everything else
     * the landscape one. The user can move / resize / hide every control in
     * the "customize layout" screen afterwards; those edits are stored per
     * orientation like the ones of any other panel.
     */
    public void buildDefaultLayout(ArrayList<InputValue> values) {
        if (values == null || mm == null) return;

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

        boolean portrait = portrait();
        float[][] sticks = portrait ? PORTRAIT_STICKS : LANDSCAPE_STICKS;
        float[][] dpad = portrait ? PORTRAIT_DPAD : LANDSCAPE_DPAD;
        float[][] buttons = portrait ? PORTRAIT_BUTTONS : LANDSCAPE_BUTTONS;

        // Analog sticks: the background circle is the touch area.
        for (int i = 0; i < STICK_COUNT && i < sticks.length; i++) {
            float[] k = sticks[i];
            int[] data = { TouchController.TYPE_CONSOLE_STICK, i,
                Math.round((k[0] - k[2]) * mw), Math.round((k[1] - k[3]) * mh),
                Math.max(1, Math.round(2f * k[2] * mw)),
                Math.max(1, Math.round(2f * k[3] * mh)) };
            values.add(new InputValue(data, mm));
        }

        // The d-pad: ONE entry covering the union of the cfg's zones. The
        // union centre equals the cross centre by construction, so moving /
        // resizing this rect always moves the whole pad, directions included.
        float ux1 = Float.MAX_VALUE, uy1 = Float.MAX_VALUE;
        float ux2 = -Float.MAX_VALUE, uy2 = -Float.MAX_VALUE;
        for (float[] z : dpad) {
            ux1 = Math.min(ux1, z[0] - z[2]);
            uy1 = Math.min(uy1, z[1] - z[3]);
            ux2 = Math.max(ux2, z[0] + z[2]);
            uy2 = Math.max(uy2, z[1] + z[3]);
        }
        int[] data = { TouchController.TYPE_CONSOLE_DPAD, 0,
            Math.round(ux1 * mw), Math.round(uy1 * mh),
            Math.max(1, Math.round((ux2 - ux1) * mw)),
            Math.max(1, Math.round((uy2 - uy1) * mh)) };
        values.add(new InputValue(data, mm));

        // Buttons.
        for (int i = 0; i < PAD_COUNT && i < buttons.length; i++) {
            float[] k = buttons[i];
            int top = Math.round((k[1] - k[3]) * mh);
            if (portrait && (i == PAD_START || i == PAD_SELECT)) {
                top -= PORTRAIT_START_SELECT_RAISE;
            }
            int[] bd = { TouchController.TYPE_CONSOLE_IMG, i,
                Math.round((k[0] - k[2]) * mw), top,
                Math.max(1, Math.round(2f * k[2] * mw)),
                Math.max(1, Math.round(2f * k[3] * mh)) };
            values.add(new InputValue(bd, mm));
        }
    }
}

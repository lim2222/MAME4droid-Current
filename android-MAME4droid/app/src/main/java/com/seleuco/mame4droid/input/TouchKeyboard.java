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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.R;

import java.util.ArrayList;

/**
 * On screen PC keyboard panel (generic).
 *
 * A full keyboard overlay that replaces the stick / action buttons while it is
 * the selected controller (same rule as the mahjong and hanafuda panels) and
 * owns its own copies of the system buttons. Every key is an independent
 * control (its own {@link InputValue} of type
 * {@link TouchController#TYPE_KEYBOARD_IMG}, value = index into this table) so
 * it can be moved, resized, faded and hidden individually in the "customize
 * layout" screen - the intended workflow there is HIDE ALL, then un-hide the
 * few keys a given game actually needs.
 *
 * The default layout is taken verbatim from the RetroArch overlay configuration
 * this panel was built from ({@code keyboard-portrait-sd/sf} and
 * {@code keyboard-landscape-sd/sf}); the cfg's normalized rects are mapped onto
 * the main rect of the current orientation, so no key has to be re-arranged.
 *
 * The two artwork sets of that configuration ("sd" / "sf") only differ in the
 * shifted look of a key, exactly like a real keyboard: the shifted set is shown
 * while Shift is held or while CapsLock is locked on. CapsLock is the key the
 * cfg binds to {@code overlay_next}; here it toggles that artwork state (and
 * still sends its own key code, so the game sees CapsLock too).
 *
 * Keys send plain Android key codes through {@link Emulator#setKeyData}, which
 * MAME4droid's OSD maps to MAME keys (src/osd/myosd/droid/myosd_droid.cpp,
 * droid_map_keyboard()). As on the other panels a short minimum hold is
 * enforced, because the native keyboard state is polled once per frame and a
 * down + up inside one frame would be lost.
 */
public class TouchKeyboard implements IController {

    /* ---- key table -------------------------------------------------------- */

    /** Number of keys of the overlay. */
    public static final int BUTTON_COUNT = 101;

    /** CapsLock key of the cfg ({@code overlay_next}): toggles the shifted art. */
    public static final int CAPS_INDEX = 44;

    /** The two Shift keys, whose held state also flips the artwork. */
    private static final int SHIFT_LEFT_INDEX = 57;
    private static final int SHIFT_RIGHT_INDEX = 68;

    /** Normalized (0..1) key rects of the RetroArch "keyboard-portrait-sd" overlay, in cfg order: {x, y, w, h}. x / w are relative to the width, y / h to the height of the main rect. */
    private static final float[][] PORTRAIT_KEYS = {
        {0.020833f, 0.774722f, 0.020833f, 0.015625f}, // retrok_escape
        {0.108796f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f1
        {0.152778f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f2
        {0.196759f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f3
        {0.240741f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f4
        {0.307870f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f5
        {0.351852f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f6
        {0.395833f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f7
        {0.439815f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f8
        {0.506944f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f9
        {0.550926f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f10
        {0.594907f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f11
        {0.638889f, 0.774722f, 0.020833f, 0.015625f}, // retrok_f12
        {0.699074f, 0.774722f, 0.020833f, 0.015625f}, // retrok_print_screen
        {0.743056f, 0.774722f, 0.020833f, 0.015625f}, // retrok_scroll_lock
        {0.787037f, 0.774722f, 0.020833f, 0.015625f}, // retrok_pause
        {0.020833f, 0.832431f, 0.020833f, 0.015625f}, // retrok_tilde
        {0.064815f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num1
        {0.108796f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num2
        {0.152778f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num3
        {0.196759f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num4
        {0.240741f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num5
        {0.284722f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num6
        {0.328704f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num7
        {0.372685f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num8
        {0.416667f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num9
        {0.460648f, 0.832431f, 0.020833f, 0.015625f}, // retrok_num0
        {0.504630f, 0.832431f, 0.020833f, 0.015625f}, // retrok_minus
        {0.548611f, 0.832431f, 0.020833f, 0.015625f}, // retrok_equals
        {0.615741f, 0.832431f, 0.043981f, 0.015625f}, // retrok_backspace
        {0.020833f, 0.865417f, 0.032407f, 0.015625f}, // retrok_tab
        {0.087963f, 0.865417f, 0.020833f, 0.015625f}, // retrok_q
        {0.131944f, 0.865417f, 0.020833f, 0.015625f}, // retrok_w
        {0.175926f, 0.865417f, 0.020833f, 0.015625f}, // retrok_e
        {0.219907f, 0.865417f, 0.020833f, 0.015625f}, // retrok_r
        {0.263889f, 0.865417f, 0.020833f, 0.015625f}, // retrok_t
        {0.307870f, 0.865417f, 0.020833f, 0.015625f}, // retrok_y
        {0.351852f, 0.865417f, 0.020833f, 0.015625f}, // retrok_u
        {0.395833f, 0.865417f, 0.020833f, 0.015625f}, // retrok_i
        {0.439815f, 0.865417f, 0.020833f, 0.015625f}, // retrok_o
        {0.483796f, 0.865417f, 0.020833f, 0.015625f}, // retrok_p
        {0.527778f, 0.865417f, 0.020833f, 0.015625f}, // retrok_leftbracket
        {0.571759f, 0.865417f, 0.020833f, 0.015625f}, // retrok_rightbracket
        {0.627315f, 0.865417f, 0.032407f, 0.015625f}, // retrok_backslash
        {0.020833f, 0.898403f, 0.038194f, 0.015625f}, // overlay_next
        {0.099537f, 0.898403f, 0.020833f, 0.015625f}, // retrok_a
        {0.143519f, 0.898403f, 0.020833f, 0.015625f}, // retrok_s
        {0.187500f, 0.898403f, 0.020833f, 0.015625f}, // retrok_d
        {0.231481f, 0.898403f, 0.020833f, 0.015625f}, // retrok_f
        {0.275463f, 0.898403f, 0.020833f, 0.015625f}, // retrok_g
        {0.319444f, 0.898403f, 0.020833f, 0.015625f}, // retrok_h
        {0.363426f, 0.898403f, 0.020833f, 0.015625f}, // retrok_j
        {0.407407f, 0.898403f, 0.020833f, 0.015625f}, // retrok_k
        {0.451389f, 0.898403f, 0.020833f, 0.015625f}, // retrok_l
        {0.495370f, 0.898403f, 0.020833f, 0.015625f}, // retrok_semicolon
        {0.539352f, 0.898403f, 0.020833f, 0.015625f}, // retrok_quote
        {0.611111f, 0.898403f, 0.048611f, 0.015625f}, // retrok_enter
        {0.020833f, 0.931389f, 0.048611f, 0.015625f}, // retrok_shift
        {0.120370f, 0.931389f, 0.020833f, 0.015625f}, // retrok_z
        {0.164352f, 0.931389f, 0.020833f, 0.015625f}, // retrok_x
        {0.208333f, 0.931389f, 0.020833f, 0.015625f}, // retrok_c
        {0.252315f, 0.931389f, 0.020833f, 0.015625f}, // retrok_v
        {0.296296f, 0.931389f, 0.020833f, 0.015625f}, // retrok_b
        {0.340278f, 0.931389f, 0.020833f, 0.015625f}, // retrok_n
        {0.384259f, 0.931389f, 0.020833f, 0.015625f}, // retrok_m
        {0.428241f, 0.931389f, 0.020833f, 0.015625f}, // retrok_comma
        {0.472222f, 0.931389f, 0.020833f, 0.015625f}, // retrok_period
        {0.516204f, 0.931389f, 0.020833f, 0.015625f}, // retrok_slash
        {0.599537f, 0.931389f, 0.060185f, 0.015625f}, // retrok_rshift
        {0.020833f, 0.964375f, 0.031250f, 0.015625f}, // retrok_ctrl
        {0.142361f, 0.964375f, 0.031250f, 0.015625f}, // retrok_alt
        {0.268519f, 0.964375f, 0.153935f, 0.015625f}, // retrok_space
        {0.517361f, 0.964375f, 0.031250f, 0.015625f}, // retrok_ralt
        {0.628472f, 0.964375f, 0.031250f, 0.015625f}, // retrok_rctrl
        {0.699074f, 0.832431f, 0.020833f, 0.015625f}, // retrok_insert
        {0.743056f, 0.832431f, 0.020833f, 0.015625f}, // retrok_home
        {0.787037f, 0.832431f, 0.020833f, 0.015625f}, // retrok_pageup
        {0.699074f, 0.865417f, 0.020833f, 0.015625f}, // retrok_del
        {0.743056f, 0.865417f, 0.020833f, 0.015625f}, // retrok_end
        {0.787037f, 0.865417f, 0.020833f, 0.015625f}, // retrok_pagedown
        {0.743056f, 0.931389f, 0.020833f, 0.015625f}, // retrok_up
        {0.699074f, 0.964375f, 0.020833f, 0.015625f}, // retrok_left
        {0.743056f, 0.964375f, 0.020833f, 0.015625f}, // retrok_down
        {0.787037f, 0.964375f, 0.020833f, 0.015625f}, // retrok_right
        {0.847222f, 0.832431f, 0.020833f, 0.015625f}, // retrok_numlock
        {0.891204f, 0.832431f, 0.020833f, 0.015625f}, // retrok_divide
        {0.935185f, 0.832431f, 0.020833f, 0.015625f}, // retrok_multiply
        {0.979167f, 0.832431f, 0.020833f, 0.015625f}, // retrok_kp_minus
        {0.847222f, 0.865417f, 0.020833f, 0.015625f}, // retrok_keypad7
        {0.891204f, 0.865417f, 0.020833f, 0.015625f}, // retrok_keypad8
        {0.935185f, 0.865417f, 0.020833f, 0.015625f}, // retrok_keypad9
        {0.979167f, 0.865417f, 0.020833f, 0.048611f}, // retrok_kp_plus
        {0.847222f, 0.898403f, 0.020833f, 0.015625f}, // retrok_keypad4
        {0.891204f, 0.898403f, 0.020833f, 0.015625f}, // retrok_keypad5
        {0.935185f, 0.898403f, 0.020833f, 0.015625f}, // retrok_keypad6
        {0.847222f, 0.931389f, 0.020833f, 0.015625f}, // retrok_keypad1
        {0.891204f, 0.931389f, 0.020833f, 0.015625f}, // retrok_keypad2
        {0.935185f, 0.931389f, 0.020833f, 0.015625f}, // retrok_keypad3
        {0.979167f, 0.931389f, 0.020833f, 0.038875f}, // retrok_kp_enter
        {0.862611f, 0.964375f, 0.050815f, 0.015625f}, // retrok_keypad0
        {0.935185f, 0.964375f, 0.020833f, 0.015625f}, // retrok_kp_equals
    };

    /** Same rects from "keyboard-landscape-sd" (the portrait and landscape overlays only differ in key size / row spacing, never in key order). */
    private static final float[][] LANDSCAPE_KEYS = {
        {0.020833f, 0.586722f, 0.020833f, 0.030883f}, // retrok_escape
        {0.108796f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f1
        {0.152778f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f2
        {0.196759f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f3
        {0.240741f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f4
        {0.307870f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f5
        {0.351852f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f6
        {0.395833f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f7
        {0.439815f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f8
        {0.506944f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f9
        {0.550926f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f10
        {0.594907f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f11
        {0.638889f, 0.586722f, 0.020833f, 0.030883f}, // retrok_f12
        {0.699074f, 0.586722f, 0.020833f, 0.030883f}, // retrok_print_screen
        {0.743056f, 0.586722f, 0.020833f, 0.030883f}, // retrok_scroll_lock
        {0.787037f, 0.586722f, 0.020833f, 0.030883f}, // retrok_pause
        {0.020833f, 0.687431f, 0.020833f, 0.030883f}, // retrok_tilde
        {0.064815f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num1
        {0.108796f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num2
        {0.152778f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num3
        {0.196759f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num4
        {0.240741f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num5
        {0.284722f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num6
        {0.328704f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num7
        {0.372685f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num8
        {0.416667f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num9
        {0.460648f, 0.687431f, 0.020833f, 0.030883f}, // retrok_num0
        {0.504630f, 0.687431f, 0.020833f, 0.030883f}, // retrok_minus
        {0.548611f, 0.687431f, 0.020833f, 0.030883f}, // retrok_equals
        {0.615741f, 0.687431f, 0.043981f, 0.030883f}, // retrok_backspace
        {0.020833f, 0.753417f, 0.032407f, 0.030883f}, // retrok_tab
        {0.087963f, 0.753417f, 0.020833f, 0.030883f}, // retrok_q
        {0.131944f, 0.753417f, 0.020833f, 0.030883f}, // retrok_w
        {0.175926f, 0.753417f, 0.020833f, 0.030883f}, // retrok_e
        {0.219907f, 0.753417f, 0.020833f, 0.030883f}, // retrok_r
        {0.263889f, 0.753417f, 0.020833f, 0.030883f}, // retrok_t
        {0.307870f, 0.753417f, 0.020833f, 0.030883f}, // retrok_y
        {0.351852f, 0.753417f, 0.020833f, 0.030883f}, // retrok_u
        {0.395833f, 0.753417f, 0.020833f, 0.030883f}, // retrok_i
        {0.439815f, 0.753417f, 0.020833f, 0.030883f}, // retrok_o
        {0.483796f, 0.753417f, 0.020833f, 0.030883f}, // retrok_p
        {0.527778f, 0.753417f, 0.020833f, 0.030883f}, // retrok_leftbracket
        {0.571759f, 0.753417f, 0.020833f, 0.030883f}, // retrok_rightbracket
        {0.627315f, 0.753417f, 0.032407f, 0.030883f}, // retrok_backslash
        {0.020833f, 0.819403f, 0.038194f, 0.030883f}, // overlay_next
        {0.099537f, 0.819403f, 0.020833f, 0.030883f}, // retrok_a
        {0.143519f, 0.819403f, 0.020833f, 0.030883f}, // retrok_s
        {0.187500f, 0.819403f, 0.020833f, 0.030883f}, // retrok_d
        {0.231481f, 0.819403f, 0.020833f, 0.030883f}, // retrok_f
        {0.275463f, 0.819403f, 0.020833f, 0.030883f}, // retrok_g
        {0.319444f, 0.819403f, 0.020833f, 0.030883f}, // retrok_h
        {0.363426f, 0.819403f, 0.020833f, 0.030883f}, // retrok_j
        {0.407407f, 0.819403f, 0.020833f, 0.030883f}, // retrok_k
        {0.451389f, 0.819403f, 0.020833f, 0.030883f}, // retrok_l
        {0.495370f, 0.819403f, 0.020833f, 0.030883f}, // retrok_semicolon
        {0.539352f, 0.819403f, 0.020833f, 0.030883f}, // retrok_quote
        {0.611111f, 0.819403f, 0.048611f, 0.030883f}, // retrok_enter
        {0.020833f, 0.885389f, 0.048611f, 0.030883f}, // retrok_shift
        {0.120370f, 0.885389f, 0.020833f, 0.030883f}, // retrok_z
        {0.164352f, 0.885389f, 0.020833f, 0.030883f}, // retrok_x
        {0.208333f, 0.885389f, 0.020833f, 0.030883f}, // retrok_c
        {0.252315f, 0.885389f, 0.020833f, 0.030883f}, // retrok_v
        {0.296296f, 0.885389f, 0.020833f, 0.030883f}, // retrok_b
        {0.340278f, 0.885389f, 0.020833f, 0.030883f}, // retrok_n
        {0.384259f, 0.885389f, 0.020833f, 0.030883f}, // retrok_m
        {0.428241f, 0.885389f, 0.020833f, 0.030883f}, // retrok_comma
        {0.472222f, 0.885389f, 0.020833f, 0.030883f}, // retrok_period
        {0.516204f, 0.885389f, 0.020833f, 0.030883f}, // retrok_slash
        {0.599537f, 0.885389f, 0.060185f, 0.030883f}, // retrok_rshift
        {0.020833f, 0.951375f, 0.031250f, 0.030883f}, // retrok_ctrl
        {0.142361f, 0.951375f, 0.031250f, 0.030883f}, // retrok_alt
        {0.268519f, 0.951375f, 0.153935f, 0.030883f}, // retrok_space
        {0.517361f, 0.951375f, 0.031250f, 0.030883f}, // retrok_ralt
        {0.628472f, 0.951375f, 0.031250f, 0.030883f}, // retrok_rctrl
        {0.699074f, 0.687431f, 0.020833f, 0.030883f}, // retrok_insert
        {0.743056f, 0.687431f, 0.020833f, 0.030883f}, // retrok_home
        {0.787037f, 0.687431f, 0.020833f, 0.030883f}, // retrok_pageup
        {0.699074f, 0.753417f, 0.020833f, 0.030883f}, // retrok_del
        {0.743056f, 0.753417f, 0.020833f, 0.030883f}, // retrok_end
        {0.787037f, 0.753417f, 0.020833f, 0.030883f}, // retrok_pagedown
        {0.743056f, 0.885389f, 0.020833f, 0.030883f}, // retrok_up
        {0.699074f, 0.951375f, 0.020833f, 0.030883f}, // retrok_left
        {0.743056f, 0.951375f, 0.020833f, 0.030883f}, // retrok_down
        {0.787037f, 0.951375f, 0.020833f, 0.030883f}, // retrok_right
        {0.847222f, 0.687431f, 0.020833f, 0.030883f}, // retrok_numlock
        {0.891204f, 0.687431f, 0.020833f, 0.030883f}, // retrok_divide
        {0.935185f, 0.687431f, 0.020833f, 0.030883f}, // retrok_multiply
        {0.979167f, 0.687431f, 0.020833f, 0.030883f}, // retrok_kp_minus
        {0.847222f, 0.753417f, 0.020833f, 0.030883f}, // retrok_keypad7
        {0.891204f, 0.753417f, 0.020833f, 0.030883f}, // retrok_keypad8
        {0.935185f, 0.753417f, 0.020833f, 0.030883f}, // retrok_keypad9
        {0.979167f, 0.753417f, 0.020833f, 0.064236f}, // retrok_kp_plus
        {0.847222f, 0.819403f, 0.020833f, 0.030883f}, // retrok_keypad4
        {0.891204f, 0.819403f, 0.020833f, 0.030883f}, // retrok_keypad5
        {0.935185f, 0.819403f, 0.020833f, 0.030883f}, // retrok_keypad6
        {0.847222f, 0.885389f, 0.020833f, 0.030883f}, // retrok_keypad1
        {0.891204f, 0.885389f, 0.020833f, 0.030883f}, // retrok_keypad2
        {0.935185f, 0.885389f, 0.020833f, 0.030883f}, // retrok_keypad3
        {0.979167f, 0.885389f, 0.020833f, 0.096869f}, // retrok_kp_enter
        {0.862611f, 0.951375f, 0.050815f, 0.030883f}, // retrok_keypad0
        {0.935185f, 0.951375f, 0.020833f, 0.030883f}, // retrok_kp_equals
    };

    /** Android key code of every key, cfg order. */
    private static final int[] KEY_CODE = {
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
        KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7,
        KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11,
        KeyEvent.KEYCODE_F12, KeyEvent.KEYCODE_SYSRQ, KeyEvent.KEYCODE_SCROLL_LOCK, KeyEvent.KEYCODE_BREAK,
        KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_MINUS,
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_Q,
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
        KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O,
        KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET,
        KeyEvent.KEYCODE_BACKSLASH,
        KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J,
        KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X,
        KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N,
        KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH,
        KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_INSERT,
        KeyEvent.KEYCODE_MOVE_HOME,
        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
        KeyEvent.KEYCODE_NUMPAD_7, KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_NUMPAD_9,
        KeyEvent.KEYCODE_NUMPAD_ADD,
        KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_6,
        KeyEvent.KEYCODE_NUMPAD_1,
        KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_NUMPAD_0,
        KeyEvent.KEYCODE_NUMPAD_EQUALS,
    };

    /** Artwork of every key (the "sd", un-shifted overlay). */
    private static final int[] IMAGE_RES = {
        R.drawable.pc_esc, R.drawable.pc_f1, R.drawable.pc_f2, R.drawable.pc_f3,
        R.drawable.pc_f4, R.drawable.pc_f5, R.drawable.pc_f6, R.drawable.pc_f7,
        R.drawable.pc_f8, R.drawable.pc_f9, R.drawable.pc_f10, R.drawable.pc_f11,
        R.drawable.pc_f12, R.drawable.pc_prt_sc, R.drawable.pc_scr_lk, R.drawable.pc_pause,
        R.drawable.pc_tilde, R.drawable.pc_1, R.drawable.pc_2, R.drawable.pc_3,
        R.drawable.pc_4, R.drawable.pc_5, R.drawable.pc_6, R.drawable.pc_7,
        R.drawable.pc_8, R.drawable.pc_9, R.drawable.pc_0, R.drawable.pc_minus,
        R.drawable.pc_equals, R.drawable.pc_backspace, R.drawable.pc_tab, R.drawable.pc_q,
        R.drawable.pc_w, R.drawable.pc_e, R.drawable.pc_r, R.drawable.pc_t,
        R.drawable.pc_y, R.drawable.pc_u, R.drawable.pc_i, R.drawable.pc_o,
        R.drawable.pc_p, R.drawable.pc_open_bracket, R.drawable.pc_close_bracket, R.drawable.pc_backslash,
        R.drawable.pc_capslock, R.drawable.pc_a, R.drawable.pc_s, R.drawable.pc_d,
        R.drawable.pc_f, R.drawable.pc_g, R.drawable.pc_h, R.drawable.pc_j,
        R.drawable.pc_k, R.drawable.pc_l, R.drawable.pc_semicolon, R.drawable.pc_apostrophe,
        R.drawable.pc_enter, R.drawable.pc_l_shift, R.drawable.pc_z, R.drawable.pc_x,
        R.drawable.pc_c, R.drawable.pc_v, R.drawable.pc_b, R.drawable.pc_n,
        R.drawable.pc_m, R.drawable.pc_comma, R.drawable.pc_period, R.drawable.pc_fwd_slash,
        R.drawable.pc_r_shift, R.drawable.pc_l_ctrl, R.drawable.pc_l_alt, R.drawable.pc_spacebar,
        R.drawable.pc_r_alt, R.drawable.pc_r_ctrl, R.drawable.pc_insert, R.drawable.pc_home,
        R.drawable.pc_pg_up, R.drawable.pc_delete, R.drawable.pc_end, R.drawable.pc_pg_down,
        R.drawable.pc_up_arrow, R.drawable.pc_left_arrow, R.drawable.pc_down_arrow,
        R.drawable.pc_right_arrow,
        R.drawable.pc_num_lock, R.drawable.pc_kp_fwd_slash, R.drawable.pc_kp_asterisk,
        R.drawable.pc_kp_minus,
        R.drawable.pc_kp_7, R.drawable.pc_kp_8, R.drawable.pc_kp_9, R.drawable.pc_kp_plus,
        R.drawable.pc_kp_4, R.drawable.pc_kp_5, R.drawable.pc_kp_6, R.drawable.pc_kp_1,
        R.drawable.pc_kp_2, R.drawable.pc_kp_3, R.drawable.pc_kp_enter, R.drawable.pc_kp_0,
        R.drawable.pc_kp_period,
    };

    /** Artwork shown while Shift is held or CapsLock is on (the "sf" overlay); 0 when the key has no shifted variant. */
    private static final int[] SHIFT_IMAGE_RES = {
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        R.drawable.pc_shift_tilde, R.drawable.pc_shift_1, R.drawable.pc_shift_2, R.drawable.pc_shift_3,
        R.drawable.pc_shift_4, R.drawable.pc_shift_5, R.drawable.pc_shift_6, R.drawable.pc_shift_7,
        R.drawable.pc_shift_8, R.drawable.pc_shift_9, R.drawable.pc_shift_0, R.drawable.pc_shift_minus,
        R.drawable.pc_shift_equals, 0, 0, R.drawable.pc_shift_q,
        R.drawable.pc_shift_w, R.drawable.pc_shift_e, R.drawable.pc_shift_r, R.drawable.pc_shift_t,
        R.drawable.pc_shift_y, R.drawable.pc_shift_u, R.drawable.pc_shift_i, R.drawable.pc_shift_o,
        R.drawable.pc_shift_p, R.drawable.pc_shift_open_bracket, R.drawable.pc_shift_close_bracket,
        R.drawable.pc_shift_backslash,
        R.drawable.pc_shift_capslock, R.drawable.pc_shift_a, R.drawable.pc_shift_s, R.drawable.pc_shift_d,
        R.drawable.pc_shift_f, R.drawable.pc_shift_g, R.drawable.pc_shift_h, R.drawable.pc_shift_j,
        R.drawable.pc_shift_k, R.drawable.pc_shift_l, R.drawable.pc_shift_semicolon,
        R.drawable.pc_shift_apostrophe,
        0, R.drawable.pc_shift_l_shift, R.drawable.pc_shift_z, R.drawable.pc_shift_x,
        R.drawable.pc_shift_c, R.drawable.pc_shift_v, R.drawable.pc_shift_b, R.drawable.pc_shift_n,
        R.drawable.pc_shift_m, R.drawable.pc_shift_comma, R.drawable.pc_shift_period,
        R.drawable.pc_shift_fwd_slash,
        R.drawable.pc_shift_r_shift, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0,
    };

    /**
     * Minimum time a key stays down. MAME's keyboard state is polled once per
     * frame, so a tap shorter than a frame would never be seen.
     */
    private static final int MIN_HOLD_MS = 45;

    /** Alpha used to draw a hidden key while the customizer is open. */
    public static final int HIDDEN_ALPHA = 55;

    /* ---- state ----------------------------------------------------------- */

    protected MAME4droid mm = null;

    private TouchController touchController = null;

    /** Visual properties pushed by InputView.updateImages(). */
    private final BitmapDrawable[] images = new BitmapDrawable[BUTTON_COUNT];
    private final BitmapDrawable[] shiftImages = new BitmapDrawable[BUTTON_COUNT];
    private final Rect[] rects = new Rect[BUTTON_COUNT];
    private final int[] alphas = new int[BUTTON_COUNT];

    /** Scratch rect reused while drawing a pressed (shrunk) key. */
    private final Rect tmpRect = new Rect();

    /** Per key key state. */
    private final boolean[] pressed = new boolean[BUTTON_COUNT];
    private final long[] pressTime = new long[BUTTON_COUNT];
    private final boolean[] releaseScheduled = new boolean[BUTTON_COUNT];

    private Handler handler = null;

    /** CapsLock state (sticky) and how many Shift keys are held right now. */
    private boolean capsOn = false;
    private int shiftHeld = 0;

    public void setMAME4droid(MAME4droid value) {
        mm = value;
    }

    public boolean isEnabled() {
        return mm != null && mm.getPrefsHelper().isKeyboardPanel();
    }

    public static int getKeyCode(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return 0;
        return KEY_CODE[index];
    }

    /** Index of the key carrying the given Android keycode, -1 if none. */
    public static int indexOfKeyCode(int keyCode) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (KEY_CODE[i] == keyCode) return i;
        }
        return -1;
    }

    public static int getImageRes(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return 0;
        return IMAGE_RES[index];
    }

    /** True while Shift is held xor CapsLock is on: the shifted artwork set. */
    private boolean shifted() {
        return capsOn ^ (shiftHeld > 0);
    }

    private BitmapDrawable imageFor(int index) {
        if (shifted() && SHIFT_IMAGE_RES[index] != 0) {
            BitmapDrawable img = shiftImages[index];
            if (img == null) {
                img = (BitmapDrawable) mm.getResources().getDrawable(SHIFT_IMAGE_RES[index], null);
                shiftImages[index] = img;
            }
            return img;
        }
        BitmapDrawable img = images[index];
        if (img == null) {
            img = (BitmapDrawable) mm.getResources().getDrawable(IMAGE_RES[index], null);
            images[index] = img;
        }
        return img;
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
     * the app is paused so no key is left stuck high in MAME. Shift is treated
     * as a momentary key for the artwork too, so nothing stays shifted either.
     */
    public void reset() {
        clearPointerOwnership();
        boolean dirty = false;
        boolean art = shiftHeld != 0;
        shiftHeld = 0;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (pressed[i] || releaseScheduled[i]) {
                pressed[i] = false;
                releaseScheduled[i] = false;
                sendKey(i, false);
                dirty = true;
            }
        }
        if (dirty && mm != null && mm.getInputView() != null) {
            mm.getInputView().postInvalidate();
        } else if (art && mm != null && mm.getInputView() != null) {
            mm.getInputView().postInvalidate();
        }
    }

    /** A finger went down on a key. */
    public void press(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;
        if (pressed[index]) return;

        pressed[index] = true;
        pressTime[index] = SystemClock.uptimeMillis();
        releaseScheduled[index] = false;

        // CapsLock / Shift only change which artwork every key draws.
        boolean art = false;
        if (index == CAPS_INDEX) {
            capsOn = !capsOn;
            art = true;
        } else if (index == SHIFT_LEFT_INDEX || index == SHIFT_RIGHT_INDEX) {
            shiftHeld++;
            art = true;
        }

        sendKey(index, true);
        if (art) repaintAll(); else repaint(index);
        invalidateConsoleKeyCopy(index);

        TouchController tc = touchController();
        if (tc != null) tc.fireHaptic();
    }

    /** The finger that owned a key lifted. */
    public void release(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;
        if (!pressed[index]) return;

        final boolean art = index == SHIFT_LEFT_INDEX || index == SHIFT_RIGHT_INDEX;
        if (art) shiftHeld = Math.max(0, shiftHeld - 1);

        long elapsed = SystemClock.uptimeMillis() - pressTime[index];
        if (elapsed >= MIN_HOLD_MS) {
            pressed[index] = false;
            sendKey(index, false);
            if (art) repaintAll(); else repaint(index);
            invalidateConsoleKeyCopy(index);
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
                if (art) repaintAll(); else repaint(idx);
                invalidateConsoleKeyCopy(idx);
            }
        }, MIN_HOLD_MS - elapsed);
    }

    public boolean isPressed(int index) {
        return index >= 0 && index < BUTTON_COUNT && pressed[index];
    }

    /** Forgets which pointer held which key (used when inputs are reset). */
    public void clearPointerOwnership() {
        TouchController tc = touchController();
        if (tc != null) tc.clearPanelTouches();
    }

    private void sendKey(int index, boolean down) {
        Emulator.setKeyData(KEY_CODE[index], down ? Emulator.KEY_DOWN : Emulator.KEY_UP, (char) 0);
    }

    /* ---- drawing --------------------------------------------------------- */

    /** Repaints a single key so its pressed (shrunk) state shows up at once. */
    private void repaint(int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;
        if (mm == null || mm.getInputView() == null) return;
        Rect r = rects[index];
        if (r == null) return;
        mm.getInputView().postInvalidate(r.left, r.top, r.right, r.bottom);
    }

    /**
     * Repaints the whole panel. Used when CapsLock / Shift flip every key's
     * artwork at once.
     */
    private void repaintAll() {
        if (mm == null || mm.getInputView() == null) return;
        mm.getInputView().postInvalidate();
    }

    /**
     * The console panel's L3 / R3 copies of a key follow this class's
     * keyboard state: they draw their pressed (shrunk) state from it, not
     * from the digital button bits. No-op for every key that is not one of
     * those copies.
     */
    private void invalidateConsoleKeyCopy(int index) {
        if (mm == null || mm.getInputHandler() == null) return;
        mm.getInputHandler().getTouchConsole().invalidateKeyButton(index);
    }

    /**
     * Stores the resolved geometry / opacity of a keyboard control. Called from
     * InputView.updateImages() for every TYPE_KEYBOARD_IMG element.
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

        imageFor(index);
    }

    /**
     * Draws one key. The artwork is the same in both states; a press simply
     * shrinks it slightly around its centre. Which of the two artwork sets is
     * shown depends on the CapsLock / Shift state.
     */
    public void draw(Canvas canvas, int index) {
        if (index < 0 || index >= BUTTON_COUNT) return;

        Rect r = rects[index];
        if (r == null || mm == null) return;

        BitmapDrawable img = imageFor(index);
        if (img == null) return;

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
            shiftImages[i] = null;
            rects[i] = null;
        }
    }

    /* ---- default layout -------------------------------------------------- */

    /** The cfg's raw keys are too small to tap comfortably: grown by 200%. */
    public static final float DEFAULT_KEY_SCALE = 2f;

    private static final int NO_GAP = -1;

    /**
     * Appends the keyboard to the freshly parsed controller layout, using the
     * RetroArch cfg's own rects: {x, y, w, h} normalized to the main rect, in
     * cfg order, portrait table on portrait screens and landscape table
     * otherwise. Every key keeps its cfg position and is grown by
     * {@link #DEFAULT_KEY_SCALE} around its centre, but each side stops half a
     * neighbour gap short (the neighbour grows too) or at the screen border,
     * so the rows end up touching without any key ever swallowing another
     * one's hit area. The user is free to move / resize / hide every single
     * key in the "customize layout" screen afterwards; those edits are stored
     * per orientation like the ones of any other panel.
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

        boolean portrait = mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_PORTRAIT;
        float[][] table = portrait ? PORTRAIT_KEYS : LANDSCAPE_KEYS;
        int n = Math.min(BUTTON_COUNT, table.length);

        int[][] base = new int[n][4];
        for (int i = 0; i < n; i++) {
            float[] k = table[i];
            base[i][0] = Math.round(k[0] * mw);
            base[i][1] = Math.round(k[1] * mh);
            base[i][2] = Math.max(1, Math.round(k[2] * mw));
            base[i][3] = Math.max(1, Math.round(k[3] * mh));
        }

        for (int i = 0; i < n; i++) {
            int bx = base[i][0], by = base[i][1], bw = base[i][2], bh = base[i][3];
            int targetW = Math.round((DEFAULT_KEY_SCALE - 1f) * bw / 2f);
            int targetH = Math.round((DEFAULT_KEY_SCALE - 1f) * bh / 2f);

            int gapL = NO_GAP, gapR = NO_GAP, gapT = NO_GAP, gapB = NO_GAP;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                int jx = base[j][0], jy = base[j][1], jw = base[j][2], jh = base[j][3];
                // keys sharing this key's vertical band limit its sideways growth
                if (Math.min(by + bh, jy + jh) > Math.max(by, jy)) {
                    if (jx + jw <= bx) {
                        int g = bx - jx - jw;
                        if (gapL == NO_GAP || g < gapL) gapL = g;
                    } else if (jx >= bx + bw) {
                        int g = jx - bx - bw;
                        if (gapR == NO_GAP || g < gapR) gapR = g;
                    } else {
                        gapL = 0;
                        gapR = 0;
                    }
                }
                // keys sharing its horizontal band limit its vertical growth
                if (Math.min(bx + bw, jx + jw) > Math.max(bx, jx)) {
                    if (jy + jh <= by) {
                        int g = by - jy - jh;
                        if (gapT == NO_GAP || g < gapT) gapT = g;
                    } else if (jy >= by + bh) {
                        int g = jy - by - bh;
                        if (gapB == NO_GAP || g < gapB) gapB = g;
                    } else {
                        gapT = 0;
                        gapB = 0;
                    }
                }
            }

            int growL = Math.min(targetW, Math.min(bx, gapL == NO_GAP ? targetW : gapL / 2));
            int growR = Math.min(targetW, Math.min(mw - bx - bw, gapR == NO_GAP ? targetW : gapR / 2));
            int growT = Math.min(targetH, Math.min(by, gapT == NO_GAP ? targetH : gapT / 2));
            int growB = Math.min(targetH, Math.min(mh - by - bh, gapB == NO_GAP ? targetH : gapB / 2));

            int left = bx - growL;
            int top = by - growT;
            int w = bw + growL + growR;
            int h = bh + growT + growB;
            int[] data = { TouchController.TYPE_KEYBOARD_IMG, i, left, top, w, h };
            values.add(new InputValue(data, mm));
        }
    }
}

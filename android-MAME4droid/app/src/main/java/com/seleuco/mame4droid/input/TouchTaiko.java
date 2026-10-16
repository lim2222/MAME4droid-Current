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
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;

/**
 * On screen "tatacon" (Taiko no Tatsujin drum) for Namco System 10 Taiko games.
 *
 * A single drum is mapped to the four player-1 drum channels exposed by the
 * namcos10 driver (EXIO analog inputs), which are ordinary MAME buttons:
 *
 *   P1L Surface (left don)  -> EXIO_ANALOG3 -> IPT_BUTTON3 -> BTN3_VALUE
 *   P1R Surface (right don) -> EXIO_ANALOG2 -> IPT_BUTTON2 -> BTN2_VALUE
 *   P1L Rim     (left ka)   -> EXIO_ANALOG4 -> IPT_BUTTON4 -> BTN4_VALUE
 *   P1R Rim     (right ka)  -> EXIO_ANALOG1 -> IPT_BUTTON1 -> BTN1_VALUE
 *
 * Hit testing mirrors the Cemu / Vita3K tatacon: the drawn drum is an ellipse,
 * its centre is the drum head (don) and everything outside the head is the rim
 * (ka). Head and rim are each split into a left and a right zone by the vertical
 * centre line. Unlike Cemu the rim is deliberately the whole control rectangle
 * (not clipped to the ellipse) so that, once the drum is scaled up to cover the
 * screen, every touch lands on a zone and nothing is missed.
 *
 * The graphic is taken from drawable/tatacon.png when present; otherwise a
 * simple placeholder drum is drawn so the control is still usable.
 */
public class TouchTaiko implements IController {

    /* ---- geometry tuning ------------------------------------------------- */

    /**
     * Radius of the drum head (DON) as a fraction of the control's half-width
     * and half-height. Starts from the Cemu / Vita3K overlay (0.28 / 0.35); Y is
     * enlarged a little so that the very top edge of the real drum head in
     * tatacon.png still counts as don instead of ka.
     */
    public static final float DON_RATIO_X = 0.28f;
    public static final float DON_RATIO_Y = 0.36f;

    /**
     * Vertical offset of the drum head centre from the control centre, as a
     * fraction of the half-height. The head of tatacon.png sits a touch above
     * the middle of the control, so the don ellipse is shifted up to match it:
     * the top edge is covered without swallowing extra of the lower rim.
     */
    public static final float DON_CENTER_OFFSET_Y = -0.028f;

    /* ---------------------------------------------------------------------- */

    public static final int ZONE_NONE = -1;
    public static final int ZONE_DON_LEFT = 0;
    public static final int ZONE_DON_RIGHT = 1;
    public static final int ZONE_KA_LEFT = 2;
    public static final int ZONE_KA_RIGHT = 3;

    /** Same pool size used by TouchController. */
    private static final int MAX_POINTERS = 20;

    /** Peach "hit" glow used for the drum head, matching Cemu / Vita3K. */
    private static final int GLOW_COLOR = 0xFFFBAD96;
    /** Glow oval size as a fraction of the control's half-width / half-height. */
    private static final float GLOW_W = 0.46f * 1.1f;
    private static final float GLOW_H = 0.60f * 1.1f;
    /** Drum dips by this fraction of its height while pressed. */
    private static final float PRESS_OFFSET = 0.01f;

    protected MAME4droid mm = null;

    /** Touch/draw area in view coordinates, kept in sync by TouchController. */
    private final Rect area = new Rect();
    private float cx, cy, halfW, halfH;

    /** Pointers currently owned by the drum, by pointer id. */
    private final boolean[] owned = new boolean[MAX_POINTERS];
    private final int[] zoneOf = new int[MAX_POINTERS];

    /** Union of the button bits the drum currently holds, so we can release. */
    private int heldMask = 0;

    private BitmapDrawable drumImg = null;
    private boolean imgResolved = false;

    private Paint fallbackFace;
    private Paint fallbackRim;
    private Paint glowPaint;
    private int glowKey = Integer.MIN_VALUE;
    private final RectF outerOval = new RectF();
    private final RectF innerOval = new RectF();

    public void setMAME4droid(MAME4droid value) {
        mm = value;
        if (mm == null) return;

        if (fallbackRim == null) {
            fallbackRim = new Paint();
            fallbackRim.setAntiAlias(true);
            fallbackRim.setColor(0xFF5D4037); // dark brown drum shell
        }
        if (fallbackFace == null) {
            fallbackFace = new Paint();
            fallbackFace.setAntiAlias(true);
            fallbackFace.setColor(0xFFE0C097); // light drum head
        }
    }

    /** Resolve drawable/tatacon.png once, without a hard resource dependency. */
    private void resolveImage() {
        if (imgResolved) return;
        imgResolved = true;
        try {
            int id = mm.getResources().getIdentifier("tatacon", "drawable", mm.getPackageName());
            if (id != 0) {
                drumImg = (BitmapDrawable) mm.getResources().getDrawable(id);
            }
        } catch (Throwable t) {
            drumImg = null;
        }
    }

    public void setArea(Rect r) {
        if (r == null || r.isEmpty()) return;
        area.set(r);
        cx = area.exactCenterX();
        cy = area.exactCenterY();
        halfW = area.width() * 0.5f;
        halfH = area.height() * 0.5f;
    }

    public Rect getArea() {
        return area;
    }

    public boolean isEnabled() {
        return mm != null && mm.getPrefsHelper().isTatacon();
    }

    /** True while the given pointer id is captured by the drum. */
    public boolean handlesPointer(int pointerId) {
        return pointerId >= 0 && pointerId < MAX_POINTERS && owned[pointerId];
    }

    public void reset() {
        releasePointers();
        heldMask = 0;
    }

    /**
     * Drops every captured pointer but keeps {@link #heldMask}. Used on
     * ACTION_UP / CANCEL so the release of the currently held drum buttons can
     * still be computed (and pushed to the emulator) afterwards. Clearing the
     * mask here would leave the buttons stuck high, which is exactly the bug
     * where a repeated don/ka hit on the same side would not register again.
     */
    private void releasePointers() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            owned[i] = false;
            zoneOf[i] = ZONE_NONE;
        }
    }

    /**
     * Which drum zone a point falls in (Cemu/Vita3K style radial hit test).
     *
     * The centre ellipse is the head (don); <em>everything</em> else inside the
     * control is the rim (ka), split left / right by the vertical centre line.
     * There is deliberately no dead zone: once the drum is scaled up to cover
     * the screen, any touch lands on don or ka.
     */
    public int zoneForTouch(float x, float y) {
        if (halfW <= 0f || halfH <= 0f) return ZONE_NONE;

        float dx = (x - cx) / halfW;
        // Measured from the drum head centre, which sits above the control centre.
        float dy = (y - cy) / halfH - DON_CENTER_OFFSET_Y;

        float donCheck = (dx * dx) / (DON_RATIO_X * DON_RATIO_X)
                + (dy * dy) / (DON_RATIO_Y * DON_RATIO_Y);
        if (donCheck <= 1.0f) {
            return dx < 0f ? ZONE_DON_LEFT : ZONE_DON_RIGHT;
        }
        return dx < 0f ? ZONE_KA_LEFT : ZONE_KA_RIGHT;
    }

    private static int zoneMask(int zone) {
        switch (zone) {
            case ZONE_DON_LEFT:  return BTN3_VALUE;
            case ZONE_DON_RIGHT: return BTN2_VALUE;
            case ZONE_KA_LEFT:   return BTN4_VALUE;
            case ZONE_KA_RIGHT:  return BTN1_VALUE;
        }
        return 0;
    }

    /**
     * Route a MotionEvent to the drum. Only pointers that went down inside the
     * drum are captured; those are then skipped by TouchController, exactly
     * like TouchStick owns its grab pointer.
     */
    public void handleMotion(MotionEvent event, int[] digital_data) {
        if (mm == null || !isEnabled()) {
            reset();
            return;
        }

        int actionMasked = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int actionPid = event.getPointerId(actionIndex);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (actionPid >= 0 && actionPid < MAX_POINTERS) {
                    int px = (int) event.getX(actionIndex);
                    int py = (int) event.getY(actionIndex);
                    // The drum may cover the whole screen: yield to the on-screen
                    // coin / start / option / exit buttons so they stay reachable.
                    if (area.contains(px, py) && !overButton(px, py)) {
                        owned[actionPid] = true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Keep heldMask so the block below releases the buttons we held.
                releasePointers();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (actionPid >= 0 && actionPid < MAX_POINTERS) {
                    owned[actionPid] = false;
                    zoneOf[actionPid] = ZONE_NONE;
                }
                break;
        }

        // Refresh the zone of every pointer still held by the drum.
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pid = event.getPointerId(i);
            if (pid < 0 || pid >= MAX_POINTERS || !owned[pid]) continue;
            zoneOf[pid] = zoneForTouch(event.getX(i), event.getY(i));
        }

        int newMask = 0;
        for (int pid = 0; pid < MAX_POINTERS; pid++) {
            if (owned[pid]) newMask |= zoneMask(zoneOf[pid]);
        }

        // Release bits we no longer hold, then press the new ones.
        digital_data[0] &= ~(heldMask & ~newMask);
        digital_data[0] |= newMask;
        heldMask = newMask;

        Emulator.setDigitalData(0, digital_data[0]);
    }

    private RectF outerRectF() {
        outerOval.set(area.left, area.top, area.right, area.bottom);
        return outerOval;
    }

    private RectF innerRectF() {
        float iw = halfW * DON_RATIO_X;
        float ih = halfH * DON_RATIO_Y;
        float icY = cy + halfH * DON_CENTER_OFFSET_Y;
        innerOval.set(cx - iw, icY - ih, cx + iw, icY + ih);
        return innerOval;
    }

    /** True when the point is inside an on-screen button handled by TouchController. */
    private boolean overButton(int x, int y) {
        if (mm.getInputHandler() == null) return false;
        TouchController tc = mm.getInputHandler().getTouchController();
        return tc != null && tc.isOverButton(x, y);
    }

    /** Build (once per size) the peach radial-gradient head highlight. */
    private void ensureGlowPaint() {
        int key = ((area.left * 31 + area.top) * 31 + area.width()) * 31 + area.height();
        if (glowPaint != null && key == glowKey) return;
        glowKey = key;
        float r = Math.max(2f, halfW * GLOW_W * 0.5f);
        glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setShader(new RadialGradient(cx, cy, r,
                new int[]{ GLOW_COLOR, GLOW_COLOR & 0x00FFFFFF },
                new float[]{ 0f, 1f },
                Shader.TileMode.CLAMP));
    }

    public void draw(Canvas canvas) {
        if (mm == null || area.isEmpty() || halfW <= 0f || halfH <= 0f) return;
        resolveImage();

        int alpha = mm.getMainHelper().getControllerAlpha();

        boolean pressed = (heldMask != 0);
        boolean donPressed = (heldMask & (BTN3_VALUE | BTN2_VALUE)) != 0;

        // Whole drum dips slightly and brightens while pressed, like Cemu / Vita3K.
        int save = canvas.save();
        if (pressed) {
            canvas.translate(0f, area.height() * PRESS_OFFSET);
        }

        if (drumImg != null) {
            drumImg.setBounds(area);
            drumImg.setAlpha(pressed ? Math.min((int) (alpha * 1.15f), 255) : alpha);
            drumImg.draw(canvas);
        } else {
            // Placeholder drum so the control works without tatacon.png.
            canvas.drawOval(outerRectF(), fallbackRim);
            canvas.drawOval(innerRectF(), fallbackFace);
        }
        canvas.restoreToCount(save);

        if (!isEnabled()) return;

        // Only the drum head flashes; the rim (ka) deliberately has no effect.
        if (donPressed) {
            ensureGlowPaint();
            glowPaint.setAlpha(Math.min((int) (alpha * 1.45f), 255));
            float gw = Math.max(2f, halfW * GLOW_W);
            float gh = Math.max(2f, halfH * GLOW_H);
            canvas.drawOval(cx - gw * 0.5f, cy - gh * 0.5f,
                    cx + gw * 0.5f, cy + gh * 0.5f, glowPaint);
        }
    }
}

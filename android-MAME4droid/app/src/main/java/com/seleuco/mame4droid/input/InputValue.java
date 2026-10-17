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

import com.seleuco.mame4droid.MAME4droid;

import android.graphics.Rect;


public class InputValue {

    private int type;
    private int value;

    private int o_x1;
    private int o_y1;
    private int o_x2;
    private int o_y2;

    private float dx = 1;
    private float dy = 1;
    private int ax = 0;
    private int ay = 0;

    private int xoff_tmp = 0;
    private int yoff_tmp = 0;

    private int xoff = 0;
    private int yoff = 0;

    // Per-control opacity override: -1 means "not set, follow the global button
    // alpha", 0..255 is an explicit value set in the layout customizer.
    private int alpha = -1;

    // Hidden in the layout customizer (HIDE mode). Hidden controls are not drawn
    // and do not take touch input, but stay visible (dimmed) while the customizer
    // is open so they can be brought back.
    private boolean hidden = false;

    // Size scale set in the layout customizer's RESIZE mode. The factor is applied
    // on top of the base rectangle (original size plus the size presets) and around
    // a common anchor, so a group of related controls (a button and its artwork, or
    // every piece of the stick) keeps its shape and stays proportional on screens of
    // any size. An anchor of -1 means "grow around the control's own centre".
    private float scaleX = 1f;
    private float scaleY = 1f;
    private int anchorX = -1;
    private int anchorY = -1;

    private int sz_x1;
    private int sz_y1;
    private int sz_x2;
    private int sz_y2;

    private Rect rect = null;

    private Rect origRect = null;

    private MAME4droid mm = null;

    public InputValue(int d[], MAME4droid mm) {
        this.mm = mm;
        //data = d;
        type = d[0];
        value = d[1];

        if (type == TouchController.TYPE_STICK_RECT && mm.getPrefsHelper().isTouchDZ()) {
            if (value == IController.STICK_LEFT) {
                d[4] -= d[4] * 0.18f;
            }
            if (value == IController.STICK_RIGHT) {
                d[2] += d[4] * 0.18f;
                d[4] -= (d[4] * 0.18f);
            }
        }

        o_x1 = d[2];
        o_y1 = d[3];
        o_x2 = o_x1 + d[4];
        o_y2 = o_y1 + d[5];
    }

    public void setFixData(float dx, float dy, int ax, int ay) {
        this.dx = dx;
        this.dy = dy;
        this.ax = ax;
        this.ay = ay;
        rect = null;
    }

    public void setSize(int sz_x1, int sz_y1, int sz_x2, int sz_y2) {
        this.sz_x1 = sz_x1;
        this.sz_x2 = sz_x2;
        this.sz_y1 = sz_y1;
        this.sz_y2 = sz_y2;
        rect = null;
    }

    /** Horizontal size scale set in the customizer's RESIZE mode, 1 = untouched. */
    public float getScaleX() {
        return scaleX;
    }

    /** Vertical size scale set in the customizer's RESIZE mode, 1 = untouched. */
    public float getScaleY() {
        return scaleY;
    }

    public void setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        rect = null;
    }

    /** Point (in layout units) the control is scaled around. */
    public void setScaleAnchor(int anchorX, int anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        rect = null;
    }

    /** Drops any RESIZE-mode scaling, restoring the base size. */
    public void clearScale() {
        scaleX = 1f;
        scaleY = 1f;
        anchorX = -1;
        anchorY = -1;
        rect = null;
    }

    /** Horizontal centre of the base rectangle, in layout units. */
    public float getBaseCenterX() {
        return (o_x1 + sz_x1 + o_x2 + sz_x2) / 2f;
    }

    /** Vertical centre of the base rectangle, in layout units. */
    public float getBaseCenterY() {
        return (o_y1 + sz_y1 + o_y2 + sz_y2) / 2f;
    }

    public void setOffset(int xoff, int yoff) {
        this.xoff = xoff;
        this.yoff = yoff;
        xoff_tmp = 0;
        yoff_tmp = 0;
        rect = null;
    }

    public void setOffsetTMP(int xoff_tmp, int yoff_tmp) {
        this.xoff_tmp = xoff_tmp;
        this.yoff_tmp = yoff_tmp;
        rect = null;
    }

    public void commitChanges() {
        xoff += xoff_tmp;
        yoff += yoff_tmp;
        xoff_tmp = 0;
        yoff_tmp = 0;
    }

    public Rect getRect() {
        if (rect == null) {
            // Base rectangle: original size plus the global buttons / stick size preset.
            float bx1 = o_x1 + sz_x1;
            float by1 = o_y1 + sz_y1;
            float bx2 = o_x2 + sz_x2;
            float by2 = o_y2 + sz_y2;

            // RESIZE-mode scale, grown around the anchor (own centre when unset).
            if (scaleX != 1f || scaleY != 1f) {
                float cx = anchorX >= 0 ? anchorX : (bx1 + bx2) / 2f;
                float cy = anchorY >= 0 ? anchorY : (by1 + by2) / 2f;
                bx1 = cx + (bx1 - cx) * scaleX;
                by1 = cy + (by1 - cy) * scaleY;
                bx2 = cx + (bx2 - cx) * scaleX;
                by2 = cy + (by2 - cy) * scaleY;
            }

            rect =
                    new Rect((int) (bx1 * dx) + ax + xoff + xoff_tmp,
                            (int) (by1 * dy) + ay + yoff + yoff_tmp,
                            (int) (bx2 * dx) + ax + xoff + xoff_tmp,
                            (int) (by2 * dy) + ay + yoff + yoff_tmp
                    );
        }
        return rect;
    }

    protected Rect getOrigRect() {
        if (origRect == null) {
            origRect = new Rect(o_x1, o_y1, o_x2, o_y2);
        }
        return origRect;
    }


    public int getType() {
        return type;
    }

    public int getValue() {
        return value;
    }

    public int getXoff_tmp() {
        return xoff_tmp;
    }

    public int getYoff_tmp() {
        return yoff_tmp;
    }

    public int getXoff() {
        return xoff;
    }

    public int getYoff() {
        return yoff;
    }

    /** Explicit opacity 0..255, or -1 when this control has none and follows the global alpha. */
    public int getAlpha() {
        return alpha;
    }

    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    /** True when this control was hidden in the layout customizer's HIDE mode. */
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}

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

import java.util.ArrayList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.view.MotionEvent;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.helpers.DialogHelper;
import com.seleuco.mame4droid.helpers.PrefsHelper;

public class ControlCustomizer {

	private static boolean enabled = false;

	// Constant for the movement snap-to-grid threshold to keep layouts clean
	private static final int SNAP_TO_GRID = 5;

	// Coordinates of the initial touch position
	private int initialTouchX = 0;
	private int initialTouchY = 0;

	// Initial offset of the control when it starts to be dragged
	private int initialXOffset = 0;
	private int initialYOffset = 0;

	// Tracks the active pointer ID to prevent multi-touch warping during drags
	private int activePointerId = -1;

	private InputValue valueMoved = null;

	// Scale of the control being resized, captured when the drag starts so the
	// gesture never jumps.
	private float initialScaleX = 1f;
	private float initialScaleY = 1f;

	// Edit modes. Only one is active at a time.
	private static final int MODE_MOVE = 0;
	private static final int MODE_RESIZE = 1;
	private static final int MODE_OPACITY = 2;
	private static final int MODE_HIDE = 3;
	private int mode = MODE_MOVE;

	// Opacity editing (MODE_OPACITY): one pixel of horizontal drag is one step of
	// alpha, so sweeping the whole 0..255 range takes a 255 px drag.
	private static final int ALPHA_STEP_PER_PX = 1;

	// Alpha of the control being dragged. Kept up to date incrementally so that
	// reversing the drag responds at once even after the range was clamped.
	private int draggingAlpha = 255;
	private int lastDragX = 0;

	protected MAME4droid mm = null;

	// Cached UI objects to prevent Garbage Collection churn during the 60fps draw loop
	private Rect saveButtonRect = new Rect();
	private Rect moveButtonRect = new Rect();
	private Rect resizeButtonRect = new Rect();
	private Rect opacityButtonRect = new Rect();
	private Rect hideButtonRect = new Rect();
	private Rect resetControlsButtonRect = new Rect();
	private Rect resetMahjongButtonRect = new Rect();
	private Rect resetTaikoButtonRect = new Rect();
	private Rect resetAllButtonRect = new Rect();
	private Paint pButton;
	private Paint pButtonOff;
	private Paint pButtonWarn;
	private Paint pText;
	private Paint pValue;
	private Paint pRect;
	private Paint pHiddenRect;
	private boolean paintsInitialized = false;

	// Button dimensions
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 80;
	private static final int BUTTON_MARGIN = 10;
	private static final int TEXT_PADDING = 10;

	public static void setEnabled(boolean enabled) {
		ControlCustomizer.enabled = enabled;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public void setMAME4droid(MAME4droid value) {
		mm = value;
	}

	/** Groups the reset buttons work on. */
	public static final int GROUP_CONTROLS = 0;
	public static final int GROUP_MAHJONG = 1;
	public static final int GROUP_TAIKO = 2;
	public static final int GROUP_ALL = 3;

	/** True when this control belongs to the group a reset button clears. */
	private static boolean isResetGroup(int group, InputValue iv) {
		int type = iv.getType();
		if (group == GROUP_ALL) return true;
		if (group == GROUP_MAHJONG) return type == TouchController.TYPE_MAHJONG_IMG;
		if (group == GROUP_TAIKO) return type == TouchController.TYPE_TATACON_RECT;
		// GROUP_CONTROLS: the stick / dpad and the buttons with their artwork.
		return type != TouchController.TYPE_MAHJONG_IMG
			&& type != TouchController.TYPE_TATACON_RECT
			&& type != TouchController.TYPE_MAIN_RECT;
	}

	/**
	 * Puts the controls of one group back to their default position, size,
	 * opacity and visibility, leaving the panels of the other groups alone.
	 * Nothing is written to storage here: the layout is only saved when SAVE is
	 * pressed, so leaving the customizer without saving restores the previous
	 * layout.
	 */
	public void resetDefinedControlLayout(int group) {
		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		if (values == null) return;

		for (InputValue iv : values) {
			// Controls of a panel that is not on screen are left alone: resetting
			// them here would silently throw away a layout the user cannot see.
			if (!isResetGroup(group, iv) || !isEditable(iv)) continue;

			// Clears both the committed offset and the temporary one.
			iv.setOffset(0, 0);
			iv.setAlpha(-1);
			iv.setHidden(false);
			iv.clearScale();
			if (iv.getType() == TouchController.TYPE_ANALOG_RECT) {
				mm.getInputHandler().getTouchStick().setStickArea(iv.getRect());
			} else if (iv.getType() == TouchController.TYPE_TATACON_RECT) {
				mm.getInputHandler().getTouchTaiko().setArea(iv.getRect());
			}
		}

		mm.getInputView().updateImages();
		mm.getInputView().invalidate();
	}

	/**
	 * Discards the position changes of the control layout.
	 * Resets temporary offsets to zero.
	 */
	public void discardDefinedControlLayout() {
		boolean portrait = mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_PORTRAIT;

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		for (InputValue iv : values) {
			iv.setOffsetTMP(0, 0);
			// Drop opacities edited since the last save; the saved ones are re-applied below.
			iv.setAlpha(-1);
			// Same for the hidden flags.
			iv.setHidden(false);
			// And for the sizes edited in resize mode.
			iv.clearScale();
			if (iv.getType() == TouchController.TYPE_ANALOG_RECT) {
				mm.getInputHandler().getTouchStick().setStickArea(iv.getRect());
			} else if (iv.getType() == TouchController.TYPE_TATACON_RECT) {
				mm.getInputHandler().getTouchTaiko().setArea(iv.getRect());
			}
		}
		readDefinedControlLayoutAlpha(values, portrait);
		readDefinedControlLayoutHidden(values, portrait);
		readDefinedControlLayoutScale(values, portrait);
		mm.getInputView().updateImages();
	}

	/**
	 * Applies X / Y scale factors to a control, growing it around the centre of the
	 * group it belongs to: a button scales together with the image drawn for it, and
	 * every piece of the stick scales around the same point, so the group keeps its
	 * shape instead of drifting apart.
	 */
	private void applyScale(InputValue target, float scaleX, float scaleY) {
		if (target == null) return;

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();

		// Common centre (in layout units) of the group.
		float cx = 0;
		float cy = 0;
		int n = 0;
		for (InputValue iv : values) {
			if (!isSameControlGroup(target, iv)) continue;
			cx += iv.getBaseCenterX();
			cy += iv.getBaseCenterY();
			n++;
		}
		if (n == 0) return;
		cx /= n;
		cy /= n;

		for (InputValue iv : values) {
			if (!isSameControlGroup(target, iv)) continue;
			iv.setScale(scaleX, scaleY);
			iv.setScaleAnchor(Math.round(cx), Math.round(cy));
			if (iv.getType() == TouchController.TYPE_ANALOG_RECT) {
				mm.getInputHandler().getTouchStick().setStickArea(iv.getRect());
			}
		}
		if (target.getType() == TouchController.TYPE_TATACON_RECT) {
			mm.getInputHandler().getTouchTaiko().setArea(target.getRect());
		}
	}

	/**
	 * Whether two entries are pieces of the same visual unit: a button and the image
	 * drawn for it, or the rects and images making up a stick. Everything else is
	 * identified by its own type / value.
	 */
	private static boolean isSameControlGroup(InputValue a, InputValue b) {
		int typeA = a.getType();
		int typeB = b.getType();

		if (typeA == TouchController.TYPE_BUTTON_RECT || typeA == TouchController.TYPE_BUTTON_IMG) {
			return (typeB == TouchController.TYPE_BUTTON_RECT || typeB == TouchController.TYPE_BUTTON_IMG)
				&& a.getValue() == b.getValue();
		}
		if (typeA == TouchController.TYPE_STICK_IMG || typeA == TouchController.TYPE_STICK_RECT ||
			typeA == TouchController.TYPE_ANALOG_RECT) {
			return typeB == TouchController.TYPE_STICK_RECT || typeB == TouchController.TYPE_STICK_IMG ||
				typeB == TouchController.TYPE_ANALOG_RECT;
		}
		return typeA == typeB && a.getValue() == b.getValue();
	}

	/**
	 * True when this control is part of the layout currently on screen, i.e. one
	 * the customizer is allowed to show and edit. The same predicate the game
	 * uses to decide what to draw and what takes touches, so panels that are
	 * switched off leave nothing behind to grab: outside taiko mode the drum is
	 * gone, and while taiko or mahjong is on the dpad / stick / action buttons
	 * are gone. An invisible control can therefore never be edited by accident.
	 */
	private boolean isEditable(InputValue iv) {
		return Boolean.TRUE.equals(
			mm.getInputHandler().getTouchController().isHandledTouchItem(iv));
	}

	/** True when the group has at least one control on screen right now. */
	private boolean isGroupEditable(int group) {
		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		if (values == null) return false;
		for (InputValue iv : values) {
			if (isResetGroup(group, iv) && isEditable(iv)) return true;
		}
		return false;
	}

	/**
	 * Pick the control to drag at (x, y). When {@code tatacon} is false the drum
	 * is skipped, so the small on-screen buttons keep grab priority.
	 */
	private InputValue findMovable(ArrayList<InputValue> values, int x, int y, boolean tatacon) {
		for (InputValue iv : values) {
			if (!isEditable(iv)) continue;
			boolean isTatacon = (iv.getType() == TouchController.TYPE_TATACON_RECT);
			if (isTatacon != tatacon) continue;
			if ((iv.getType() == TouchController.TYPE_BUTTON_RECT ||
				iv.getType() == TouchController.TYPE_STICK_IMG ||
				isTatacon ||
				iv.getType() == TouchController.TYPE_ANALOG_RECT ||
				iv.getType() == TouchController.TYPE_MAHJONG_IMG)
				&& iv.getRect().contains(x, y)) {

				valueMoved = iv;
				initialTouchX = x;
				initialTouchY = y;
				initialXOffset = iv.getXoff_tmp();
				initialYOffset = iv.getYoff_tmp();
				return iv;
			}
		}
		return null;
	}

	/**
	 * Commits and saves the current temporary position offsets to persistent storage.
	 */
	public void saveDefinedControlLayout() {
		StringBuilder definedStr = new StringBuilder();
		StringBuilder alphaStr = new StringBuilder();
		StringBuilder hiddenStr = new StringBuilder();
		StringBuilder scaleStr = new StringBuilder();
		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		boolean first = true;
		// Legacy drum size preference, kept in sync for older versions.
		float drumScaleX = 1f;
		float drumScaleY = 1f;

		for (InputValue iv : values) {
			// Commit temporary offset changes to permanent offsets
			iv.commitChanges();
			if (iv.getXoff() != 0 || iv.getYoff() != 0) {
				if (!first) {
					definedStr.append(",");
				}
				definedStr.append(iv.getType()).append(",")
					.append(iv.getValue()).append(",")
					.append(iv.getXoff()).append(",")
					.append(iv.getYoff());
				first = false;
			}

			// Opacities live in their own, independent list so the position format
			// above stays backwards compatible.
			if (iv.getAlpha() >= 0) {
				if (alphaStr.length() > 0) {
					alphaStr.append(",");
				}
				alphaStr.append(iv.getType()).append(",")
					.append(iv.getValue()).append(",")
					.append(iv.getAlpha());
			}

			// Hidden controls are listed by type,value too. Only the hidden ones
			// are stored, so an empty list means "everything visible".
			if (iv.isHidden()) {
				if (hiddenStr.length() > 0) {
					hiddenStr.append(",");
				}
				hiddenStr.append(iv.getType()).append(",")
					.append(iv.getValue());
			}

			// Same idea for the sizes edited in resize mode, as type,value,X,Y groups.
			if (iv.getScaleX() != 1f || iv.getScaleY() != 1f) {
				if (scaleStr.length() > 0) {
					scaleStr.append(",");
				}
				scaleStr.append(iv.getType()).append(",")
					.append(iv.getValue()).append(",")
					.append(iv.getScaleX()).append(",")
					.append(iv.getScaleY());
			}
			if (iv.getType() == TouchController.TYPE_TATACON_RECT) {
				drumScaleX = iv.getScaleX();
				drumScaleY = iv.getScaleY();
			}
		}

		if (mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
			mm.getPrefsHelper().setDefinedControlLayoutLand(definedStr.toString());
			mm.getPrefsHelper().setDefinedControlLayoutAlphaLand(alphaStr.toString());
			mm.getPrefsHelper().setDefinedControlLayoutHiddenLand(hiddenStr.toString());
			mm.getPrefsHelper().setDefinedControlLayoutScaleLand(scaleStr.toString());
			mm.getPrefsHelper().setTataconScales(false, drumScaleX, drumScaleY);
		} else {
			mm.getPrefsHelper().setDefinedControlLayoutPortrait(definedStr.toString());
			mm.getPrefsHelper().setDefinedControlLayoutAlphaPortrait(alphaStr.toString());
			mm.getPrefsHelper().setDefinedControlLayoutHiddenPortrait(hiddenStr.toString());
			mm.getPrefsHelper().setDefinedControlLayoutScalePortrait(scaleStr.toString());
			mm.getPrefsHelper().setTataconScales(true, drumScaleX, drumScaleY);
		}
	}

	/**
	 * Restores layout offsets from SharedPreferences and applies them to the UI components.
	 */
	public void readDefinedControlLayout() {
		boolean portrait = mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_PORTRAIT;

		// In non-full portrait only the taiko drum honours its custom position /
		// size; the classic controls keep their fixed layout to prevent clipping.
		boolean portraitNonFull = portrait && !Emulator.isPortraitFull();

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		String definedStr = (mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_LANDSCAPE)
			? mm.getPrefsHelper().getDefinedControlLayoutLand()
			: mm.getPrefsHelper().getDefinedControlLayoutPortrait();

		if (definedStr != null && !definedStr.isEmpty()) {
			String[] tokens = definedStr.split(",");

			// Failsafe: Ensure data stream is intact (Type, Value, X, Y chunks)
			if (tokens.length % 4 != 0) return;

			for (int i = 0; i < tokens.length; i += 4) {
				try {
					int type = Integer.parseInt(tokens[i]);
					int value = Integer.parseInt(tokens[i + 1]);
					int xoff = Integer.parseInt(tokens[i + 2]);
					int yoff = Integer.parseInt(tokens[i + 3]);

					if (portraitNonFull && type != TouchController.TYPE_TATACON_RECT) continue;

					for (InputValue iv : values) {
						if (iv.getType() == type && iv.getValue() == value) {
							iv.setOffset(xoff, yoff);
							if (type == TouchController.TYPE_ANALOG_RECT) {
								mm.getInputHandler().getTouchStick().setStickArea(iv.getRect());
							} else if (type == TouchController.TYPE_TATACON_RECT) {
								mm.getInputHandler().getTouchTaiko().setArea(iv.getRect());
							}
							break;
						}
					}
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
			}
		}
		readDefinedControlLayoutAlpha(values, portrait);
		readDefinedControlLayoutHidden(values, portrait);
		readDefinedControlLayoutScale(values, portrait);
		mm.getInputView().updateImages();
	}

	/**
	 * Restores the controls hidden with the customizer's HIDE mode. Stored as
	 * {@code type,value} pairs, separately from the position offsets; an empty /
	 * missing list means everything is visible.
	 */
	private void readDefinedControlLayoutHidden(ArrayList<InputValue> values, boolean portrait) {
		String hiddenStr = portrait
			? mm.getPrefsHelper().getDefinedControlLayoutHiddenPortrait()
			: mm.getPrefsHelper().getDefinedControlLayoutHiddenLand();

		if (hiddenStr == null || hiddenStr.length() == 0) return;

		String[] tokens = hiddenStr.split(",");
		if (tokens.length == 0 || (tokens.length % 2) != 0) return;

		for (int i = 0; i + 1 < tokens.length; i += 2) {
			try {
				int type = Integer.parseInt(tokens[i].trim());
				int value = Integer.parseInt(tokens[i + 1].trim());
				for (InputValue iv : values) {
					if (iv.getType() == type && iv.getValue() == value) {
						iv.setHidden(true);
						break;
					}
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Restores the sizes edited with the customizer's RESIZE mode. Stored as
	 * {@code type,value,scaleX,scaleY} groups, separately from the offsets, and
	 * only for the controls that were actually resized.
	 */
	private void readDefinedControlLayoutScale(ArrayList<InputValue> values, boolean portrait) {
		// In non-full portrait only the taiko drum honours its custom size; the
		// classic controls keep their fixed layout to prevent clipping.
		boolean portraitNonFull = portrait && !Emulator.isPortraitFull();

		String scaleStr = portrait
			? mm.getPrefsHelper().getDefinedControlLayoutScalePortrait()
			: mm.getPrefsHelper().getDefinedControlLayoutScaleLand();

		boolean drumLoaded = false;

		if (scaleStr != null && scaleStr.length() > 0) {
			String[] tokens = scaleStr.split(",");

			// Failsafe: Ensure data stream is intact (Type, Value, X, Y chunks)
			if (tokens.length % 4 == 0) {
				for (int i = 0; i + 3 < tokens.length; i += 4) {
					try {
						int type = Integer.parseInt(tokens[i].trim());
						int value = Integer.parseInt(tokens[i + 1].trim());
						float scaleX = clampScale(Float.parseFloat(tokens[i + 2].trim()));
						float scaleY = clampScale(Float.parseFloat(tokens[i + 3].trim()));

						if (portraitNonFull && type != TouchController.TYPE_TATACON_RECT) continue;

						for (InputValue iv : values) {
							if (iv.getType() == type && iv.getValue() == value) {
								applyScale(iv, scaleX, scaleY);
								if (type == TouchController.TYPE_TATACON_RECT) drumLoaded = true;
								break;
							}
						}
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}
				}
			}
		}

		// Older versions kept the drum size in its own preference: honour it when
		// nothing newer was stored for the drum.
		if (!drumLoaded) {
			float drumScaleX = mm.getPrefsHelper().getTataconScaleX(portrait);
			float drumScaleY = mm.getPrefsHelper().getTataconScaleY(portrait);
			if (drumScaleX != 1f || drumScaleY != 1f) {
				for (InputValue iv : values) {
					if (iv.getType() == TouchController.TYPE_TATACON_RECT) {
						applyScale(iv, drumScaleX, drumScaleY);
						break;
					}
				}
			}
		}
	}

	/**
	 * Restores the per-control opacity overrides saved by the customizer. Stored as
	 * {@code type,value,alpha} triples, separately from the position offsets.
	 */
	private void readDefinedControlLayoutAlpha(ArrayList<InputValue> values, boolean portrait) {
		String alphaStr = portrait
			? mm.getPrefsHelper().getDefinedControlLayoutAlphaPortrait()
			: mm.getPrefsHelper().getDefinedControlLayoutAlphaLand();

		if (alphaStr == null || alphaStr.isEmpty()) return;

		String[] tokens = alphaStr.split(",");

		// Failsafe: Ensure data stream is intact (Type, Value, Alpha chunks)
		if (tokens.length % 3 != 0) return;

		for (int i = 0; i < tokens.length; i += 3) {
			try {
				int type = Integer.parseInt(tokens[i]);
				int value = Integer.parseInt(tokens[i + 1]);
				int alpha = Integer.parseInt(tokens[i + 2]);

				if (alpha < 0 || alpha > 255) continue;

				for (InputValue iv : values) {
					if (iv.getType() == type && iv.getValue() == value) {
						iv.setAlpha(alpha);
						break;
					}
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Synchronizes related bounding boxes (e.g., visual image bounds vs logical touch bounds)
	 * for the actively dragged control.
	 */
	protected void updateRelatedRects() {
		if (valueMoved == null) return;

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();

		if (valueMoved.getType() == TouchController.TYPE_BUTTON_RECT) {
			for (InputValue iv : values) {
				if (iv.getType() == TouchController.TYPE_BUTTON_IMG && iv.getValue() == valueMoved.getValue()) {
					iv.setOffsetTMP(valueMoved.getXoff_tmp(), valueMoved.getYoff_tmp());
					break;
				}
			}
		} else if (valueMoved.getType() == TouchController.TYPE_STICK_IMG || valueMoved.getType() == TouchController.TYPE_ANALOG_RECT) {
			for (InputValue iv : values) {
				if (iv.getType() == TouchController.TYPE_STICK_RECT ||
					iv.getType() == TouchController.TYPE_STICK_IMG ||
					iv.getType() == TouchController.TYPE_ANALOG_RECT) {
					iv.setOffsetTMP(valueMoved.getXoff_tmp(), valueMoved.getYoff_tmp());
				}
				if (iv.getType() == TouchController.TYPE_ANALOG_RECT) {
					mm.getInputHandler().getTouchStick().setStickArea(valueMoved.getRect());
				}
			}
		} else if (valueMoved.getType() == TouchController.TYPE_TATACON_RECT) {
			mm.getInputHandler().getTouchTaiko().setArea(valueMoved.getRect());
		}
	}

	/**
	 * Translates user touch input into layout coordinate offsets.
	 */
	public void handleMotion(MotionEvent event) {
		int action = event.getActionMasked();
		int pointerIndex = event.getActionIndex();

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				activePointerId = event.getPointerId(0);
				int x = (int) event.getX(0);
				int y = (int) event.getY(0);

				// Mode toggles / save button.
				if (moveButtonRect.contains(x, y)) {
					mode = MODE_MOVE;
					mm.getInputView().invalidate();
					return;
				}
				if (resizeButtonRect.contains(x, y)) {
					mode = MODE_RESIZE;
					mm.getInputView().invalidate();
					return;
				}
				if (opacityButtonRect.contains(x, y)) {
					mode = MODE_OPACITY;
					mm.getInputView().invalidate();
					return;
				}
				if (hideButtonRect.contains(x, y)) {
					mode = MODE_HIDE;
					mm.getInputView().invalidate();
					return;
				}
				if (resetControlsButtonRect.contains(x, y)) {
					// Immediate, but only in memory: SAVE writes it, leaving the
					// customizer without saving keeps the stored layout.
					resetDefinedControlLayout(GROUP_CONTROLS);
					return;
				}
				if (resetMahjongButtonRect.contains(x, y)) {
					resetDefinedControlLayout(GROUP_MAHJONG);
					return;
				}
				if (resetTaikoButtonRect.contains(x, y)) {
					resetDefinedControlLayout(GROUP_TAIKO);
					return;
				}
				if (resetAllButtonRect.contains(x, y)) {
					resetDefinedControlLayout(GROUP_ALL);
					return;
				}
				if (saveButtonRect.contains(x, y)) {
					mm.showDialog(DialogHelper.DIALOG_FINISH_CUSTOM_LAYOUT);
					mm.getInputView().invalidate();
					return;
				}

				ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();

				if (mode == MODE_RESIZE) {
					// Any control can be resized: horizontal drag = width, vertical =
					// height. Small controls keep grab priority over the taiko drum.
					InputValue target = findMovable(values, x, y, false);
					if (target == null) target = findMovable(values, x, y, true);
					if (target != null) {
						initialScaleX = target.getScaleX();
						initialScaleY = target.getScaleY();
					}
				} else if (mode == MODE_HIDE) {
					// Tap a control to hide it; tap it again to bring it back.
					// Nothing is dragged in this mode.
					InputValue target = findMovable(values, x, y, false);
					if (target == null) target = findMovable(values, x, y, true);
					valueMoved = null;
					if (target != null) {
						setControlHidden(target, !target.isHidden());
						mm.getInputView().updateImages();
						mm.getInputView().invalidate();
					}
				} else {
					// Move / opacity mode: buttons keep grab priority over the drum.
					if (findMovable(values, x, y, false) == null) {
						findMovable(values, x, y, true);
					}
					if (mode == MODE_OPACITY && valueMoved != null) {
						// Start from whatever the control currently shows, so the
						// first drag never jumps.
						int current = valueMoved.getAlpha();
						draggingAlpha = current >= 0 ? current : mm.getMainHelper().getControllerAlpha();
						lastDragX = x;
					}
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (valueMoved == null) break;
				// HIDE mode only toggles: never drag a control by accident.
				if (mode == MODE_HIDE) break;

				int pIndex = event.findPointerIndex(activePointerId);
				if (pIndex == -1) break;

				int currX = (int) event.getX(pIndex);
				int currY = (int) event.getY(pIndex);

				int deltaX = currX - initialTouchX;
				int deltaY = currY - initialTouchY;

				if (mode == MODE_RESIZE) {
					// One layout unit maps to dx / dy screen pixels. Dragging by the
					// control's own width / height doubles its size.
					TouchController tc = mm.getInputHandler().getTouchController();
					float baseWpx = valueMoved.getOrigRect().width() * tc.dx;
					float baseHpx = valueMoved.getOrigRect().height() * tc.dy;
					if (baseWpx < 1f) baseWpx = 1f;
					if (baseHpx < 1f) baseHpx = 1f;

					applyScale(valueMoved,
						clampScale(initialScaleX + deltaX / baseWpx),
						clampScale(initialScaleY + deltaY / baseHpx));
					mm.getInputView().updateImages();
					mm.getInputView().invalidate();
					break;
				}

				if (mode == MODE_OPACITY) {
					// Slide right to increase, left to decrease. Measured against the
					// previous event so hitting the clamp does not "stick".
					draggingAlpha = clampAlpha(draggingAlpha
						+ (currX - lastDragX) * ALPHA_STEP_PER_PX);
					lastDragX = currX;

					applyControlAlpha(valueMoved, draggingAlpha);
					mm.getInputView().invalidate();
					break;
				}

				// Move mode: snap-to-grid offset.
				int newXOffset = initialXOffset + (deltaX / SNAP_TO_GRID) * SNAP_TO_GRID;
				int newYOffset = initialYOffset + (deltaY / SNAP_TO_GRID) * SNAP_TO_GRID;

				valueMoved.setOffsetTMP(newXOffset, newYOffset);
				updateRelatedRects();

				if (deltaX != 0 || deltaY != 0) {
					mm.getInputView().updateImages();
					mm.getInputView().invalidate();
				}
				break;

			case MotionEvent.ACTION_POINTER_UP:
				if (event.getPointerId(pointerIndex) == activePointerId) {
					valueMoved = null;
					activePointerId = -1;
					mm.getInputView().invalidate();
				}
				break;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				valueMoved = null;
				activePointerId = -1;
				mm.getInputView().invalidate();
				break;
		}
	}

	/** Keeps a resized control within a sane range (5% .. 2000% of its base size). */
	private static float clampScale(float value) {
		// Also catches NaN from a corrupted preference.
		if (!(value >= 0.2f)) return 0.2f;
		if (value > 20.0f) return 20.0f;
		return value;
	}

	private static int clampAlpha(int value) {
		if (value < 0) return 0;
		if (value > 255) return 255;
		return value;
	}

	/**
	 * Sets the opacity of a control and of everything sharing its artwork, then
	 * refreshes the drawn images so the change is visible immediately.
	 */
	private void applyControlAlpha(InputValue iv, int alpha) {
		if (iv == null) return;

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();

		if (iv.getType() == TouchController.TYPE_BUTTON_RECT) {
			// The artwork is drawn from the matching TYPE_BUTTON_IMG entry.
			iv.setAlpha(alpha);
			for (InputValue other : values) {
				if (other.getType() == TouchController.TYPE_BUTTON_IMG && other.getValue() == iv.getValue()) {
					other.setAlpha(alpha);
					break;
				}
			}
		} else if (iv.getType() == TouchController.TYPE_STICK_IMG ||
			iv.getType() == TouchController.TYPE_ANALOG_RECT) {
			// The stick is a single visual unit, so keep every related entry equal.
			for (InputValue other : values) {
				if (other.getType() == TouchController.TYPE_STICK_RECT ||
					other.getType() == TouchController.TYPE_STICK_IMG ||
					other.getType() == TouchController.TYPE_ANALOG_RECT) {
					other.setAlpha(alpha);
				}
			}
		} else {
			iv.setAlpha(alpha);
		}

		// updateImages() also pushes the resolved alpha to the stick and the drum,
		// which draw themselves rather than using a Drawable.
		mm.getInputView().updateImages();
	}

	/**
	 * Hides or shows a control and everything sharing its artwork. Hidden controls
	 * are not drawn and do not take touches; they stay visible (dimmed) while the
	 * customizer is open so they can be brought back.
	 */
	private void setControlHidden(InputValue iv, boolean hidden) {
		if (iv == null) return;

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();

		if (iv.getType() == TouchController.TYPE_BUTTON_RECT) {
			// The artwork is drawn from the matching TYPE_BUTTON_IMG entry.
			iv.setHidden(hidden);
			for (InputValue other : values) {
				if (other.getType() == TouchController.TYPE_BUTTON_IMG && other.getValue() == iv.getValue()) {
					other.setHidden(hidden);
					break;
				}
			}
		} else if (iv.getType() == TouchController.TYPE_STICK_IMG ||
			iv.getType() == TouchController.TYPE_ANALOG_RECT) {
			// The stick is a single visual unit, so keep every related entry equal.
			for (InputValue other : values) {
				if (other.getType() == TouchController.TYPE_STICK_RECT ||
					other.getType() == TouchController.TYPE_STICK_IMG ||
					other.getType() == TouchController.TYPE_ANALOG_RECT) {
					other.setHidden(hidden);
				}
			}
		} else {
			iv.setHidden(hidden);
		}
	}

	/**
	 * Lazy-load Paint objects to ensure strict memory constraints on the UI thread.
	 */
	private void initPaints() {
		if (paintsInitialized) return;

		pButton = new Paint();
		pButton.setColor(Color.YELLOW);
		pButton.setStyle(Style.FILL);
		pButton.setAlpha(200);

		pButtonOff = new Paint();
		pButtonOff.setColor(Color.LTGRAY);
		pButtonOff.setStyle(Style.FILL);
		pButtonOff.setAlpha(200);

		// RESET throws the whole layout away, so it gets a warning colour.
		pButtonWarn = new Paint();
		pButtonWarn.setColor(Color.rgb(255, 140, 0));
		pButtonWarn.setStyle(Style.FILL);
		pButtonWarn.setAlpha(200);

		pText = new Paint();
		pText.setColor(Color.BLACK);
		pText.setTextSize(30);
		pText.setTypeface(Typeface.DEFAULT_BOLD);
		pText.setTextAlign(Paint.Align.CENTER);

		// Live opacity readout: white with a dark halo so it stays readable on any
		// game artwork underneath.
		pValue = new Paint();
		pValue.setColor(Color.WHITE);
		pValue.setTextSize(40);
		pValue.setTypeface(Typeface.DEFAULT_BOLD);
		pValue.setTextAlign(Paint.Align.CENTER);
		pValue.setShadowLayer(6f, 0f, 0f, Color.BLACK);

		pRect = new Paint();
		pRect.setARGB(30, 255, 255, 255);
		pRect.setStyle(Style.FILL);

		// Hidden controls in HIDE mode.
		pHiddenRect = new Paint();
		pHiddenRect.setARGB(70, 255, 0, 0);
		pHiddenRect.setStyle(Style.FILL);

		paintsInitialized = true;
	}

	/** Draws a button box + centred label, shrinking the text to fit. */
	private void drawButton(Canvas canvas, Rect rect, String label, boolean active) {
		drawButton(canvas, rect, label, active ? pButton : pButtonOff);
	}

	/** Same, with an explicit background (used by the destructive RESET button). */
	private void drawButton(Canvas canvas, Rect rect, String label, Paint background) {
		canvas.drawRect(rect, background);

		float oldSize = pText.getTextSize();
		float textWidth = pText.measureText(label);
		if (textWidth + (2 * TEXT_PADDING) > rect.width()) {
			float scale = (float) (rect.width() - (2 * TEXT_PADDING)) / textWidth;
			pText.setTextSize(oldSize * scale);
		}
		float textY = rect.centerY() - ((pText.descent() + pText.ascent()) / 2);
		canvas.drawText(label, rect.centerX(), textY, pText);
		pText.setTextSize(oldSize);
	}

	public void draw(Canvas canvas) {
		if (canvas == null) return;

		// Ensure rendering tools are primed
		initPaints();

		// Render the semi-transparent hitboxes over the interactive controls
		ArrayList<InputValue> ids = mm.getInputHandler().getTouchController().getAllInputData();

		for (InputValue v : ids) {
			Rect r = v.getRect();
			if (r != null) {
				boolean draw = false;
				int type = v.getType();
				int controllerType = mm.getPrefsHelper().getControllerType();

				if (type == TouchController.TYPE_BUTTON_RECT) {
					draw = true;
				} else if (controllerType == PrefsHelper.PREF_DIGITAL_DPAD && type == TouchController.TYPE_STICK_RECT) {
					draw = true;
				} else if (controllerType != PrefsHelper.PREF_DIGITAL_DPAD && type == TouchController.TYPE_ANALOG_RECT) {
					draw = true;
				} else if (type == TouchController.TYPE_TATACON_RECT) {
					draw = true;
				} else if (type == TouchController.TYPE_MAHJONG_IMG) {
					draw = true;
				}

				// Only the controls of the active panel get a hitbox: an invisible
				// control must not look grabbable either.
				if (draw && isEditable(v)) {
					// Hidden controls get a red box so they are easy to spot while
					// the customizer is open.
					canvas.drawRect(r, v.isHidden() ? pHiddenRect : pRect);
				}
			}
		}

		// Edit modes and SAVE on the first row, one reset per panel on the second.
		// Both hug the top edge so they never sit on the controls being edited.
		int top = BUTTON_MARGIN;
		int bottom = top + BUTTON_HEIGHT;

		int btnW = Math.min(BUTTON_WIDTH, (canvas.getWidth() - 6 * BUTTON_MARGIN) / 5);
		int step = btnW + BUTTON_MARGIN;
		int startX = (canvas.getWidth() - (btnW * 5 + 4 * BUTTON_MARGIN)) / 2;

		moveButtonRect.set(startX, top, startX + btnW, bottom);
		resizeButtonRect.set(startX + step, top, startX + step + btnW, bottom);
		opacityButtonRect.set(startX + 2 * step, top, startX + 2 * step + btnW, bottom);
		hideButtonRect.set(startX + 3 * step, top, startX + 3 * step + btnW, bottom);
		saveButtonRect.set(startX + 4 * step, top, startX + 4 * step + btnW, bottom);

		drawButton(canvas, moveButtonRect, "MOVE", mode == MODE_MOVE);
		drawButton(canvas, resizeButtonRect, "RESIZE", mode == MODE_RESIZE);
		drawButton(canvas, opacityButtonRect, "OPACITY", mode == MODE_OPACITY);
		drawButton(canvas, hideButtonRect, "HIDE", mode == MODE_HIDE);
		drawButton(canvas, saveButtonRect, "SAVE", true);

		int rTop = bottom + BUTTON_MARGIN;
		int rBottom = rTop + BUTTON_HEIGHT;
		int rW = Math.min(BUTTON_WIDTH, (canvas.getWidth() - 5 * BUTTON_MARGIN) / 4);
		int rStep = rW + BUTTON_MARGIN;
		int rStart = (canvas.getWidth() - (rW * 4 + 3 * BUTTON_MARGIN)) / 2;

		resetControlsButtonRect.set(rStart, rTop, rStart + rW, rBottom);
		resetMahjongButtonRect.set(rStart + rStep, rTop, rStart + rStep + rW, rBottom);
		resetTaikoButtonRect.set(rStart + 2 * rStep, rTop, rStart + 2 * rStep + rW, rBottom);
		resetAllButtonRect.set(rStart + 3 * rStep, rTop, rStart + 3 * rStep + rW, rBottom);

		// A reset button is greyed out when its panel is not on screen, so it is
		// clear that tapping it would have nothing to put back.
		drawButton(canvas, resetControlsButtonRect, "RESET CTRL",
			isGroupEditable(GROUP_CONTROLS) ? pButtonWarn : pButtonOff);
		drawButton(canvas, resetMahjongButtonRect, "RESET MJ",
			isGroupEditable(GROUP_MAHJONG) ? pButtonWarn : pButtonOff);
		drawButton(canvas, resetTaikoButtonRect, "RESET TAIKO",
			isGroupEditable(GROUP_TAIKO) ? pButtonWarn : pButtonOff);
		drawButton(canvas, resetAllButtonRect, "RESET ALL", pButtonWarn);

		// While editing opacity, show the live percentage on the control being
		// dragged so the value is not guesswork.
		if (mode == MODE_OPACITY && valueMoved != null) {
			Rect r = valueMoved.getRect();
			int percent = Math.round(draggingAlpha * 100f / 255f);
			float textY = r.centerY() - ((pValue.descent() + pValue.ascent()) / 2);
			canvas.drawText(percent + "%", r.centerX(), textY, pValue);
		} else if (mode == MODE_RESIZE && valueMoved != null) {
			// Same idea for the size: X and Y percentages, relative to the base size.
			Rect r = valueMoved.getRect();
			int percentX = Math.round(valueMoved.getScaleX() * 100f);
			int percentY = Math.round(valueMoved.getScaleY() * 100f);
			float textY = r.centerY() - ((pValue.descent() + pValue.ascent()) / 2);
			canvas.drawText(percentX + "% x " + percentY + "%", r.centerX(), textY, pValue);
		}
	}
}

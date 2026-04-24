package com.openggf.sprites;

import org.apache.commons.lang3.StringUtils;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;

public abstract class AbstractSprite implements Sprite {
	protected final SonicConfigurationService configService = com.openggf.game.RuntimeManager
			.getEngineServices().configuration();
	protected final GraphicsManager graphicsManager = com.openggf.game.RuntimeManager
			.getEngineServices().graphics();

	protected String code;

	protected short xPixel;
	protected short yPixel;

	// ROM uses 16:16 fixed-point (16-bit subpixel). Storing full 16-bit fraction
	// to prevent cumulative carry errors vs the ROM's 32-bit position arithmetic.
	protected short xSubpixel;
	protected short ySubpixel;

	protected int width;
	protected int height;

	protected Sensor[] pushSensors;
	protected Sensor[] groundSensors;
	protected Sensor[] ceilingSensors;

	protected byte gravity = 56;

	protected Direction direction;

	protected byte layer = 0;

	protected AbstractSprite(String code, short xPixel, short yPixel) {
		this.code = code;
		this.xPixel = xPixel;
		this.yPixel = yPixel;
		direction = Direction.RIGHT;
		createSensorLines();
	}

	protected AbstractSprite(String code, short xPixel, short yPixel,
			Direction direction) {
		this(code, xPixel, yPixel);
		this.direction = direction;
	}

	public final String getCode() {
		return (code != null) ? code : StringUtils.EMPTY;
	}

	public final void setCode(String code) {
		this.code = code;
	}

	public final short getCentreX() {
		return (short) (xPixel + (width / 2));
	}

	public final short getCentreY() {
		return (short) (yPixel + (height / 2));
	}

	public void setCentreX(short x) {
		this.xPixel = (short) (x - (width / 2));
		this.xSubpixel = (short) 0;
	}

	public void setCentreY(short y) {
		short beforeY = this.yPixel;
		short beforeCentreY = getCentreY();
		this.yPixel = (short) (y - (height / 2));
		this.ySubpixel = (short) 0;
		traceS3kAizYProbe("setCentreY", beforeY, beforeCentreY);
	}

	/**
	 * Sets the ROM centre X while preserving the current subpixel fraction.
	 * Mirrors a 68000 {@code move.w} to {@code x_pos}, which does not touch
	 * the low 16-bit subpixel field.
	 */
	public void setCentreXPreserveSubpixel(short x) {
		this.xPixel = (short) (x - (width / 2));
	}

	/**
	 * Sets the ROM centre Y while preserving the current subpixel fraction.
	 * Mirrors a 68000 {@code move.w} to {@code y_pos}, which does not touch
	 * the low 16-bit subpixel field.
	 */
	public void setCentreYPreserveSubpixel(short y) {
		short beforeY = this.yPixel;
		short beforeCentreY = getCentreY();
		this.yPixel = (short) (y - (height / 2));
		traceS3kAizYProbe("setCentreYPreserveSubpixel", beforeY, beforeCentreY);
	}

	public final short getX() {
		// SPG: Collision calculations use whole pixel position, ignoring subpixels.
		// No rounding should be done - the pixel position is authoritative.
		return xPixel;
	}

	public final void setX(short x) {
		if (x < 0) {
			this.xPixel = 0;
			this.xSubpixel = 0; // Boundary clamp: full reset
		} else {
			this.xPixel = x;
			// ROM-accurate: move.w to x_pos does not touch x_sub.
			// Subpixel fraction is preserved across position updates.
		}
	}

	public final short getY() {
		// SPG: Collision calculations use whole pixel position, ignoring subpixels.
		// No rounding should be done - the pixel position is authoritative.
		return yPixel;
	}

	public short getBottomY() {
		return (short) (getCentreY() - (getHeight() / 2));
	}

	public short getTopY() {
		return (short) (getCentreY() + (getHeight() / 2));
	}

	public short getLeftX() {
		return (short) (getCentreX() - (getWidth() / 2));
	}

	public short getRightX() {
		return (short) (getCentreX() + (getWidth() / 2));
	}

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	public final void setY(short y) {
		short beforeY = this.yPixel;
		short beforeCentreY = getCentreY();
		this.yPixel = y;
		// ROM-accurate: move.w to y_pos does not touch y_sub.
		// Subpixel fraction is preserved across position updates.
		traceS3kAizYProbe("setY", beforeY, beforeCentreY);
	}

	private void traceS3kAizYProbe(String op, short beforeY, short beforeCentreY) {
		if (!Boolean.getBoolean("s3k.aiz.yprobe")) {
			return;
		}
		if (!(this instanceof com.openggf.sprites.playable.AbstractPlayableSprite)) {
			return;
		}
		com.openggf.level.LevelManager levelManager = com.openggf.game.GameServices.level();
		if (levelManager == null || levelManager.getObjectManager() == null) {
			return;
		}
		int frameCounter = levelManager.getObjectManager().getFrameCounter();
		int beforeCentreX = getCentreX() & 0xFFFF;
		int afterCentreX = getCentreX() & 0xFFFF;
		int afterCentreY = getCentreY() & 0xFFFF;
		int minY = Integer.getInteger("s3k.aiz.yprobe.minY", 0x0380);
		int minX = Integer.getInteger("s3k.aiz.yprobe.minX", 0x1900);
		int maxX = Integer.getInteger("s3k.aiz.yprobe.maxX", 0x1990);
		if (Math.max(beforeCentreX, afterCentreX) < minX
				|| Math.min(beforeCentreX, afterCentreX) > maxX
				|| Math.max(beforeCentreY & 0xFFFF, afterCentreY) < minY
				|| Math.min(beforeCentreY & 0xFFFF, afterCentreY) > 0x03E0) {
			return;
		}
		if (beforeY == this.yPixel && beforeCentreY == getCentreY()) {
			return;
		}
		StackTraceElement caller = null;
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		for (StackTraceElement element : stack) {
			String className = element.getClassName();
			if (!className.equals(AbstractSprite.class.getName())
					&& !className.equals(Thread.class.getName())) {
				caller = element;
				break;
			}
		}
		String callerSummary = caller == null
				? "<unknown>"
				: caller.getClassName() + "#" + caller.getMethodName() + ":" + caller.getLineNumber();
		System.out.printf(
				"s3k-aiz-yprobe frame=%d caller=%s op=%s centre=(%04X,%04X)->(%04X,%04X) y=%04X->%04X sub=%04X%n",
				frameCounter,
				callerSummary,
				op,
				beforeCentreX,
				beforeCentreY & 0xFFFF,
				getCentreX() & 0xFFFF,
				afterCentreY,
				beforeY & 0xFFFF,
				this.yPixel & 0xFFFF,
				this.ySubpixel & 0xFFFF);
	}

	/**
	 * Shifts the pixel X position by a delta without zeroing subpixels.
	 * ROM-accurate: matches 68000 {@code sub.w d2, obX(a1)} which modifies
	 * only the pixel word, preserving any accumulated subpixel fraction.
	 * Used by platform riding code (MvSonicOnPtfm / MvSonicOnPtfm2).
	 */
	public final void shiftX(int delta) {
		this.xPixel += delta;
	}

	/**
	 * Shifts the pixel Y position by a delta without zeroing subpixels.
	 * ROM-accurate: matches 68000 position word modification that preserves
	 * subpixel fraction. Used by platform riding code.
	 */
	public final void shiftY(int delta) {
		this.yPixel += delta;
	}

	public final void move(short xSpeed, short ySpeed) {
		// ROM-accurate 16:16 fixed-point position update (SpeedToPos).
		// ROM: move.l obX(a0),d2 — loads 32-bit value (pixel:16 | subpixel:16)
		//      ext.l d0 / asl.l #8,d0 — velocity * 256, sign-extended to 32-bit
		//      add.l d0,d2 — 32-bit add preserving all 16 subpixel bits
		//      move.l d2,obX(a0) — stores back full 32-bit value
		int xPos = (xPixel << 16) | (xSubpixel & 0xFFFF);
		int yPos = (yPixel << 16) | (ySubpixel & 0xFFFF);

		xPos += (int) xSpeed << 8;
		yPos += (int) ySpeed << 8;

		xPixel = (short) (xPos >> 16);
		xSubpixel = (short) (xPos & 0xFFFF);

		yPixel = (short) (yPos >> 16);
		ySubpixel = (short) (yPos & 0xFFFF);
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public float getGravity() {
		return gravity;
	}

	/**
	 * Returns the high 8 bits of the 16-bit subpixel fraction.
	 * This is the portion that corresponds to 1/256 pixel units
	 * (the same units as velocity). Callers using {@code & 0xFF}
	 * get the same value as before the 16:16 upgrade.
	 */
	public byte getXSubpixel() {
		return (byte) (xSubpixel >> 8);
	}

	/**
	 * Returns the high 8 bits of the 16-bit subpixel fraction.
	 * @see #getXSubpixel()
	 */
	public byte getYSubpixel() {
		return (byte) (ySubpixel >> 8);
	}

	/** Returns the full 16-bit subpixel value (for diagnostic comparison). */
	public int getXSubpixelRaw() {
		return xSubpixel & 0xFFFF;
	}

	/** Returns the full 16-bit subpixel value (for diagnostic comparison). */
	public int getYSubpixelRaw() {
		return ySubpixel & 0xFFFF;
	}

	/**
	 * Restores the full 16-bit subpixel fraction directly.
	 * Used by trace/bootstrap code that needs ROM-accurate sprite state hydration.
	 */
	public void setSubpixelRaw(int xSubpixel, int ySubpixel) {
		this.xSubpixel = (short) xSubpixel;
		this.ySubpixel = (short) ySubpixel;
	}

	public Sensor[] getPushSensors() {
		return pushSensors;
	}

	public void setPushSensors(Sensor[] pushSensors) {
		this.pushSensors = pushSensors;
	}

	public Sensor[] getGroundSensors() {
		return groundSensors;
	}

	public void setGroundSensors(Sensor[] groundSensors) {
		this.groundSensors = groundSensors;
	}

	public Sensor[] getCeilingSensors() {
		return ceilingSensors;
	}

	public void setCeilingSensors(Sensor[] ceilingSensors) {
		this.ceilingSensors = ceilingSensors;
	}

	protected abstract void createSensorLines();

	public void setLayer(byte layer) {
		this.layer = layer;
	}

	public byte getLayer() {
		return layer;
	}
}

package com.openggf.sprites;

import org.apache.commons.lang3.StringUtils;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;

public abstract class AbstractSprite implements Sprite {
	protected final SonicConfigurationService configService = SonicConfigurationService
			.getInstance();
	protected final GraphicsManager graphicsManager = GraphicsManager
			.getInstance();

	protected String code;

	protected short xPixel;
	protected short yPixel;

	protected byte xSubpixel;
	protected byte ySubpixel;

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
		this.yPixel = (short) (y - (height / 2));
		this.ySubpixel = (short) 0;
	}

	public final short getX() {
		// SPG: Collision calculations use whole pixel position, ignoring subpixels.
		// No rounding should be done - the pixel position is authoritative.
		return xPixel;
	}

	public final void setX(short x) {
		if (x < 0) {
			this.xPixel = 0;
		} else {
			this.xPixel = x;
		}
		this.xSubpixel = 0;
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
		this.yPixel = y;
		this.ySubpixel = 0;
	}

	public final void move(short xSpeed, short ySpeed) {
		/*
		 * Speeds are provied in subpixels, need to convert current
		 * Pixel/Subpixel values to subpixels, add our speeds and convert back.
		 */
		long xTotal = (xPixel * 256) + (xSubpixel & 0xFF);
		long yTotal = (yPixel * 256) + (ySubpixel & 0xFF);

		xTotal += xSpeed;
		yTotal += ySpeed;

		// ROM-accurate: 68000 asr.l #8 rounds toward negative infinity
		// Java / rounds toward zero, which causes drift when moving left/up
		short updatedXPixel = (short) (xTotal >> 8);
		short updatedYPixel = (short) (yTotal >> 8);

		byte updatedXSubpixel = (byte) (xTotal & 0xFF);
		byte updatedYSubpixel = (byte) (yTotal & 0xFF);

		xPixel = updatedXPixel;
		xSubpixel = updatedXSubpixel;

		// ROM: Y is signed 16-bit — sprites can have negative Y (above camera view).
		// Camera handles its own Y clamping via minY; sprites are not restricted.
		yPixel = updatedYPixel;
		ySubpixel = updatedYSubpixel;

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

	public byte getXSubpixel() {
		return xSubpixel;
	}

	public byte getYSubpixel() {
		return ySubpixel;
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

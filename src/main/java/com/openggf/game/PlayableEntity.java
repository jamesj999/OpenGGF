package com.openggf.game;

import com.openggf.physics.Direction;

/**
 * Abstraction over the playable sprite that game objects interact with.
 * <p>
 * This interface decouples {@code level.objects} from the concrete
 * {@code sprites.playable.AbstractPlayableSprite} hierarchy, breaking
 * the circular dependency between the two packages. All methods mirror
 * the existing public API on {@code AbstractPlayableSprite} (or its
 * parent {@code AbstractSprite}).
 */
public interface PlayableEntity {

    // ── Position ────────────────────────────────────────────────────
    short getCentreX();
    short getCentreY();
    void setCentreX(short x);
    short getX();
    short getY();
    void setY(short y);
    void shiftX(int delta);
    void move(short xSpeed, short ySpeed);

    /** No-arg move using internal xSpeed/ySpeed. */
    void move();

    /** Centre X from N frames ago (position history ring buffer). */
    short getCentreX(int framesBehind);

    /** Centre Y from N frames ago (position history ring buffer). */
    short getCentreY(int framesBehind);

    // ── Dimensions ──────────────────────────────────────────────────
    int getHeight();
    short getYRadius();
    short getXRadius();
    short getRollHeightAdjustment();

    // ── Physics ─────────────────────────────────────────────────────
    short getXSpeed();
    short getYSpeed();
    void setXSpeed(short xSpeed);
    void setYSpeed(short ySpeed);
    short getGSpeed();
    void setGSpeed(short gSpeed);
    boolean getAir();
    void setAir(boolean air);
    byte getAngle();
    void setAngle(byte angle);

    // ── Ground mode ─────────────────────────────────────────────────
    boolean getRolling();
    void setRolling(boolean rolling);
    boolean getSpindash();
    GroundMode getGroundMode();
    void setGroundMode(GroundMode groundMode);

    // ── Object interaction ──────────────────────────────────────────
    boolean isObjectControlled();
    void setOnObject(boolean onObject);
    void setPushing(boolean pushing);
    boolean getPinballMode();
    boolean isCpuControlled();

    // ── Collision path ──────────────────────────────────────────────
    void setTopSolidBit(byte topSolidBit);
    void setLrbSolidBit(byte lrbSolidBit);
    void setLayer(byte layer);
    boolean isHighPriority();
    void setHighPriority(boolean highPriority);

    // ── Vulnerability ───────────────────────────────────────────────
    boolean getDead();
    boolean isDebugMode();
    boolean getInvulnerable();
    boolean hasShield();
    ShieldType getShieldType();
    int getInvincibleFrames();
    int getDoubleJumpFlag();
    boolean isSuperSonic();

    // ── Damage ──────────────────────────────────────────────────────
    boolean getCrouching();
    int getRingCount();
    Direction getDirection();
    boolean applyHurt(int sourceX);
    boolean applyHurt(int sourceX, boolean spikeHit);
    boolean applyHurt(int sourceX, DamageCause cause);
    boolean applyHurtOrDeath(int sourceX, boolean spikeHit, boolean hadRings);
    boolean applyHurtOrDeath(int sourceX, DamageCause cause, boolean hadRings);
    boolean applyCrushDeath();

    // ── Scoring ─────────────────────────────────────────────────────
    int incrementBadnikChain();

    // ── Feature set ─────────────────────────────────────────────────
    PhysicsFeatureSet getPhysicsFeatureSet();
}

package com.openggf.level;

/**
 * In-place transition request for seamless level events.
 */
public final class SeamlessLevelTransitionRequest {
    public enum TransitionType {
        MUTATE_ONLY,
        RELOAD_SAME_LEVEL,
        RELOAD_TARGET_LEVEL
    }

    private final TransitionType type;
    private final int targetZone;
    private final int targetAct;
    private final boolean deactivateLevelNow;
    private final boolean preserveMusic;
    private final boolean preserveLevelGamestate;
    private final boolean showInLevelTitleCard;
    private final boolean forceAirOnStaleObjectSupportLoss;
    private final boolean preserveOffsetCameraPosition;
    private final Integer postTransitionMinY;
    private final Integer postTransitionMaxYTarget;
    private final int playerOffsetX;
    private final int playerOffsetY;
    private final int cameraOffsetX;
    private final int cameraOffsetY;
    private final String mutationKey;
    private final int musicOverrideId;

    private SeamlessLevelTransitionRequest(Builder builder) {
        this.type = builder.type;
        this.targetZone = builder.targetZone;
        this.targetAct = builder.targetAct;
        this.deactivateLevelNow = builder.deactivateLevelNow;
        this.preserveMusic = builder.preserveMusic;
        this.preserveLevelGamestate = builder.preserveLevelGamestate;
        this.showInLevelTitleCard = builder.showInLevelTitleCard;
        this.forceAirOnStaleObjectSupportLoss = builder.forceAirOnStaleObjectSupportLoss;
        this.preserveOffsetCameraPosition = builder.preserveOffsetCameraPosition;
        this.postTransitionMinY = builder.postTransitionMinY;
        this.postTransitionMaxYTarget = builder.postTransitionMaxYTarget;
        this.playerOffsetX = builder.playerOffsetX;
        this.playerOffsetY = builder.playerOffsetY;
        this.cameraOffsetX = builder.cameraOffsetX;
        this.cameraOffsetY = builder.cameraOffsetY;
        this.mutationKey = builder.mutationKey;
        this.musicOverrideId = builder.musicOverrideId;
    }

    public TransitionType type() {
        return type;
    }

    public int targetZone() {
        return targetZone;
    }

    public int targetAct() {
        return targetAct;
    }

    public boolean deactivateLevelNow() {
        return deactivateLevelNow;
    }

    public boolean preserveMusic() {
        return preserveMusic;
    }

    public boolean preserveLevelGamestate() {
        return preserveLevelGamestate;
    }

    public boolean showInLevelTitleCard() {
        return showInLevelTitleCard;
    }

    public boolean forceAirOnStaleObjectSupportLoss() {
        return forceAirOnStaleObjectSupportLoss;
    }

    public boolean preserveOffsetCameraPosition() {
        return preserveOffsetCameraPosition;
    }

    public Integer postTransitionMinY() {
        return postTransitionMinY;
    }

    public Integer postTransitionMaxYTarget() {
        return postTransitionMaxYTarget;
    }

    public int playerOffsetX() {
        return playerOffsetX;
    }

    public int playerOffsetY() {
        return playerOffsetY;
    }

    public int cameraOffsetX() {
        return cameraOffsetX;
    }

    public int cameraOffsetY() {
        return cameraOffsetY;
    }

    public String mutationKey() {
        return mutationKey;
    }

    public int musicOverrideId() {
        return musicOverrideId;
    }

    public static Builder builder(TransitionType type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final TransitionType type;
        private int targetZone = -1;
        private int targetAct = -1;
        private boolean deactivateLevelNow;
        private boolean preserveMusic = true;
        private boolean preserveLevelGamestate;
        private boolean showInLevelTitleCard;
        private boolean forceAirOnStaleObjectSupportLoss;
        private boolean preserveOffsetCameraPosition;
        private Integer postTransitionMinY;
        private Integer postTransitionMaxYTarget;
        private int playerOffsetX;
        private int playerOffsetY;
        private int cameraOffsetX;
        private int cameraOffsetY;
        private String mutationKey;
        private int musicOverrideId = -1;

        private Builder(TransitionType type) {
            this.type = type;
        }

        public Builder targetZoneAct(int zone, int act) {
            this.targetZone = zone;
            this.targetAct = act;
            return this;
        }

        public Builder deactivateLevelNow(boolean deactivateLevelNow) {
            this.deactivateLevelNow = deactivateLevelNow;
            return this;
        }

        public Builder preserveMusic(boolean preserveMusic) {
            this.preserveMusic = preserveMusic;
            return this;
        }

        public Builder preserveLevelGamestate(boolean preserveLevelGamestate) {
            this.preserveLevelGamestate = preserveLevelGamestate;
            return this;
        }

        public Builder showInLevelTitleCard(boolean showInLevelTitleCard) {
            this.showInLevelTitleCard = showInLevelTitleCard;
            return this;
        }

        public Builder forceAirOnStaleObjectSupportLoss(boolean forceAirOnStaleObjectSupportLoss) {
            this.forceAirOnStaleObjectSupportLoss = forceAirOnStaleObjectSupportLoss;
            return this;
        }

        public Builder preserveOffsetCameraPosition(boolean preserveOffsetCameraPosition) {
            this.preserveOffsetCameraPosition = preserveOffsetCameraPosition;
            return this;
        }

        public Builder postTransitionMinY(int minY) {
            this.postTransitionMinY = minY;
            return this;
        }

        public Builder postTransitionMinYIfPresent(Integer minY) {
            this.postTransitionMinY = minY;
            return this;
        }

        public Builder postTransitionMaxYTarget(int maxYTarget) {
            this.postTransitionMaxYTarget = maxYTarget;
            return this;
        }

        public Builder postTransitionMaxYTargetIfPresent(Integer maxYTarget) {
            this.postTransitionMaxYTarget = maxYTarget;
            return this;
        }

        public Builder playerOffset(int x, int y) {
            this.playerOffsetX = x;
            this.playerOffsetY = y;
            return this;
        }

        public Builder cameraOffset(int x, int y) {
            this.cameraOffsetX = x;
            this.cameraOffsetY = y;
            return this;
        }

        public Builder mutationKey(String mutationKey) {
            this.mutationKey = mutationKey;
            return this;
        }

        public Builder musicOverrideId(int musicOverrideId) {
            this.musicOverrideId = musicOverrideId;
            return this;
        }

        public SeamlessLevelTransitionRequest build() {
            return new SeamlessLevelTransitionRequest(this);
        }
    }
}

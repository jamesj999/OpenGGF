package uk.co.jamesj999.sonic.level.objects;

public record SolidContact(boolean standing, boolean touchSide, boolean touchBottom, boolean touchTop, boolean pushing) {
    static final SolidContact NONE = new SolidContact(false, false, false, false, false);
    static final SolidContact STANDING = new SolidContact(true, false, false, true, false);
    static final SolidContact SIDE_NO_PUSH = new SolidContact(false, true, false, false, false);
    static final SolidContact SIDE_PUSH = new SolidContact(false, true, false, false, true);
    static final SolidContact CEILING = new SolidContact(false, false, true, false, false);
}

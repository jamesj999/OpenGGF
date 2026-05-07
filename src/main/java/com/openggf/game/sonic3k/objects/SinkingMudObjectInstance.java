package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Object 0x4F - SinkingMud (Sonic 3 & Knuckles).
 *
 * <p>Invisible top-solid mud/quicksand volume used in MGZ and competition zones.
 * Width is {@code subtype << 4} pixels; the surface starts at a half-height of
 * {@code $30} in single-player and {@code $18} in competition mode. While a player
 * stands on the mud, their personal surface depth decreases by 1 per frame. When
 * they step off, it recovers by 2 per frame. If the surface is already exhausted at
 * the start of a standing frame, the player is killed and the depth resets.
 *
 * <p>ROM: {@code Obj_SinkingMud} / {@code SolidObjectTop_1P} (sonic3k.asm:68500-68661)
 */
public class SinkingMudObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int PRIORITY = 4;
    private static final int MAX_RAW_SURFACE = 0x30;

    private static final float DEBUG_R = 1.0f;
    private static final float DEBUG_G = 1.0f;
    private static final float DEBUG_B = 1.0f;

    private final int halfWidth;
    private final Map<PlayableEntity, Integer> rawSurfaceByPlayer = new IdentityHashMap<>();
    private final Map<PlayableEntity, Boolean> standingNextUpdate = new IdentityHashMap<>();
    private final Set<PlayableEntity> killedThisFrame =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private List<PlayableEntity> trackedPlayers = List.of();

    public SinkingMudObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SinkingMud");
        this.halfWidth = (spawn.subtype() & 0xFF) << 3;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        int surface = sharedSurfaceHeight();
        return new SolidObjectParams(halfWidth, surface, surface);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return player != null && !player.getDead() && !killedThisFrame.contains(player);
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (player == null || !contact.standing() || killedThisFrame.contains(player)) {
            return;
        }
        standingNextUpdate.put(player, true);
        snapPlayerToOwnSurface(player);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        killedThisFrame.clear();
        trackedPlayers = collectPlayers(player);
        for (PlayableEntity entity : trackedPlayers) {
            advancePlayerSurface(entity);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!isDebugViewEnabled() || halfWidth <= 0) {
            return;
        }

        int fullHeight = isCompetitionZone() ? 0x30 : 0x60;
        int left = getX() - halfWidth;
        int right = getX() + halfWidth;
        int top = getY() - (fullHeight / 2);
        int bottom = getY() + (fullHeight / 2);

        appendLine(commands, left, top, right, top);
        appendLine(commands, right, top, right, bottom);
        appendLine(commands, right, bottom, left, bottom);
        appendLine(commands, left, bottom, left, top);
    }

    int rawSurfaceForTest(PlayableEntity player) {
        return rawSurfaceByPlayer.getOrDefault(player, MAX_RAW_SURFACE);
    }

    private void advancePlayerSurface(PlayableEntity player) {
        boolean wasStanding = standingNextUpdate.getOrDefault(player, false);
        standingNextUpdate.put(player, false);

        int rawSurface = rawSurfaceByPlayer.getOrDefault(player, MAX_RAW_SURFACE);
        if (wasStanding) {
            if (rawSurface == 0) {
                rawSurfaceByPlayer.put(player, MAX_RAW_SURFACE);
                killedThisFrame.add(player);
                player.applyCrushDeath();
                ObjectServices svc = tryServices();
                if (svc != null) {
                    ObjectManager objectManager = svc.objectManager();
                    if (objectManager != null) {
                        objectManager.clearRidingObject(player);
                    }
                }
                return;
            }
            rawSurface--;
        } else {
            Integer copiedSurface = copiedSurfaceFromAdjacentMud(player);
            if (copiedSurface != null) {
                rawSurface = copiedSurface;
            } else {
                rawSurface = Math.min(MAX_RAW_SURFACE, rawSurface + 2);
            }
        }
        rawSurfaceByPlayer.put(player, rawSurface);
    }

    private Integer copiedSurfaceFromAdjacentMud(PlayableEntity player) {
        if (player == null || !player.isOnObject()) {
            return null;
        }
        ObjectServices svc = tryServices();
        if (svc == null || svc.objectManager() == null) {
            return null;
        }

        ObjectInstance riding = svc.objectManager().getRidingObject(player);
        if (riding instanceof SinkingMudObjectInstance mud && riding != this) {
            return mud.rawSurfaceByPlayer.getOrDefault(player, MAX_RAW_SURFACE);
        }
        return null;
    }

    private void snapPlayerToOwnSurface(PlayableEntity player) {
        int surface = collisionSurfaceHeight(rawSurfaceByPlayer.getOrDefault(player, MAX_RAW_SURFACE));
        int newCentreY = getY() - surface - player.getYRadius();
        int newY = newCentreY - (player.getHeight() / 2);
        player.setY((short) newY);
    }

    private int sharedSurfaceHeight() {
        if (trackedPlayers.isEmpty()) {
            return collisionSurfaceHeight(MAX_RAW_SURFACE);
        }

        int highestSurface = 0;
        for (PlayableEntity player : trackedPlayers) {
            int rawSurface = rawSurfaceByPlayer.getOrDefault(player, MAX_RAW_SURFACE);
            highestSurface = Math.max(highestSurface, collisionSurfaceHeight(rawSurface));
        }
        return highestSurface;
    }

    private int collisionSurfaceHeight(int rawSurface) {
        return isCompetitionZone() ? (rawSurface >> 1) : rawSurface;
    }

    private boolean isCompetitionZone() {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return false;
        }
        int zoneId = svc.romZoneId();
        return zoneId >= Sonic3kZoneIds.ZONE_ALZ && zoneId <= Sonic3kZoneIds.ZONE_EMZ;
    }

    private List<PlayableEntity> collectPlayers(PlayableEntity mainPlayer) {
        List<PlayableEntity> players = new ArrayList<>(4);
        if (mainPlayer != null) {
            players.add(mainPlayer);
        }

        ObjectServices svc = tryServices();
        if (svc != null) {
            for (PlayableEntity sidekick : svc.sidekicks()) {
                if (sidekick != null && sidekick != mainPlayer) {
                    players.add(sidekick);
                }
            }
        }
        return players;
    }

    private boolean isDebugViewEnabled() {
        ObjectServices svc = tryServices();
        return svc != null
                && svc.configuration() != null
                && svc.configuration().getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                DEBUG_R, DEBUG_G, DEBUG_B, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                DEBUG_R, DEBUG_G, DEBUG_B, x2, y2, 0, 0));
    }
}

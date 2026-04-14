package com.openggf.game.sonic3k.dataselect;

import java.util.List;

/**
 * Documents the shape of an S3K save payload.
 * Used as a reference for serialization structure; the actual save
 * writes a {@code Map<String, Object>} via {@link S3kSaveSnapshotProvider}.
 *
 * @param zone          the current zone index
 * @param act           the current act index
 * @param mainCharacter the main playable character identifier
 * @param sidekicks     list of sidekick character identifiers
 * @param lives         the player's remaining lives
 * @param continues     the player's remaining continues
 * @param chaosEmeralds collected chaos emerald indices
 * @param superEmeralds collected super emerald indices
 * @param clear         whether this slot represents a completed game
 */
public record S3kSavePayload(
        int zone,
        int act,
        String mainCharacter,
        List<String> sidekicks,
        int lives,
        int continues,
        List<Integer> chaosEmeralds,
        List<Integer> superEmeralds,
        boolean clear
) {}

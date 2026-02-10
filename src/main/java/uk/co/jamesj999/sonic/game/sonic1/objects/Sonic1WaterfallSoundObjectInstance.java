package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Object 0x49 - Waterfall Sound.
 * <p>
 * Invisible object placed near waterfalls in GHZ. Plays the waterfall
 * sound effect (sfx_Waterfall, 0xD0) every 64 frames (~1.07 seconds).
 * <p>
 * From disassembly (_incObj/49 Waterfall Sound.asm):
 * <pre>
 *   WSnd_PlaySnd:
 *       move.b  (v_vbla_byte).w,d0
 *       andi.b  #$3F,d0
 *       bne.s   WSnd_ChkDel
 *       move.w  #sfx_Waterfall,d0
 *       jsr     (PlaySound_Special).l
 * </pre>
 */
public class Sonic1WaterfallSoundObjectInstance extends AbstractObjectInstance {

    // From disassembly: andi.b #$3F,d0 — play every 64 frames
    private static final int PLAY_INTERVAL_MASK = 0x3F;

    public Sonic1WaterfallSoundObjectInstance(ObjectSpawn spawn) {
        super(spawn, "WaterfallSound");
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // WSnd_PlaySnd: play waterfall SFX every 64 frames
        if ((frameCounter & PLAY_INTERVAL_MASK) == 0) {
            AudioManager.getInstance().playSfx(Sonic1Sfx.WATERFALL.id);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible object — no rendering
    }
}

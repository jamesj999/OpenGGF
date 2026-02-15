package uk.co.jamesj999.sonic.game.sonic3k.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kLoadBootstrap;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import java.util.logging.Logger;

/**
 * Angel Island Zone dynamic level events.
 * ROM: ScreenEvent_AIZ (sonic3k.asm)
 *
 * Act 1: Intro cinematic (when bootstrap is not skipping intro + Sonic character).
 * Act 2: Boss arena + fire transition (future work).
 */
public class Sonic3kAIZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kAIZEvents.class.getName());

    private final Sonic3kLoadBootstrap bootstrap;
    private boolean introSpawned;

    public Sonic3kAIZEvents(Camera camera, Sonic3kLoadBootstrap bootstrap) {
        super(camera);
        this.bootstrap = bootstrap;
    }

    @Override
    public void init(int act) {
        super.init(act);
        introSpawned = false;
        if (act == 0) {
            AizPlaneIntroInstance.resetIntroPhaseState();
        }
        if (shouldSpawnIntro(act)) {
            LOG.info("AIZ1 intro: will spawn intro object");
        }
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0 && shouldSpawnIntro(act)) {
            // ROM parity: the AIZ1 transition check (camera X >= $1400) belongs to
            // the level resize/event flow, not the plane object's lifetime.
            AizPlaneIntroInstance.updateMainLevelPhaseForCameraX(camera.getX());
        }
        if (act == 0 && !introSpawned && shouldSpawnIntro(act)) {
            spawnIntroObject();
            introSpawned = true;
        }
    }

    /**
     * Returns whether the intro cinematic should be spawned for the given act.
     * The intro only runs on Act 1 (act==0) when the bootstrap is not
     * skipping the intro (i.e., a fresh game start, not an intro-skip scenario).
     *
     * Package-private for test access.
     */
    boolean shouldSpawnIntro(int act) {
        return act == 0 && !bootstrap.isSkipIntro();
    }

    private void spawnIntroObject() {
        ObjectSpawn spawn = new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0);
        AizPlaneIntroInstance intro = new AizPlaneIntroInstance(spawn);
        spawnObject(intro);
        LOG.info("AIZ1 intro: spawned plane intro object");
    }
}

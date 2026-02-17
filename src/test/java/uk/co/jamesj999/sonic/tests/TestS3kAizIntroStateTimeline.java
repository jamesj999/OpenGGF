package uk.co.jamesj999.sonic.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizIntroPlaneChild;
import uk.co.jamesj999.sonic.game.sonic3k.objects.CutsceneKnucklesAiz1Instance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizIntroStateTimeline {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sonic;
    private HeadlessTestRunner runner;
    private Object oldSkipIntros;
    private Object oldMainCharacter;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");

        GraphicsManager.getInstance().initHeadless();

        sonic = new Sonic("sonic", (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(sonic);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);

        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0); // AIZ1 with intro enabled
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        runner = new HeadlessTestRunner(sonic);
    }

    @After
    public void tearDown() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
    }

    @Test
    public void recordsAizIntroStateTransitionTimeline() {
        LevelManager levelManager = LevelManager.getInstance();
        ObjectManager objectManager = levelManager.getObjectManager();
        Camera camera = Camera.getInstance();

        List<IntroSnapshot> timeline = new ArrayList<>();

        boolean sawIntro = false;
        boolean sawKnuckles = false;
        boolean sawIntroSuperVisual = false;
        boolean sawFrozenCamera = false;
        boolean sawUnfrozenCamera = false;
        boolean sawLevelStartedFalseDuringIntro = false;
        boolean sawCameraAdvanceDuringIntro = false;
        boolean sawSonicAndCameraAdvanceTogether = false;
        boolean sawSonicWithinCameraFollowWindow = false;
        boolean sawKnuxNearCamera = false;
        boolean sawLevelStartedReenabled = false;

        int prevIntroRoutine = Integer.MIN_VALUE;
        int prevKnuxRoutine = Integer.MIN_VALUE;
        int prevPlaneX = Integer.MIN_VALUE;
        int prevPlaneY = Integer.MIN_VALUE;
        boolean prevPlaneOnScreen = false;
        boolean prevIntroPresent = false;
        boolean prevKnuxPresent = false;
        boolean prevPlanePresent = false;
        boolean prevCameraFrozen = camera.getFrozen();
        boolean prevObjectControlled = sonic.isObjectControlled();
        boolean prevControlLocked = sonic.isControlLocked();
        boolean prevSonicSuper = sonic.isSuperSonic();
        boolean prevIntroSuperVisual = false;
        boolean prevLevelStarted = camera.isLevelStarted();

        Integer heldCameraX = null;
        Integer heldSonicX = null;

        final int maxFrames = 5000;
        for (int i = 0; i < maxFrames; i++) {
            runner.stepFrame(false, false, false, false, false);

            AizPlaneIntroInstance intro = null;
            CutsceneKnucklesAiz1Instance knux = null;
            AizIntroPlaneChild plane = null;
            for (ObjectInstance obj : objectManager.getActiveObjects()) {
                if (obj instanceof AizPlaneIntroInstance introObj) {
                    intro = introObj;
                } else if (obj instanceof CutsceneKnucklesAiz1Instance knuxObj) {
                    knux = knuxObj;
                } else if (obj instanceof AizIntroPlaneChild planeObj) {
                    plane = planeObj;
                }
            }

            boolean introPresent = intro != null;
            boolean knuxPresent = knux != null;
            boolean planePresent = plane != null;
            int introRoutine = introPresent ? intro.getRoutine() : -1;
            int knuxRoutine = knuxPresent ? knux.getRoutine() : -1;
            int planeX = planePresent ? plane.getX() : -1;
            int planeY = planePresent ? plane.getY() : -1;
            // Plane uses screen-space coordinates in ROM's +128 sprite-table domain.
            int planeScreenX = planeX - 128;
            int planeScreenY = planeY - 128;
            boolean planeOnScreen = planePresent
                    && planeScreenX >= 0
                    && planeScreenX < camera.getWidth()
                    && planeScreenY >= 0
                    && planeScreenY < camera.getHeight();
            boolean cameraFrozen = camera.getFrozen();
            boolean objectControlled = sonic.isObjectControlled();
            boolean controlLocked = sonic.isControlLocked();
            boolean sonicSuper = sonic.isSuperSonic();
            boolean introSuperVisual = introPresent && intro.isSuperSonicVisualActive();
            boolean levelStarted = camera.isLevelStarted();

            sawIntro |= introPresent;
            sawKnuckles |= knuxPresent;
            sawIntroSuperVisual |= introSuperVisual;
            sawFrozenCamera |= cameraFrozen;
            sawUnfrozenCamera |= !cameraFrozen;
            sawLevelStartedFalseDuringIntro |= (introPresent || knuxPresent) && !levelStarted;

            if (!levelStarted) {
                if (heldCameraX == null || heldSonicX == null) {
                    heldCameraX = (int) camera.getX();
                    heldSonicX = (int) sonic.getCentreX();
                }
                if ((introPresent || knuxPresent) && Math.abs(camera.getX()) >= 256) {
                    sawCameraAdvanceDuringIntro = true;
                }
                int sonicDelta = Math.abs(sonic.getCentreX() - heldSonicX);
                int cameraDelta = Math.abs(camera.getX() - heldCameraX);
                if (sonicDelta >= 200 && cameraDelta >= 160) {
                    sawSonicAndCameraAdvanceTogether = true;
                }
                if (sonic.getCentreX() >= 0x918) {
                    int sonicCameraDelta = sonic.getCentreX() - camera.getX();
                    if (sonicCameraDelta >= 120 && sonicCameraDelta <= 208) {
                        sawSonicWithinCameraFollowWindow = true;
                    }
                }
            } else {
                heldCameraX = null;
                heldSonicX = null;
            }
            if (knuxPresent) {
                int knuxScreenX = knux.getX() - camera.getX();
                int knuxScreenY = knux.getY() - camera.getY();
                if (knuxScreenX >= -64 && knuxScreenX <= camera.getWidth() + 64
                        && knuxScreenY >= -64 && knuxScreenY <= camera.getHeight() + 64) {
                    sawKnuxNearCamera = true;
                }
            }
            if (sawLevelStartedFalseDuringIntro && levelStarted && !introPresent && !knuxPresent) {
                sawLevelStartedReenabled = true;
            }

            boolean changed = introPresent != prevIntroPresent
                    || knuxPresent != prevKnuxPresent
                    || planePresent != prevPlanePresent
                    || introRoutine != prevIntroRoutine
                    || knuxRoutine != prevKnuxRoutine
                    || planeX != prevPlaneX
                    || planeY != prevPlaneY
                    || planeOnScreen != prevPlaneOnScreen
                    || cameraFrozen != prevCameraFrozen
                    || objectControlled != prevObjectControlled
                    || controlLocked != prevControlLocked
                    || sonicSuper != prevSonicSuper
                    || introSuperVisual != prevIntroSuperVisual
                    || levelStarted != prevLevelStarted;

            if (changed) {
                timeline.add(IntroSnapshot.capture(runner.getFrameCounter(), sonic, camera, intro, knux, plane));
                prevIntroPresent = introPresent;
                prevKnuxPresent = knuxPresent;
                prevPlanePresent = planePresent;
                prevIntroRoutine = introRoutine;
                prevKnuxRoutine = knuxRoutine;
                prevPlaneX = planeX;
                prevPlaneY = planeY;
                prevPlaneOnScreen = planeOnScreen;
                prevCameraFrozen = cameraFrozen;
                prevObjectControlled = objectControlled;
                prevControlLocked = controlLocked;
                prevSonicSuper = sonicSuper;
                prevIntroSuperVisual = introSuperVisual;
                prevLevelStarted = levelStarted;
            }

            // Stop once intro and Knuckles cutscene have both completed and camera control is restored.
            if (sawIntro && sawKnuckles && !introPresent && !knuxPresent
                    && !sonic.isObjectControlled() && !camera.getFrozen() && camera.isLevelStarted()) {
                break;
            }
        }

        String timelineDump = formatTimeline(timeline);
        System.out.println(timelineDump);

        IntroSnapshot firstSnapshot = timeline.isEmpty() ? null : timeline.get(0);
        assertTrue("AIZ intro object never spawned.\n" + timelineDump, sawIntro);
        assertTrue("No transition snapshots recorded.\n" + timelineDump, !timeline.isEmpty());
        assertTrue("AIZ intro camera start Y must clamp to AIZ1 maxY (0x390).\n" + timelineDump,
                firstSnapshot != null && firstSnapshot.cameraY == 0x390);
        assertTrue("AIZ intro should start off-screen top-left in ROM sprite-table space (+128 bias).\n" + timelineDump,
                firstSnapshot != null && firstSnapshot.introX < 128 && firstSnapshot.introY < 128);
        assertTrue("AIZ plane child (Tornado) never appeared on-screen during intro.\n" + timelineDump,
                timeline.stream().anyMatch(s -> s.planePresent && s.planeOnScreen));
        assertTrue("AIZ intro routine never reached Knuckles trigger stage (>= 22).\n" + timelineDump,
                timeline.stream().anyMatch(s -> s.introRoutine >= 22));
        assertTrue("Knuckles cutscene object never spawned.\n" + timelineDump, sawKnuckles);
        assertTrue("AIZ intro never entered super visual phase.\n" + timelineDump, sawIntroSuperVisual);
        // ROM behavior: camera freeze flag remains clear; intro uses Level_started_flag for flow state.
        assertTrue("Camera was unexpectedly frozen during intro.\n" + timelineDump, !sawFrozenCamera);
        assertTrue("Level_started_flag was never cleared during intro/cutscene.\n" + timelineDump,
                sawLevelStartedFalseDuringIntro);
        assertTrue("Camera never advanced during intro/cutscene while Level_started_flag was clear.\n" + timelineDump,
                sawCameraAdvanceDuringIntro);
        assertTrue("Sonic movement with corresponding camera advance was not observed.\n" + timelineDump,
                sawSonicAndCameraAdvanceTogether);
        assertTrue("Camera follow window relative to Sonic was not observed near Knuckles trigger range.\n" + timelineDump,
                sawSonicWithinCameraFollowWindow);
        assertTrue("Knuckles never appeared near the camera viewport after spawning.\n" + timelineDump,
                sawKnuxNearCamera);
        assertTrue("Level_started_flag was not restored after cutscene completion.\n" + timelineDump,
                sawLevelStartedReenabled);
    }

    private static String formatTimeline(List<IntroSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AIZ Intro Transition Timeline ===\n");
        sb.append("frame intro(r/x/y/super/map) plane(x/y/on) knux(r/x/y) cam(x/y/f/ls) ")
                .append("sonic(cx/cy/super/objCtrl/lock/hidden/map)\n");
        for (IntroSnapshot s : snapshots) {
            sb.append(String.format(
                    "%4d I(%2d/%5d/%4d/%s/%3d) P(%5d/%4d/%s) K(%2d/%5d/%4d) C(%5d/%4d/%s/%s) S(%5d/%4d/%s/%s/%s/%s/%3d)%n",
                    s.frame,
                    s.introRoutine, s.introX, s.introY, boolFlag(s.introSuperVisual), s.introMappingFrame,
                    s.planeX, s.planeY, boolFlag(s.planeOnScreen),
                    s.knuxRoutine, s.knuxX, s.knuxY,
                    s.cameraX, s.cameraY, boolFlag(s.cameraFrozen), boolFlag(s.levelStarted),
                    s.sonicCentreX, s.sonicCentreY,
                    boolFlag(s.sonicSuper),
                    boolFlag(s.sonicObjectControlled),
                    boolFlag(s.sonicControlLocked),
                    boolFlag(s.sonicHidden),
                    s.sonicMappingFrame));
        }
        return sb.toString();
    }

    private static String boolFlag(boolean value) {
        return value ? "Y" : "N";
    }

    private static final class IntroSnapshot {
        final int frame;
        final int cameraX;
        final int cameraY;
        final boolean cameraFrozen;
        final boolean levelStarted;
        final int sonicCentreX;
        final int sonicCentreY;
        final boolean sonicSuper;
        final boolean sonicObjectControlled;
        final boolean sonicControlLocked;
        final boolean sonicHidden;
        final int sonicMappingFrame;
        final int introRoutine;
        final int introX;
        final int introY;
        final boolean introSuperVisual;
        final int introMappingFrame;
        final boolean planePresent;
        final int planeX;
        final int planeY;
        final boolean planeOnScreen;
        final int knuxRoutine;
        final int knuxX;
        final int knuxY;

        private IntroSnapshot(int frame,
                              int cameraX,
                              int cameraY,
                              boolean cameraFrozen,
                              boolean levelStarted,
                              int sonicCentreX,
                              int sonicCentreY,
                              boolean sonicSuper,
                              boolean sonicObjectControlled,
                              boolean sonicControlLocked,
                              boolean sonicHidden,
                              int sonicMappingFrame,
                              int introRoutine,
                              int introX,
                              int introY,
                              boolean introSuperVisual,
                              int introMappingFrame,
                              boolean planePresent,
                              int planeX,
                              int planeY,
                              boolean planeOnScreen,
                              int knuxRoutine,
                              int knuxX,
                              int knuxY) {
            this.frame = frame;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraFrozen = cameraFrozen;
            this.levelStarted = levelStarted;
            this.sonicCentreX = sonicCentreX;
            this.sonicCentreY = sonicCentreY;
            this.sonicSuper = sonicSuper;
            this.sonicObjectControlled = sonicObjectControlled;
            this.sonicControlLocked = sonicControlLocked;
            this.sonicHidden = sonicHidden;
            this.sonicMappingFrame = sonicMappingFrame;
            this.introRoutine = introRoutine;
            this.introX = introX;
            this.introY = introY;
            this.introSuperVisual = introSuperVisual;
            this.introMappingFrame = introMappingFrame;
            this.planePresent = planePresent;
            this.planeX = planeX;
            this.planeY = planeY;
            this.planeOnScreen = planeOnScreen;
            this.knuxRoutine = knuxRoutine;
            this.knuxX = knuxX;
            this.knuxY = knuxY;
        }

        static IntroSnapshot capture(int frame,
                                     Sonic sonic,
                                     Camera camera,
                                     AizPlaneIntroInstance intro,
                                     CutsceneKnucklesAiz1Instance knux,
                                     AizIntroPlaneChild plane) {
            int introRoutine = intro != null ? intro.getRoutine() : -1;
            int introX = intro != null ? intro.getX() : -1;
            int introY = intro != null ? intro.getY() : -1;
            boolean introSuperVisual = intro != null && intro.isSuperSonicVisualActive();
            int introMappingFrame = intro != null ? intro.getMappingFrame() : -1;

            int planeX = plane != null ? plane.getX() : -1;
            int planeY = plane != null ? plane.getY() : -1;
            int planeScreenX = planeX - 128;
            int planeScreenY = planeY - 128;
            // Plane uses screen-space coordinates in ROM's +128 sprite-table domain.
            boolean planeOnScreen = plane != null
                    && planeScreenX >= 0
                    && planeScreenX < camera.getWidth()
                    && planeScreenY >= 0
                    && planeScreenY < camera.getHeight();

            int knuxRoutine = knux != null ? knux.getRoutine() : -1;
            int knuxX = knux != null ? knux.getX() : -1;
            int knuxY = knux != null ? knux.getY() : -1;

            return new IntroSnapshot(
                    frame,
                    camera.getX(),
                    camera.getY(),
                    camera.getFrozen(),
                    camera.isLevelStarted(),
                    sonic.getCentreX(),
                    sonic.getCentreY(),
                    sonic.isSuperSonic(),
                    sonic.isObjectControlled(),
                    sonic.isControlLocked(),
                    sonic.isHidden(),
                    sonic.getMappingFrame(),
                    introRoutine,
                    introX,
                    introY,
                    introSuperVisual,
                    introMappingFrame,
                    plane != null,
                    planeX,
                    planeY,
                    planeOnScreen,
                    knuxRoutine,
                    knuxX,
                    knuxY);
        }
    }
}

package uk.co.jamesj999.sonic.audio;

import uk.co.jamesj999.sonic.audio.driver.SmpsDriver;
import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencer;
import uk.co.jamesj999.sonic.audio.synth.Ym2612Chip;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import uk.co.jamesj999.sonic.game.sonic2.audio.smps.Sonic2SmpsLoader;

import java.io.File;
import java.util.Arrays;

import static uk.co.jamesj999.sonic.tests.RomTestUtils.ensureRomAvailable;

/**
 * Performance benchmark for audio rendering.
 * Run with: mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.audio.AudioBenchmark" -Dexec.classpathScope=test -q
 */
public class AudioBenchmark {

    private static final double SAMPLE_RATE = Ym2612Chip.getDefaultOutputRate();
    private static final int BUFFER_SIZE = 1024;
    private static final int MUSIC_EHZ = 0x81;

    // Warmup and measurement parameters
    private static final int WARMUP_SECONDS = 5;
    private static final int MEASURE_SECONDS = 10;
    private static final int RUNS = 5;

    public static void main(String[] args) {
        try {
            File romFile = ensureRomAvailable();
            Rom rom = new Rom();
            if (!rom.open(romFile.getAbsolutePath())) {
                System.err.println("Failed to open ROM");
                System.exit(1);
            }

            Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
            DacData dacData = loader.loadDacData();
            AbstractSmpsData musicData = loader.loadMusic(MUSIC_EHZ);

            System.out.println("Audio Engine Performance Benchmark");
            System.out.println("==================================");
            System.out.println("Sample rate: " + SAMPLE_RATE + " Hz");
            System.out.println("Buffer size: " + BUFFER_SIZE + " frames");
            System.out.println("Warmup: " + WARMUP_SECONDS + " seconds of audio");
            System.out.println("Measurement: " + MEASURE_SECONDS + " seconds of audio x " + RUNS + " runs");
            System.out.println();

            double[] results = new double[RUNS];

            for (int run = 0; run < RUNS; run++) {
                // Create fresh driver for each run
                SmpsDriver driver = new SmpsDriver(SAMPLE_RATE);
                driver.setRegion(SmpsSequencer.Region.NTSC);
                SmpsSequencer seq = new SmpsSequencer(musicData, dacData, driver, Sonic2SmpsSequencerConfig.CONFIG);
                seq.setSampleRate(SAMPLE_RATE);
                driver.addSequencer(seq, false);

                short[] buffer = new short[BUFFER_SIZE * 2];

                // Warmup
                int warmupIterations = (int) (WARMUP_SECONDS * SAMPLE_RATE / BUFFER_SIZE);
                for (int i = 0; i < warmupIterations; i++) {
                    driver.read(buffer);
                }

                // Measure
                int measureIterations = (int) (MEASURE_SECONDS * SAMPLE_RATE / BUFFER_SIZE);
                long start = System.nanoTime();
                for (int i = 0; i < measureIterations; i++) {
                    driver.read(buffer);
                }
                long elapsed = System.nanoTime() - start;

                double msPerSecond = elapsed / 1_000_000.0 / MEASURE_SECONDS;
                results[run] = msPerSecond;

                System.out.printf("Run %d: %.3f ms per second of audio (%.1fx real-time)%n",
                        run + 1, msPerSecond, 1000.0 / msPerSecond);
            }

            // Statistics
            Arrays.sort(results);
            double min = results[0];
            double max = results[RUNS - 1];
            double median = results[RUNS / 2];
            double sum = 0;
            for (double r : results) sum += r;
            double mean = sum / RUNS;

            System.out.println();
            System.out.println("Summary:");
            System.out.printf("  Min:    %.3f ms/sec%n", min);
            System.out.printf("  Max:    %.3f ms/sec%n", max);
            System.out.printf("  Mean:   %.3f ms/sec%n", mean);
            System.out.printf("  Median: %.3f ms/sec%n", median);
            System.out.printf("  Real-time factor: %.1fx (based on median)%n", 1000.0 / median);

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

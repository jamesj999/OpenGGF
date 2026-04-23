package com.openggf.trace.catalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceCatalogHangTest {
    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void scanReturnsQuicklyOnLargeRoot() {
        // Scanning the project root (thousands of files) with the depth-2
        // bound should complete near-instantly.
        Path root = Path.of(System.getProperty("user.dir"));
        List<TraceEntry> entries = TraceCatalog.scan(root);
        assertTrue(entries.isEmpty() || entries.size() > 0);
    }
}

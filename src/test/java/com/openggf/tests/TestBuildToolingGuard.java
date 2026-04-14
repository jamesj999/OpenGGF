package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

class TestBuildToolingGuard {

    @Test
    void surefireShouldPreloadMockitoAsJavaAgent() throws IOException {
        String file = "pom.xml";
        String content = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (!content.contains("<mockito.version>")) {
            violations.add(file + " does not define a reusable Mockito version property");
        }
        if (!content.contains("<mockito.agent.argLine>")) {
            violations.add(file + " does not define a reusable Mockito javaagent property");
        }
        if (!content.contains("<test.cds.argLine>")) {
            violations.add(file + " does not define a reusable test JVM CDS toggle property");
        }
        if (!content.contains("-javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar")) {
            violations.add(file + " does not preload mockito-core as a Surefire javaagent");
        }
        if (!content.contains("<test.cds.argLine>-Xshare:off</test.cds.argLine>")) {
            violations.add(file + " does not disable CDS for test JVMs after adding the Mockito agent");
        }
        if (!content.contains("${test.cds.argLine}")) {
            violations.add(file + " does not thread the CDS toggle through Surefire argLine");
        }

        if (!violations.isEmpty()) {
            fail("Surefire should preload Mockito cleanly without runtime self-attach or CDS bootstrap warnings:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }
}

package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.fail;

class TestBuildToolingGuard {

    @Test
    void surefireShouldPreloadMockitoAsJavaAgent() throws Exception {
        String file = "pom.xml";
        Document pom = parsePom(file);
        List<String> violations = new ArrayList<>();

        if (property(pom, "mockito.version") == null) {
            violations.add(file + " does not define a reusable Mockito version property");
        }
        String mockitoAgentArgLine = property(pom, "mockito.agent.argLine");
        if (mockitoAgentArgLine == null) {
            violations.add(file + " does not define a reusable Mockito javaagent property");
        }
        String mockitoAgentPath = property(pom, "mockito.agent.path");
        if (mockitoAgentPath == null) {
            violations.add(file + " does not define a reusable quoted Mockito agent path property");
        }
        String cdsArgLine = property(pom, "test.cds.argLine");
        if (cdsArgLine == null) {
            violations.add(file + " does not define a reusable test JVM CDS toggle property");
        }
        String surefireArgLine = property(pom, "surefire.argLine");
        if (surefireArgLine == null) {
            violations.add(file + " does not define a reusable Surefire argLine property");
        }
        if (mockitoAgentArgLine != null
                && !mockitoAgentArgLine.contains("-javaagent:")
                && !mockitoAgentArgLine.contains("@{mockito.agent.path}")) {
            violations.add(file + " does not preload mockito-core as a Surefire javaagent");
        }
        if (mockitoAgentPath != null && !mockitoAgentPath.contains("mockito-core-${mockito.version}.jar")) {
            violations.add(file + " does not resolve the Mockito javaagent from the reusable versioned jar path");
        }
        if (mockitoAgentArgLine != null && !mockitoAgentArgLine.contains("${mockito.agent.path}")) {
            violations.add(file + " does not route the Mockito javaagent through the shared mockito.agent.path property");
        }
        if (mockitoAgentPath != null && !mockitoAgentPath.contains("\"")) {
            violations.add(file + " does not quote or escape the Mockito javaagent path for Maven repositories with spaces");
        }
        if (cdsArgLine != null && !"-Xshare:off".equals(cdsArgLine)) {
            violations.add(file + " does not disable CDS for test JVMs after adding the Mockito agent");
        }
        if (surefireArgLine != null && !surefireArgLine.contains("${test.cds.argLine}")) {
            violations.add(file + " does not thread the CDS toggle through Surefire argLine");
        }
        if (surefireArgLine != null && !surefireArgLine.contains("${mockito.agent.argLine}")) {
            violations.add(file + " does not thread the Mockito agent property through Surefire argLine");
        }
        if (!surefirePluginUsesSharedArgLine(pom)) {
            violations.add(file + " does not wire the Surefire plugin to the shared surefire.argLine property");
        }

        if (!violations.isEmpty()) {
            fail("Surefire should preload Mockito cleanly without runtime self-attach or CDS bootstrap warnings:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    private static Document parsePom(String file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(Files.newBufferedReader(Path.of(file))));
    }

    private static String property(Document pom, String name) {
        NodeList nodes = pom.getElementsByTagName(name);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static boolean surefirePluginUsesSharedArgLine(Document pom) {
        NodeList argLines = pom.getElementsByTagName("argLine");
        for (int i = 0; i < argLines.getLength(); i++) {
            if ("${surefire.argLine}".equals(argLines.item(i).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }
}

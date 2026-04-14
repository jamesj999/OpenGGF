package com.openggf;

import com.openggf.version.AppVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestEngineWindowTitle {

	@Test
	public void testBuildWindowTitleUsesResolvedAppVersion() {
		assertEquals("OpenGGF " + AppVersion.get(), Engine.buildWindowTitle());
	}
}

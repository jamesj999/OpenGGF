package com.openggf.tests.rules;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRequiresGameModuleCondition {

    @AfterEach
    void tearDown() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void configuresRequestedModuleAcrossRegistrySessionAndRuntime() {
        RequiresGameModuleCondition condition = new RequiresGameModuleCondition();
        condition.beforeEach(extensionContextFor(TestFixture.class));

        assertEquals("Sonic2", GameModuleRegistry.getCurrent().getIdentifier());
        assertEquals("Sonic2", SessionManager.requireCurrentGameModule().getIdentifier());
        assertEquals("Sonic2", RuntimeManager.getCurrent().getWorldSession().getGameModule().getIdentifier());
    }

    @RequiresGameModule(SonicGame.SONIC_2)
    private static final class TestFixture {
    }

    private ExtensionContext extensionContextFor(Class<?> testClass) {
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class[]{ExtensionContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequiredTestClass" -> testClass;
                    case "getParent" -> Optional.empty();
                    case "getRoot" -> proxy;
                    case "getUniqueId" -> "requires-game-module-condition";
                    case "getDisplayName" -> testClass.getSimpleName();
                    case "getTags" -> java.util.Collections.emptySet();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}



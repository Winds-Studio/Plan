package com.djrapitops.plan.placeholder;

import com.djrapitops.plan.PlanSystem;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.transactions.events.PlayerServerRegisterTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreSessionTransaction;
import com.djrapitops.plan.storage.database.transactions.events.WorldNameStoreTransaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import utilities.RandomData;
import utilities.TestConstants;
import utilities.dagger.PlanPluginComponent;
import utilities.mocks.PluginMockComponent;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PlanPlaceholdersTest {

    private static PlanPluginComponent component;
    private static PlanPlaceholders underTest;
    private static ServerUUID serverUUID;
    private static UUID playerUUID;

    @BeforeAll
    static void prepareSystem(@TempDir Path tempDir) throws Exception {
        component = new PluginMockComponent(tempDir).getComponent();
        component.system().enable();
        serverUUID = component.system().getServerInfo().getServerUUID();
        underTest = component.placeholders();

        playerUUID = UUID.randomUUID();

        storeSomeData();
    }

    private static void storeSomeData() {
        Database database = component.system().getDatabaseSystem().getDatabase();
        database.executeTransaction(new PlayerServerRegisterTransaction(
                playerUUID,
                System::currentTimeMillis,
                RandomData.randomString(5),
                serverUUID,
                () -> RandomData.randomString(5)
        ));
        database.executeTransaction(new PlayerServerRegisterTransaction(
                TestConstants.PLAYER_TWO_UUID,
                System::currentTimeMillis,
                TestConstants.PLAYER_TWO_NAME,
                serverUUID,
                () -> RandomData.randomString(5)
        ));
        String worldName = RandomData.randomString(10);
        database.executeTransaction(new WorldNameStoreTransaction(serverUUID, worldName));
        database.executeTransaction(new StoreSessionTransaction(RandomData.randomSession(serverUUID, new String[]{worldName}, playerUUID, TestConstants.PLAYER_TWO_UUID)));
    }

    @AfterAll
    static void clearSystem() {
        if (component != null) {
            PlanSystem system = component.system();
            if (system != null) {
                system.disable();
            }
        }
    }

    @TestFactory
    @DisplayName("Server placeholders return something")
    Collection<DynamicTest> testServerPlaceholders() {
        return underTest.getRegisteredServerPlaceholders().stream()
                .map(placeholder -> DynamicTest.dynamicTest("'" + placeholder + "' returns something", () -> {
                    String result = underTest.onPlaceholderRequest(UUID.randomUUID(), placeholder, Collections.emptyList());
                    System.out.println("Placeholder '" + placeholder + "' was replaced with: '" + result + "'");
                    assertNotNull(result);
                    assertNotEquals(placeholder, result);
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    @DisplayName("Server placeholders return something on console")
    Collection<DynamicTest> testServerPlaceholdersOnConsole() {
        return underTest.getRegisteredServerPlaceholders().stream()
                .map(placeholder -> DynamicTest.dynamicTest("'" + placeholder + "' returns something", () -> {
                    String result = underTest.onPlaceholderRequest(null, placeholder, Collections.emptyList());
                    System.out.println("Placeholder '" + placeholder + "' was replaced with: '" + result + "'");
                    assertNotNull(result);
                    assertNotEquals(placeholder, result);
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    @DisplayName("Server placeholders return something for another server")
    Collection<DynamicTest> testServerPlaceholdersWithParameter() {
        return underTest.getRegisteredServerPlaceholders().stream()
                .map(placeholder -> DynamicTest.dynamicTest("'" + placeholder + ":<server>' returns something", () -> {
                    String result = underTest.onPlaceholderRequest(UUID.randomUUID(), placeholder, List.of(serverUUID.toString()));
                    System.out.println("Placeholder '" + placeholder + ":" + serverUUID.toString() + "' was replaced with: '" + result + "'");
                    assertNotNull(result);
                    assertNotEquals(placeholder, result);
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    @DisplayName("Player placeholders return something")
    Collection<DynamicTest> testPlayerPlaceholders() {
        return underTest.getRegisteredPlayerPlaceholders().stream()
                .map(placeholder -> DynamicTest.dynamicTest("'" + placeholder + "' returns something", () -> {
                    String result = underTest.onPlaceholderRequest(playerUUID, placeholder, Collections.emptyList());
                    System.out.println("Placeholder '" + placeholder + "' was replaced with: '" + result + "'");
                    assertNotNull(result);
                    assertNotEquals(placeholder, result);
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    @DisplayName("Player placeholders return nothing on Console")
    Collection<DynamicTest> testPlayerPlaceholdersOnConsole() {
        return underTest.getRegisteredPlayerPlaceholders().stream()
                .map(placeholder -> DynamicTest.dynamicTest("'" + placeholder + "' returns something", () -> {
                    String result = underTest.onPlaceholderRequest(null, placeholder, Collections.emptyList());
                    System.out.println("Placeholder '" + placeholder + "' was replaced with: '" + result + "'");
                    assertNull(result);
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    @DisplayName("Player placeholders return something on Console for other player")
    Collection<DynamicTest> testPlayerPlaceholdersOnConsoleForOtherPlayer() {
        return underTest.getRegisteredPlayerPlaceholders().stream()
                .map(placeholder -> DynamicTest.dynamicTest("'" + placeholder + "' returns something", () -> {
                    String result = underTest.onPlaceholderRequest(null, placeholder, List.of(playerUUID.toString()));
                    System.out.println("Placeholder '" + placeholder + "' was replaced with: '" + result + "'");
                    assertNotNull(result);
                    assertNotEquals(placeholder, result);
                }))
                .collect(Collectors.toList());
    }

}
/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan;

import com.djrapitops.plan.api.exceptions.EnableException;
import com.djrapitops.plan.db.SQLiteDB;
import com.djrapitops.plan.system.PlanSystem;
import com.djrapitops.plan.system.database.DBSystem;
import com.djrapitops.plan.system.settings.config.PlanConfig;
import com.djrapitops.plan.system.settings.paths.ProxySettings;
import com.djrapitops.plan.system.settings.paths.WebserverSettings;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import utilities.DBPreparer;
import utilities.RandomData;
import utilities.mocks.BungeeMockComponent;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test for Bungee PlanSystem.
 *
 * @author Rsl1122
 */
@RunWith(JUnitPlatform.class)
public class BungeeSystemTest {

    private final int TEST_PORT_NUMBER = RandomData.randomInt(9005, 9500);

    private BungeeMockComponent component;
    private DBPreparer dbPreparer;

    @BeforeEach
    void prepareSystem(@TempDir Path temp) throws Exception {
        component = new BungeeMockComponent(temp);
        dbPreparer = new DBPreparer(component.getPlanSystem(), TEST_PORT_NUMBER);
    }

    @Test
    void bungeeEnables() throws Exception {
        PlanSystem bungeeSystem = component.getPlanSystem();
        try {
            PlanConfig config = bungeeSystem.getConfigSystem().getConfig();
            config.set(WebserverSettings.PORT, TEST_PORT_NUMBER);
            config.set(ProxySettings.IP, "8.8.8.8");

            DBSystem dbSystem = bungeeSystem.getDatabaseSystem();
            SQLiteDB db = dbSystem.getSqLiteFactory().usingDefaultFile();
            db.setTransactionExecutorServiceProvider(MoreExecutors::newDirectExecutorService);
            dbSystem.setActiveDatabase(db);

            bungeeSystem.enable();
            assertTrue(bungeeSystem.isEnabled());
        } finally {
            bungeeSystem.disable();
        }
    }

    @Test
    void bungeeDoesNotEnableWithDefaultIP() {
        EnableException thrown = assertThrows(EnableException.class, () -> {
            PlanSystem bungeeSystem = component.getPlanSystem();
            try {
                PlanConfig config = bungeeSystem.getConfigSystem().getConfig();
                config.set(WebserverSettings.PORT, TEST_PORT_NUMBER);
                config.set(ProxySettings.IP, "0.0.0.0");

                DBSystem dbSystem = bungeeSystem.getDatabaseSystem();
                SQLiteDB db = dbSystem.getSqLiteFactory().usingDefaultFile();
                db.setTransactionExecutorServiceProvider(MoreExecutors::newDirectExecutorService);
                dbSystem.setActiveDatabase(db);

                bungeeSystem.enable(); // Throws EnableException
            } finally {
                bungeeSystem.disable();
            }
        });

        assertEquals("IP setting still 0.0.0.0 - Configure AlternativeIP/IP that connects to the Proxy server.", thrown.getMessage());
    }

    @Test
    void testEnableNoMySQL() {
        assertThrows(EnableException.class, () -> {
            PlanSystem bungeeSystem = component.getPlanSystem();
            try {
                PlanConfig config = bungeeSystem.getConfigSystem().getConfig();
                config.set(WebserverSettings.PORT, TEST_PORT_NUMBER);
                config.set(ProxySettings.IP, "8.8.8.8");

                bungeeSystem.enable(); // Throws EnableException
            } finally {
                bungeeSystem.disable();
            }
        });
    }

    @Test
    void testEnableWithMySQL() throws Exception {
        PlanSystem bungeeSystem = component.getPlanSystem();
        try {
            PlanConfig config = bungeeSystem.getConfigSystem().getConfig();
            // MySQL settings might not be available.
            assumeTrue(dbPreparer.setUpMySQLSettings(config).isPresent());

            config.set(WebserverSettings.PORT, TEST_PORT_NUMBER);
            config.set(ProxySettings.IP, "8.8.8.8");

            bungeeSystem.enable();
            assertTrue(bungeeSystem.isEnabled());
        } finally {
            bungeeSystem.disable();
        }
    }
}

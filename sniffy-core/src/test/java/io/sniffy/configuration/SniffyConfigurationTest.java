package io.sniffy.configuration;

import io.qameta.allure.Feature;
import io.sniffy.Sniffy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

public class SniffyConfigurationTest {

    private Properties backup;

    @Before
    public void backupSystemProperties() {
        backup = new Properties();
        backup.putAll(System.getProperties());
    }

    @After
    public void restoreSystemProperties() {
        System.setProperties(backup);
    }

    @Test
    public void testMonitorJdbc() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        // enabled
        System.setProperty("io.sniffy.monitorJdbc", "true");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorJdbc());

        System.setProperty("io.sniffy.monitorJdbc", "TRUE");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorJdbc());

        // disabled
        System.setProperty("io.sniffy.monitorJdbc", "false");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorJdbc());

        System.setProperty("io.sniffy.monitorJdbc", "");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorJdbc());

        // default value
        System.getProperties().remove("io.sniffy.monitorJdbc");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorJdbc());

        // overriden value
        System.getProperties().remove("io.sniffy.monitorJdbc");
        sniffyConfiguration.loadSniffyConfiguration();
        sniffyConfiguration.setMonitorJdbc(false);
        assertFalse(sniffyConfiguration.isMonitorJdbc());

    }

    @Test
    public void testMonitorSocket() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        // enabled
        System.setProperty("io.sniffy.monitorSocket", "true");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorSocket());

        System.setProperty("io.sniffy.monitorSocket", "TRUE");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorSocket());

        // disabled
        System.setProperty("io.sniffy.monitorSocket", "false");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorSocket());

        System.setProperty("io.sniffy.monitorSocket", "");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorSocket());

        // default value
        System.getProperties().remove("io.sniffy.monitorSocket");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorSocket());

    }

    @Test
    public void testMonitorNio() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        // enabled
        System.setProperty("io.sniffy.monitorNio", "true");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorNio());

        System.setProperty("io.sniffy.monitorNio", "TRUE");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.isMonitorNio());

        // disabled
        System.setProperty("io.sniffy.monitorNio", "false");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorNio());

        System.setProperty("io.sniffy.monitorNio", "");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorNio());

        // default value
        System.getProperties().remove("io.sniffy.monitorNio");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.isMonitorNio());

    }

    @Test
    @Feature("issues/292")
    public void testTopSqlCapacity() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        System.setProperty("io.sniffy.topSqlCapacity", "42");
        sniffyConfiguration.loadSniffyConfiguration();
        assertEquals(42, sniffyConfiguration.getTopSqlCapacity());

        // incorrect value
        System.setProperty("io.sniffy.topSqlCapacity", "bla");
        sniffyConfiguration.loadSniffyConfiguration();
        assertEquals(0, sniffyConfiguration.getTopSqlCapacity());

        // default value
        System.getProperties().remove("io.sniffy.topSqlCapacity");
        sniffyConfiguration.loadSniffyConfiguration();
        assertEquals(Sniffy.TOP_SQL_CAPACITY, sniffyConfiguration.getTopSqlCapacity());

    }

    @Test
    public void testFilterEnabled() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        // enabled
        System.setProperty("io.sniffy.filterEnabled", "true");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.getFilterEnabled());

        System.setProperty("io.sniffy.filterEnabled", "TRUE");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.getFilterEnabled());

        // disabled
        System.setProperty("io.sniffy.filterEnabled", "false");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.getFilterEnabled());

        System.setProperty("io.sniffy.filterEnabled", "");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.getFilterEnabled());

        // default value
        System.getProperties().remove("io.sniffy.filterEnabled");
        sniffyConfiguration.loadSniffyConfiguration();
        assertNull(sniffyConfiguration.getFilterEnabled());

        // overriden value
        System.getProperties().remove("io.sniffy.filterEnabled");
        sniffyConfiguration.loadSniffyConfiguration();
        sniffyConfiguration.setFilterEnabled(false);
        assertFalse(sniffyConfiguration.getFilterEnabled());

    }

    @Test
    public void testExcludePattern() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        // enabled
        System.setProperty("io.sniffy.excludePattern", "^/vets.html$");
        sniffyConfiguration.loadSniffyConfiguration();
        assertEquals("^/vets.html$", sniffyConfiguration.getExcludePattern());

        System.getProperties().remove("io.sniffy.excludePattern");
        sniffyConfiguration.loadSniffyConfiguration();
        assertNull(sniffyConfiguration.getExcludePattern());

    }

    @Test
    public void testInjectHtml() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        System.setProperty("io.sniffy.injectHtml", "true");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.getInjectHtmlEnabled());

        System.setProperty("io.sniffy.injectHtml", "TRUE");
        sniffyConfiguration.loadSniffyConfiguration();
        assertTrue(sniffyConfiguration.getInjectHtmlEnabled());

        // disabled
        System.setProperty("io.sniffy.injectHtml", "false");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.getInjectHtmlEnabled());

        System.setProperty("io.sniffy.injectHtml", "");
        sniffyConfiguration.loadSniffyConfiguration();
        assertFalse(sniffyConfiguration.getInjectHtmlEnabled());

        // default value
        System.getProperties().remove("io.sniffy.injectHtml");
        sniffyConfiguration.loadSniffyConfiguration();
        assertNull(sniffyConfiguration.getInjectHtmlEnabled());

        // overriden value
        System.getProperties().remove("io.sniffy.injectHtml");
        sniffyConfiguration.loadSniffyConfiguration();
        sniffyConfiguration.setInjectHtmlEnabled(false);
        assertFalse(sniffyConfiguration.getInjectHtmlEnabled());

    }

    @Test
    @Feature("issues/304")
    public void testInjectHtmlExcludePattern() {

        SniffyConfiguration sniffyConfiguration = SniffyConfiguration.INSTANCE;

        System.setProperty("io.sniffy.injectHtmlExcludePattern", "^/vets.html$");
        sniffyConfiguration.loadSniffyConfiguration();
        assertEquals("^/vets.html$", sniffyConfiguration.getInjectHtmlExcludePattern());

        System.getProperties().remove("io.sniffy.injectHtmlExcludePattern");
        sniffyConfiguration.loadSniffyConfiguration();
        assertNull(sniffyConfiguration.getInjectHtmlExcludePattern());

    }

}

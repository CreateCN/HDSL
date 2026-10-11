package com.mcxcc303.hdsl.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Where plugins are read from, now that a market is a market rather than a list of addresses.
///
/// A market whose request shape is its own cannot be reached by adding another address, which is
/// what the list of addresses was for and why it is gone. What is left to choose is which market is
/// read, and that choice is stored by id.
class CatalogSourcesTest {
    @Test
    void theCommunityMarketIsReadFromItsHostAndThenFromNpm() {
        assertEquals(List.of("https://awesome-dsh-plugin.com/plugins.json"),
                MarketSource.AWESOME_DSH_PLUGIN.addresses());
        assertEquals("dsh-plugin-catalog", MarketSource.AWESOME_DSH_PLUGIN.fallback(),
                "the host is GitHub Pages, and a network that cannot reach it can reach an npm mirror");
    }

    @Test
    void aMarketIsChosenByIdAndAnUnknownIdIsTheCommunityMarket() {
        assertSame(MarketSource.AWESOME_DSH_PLUGIN, MarketSource.of("awesome-dsh-plugin"));
        assertSame(MarketSource.AWESOME_DSH_PLUGIN, MarketSource.of("  awesome-dsh-plugin  "),
                "the id comes out of a settings file, so it is trimmed");
        assertSame(MarketSource.AWESOME_DSH_PLUGIN, MarketSource.of(null),
                "a launcher that has never been asked reads the community market");
        assertSame(MarketSource.AWESOME_DSH_PLUGIN, MarketSource.of("from-a-newer-launcher"),
                "a market this launcher does not know falls back to the one it does");
    }

    @Test
    void everyMarketSaysWhatItIsCalledAndWhereItIsReadFrom() {
        for (MarketSource source : MarketSource.values()) {
            assertTrue(!source.id().isBlank(), source + " has an id to be stored under");
            assertTrue(!source.label().isBlank(), source + " has a name to be shown by");
            assertTrue(!source.addresses().isEmpty(), source + " says where it is read from");
        }
    }
}
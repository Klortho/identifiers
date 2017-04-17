package gov.ncbi.ids.test;

import static org.junit.Assert.*;

import org.junit.Before;

import static org.hamcrest.core.StringContains.containsString;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import gov.ncbi.ids.IdDb;

public class TestConfig
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TestConfig.class);

    @Rule
    public TestName name = new TestName();

    /**
     * This helper checks expected values for a Config.
     */
    public void checkConfig(Config actConfig, Object... args)
    {
        for (int i = 0; i < args.length; i += 3) {
            String key = (String) args[i];
            String type = (String) args[i + 1];
            if (type.equals("boolean")) {
                boolean exp = (boolean) args[i + 2];
                assertEquals(exp, actConfig.getBoolean(key));
            }
            else if (type.equals("int")) {
                int exp = (int) args[i + 2];
                assertEquals(exp, actConfig.getInt(key));
            }
            else if (type.equals("string-exact")) {
                String exp = (String) args[i + 2];
                assertEquals(exp, actConfig.getString(key));
            }
            else if (type.equals("string-contains")) {
                String exp = (String) args[i + 2];
                assertThat(actConfig.getString(key),
                    containsString(exp));
            }
        }
    }

    @Before
    public void initialize() {
        ConfigFactory.invalidateCaches();
    }

    /**
     * Test the default config for a new literature IDs database
     */
    @Test
    public void testLitIdDb()
        throws Exception
    {
        IdDb iddb = IdDb.getLiteratureIdDb();
        checkConfig(iddb.getConfig(),
            "ncbi.ids.cache.enabled", "boolean", false,
            "ncbi.ids.cache.ttl", "int", 86400,
            "ncbi.ids.cache.size", "int", 50000,
            "ncbi.ids.resolver.wants-type", "string-exact", "aiid",
            "ncbi.ids.converter.base", "string-contains", "ncbi.nlm.nih.gov",
            "ncbi.ids.converter.params", "string-contains", "showaiid=yes"
        );
    }

    @Test
    public void testLitWithOverrides()
        throws Exception
    {
        // This simulates an application, that would load it's own
        // configuration at the "app" level. The id library's reference.conf
        // gets loaded first, and then the app's values override.
        Config config = ConfigFactory.load("test-config");
        checkConfig(config,
                "my-app.split", "string-exact", "bingo",
                "ncbi.ids.cache.enabled", "boolean", false,
                "ncbi.ids.resolver.wants-type", "string-exact", "doi"
        );

        IdDb iddb = IdDb.getLiteratureIdDb(config);
        checkConfig(iddb.getConfig(),
                "ncbi.ids.cache.enabled", "boolean", false,
                "ncbi.ids.cache.ttl", "int", 86400,
                "ncbi.ids.cache.size", "int", 50000,
                "ncbi.ids.resolver.wants-type", "string-exact", "doi",
                "ncbi.ids.converter.base", "string-contains", "ncbi.nlm.nih.gov",
                "ncbi.ids.converter.params", "string-contains", "showaiid=yes"
        );
    }

    @Test
    public void testHardcodedOverride()
            throws Exception
    {
        Config defaults = ConfigFactory.load();
        Config newConfig = defaults.withValue(
            "ncbi.ids.resolver.wants-type",
            ConfigValueFactory.fromAnyRef("pmcid"));

        IdDb iddb = IdDb.getLiteratureIdDb(newConfig);
        checkConfig(iddb.getConfig(),
                "ncbi.ids.cache.enabled", "boolean", false,
                "ncbi.ids.cache.size", "int", 50000,
                "ncbi.ids.resolver.wants-type", "string-exact", "pmcid"
        );
    }
}

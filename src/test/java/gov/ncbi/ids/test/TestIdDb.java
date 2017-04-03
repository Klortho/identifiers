package gov.ncbi.ids.test;

import static gov.ncbi.ids.test.TestIdentifier.checkId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.Identifier;


/**
 * These tests are parameterized, so that they all run twice; once for each:
 * - Using the static global `_litIds`
 * - Using an IdDb that it reads from a JSON file
 *   src/test/resources/literature-id-db.json
 */
@RunWith(Parameterized.class)
public class TestIdDb
{
    private static final Logger log = LoggerFactory.getLogger(TestIdDb.class);

    public static final String TEST_ID_DATABASE = "litids-db.json";
    public static URL resolveUrl(String path) {
        URL url = TestIdDb.class.getClassLoader().getResource(path);
        if (url == null) {
            String msg = "Failed to get the URL for " + path;
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return url;
    }
    public static final URL jsonUrl = resolveUrl(TEST_ID_DATABASE);

    private IdDb iddb;

    public TestIdDb(String iddbSource)
        throws IOException
    {
        if (iddbSource.equals("global"))
            this.iddb = IdDb.litIds();
        else if (iddbSource.equals("json-url"))
            this.iddb = fromJsonUrl();
        else if (iddbSource.equals("json-string"))
            this.iddb = fromJsonString();
    }

    /**
     * Read a test IdDb from a JSON file
     */
    private static IdDb fromJsonUrl()
        throws IOException
    {
        return IdDb.fromJson(jsonUrl);
    }

    /**
     * Read a test IdDb from a JSON string
     */
    private static IdDb fromJsonString()
        throws IOException
    {
        String jsonString = IOUtils.toString(jsonUrl, "utf-8");
        return IdDb.fromJson(jsonString);
    }

    @Parameters
    public static Collection<Object[]> data()
        throws IOException
    {
        Collection<Object[]> ret = new ArrayList<Object[]>();
        ret.add(new Object[] { "global" });
        ret.add(new Object[] { "json-url" });
        ret.add(new Object[] { "json-string" });
        return ret;
    }

    @Rule
    public TestName name = new TestName();

    /**
     * Test getting types, and their names
     */
    @Test
    public void testTypesNames()
    {
        assertNotNull(iddb);
        assertEquals(5, iddb.getTypes().size());

        IdType pmid = iddb.getType("pmid");
        IdType pmcid = iddb.getType("pmcid");
        IdType mid = iddb.getType("mid");
        IdType doi = iddb.getType("doi");
        IdType aiid = iddb.getType("aiid");

        List<IdType> types = iddb.getTypes();
        assertEquals(pmid, types.get(0));
        assertEquals(pmcid, types.get(1));
        assertEquals(mid, types.get(2));
        assertEquals(doi, types.get(3));
        assertEquals(aiid, types.get(4));

        assertEquals("pmid", pmid.getName());
        assertEquals("pmcid", pmcid.getName());
        assertEquals("mid", mid.getName());
        assertEquals("doi", doi.getName());
        assertEquals("aiid", aiid.getName());

        // Check that the getType lookup is case-insensitive:
        checkGetType(iddb, pmid, "PmId");

        // Check that a bad name returns null
        checkGetType(iddb, null, "bingo");

        // So does null
        checkGetType(iddb, null, null);
    }

    /**
     * Test matching strings against patterns.
     */
    @Test
    public void testFindAndMatchType()
    {
        String id;
        IdType pmid = iddb.getType("pmid");
        IdType pmcid = iddb.getType("pmcid");
        IdType mid = iddb.getType("mid");
        IdType doi = iddb.getType("doi");
        IdType aiid = iddb.getType("aiid");

        assertEquals(pmid, iddb.findType("123456"));
        assertEquals(pmcid, iddb.findType("pMc3455"));
        assertEquals(mid, iddb.findType("pdMc3455"));
        assertEquals(doi, iddb.findType("10.1093/cercor/bhs015"));
        assertNull(iddb.findType("ukn.8778.ci095-1"));

        // Check an ID string that matches multiple types
        id = "76389932";
        List<IdType> types = iddb.findTypes(id);
        assertEquals(3, types.size());
        assert(types.contains(pmid));
        assert(types.contains(pmcid));
        assertFalse(types.contains(mid));
        assertFalse(types.contains(doi));
        assert(types.contains(aiid));
    }

    /**
     * Test constructing Identifiers in a variety of ways.
     */
    @Test
    public void testMakingIds()
    {
        IdType pmid = iddb.getType("pmid");
        IdType pmcid = iddb.getType("pmcid");
        IdType mid = iddb.getType("mid");
        IdType doi = iddb.getType("doi");
        IdType aiid = iddb.getType("aiid");

        Identifier id;

        // Make Identifiers from value strings alone

        id = iddb.id("123456");
        checkId("1.1", pmid, "123456", "pmid:123456", false, id);

        id = iddb.id("44567.8");
        checkId("1.2", pmid, "44567.8", "pmid:44567.8", true, id);

        id = iddb.id("pmC899476");
        checkId("1.3", pmcid, "PMC899476", "pmcid:PMC899476", false, id);

        id = iddb.id("PmC44567.8");
        checkId("1.4", pmcid, "PMC44567.8", "pmcid:PMC44567.8", true, id);

        id = iddb.id("Squabble3");
        checkId("1.5", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id("10.1016/j.fsi.2017.03.003");
        checkId("1.6", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid id string
        assertNull("1.7", iddb.id("7U.8*5-uuj"));

        // Same tests but using makeId(null, value)
        id = iddb.id((IdType) null, "123456");
        checkId("2.1", pmid, "123456", "pmid:123456", false, id);

        id = iddb.id((IdType) null, "44567.8");
        checkId("2.2", pmid, "44567.8", "pmid:44567.8", true, id);

        id = iddb.id((IdType) null, "pmC899476");
        checkId("2.3", pmcid, "PMC899476", "pmcid:PMC899476", false, id);

        id = iddb.id((IdType) null, "PmC44567.8");
        checkId("2.4", pmcid, "PMC44567.8", "pmcid:PMC44567.8", true, id);

        id = iddb.id((IdType) null, "Squabble3");
        checkId("2.5", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id((IdType) null, "10.1016/j.fsi.2017.03.003");
        checkId("2.6", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid id string
        id = iddb.id((IdType) null, "7U.8*5-uuj");
        assertNull("2.7", id);

        // Specify the type by name (case insensitive)

        id = iddb.id(pmid, "487365");
        checkId("3.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id(aiid, "487365");
        checkId("3.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id(pmcid, "84786");
        checkId("3.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "84786.1");
        checkId("3.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(pmcid, "PmC84786");
        assertTrue(iddb.isValid("PmC84786"));
        assertTrue(iddb.isValid(pmcid, "PmC84786"));
        checkId("3.5", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "PMc84786.1");
        assertTrue(iddb.isValid("PMc84786.1"));
        assertTrue(iddb.isValid(pmcid, "PMc84786.1"));
        checkId("3.6", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(mid, "Squabble3");
        assertTrue(iddb.isValid("Squabble3"));
        checkId("3.7", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id(doi, "10.1016/j.fsi.2017.03.003");
        checkId("3.8", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid string
        id = iddb.id(pmcid, "PPMC77684");
        assertNull("3.10", id);

        // Specify the type with IdType objects

        id = iddb.id(pmid, "487365");
        checkId("4.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id(aiid, "487365");
        checkId("4.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id(pmcid, "84786");
        checkId("4.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "84786.1");
        checkId("4.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(pmcid, "PMc84786");
        checkId("4.5", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "PmC84786.1");
        checkId("4.6", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(mid, "Squabble3");
        checkId("4.7", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id(doi, "10.1016/j.fsi.2017.03.003");
        checkId("4.8", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid string
        id = iddb.id(pmcid, "PPMC77684");
        assertNull("4.9", id);


        // Specify the type with prefixes (case insensitive)

        id = iddb.id("pmid:487365");
        checkId("5.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id("aIId:487365");
        checkId("5.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id("PMCid:84786");
        checkId("5.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id("pmcId:84786.1");
        checkId("5.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("pmcid:PMc84786");
        checkId("5.5", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id("PMCID:PmC84786.1");
        checkId("5.6", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("Mid:Squabble3");
        checkId("5.7", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id("doi:10.1016/j.fsi.2017.03.003");
        checkId("5.8", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid string
        id = iddb.id("pmcid:PPMC77684");
        assertNull("5.9", id);

        // Some combinations of type and prefixes

        id = iddb.id(pmid, "pmid:487365");
        checkId("6.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id(aiid, "aIId:487365");
        checkId("6.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id(pmcid, "PMCid:84786");
        checkId("6.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "pmcId:84786.1");
        checkId("6.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(pmid, "pmcid:PMc84786");
        assertNull("6.5", id);

        id = iddb.id(mid, "pmcid:pmc84786.1");
        assertNull("6.6", id);
    }

    ///////////////////////////////////////////////////////////////////
    // Helper functions

    public void checkGetType(IdDb iddb, IdType exp, String testStr) {
        // check getType
        IdType got0 = iddb.getType(testStr);
        assertEquals(exp, got0);

        // check lookupType
        boolean exception = false;
        try {
            IdType got1 = iddb.lookupType(testStr);
            assertEquals(exp, got1);
        }
        catch (IllegalArgumentException e) { exception = true; }
        assertEquals(exception, exp == null && testStr != null);
    }
}

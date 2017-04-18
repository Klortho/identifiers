package gov.ncbi.ids.test;

import static gov.ncbi.ids.IdDbJsonReader.getJsonFeatures;
import static gov.ncbi.ids.RequestId.State.*;
import static gov.ncbi.ids.test.TestRequestId.checkState;
import static gov.ncbi.ids.Id.IdScope.EXPRESSION;
import static gov.ncbi.test.TestHelper.assertThrows;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import gov.ncbi.ids.Id;
import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdResolver;
import gov.ncbi.ids.IdSet;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.Identifier;
import gov.ncbi.ids.NonVersionedIdSet;
import gov.ncbi.ids.RequestId;
import gov.ncbi.ids.VersionedIdSet;


/**
 * This uses a mocked Jackson ObjectMapper for testing the calls to the
 * external ID resolver service. The responses are given in JSON files in
 * src/test/resources/id-resolver-mock-responses/.
 */
public class TestIdResolver
{
    private static final ClassLoader loader = TestIdResolver.class.getClassLoader();
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TestIdResolver.class);

    @Rule
    public TestName name = new TestName();

    // These point to the global static literature id db.
    private IdDb iddb;
    private IdResolver resolver;
    private static IdType aiid;
    private static IdType doi;
    private static IdType mid;
    private static IdType pmcid;
    private static IdType pmid;

    // Real ObjectMapper for reading JSON from local filesystem
    static final ObjectMapper realMapper = new ObjectMapper()
            .enable(getJsonFeatures());

    /////////////////////////////////////////////////////////////
    // Set up the mocked ObjectMapper

    // Mock ObjectMapper
    static ObjectMapper mockMapper;

    /**
     * Cache for mocked json responses. The keys are the file basenames of the
     * files in src/test/resources/resolver-mock-responses. This gets cleared
     * for every test.
     */
    static Map<String, JsonNode> mockResponseCache;

    /**
     * This function returns a JsonNode from a mock-response file, given
     * the file's name.
     * @param name  Just the bare name of the JSON file, without path
     *   or extension
     */
    static ObjectNode getMockResponse(String name)
        throws IOException
    {
        JsonNode resp = null;
        if (mockResponseCache.containsKey(name)) {
            resp = mockResponseCache.get(name);
        }
        else {
            String path = "resolver-mock-responses/" + name + ".json";
            URL url = loader.getResource(path);
            if (url == null) throw new IllegalArgumentException(
                "Error in the tests somewhere: unable to find the " +
                "mock-response file at '" + path + "'");
            log.trace("Fetching JSON from mock location " + url);
            resp = realMapper.readTree(url);
            mockResponseCache.put(name, resp);
        }
        return (ObjectNode) resp;
    }

    /**
     * This cross-references the pattern that we look for in the query string
     * to the name of the JSON file containing the mocked response.
     */
    static final String[][] mockUrlPatterns = new String[][] {
        new String[] {
            "idtype=pmid&ids=26829486,22368089", "two-good-pmids"
        },
        new String[] {
            "idtype=pmid&ids=26829486,7777", "one-good-one-bad-pmid"
        },
        new String[] {
            "idtype=pmid&ids=22368089,1,26829486", "set0"
        },
        new String[] {
            "idtype=pmcid&ids=PMC3539452", "one-pmcid"
        },
        new String[] {
            "idtype=pmid&ids=22222", "bad-response2"
        },
        new String[] {
            "idtype=pmid&ids=33333", "bad-response3"
        },
        new String[] {
            "idtype=pmid&ids=44444", "bad-response4"
        },
        new String[] {
            "idtype=pmid&ids=26829486", "pmid-26829486"
        }
    };

    /**
     * This function takes a URL and attempts to match it against the patterns
     * above.
     * @return The name of the pattern. This will not return null.
     * @throws IllegalArgumentException if the URL can't be matched.
     */
    static String getUrlPattern(URL url) {
        for (String[] pair : mockUrlPatterns) {
            String pattern = pair[0];
            String pname = pair[1];
            String q = url.getQuery();
            if (q != null && q.matches("^.*" + pattern + ".*$")) {
                return pname;
            }
        }
        // We should have a mock for every URL that's generated by the tests
        throw new IllegalArgumentException(
            "Unable to find a mock response for the URL " + url);
    }

    /////////////////////////////////////////////////////////////////
    // Initializer and its helpers. The initList() doesn't get
    // invoked automatically (via the `@Before` annotation)
    // because the tests do not all have the same requirements.

    /**
     * Before every test, clear out any lingering cache data.
     */
    @Before
    public void initialize() {
        ConfigFactory.invalidateCaches();
    }

    /**
     * Initialize with the literature id database with the default config.
     */
    public void initLit()
        throws Exception
    {
        initLit(null);
    }

    /**
     * Initialize with the default config, but a different wanted IdType
     */
    public void initLit(String wanted)
        throws Exception
    {
        Config defaults = ConfigFactory.load();
        Config config = wanted == null ? defaults :
            defaults.withValue("ncbi.ids.resolver.wanted-type",
                ConfigValueFactory.fromAnyRef(wanted));

        //log.trace("getting literature id database with config: " + config);
        iddb = IdDb.getLiteratureIdDb(config);

        resolver = iddb.newResolver();

        // For convenience:
        pmid = iddb.getType("pmid");
        pmcid = iddb.getType("pmcid");
        mid = iddb.getType("mid");
        doi = iddb.getType("doi");
        aiid = iddb.getType("aiid");

        // Reset the cache with every test
        mockResponseCache = new HashMap<String, JsonNode>();

        // This is the mock object
        mockMapper = mock(ObjectMapper.class);
        resolver.setMapper(mockMapper);

        // Intercept calls to readTree()
        when(mockMapper.readTree((URL) any())).thenAnswer(
            new Answer<JsonNode>() {
                @Override
                public JsonNode answer(InvocationOnMock invocation)
                        throws Throwable
                {
                    Object[] args = invocation.getArguments();
                    URL url = (URL) args[0];
                    String pname = getUrlPattern(url);
                    if (pname == null) return null;
                    return getMockResponse(pname);
                }
            }
        );
    }

    /////////////////////////////////////////////////////////////
    // Tests

    /**
     * Test the IdResolver class constructors and data access methods.
     */
    @Test
    public void testConstructor()
        throws Exception
    {
        // When using the constructor directly, you must supply a valid
        // IdDb object
        assertThrows(IllegalArgumentException.class,
            () -> {
                @SuppressWarnings("unused")
                IdResolver r0 = new IdResolver(null);
            });

        // Use constructor directly, default config
        IdDb dummy = new IdDb("dummy");
        IdResolver r1 = new IdResolver(dummy);
        assertEquals(86400, r1.getConfig().getInt("ncbi.ids.cache.ttl"));

        // Override a config setting
        Config override = ConfigFactory.parseString("ncbi.ids.cache.ttl=42");
        IdResolver r2 = new IdResolver(dummy, override);
        assertEquals(42, r2.getConfig().getInt("ncbi.ids.cache.ttl"));

        // Test the custom initialization routine above
        initLit();
        assertSame(iddb, resolver.getIdDb());
        assertEquals(aiid, resolver.getWantedType());

        log.debug("Config: " + resolver.getConfig());
    }

    /**
     * Test the test -- make sure the mocking works.
     */
    @Test
    public void testMock()
        throws Exception
    {
        //assertThrows(IllegalArgumentException.class, )

        URL badUrl = new URL("file:///broken/url");
        assertThrows(IllegalArgumentException.class,
                () -> getUrlPattern(badUrl)
            );


        URL url = new URL("http://ex.com/?idtype=pmid&ids=26829486,22368089");
        ObjectNode resp = (ObjectNode) mockMapper.readTree(url);

        TextNode status = (TextNode) resp.get("status");
        assertEquals("ok", status.asText());
        TextNode respDate = (TextNode) resp.get("responseDate");
        assertEquals("2017-03-01 16:31:53", respDate.asText());

        ArrayNode records = (ArrayNode) resp.get("records");
        assertNotNull(records);
        assertEquals(2, records.size());
        ObjectNode record0 = (ObjectNode) records.get(0);

        TextNode pmcIdent = (TextNode) record0.get("pmcid");
        assertEquals("PMC3539452", pmcIdent.asText());
    }

    /**
     * Test that the list of RequestIds is created
     */
    @Test
    public void testParseRequestIds()
        throws Exception
    {
        initLit();
        List<RequestId> rids;

        rids = resolver.parseRequestIds("pMid", "12345,67890");
        assertEquals(2, rids.size());
        RequestId rid0 = rids.get(0);
        assertEquals(UNKNOWN, rid0.getState());
        assertTrue(rid0.isWellFormed());
        assertFalse(rid0.isResolved());
        assertEquals(false, rid0.isGood());
        assertEquals("pMid", rid0.getRequestedType());
        assertEquals("12345", rid0.getRequestedValue());
        assertEquals(pmid.id("12345"), rid0.getQueryId());

        // mixed types
        rids = resolver.parseRequestIds(null, "PMC6788,845763,NIHMS99878,PMC778.4");
        assertEquals(4, rids.size());
        assertEquals(pmcid, rids.get(0).getQueryIdType());
        assertEquals(pmid, rids.get(1).getQueryIdType());
        assertEquals(mid, rids.get(2).getQueryIdType());
        assertEquals(pmcid, rids.get(3).getQueryIdType());

        // some non-well-formed
        rids = resolver.parseRequestIds("pmcid", "PMC6788,845763,NIHMS99878,PMC778.4");
        assertEquals(4, rids.size());
        assertEquals(pmcid.id("PMC6788"), rids.get(0).getQueryId());
        assertEquals(pmcid.id("PMC845763"), rids.get(1).getQueryId());
        assertEquals(NOT_WELL_FORMED, rids.get(2).getState());
        assertEquals(pmcid.id("PMC778.4"), rids.get(3).getQueryId());
    }

    /**
     * Helper function to verify the contents of a List of RequestIds.
     */
    public void
    checkGroup(IdType type, Map<IdType, List<RequestId>> groups, String ...exp)
    {
        List<RequestId> reqIds = groups.get(type);
        assertNotNull(reqIds);

        List<String> group = reqIds.stream()
            .map(rid -> rid.getMainCurie())
            .collect(Collectors.toList());
        assertEquals(Arrays.asList(exp), group);
    }

    /**
     * Test the `groupsToResolve()` function, that divides the List
     * of RequestIds into groups, where each group is suitable for
     * sending to the resolver service in one HTTP request.
     */
    @Test
    public void testGroupsToResolve()
        throws Exception
    {
        initLit();
        List<RequestId> rids;
        log.debug("iddb: " + iddb);

        RequestId rrid = new RequestId(iddb, "pmcid", "1234");
        IdSet rset = new NonVersionedIdSet(iddb);
        rset.add(pmcid.id("pmc1234"), doi.id("10.12/34/56"));
        rrid.resolve(rset);

        rids = Arrays.asList(
            new RequestId(iddb, "pmid", "34567"),    //  0 - has wanted
            new RequestId(iddb, "pmcid", "77898"),   //  1
            (RequestId) null,                           //  2 - null
            new RequestId(iddb, "mid", "MID7"),      //  3
            new RequestId(iddb, "pmcid", "77a898"),  //  4 - bad
            new RequestId(iddb, "pmid", "34567.5"),  //  5 - has wanted
            new RequestId(iddb, "aiid", "77898"),    //  6
            new RequestId(iddb, "pmcid", "34567"),   //  7
            rrid,                                      //  8 - already resolved
            new RequestId(iddb, "blech", "77898"),   //  9 - bad
            new RequestId(iddb, "pmcid", "77898.1"), // 10
            new RequestId(iddb, "aiid", "778")       // 11
        );

        Map<IdType, List<RequestId>> groups = resolver.groupsToResolve(rids);
        assertEquals(3, groups.entrySet().size());

        // Don't have to resolve the wanted type
        assertNull(groups.get(aiid));
        checkGroup(pmid, groups, "pmid:34567", "pmid:34567.5");
        checkGroup(pmcid, groups,
            "pmcid:PMC77898", "pmcid:PMC34567", "pmcid:PMC77898.1");
        checkGroup(mid, groups, "mid:MID7");

        //checkGroup(aiid, groups, "aiid:77898", "aiid:778");
    }

    /**
     * Test the resolverUrl() method, that generates a URL to the resolver
     * service, given a list of request IDs.
     */
    @Test
    public void testResolverUrl()
        throws Exception
    {
        initLit();
        IdType fromType;
        List<RequestId> rids;
        URL requestURL;

        // Get the URL for two pmids that both need resolving
        fromType = pmid;
        rids = Arrays.asList(
            new RequestId(iddb, "pmid", "34567"),
            new RequestId(iddb, "pmid", "77898")
        );
        log.trace("rids: " + rids);
        requestURL = resolver.resolverUrl(fromType, rids);
        log.trace("resolver URL: " + requestURL);
        assertTrue(requestURL.toString().endsWith(
            "idtype=pmid&ids=34567,77898"));

        // Verify some exceptions
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolverUrl(pmcid, null)
        );

        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolverUrl(aiid, new ArrayList<RequestId>())
        );

        rids = Arrays.asList(
                new RequestId(iddb, "pmcid", "123.34"),
                new RequestId(iddb, "pmcid", "13.4"),
                new RequestId(iddb, "pmcid", "333"),
                new RequestId(iddb, "pmcid", "12")
        );
        requestURL = resolver.resolverUrl(fromType, rids);
        assertTrue(requestURL.toString().endsWith(
            "idtype=pmcid&ids=PMC123.34,PMC13.4,PMC333,PMC12"));
    }

    /**
     * Uses two-good-pmids.json.
     * Verify that this generates good IdSets from a JSON response
     * of the ID resolver. Also tests that the `current` field can
     * be either a string or a boolean.
     */
    @Test
    public void testRecordFromJson()
        throws Exception
    {
        initLit();
        ObjectNode jsonResp = getMockResponse("two-good-pmids");
        ArrayNode records = (ArrayNode) jsonResp.get("records");
        assertEquals(2, records.size());

        ObjectNode recordA = (ObjectNode) records.get(0);
        NonVersionedIdSet parentA =
            resolver.readIdSet(recordA);
        assertFalse(parentA.isVersioned());
        assertEquals("PMC3539452", parentA.getId(pmcid).getValue());

        // check the versioned kids
        List<VersionedIdSet> kidsA = parentA.getVersions();
        assertEquals(1, kidsA.size());
        VersionedIdSet kidA0 = kidsA.get(0);
        assertTrue(kidA0.isCurrent());
        assertSame(parentA.getCurrent(), kidA0);
        assertEquals(pmcid.id("PMC3539452.1"), kidA0.getId(pmcid));

        NonVersionedIdSet parentB =
            resolver.readIdSet(
            records.get(1));
        assertEquals("26829486", parentB.getId(pmid).getValue());
        List<VersionedIdSet> kidsB = parentB.getVersions();
        assertEquals(kidsB.get(0), parentB.getCurrent());
    }

    /**
     * Uses three-versions.json. Tests that a good IdSet "family"
     * is created for an article that has three separate versions.
     */
    @Test
    public void testIdSetFromJson1()
            throws Exception
    {
        initLit();
        ObjectNode jsonResp = getMockResponse("three-versions");
        ArrayNode records = (ArrayNode) jsonResp.get("records");
        assertEquals(1, records.size());

        NonVersionedIdSet parent =
            resolver.readIdSet(
            records.get(0));
        List<VersionedIdSet> kids = parent.getVersions();
        assertEquals(3, kids.size());

        // check the current
        VersionedIdSet kid0 = kids.get(0),
                       kid1 = kids.get(1),
                       kid2 = kids.get(2);
        assertFalse(kid0.isCurrent());
        assertFalse(kid1.isCurrent());
        assertTrue(kid2.isCurrent());
        assertSame(kid2, parent.getCurrent());
        assertSame(kid2, parent.getComplement());
        assertSame(parent, kid2.getComplement());

        assertEquals(mid.id("NIHMS20955"), kid0.getId(mid));
        assertEquals(aiid.id("1950588"), kid1.getId(aiid));
        assertEquals(pmcid.id("PMC1868567.3"), kid2.getId(pmcid));
    }

    /**
     * Tests the "current" field in the JSON response.
     */
    @Test
    public void testBadResponse0()
        throws Exception
    {
        initLit("doi");

        ObjectNode jsonResp = getMockResponse("bad-response0");

        assertThrows(IOException.class, () -> {
            resolver.readIdSet(jsonResp.get("records").get(0));
        });

        assertThrows(IOException.class, () -> {
            resolver.readIdSet(jsonResp.get("records").get(1));
        });

        assertThrows(IOException.class, () -> {
            resolver.readIdSet(jsonResp.get("records").get(2));
        });
}

    /**
     * Test a response from the id converter that doesn't conform
     * to the expected schema.
     */
    @Test
    public void testBadResponse1()
        throws Exception
    {
        initLit("doi");

        ObjectNode jsonResp = getMockResponse("bad-response1");
        assertThrows(IOException.class, () -> {
            resolver.readIdSet(jsonResp.get("records").get(0));
        });
    }

    /**
     * Test a response from the converter that reports an error
     * was encountered in the converter.
     */
    @Test
    public void testBadResponse2()
        throws Exception
    {
        initLit("doi");
        List<RequestId> ridList = resolver.resolveIds("22222");
        // Verify that we get one RequestId, but that it doesn't have a doi
        assertEquals(1, ridList.size());
        assertFalse(ridList.get(0).hasType(doi));
    }

    /**
     * Test a completely bad response.
     */
    @Test
    public void testBadResponse3()
        throws Exception
    {
        initLit("doi");
        assertThrows(JsonProcessingException.class,
            () -> resolver.resolveIds("33333"));
    }

    /**
     * Test a response that's missing a status field
     */
    @Test
    public void testBadResponse4()
        throws Exception
    {
        initLit("doi");
        List<RequestId> ridList = resolver.resolveIds("44444");
        assertEquals(1, ridList.size());
        assertFalse(ridList.get(0).hasType(doi));
    }


    /**
     * Verify that if we create an IdResolver that wants to resolve every ID
     * to pmcids, and feed it a list of pmids, that it will call the resolver
     * service on them.
     */
    @Test
    public void testIdResolver_0()
        throws Exception
    {
        initLit("pmcid");
        assertEquals(pmcid, resolver.getWantedType());

        List<RequestId> ridList = resolver.resolveIds("26829486,22368089");
        assertEquals(2, ridList.size());

        RequestId rid0 = ridList.get(0);
        assertTrue(rid0.hasType(pmid) && rid0.hasType(pmcid));
        assertEquals("PMC4734780", rid0.getId(pmcid).getValue());
        checkState("rid0", GOOD, rid0);

        RequestId rid1 = ridList.get(1);
        assertTrue(rid0.hasType(pmid) && rid1.hasType(pmcid));
        assertEquals("PMC3539452", rid1.getId(pmcid).getValue());
        checkState("rid1", GOOD, rid1);
    }

    /**
     * Verify that we get the same results when the list is peppered with
     * some bad IDs.
     */
    @Test
    public void testIdResolver_1()
        throws Exception
    {
        initLit("pmcid");
        resolver.setMapper(mockMapper);
        assertEquals(pmcid, resolver.getWantedType());

        List<RequestId> ridList =
            resolver.resolveIds("fleegle,22368089,1,26829486");
        assertEquals(4, ridList.size());

        RequestId rid0 = ridList.get(0);
        checkState("Non-well-formed identifier `fleegle`", NOT_WELL_FORMED, rid0);

        RequestId rid1 = ridList.get(1);
        checkState("pmid:22368089", GOOD, rid1);

        RequestId rid2 = ridList.get(2);
        checkState("pmid:1", GOOD, rid2);

        RequestId rid3 = ridList.get(3);
        checkState("pmid:26829486", GOOD, rid3);
    }

    /**
     * Verify that if IDs already have the "wanted" type, then they don't
     * get sent to the resolver.
     */
    @Test
    public void testWantedIds()
            throws Exception
    {
        initLit();
        assertEquals(aiid, resolver.getWantedType());

        List<RequestId> ridList = resolver.resolveIds(
            "26829486,PMC3539452,aiid:3539450");
        //List<RequestId> ridList = resolver.resolveIds("26829486");
        assertEquals(3, ridList.size());

        //------
        RequestId rid0 = ridList.get(0);
        assertNull(rid0.getQueryType());
        assertEquals("26829486", rid0.getQueryValue());
        assertEquals(pmid.id("26829486"), rid0.getQueryId());
        assertEquals(pmid, rid0.getQueryIdType());
        assertEquals("26829486", rid0.getQueryIdValue());
        assertTrue(rid0.hasType(pmid));

        IdSet idSet0 = rid0.getIdSet();
        log.trace("idSet: " + idSet0);

        Identifier pmid0 = rid0.getId(pmid);
        assertNotNull(pmid0);
        assertEquals("26829486", pmid0.getValue());

        assertTrue(rid0.hasType(aiid));
        assertEquals(GOOD, rid0.getState());
        checkState("rid0", GOOD, rid0);
        Identifier aiid0 = rid0.getId(aiid);
        assertNotNull(aiid0);
        assertEquals("4734780", aiid0.getValue());
        checkState("rid0", GOOD, rid0);

        //------
        RequestId rid1 = ridList.get(1);
        assertNull(rid1.getQueryType());
        assertEquals("PMC3539452", rid1.getQueryValue());
        assertEquals(pmcid.id("PMC3539452"), rid1.getQueryId());
        assertEquals(pmcid, rid1.getQueryIdType());
        assertEquals("PMC3539452", rid1.getQueryIdValue());
        assertTrue(rid1.hasType(pmcid));

        IdSet idSet1 = rid1.getIdSet();
        log.trace("idSet: " + idSet1);

        Identifier pmcid1 = rid1.getId(pmcid);
        assertNotNull(pmcid1);
        assertEquals("PMC3539452", pmcid1.getValue());

        assertTrue(rid1.hasType(aiid));
        assertEquals(GOOD, rid1.getState());
        checkState("rid1", GOOD, rid1);
        Identifier aiid1 = rid1.getId(aiid);
        assertNotNull(aiid1);
        assertEquals("3539452", aiid1.getValue());
        checkState("rid1", GOOD, rid1);

        //------  aiid:1324
        RequestId rid2 = ridList.get(2);
        //assertEquals("aiid", rid2.getQueryType());
        assertEquals("aiid:3539450", rid2.getQueryValue());
        assertEquals(aiid.id("3539450"), rid2.getQueryId());
        assertEquals(aiid, rid2.getQueryIdType());
        assertEquals("3539450", rid2.getQueryIdValue());
        assertTrue(rid2.hasType(aiid));

        IdSet idSet2 = rid2.getIdSet();
        log.trace("idSet: " + idSet2);

        Identifier aiid2 = rid2.getId(aiid);
        assertNotNull(aiid2);
        assertEquals("3539450", aiid2.getValue());

        assertTrue(rid2.hasType(aiid));
        assertEquals(UNKNOWN, rid2.getState());
        checkState("rid2", UNKNOWN, rid2);
    }
}

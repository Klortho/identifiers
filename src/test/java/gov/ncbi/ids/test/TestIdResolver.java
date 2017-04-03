package gov.ncbi.ids.test;

import static gov.ncbi.ids.IdDbJsonReader.jsonFeatures;
import static gov.ncbi.ids.RequestId.State.*;
import static gov.ncbi.ids.test.TestRequestId.checkState;
import static gov.ncbi.testing.TestHelper.assertThrows;
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdResolver;
import gov.ncbi.ids.IdSet;
import gov.ncbi.ids.IdType;
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
            .enable(jsonFeatures);

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
            URL url = loader.getResource("resolver-mock-responses/" +
                name + ".json");
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
        }
    };

    /**
     * This function takes a URL and attempts to match it against the patterns
     * above.
     * @return The name of the pattern, or null if none is found.
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
        return null;
    }

    /////////////////////////////////////////////////////////////////
    // Test initializers. These don't use the `@Before` annotation,
    // because some of the tests will have slightly different
    // requirements, and I don't know how to parameterize the
    // @Before.

    /**
     * Initialize with the literature id database, and default config.
     */
    public void initLit()
        throws Exception
    {
        initLit(null, null);
    }

    /**
     * Initialize with the default config, but a different wantedIdType
     */
    public void initLit(String wanted)
        throws Exception
    {
        initLit(null, wanted);
    }

    /**
     * Helper to override the `wants-type` configuration value.
     */
    private Config confWants(Config conf, String wants) {
        // default config - deal with the case when conf==null:
        Config dconf = conf == null ? ConfigFactory.load() : conf;
        // Create a new Config object for the single value 'wants-type':
        Config wconf = ConfigFactory.parseString("ncbi-ids.wants-type=" + wants);
        // Effective config:
        Config econf = wconf.withFallback(dconf);
        log.trace("Effective config: " + econf.root().render());
        return econf;
    }

    /**
     * Initialize the iddb with the literature id database, with the
     * given config, but overriding wantedIdType
     */
    // default config (src/main/resources/reference.conf).
    public void initLit(Config config, String wanted)
        throws Exception
    {
        iddb = IdDb.getLiteratureIdDb(confWants(config, wanted));
        resolver = iddb.newResolver();

        // for convenience:
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
    // Test constructor and its helpers

    /**
     * Test the IdResolver class constructors and data access methods.
     */
    @Test
    public void testConstructor()
        throws Exception
    {
        initLit();
        assertTrue(iddb == resolver.getIdDb());
        assertEquals(pmid, resolver.getWantedType());

        log.debug(resolver.dumpConfig());
    }

    /////////////////////////////////////////////////////////////
    // Test the JSON mocking feature.

    /**
     * Test the test -- make sure the mocking works.
     */
    @Test
    public void testMock()
        throws Exception
    {
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

    /////////////////////////////////////////////////////////////
    // Test each method/function in the pipeline

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
        assertEquals(pmid.id("12345"), rid0.getMainId());

        // mixed types
        rids = resolver.parseRequestIds(null, "PMC6788,845763,NIHMS99878,PMC778.4");
        assertEquals(4, rids.size());
        assertEquals(pmcid, rids.get(0).getMainType());
        assertEquals(pmid, rids.get(1).getMainType());
        assertEquals(mid, rids.get(2).getMainType());
        assertEquals(pmcid, rids.get(3).getMainType());

        // some non-well-formed
        rids = resolver.parseRequestIds("pmcid", "PMC6788,845763,NIHMS99878,PMC778.4");
        assertEquals(4, rids.size());
        assertEquals(pmcid.id("PMC6788"), rids.get(0).getMainId());
        assertEquals(pmcid.id("PMC845763"), rids.get(1).getMainId());
        assertEquals(NOT_WELL_FORMED, rids.get(2).getState());
        assertEquals(pmcid.id("PMC778.4"), rids.get(3).getMainId());
    }

    /**
     * Helper function to verify the contents of a List of RequestIds.
     */
    public void
    checkGroup(IdType type, Map<IdType, List<RequestId>> groups, String ...exp)
    {
        List<String> group = groups.get(type).stream()
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
        checkGroup(pmcid, groups,
            "pmcid:PMC77898", "pmcid:PMC34567", "pmcid:PMC77898.1");
        checkGroup(mid, groups, "mid:MID7");
        checkGroup(aiid, groups, "aiid:77898", "aiid:778");
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
     * Tests the "current" field.
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
     * A record that has more than bad ID values should fail.
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

    @Test
    public void testIdResolver_0()
        throws Exception
    {
        initLit("pmcid");
        assertEquals(pmcid, resolver.getWantedType());

        List<RequestId> ridList = resolver.resolveIds("26829486,22368089");
        assertEquals(2, ridList.size());

        RequestId rid0 = ridList.get(0);
        assertEquals("PMC4734780", rid0.getId(pmcid).getValue());
        RequestId rid1 = ridList.get(1);
        assertEquals("PMC3539452", rid1.getId(pmcid).getValue());
    }

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

        checkState("For `fleegle`", NOT_WELL_FORMED, ridList.get(0));
        checkState("For `22368089`", GOOD, ridList.get(1));
        checkState("For `1`", INVALID, ridList.get(2));
        checkState("For `22368089,1,26829486`", GOOD, ridList.get(3));
    }
}

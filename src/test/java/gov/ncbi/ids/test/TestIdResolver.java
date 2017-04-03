package gov.ncbi.ids.test;

import static com.fasterxml.jackson.core.JsonParser.Feature.*;
import static gov.ncbi.ids.RequestId.State.NOT_WELL_FORMED;
import static gov.ncbi.ids.RequestId.State.UNKNOWN;
import static gov.ncbi.testing.TestHelper.assertThrows;

import static gov.ncbi.ids.IdDbJsonReader.jsonFeatures;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
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

import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdResolver;
import gov.ncbi.ids.IdSet;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.NonVersionedIdSet;
import gov.ncbi.ids.RequestId;
import gov.ncbi.ids.RequestId.MaybeBoolean;
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
    private static IdDb litIds = null;
    private static IdType aiid;
    private static IdType doi;
    private static IdType mid;
    private static IdType pmcid;
    private static IdType pmid;

    // Real ObjectMapper for reading JSON from local filesystem
    static final ObjectMapper realMapper = new ObjectMapper()
            .enable(jsonFeatures);


    private final ObjectMapper mapper = new ObjectMapper()
            .enable(jsonFeatures);

    // Mock ObjectMapper
    static ObjectMapper mockMapper;

    /**
     * Cache for mocked json responses. The keys are the file basenames of the
     * files in src/test/resources/resolver-mock-responses. This gets cleared
     * for every test.
     */
    static Map<String, JsonNode> mockResponseCache;


    /////////////////////////////////////////////////////////////
    // Methods related to mocking responses.

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


    @Before
    public void initialize()
        throws Exception
    {
        litIds = IdDb.litIds();           // pmid, pmcid, mid, doi, aiid
        pmid = litIds.getType("pmid");
        pmcid = litIds.getType("pmcid");
        mid = litIds.getType("mid");
        doi = litIds.getType("doi");
        aiid = litIds.getType("aiid");

        // Reset the cache with every test
        mockResponseCache = new HashMap<String, JsonNode>();

        // This is the mock object
        mockMapper = mock(ObjectMapper.class);

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
        IdResolver resolver = new IdResolver(litIds, pmid);
        assertTrue(litIds == resolver.getIdDb());
        assertEquals(pmid, resolver.getWantedType());

        log.debug(resolver.getConfig());
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
        ObjectNode record1 = (ObjectNode) records.get(1);
        log.debug("record0: " + record0);
        log.debug("record1: " + record1);

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
        IdResolver resolver = new IdResolver(litIds, pmid);
        List<RequestId> rids;

        rids = resolver.parseRequestIds("pMid", "12345,67890");
        assertEquals(2, rids.size());
        RequestId rid0 = rids.get(0);
        assertEquals(UNKNOWN, rid0.getState());
        assertTrue(rid0.isWellFormed());
        assertFalse(rid0.isResolved());
        assertEquals(MaybeBoolean.MAYBE, rid0.isGood());
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
        log.debug("should be non-well-formed: " + rids.get(1));
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
        IdResolver resolver = new IdResolver(litIds, pmid);

        List<RequestId> rids;

        RequestId rrid = new RequestId(litIds, "pmcid", "1234");
        IdSet rset = new NonVersionedIdSet(litIds);
        rset.add(pmcid.id("pmc1234"), doi.id("10.12/34/56"));
        rrid.resolve(rset);

        rids = Arrays.asList(
            new RequestId(litIds, "pmid", "34567"),    //  0 - has wanted
            new RequestId(litIds, "pmcid", "77898"),   //  1
            (RequestId) null,                          //  2 - null
            new RequestId(litIds, "mid", "MID7"),      //  3
            new RequestId(litIds, "pmcid", "77a898"),  //  4 - bad
            new RequestId(litIds, "pmid", "34567.5"),  //  5 - has wanted
            new RequestId(litIds, "aiid", "77898"),    //  6
            new RequestId(litIds, "pmcid", "34567"),   //  7
            rrid,                                      //  8 - already resolved
            new RequestId(litIds, "blech", "77898"),   //  9 - bad
            new RequestId(litIds, "pmcid", "77898.1"), // 10
            new RequestId(litIds, "aiid", "778")       // 11
        );

        Map<IdType, List<RequestId>> groups = resolver.groupsToResolve(rids);
        System.out.println(groups);
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
        IdResolver resolver = new IdResolver(litIds, pmid);

        IdType fromType;
        List<RequestId> rids;
        URL requestURL;

        // Get the URL for two pmids that both need resolving
        fromType = pmid;
        rids = Arrays.asList(
            new RequestId(litIds, "pmid", "34567"),
            new RequestId(litIds, "pmid", "77898")
        );
        log.debug("rids: " + rids);
        requestURL = resolver.resolverUrl(fromType, rids);
        log.debug("resolver URL: " + requestURL);
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
                new RequestId(litIds, "pmcid", "123.34"),
                new RequestId(litIds, "pmcid", "13.4"),
                new RequestId(litIds, "pmcid", "333"),
                new RequestId(litIds, "pmcid", "12")
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
        IdResolver resolver = new IdResolver(litIds, pmcid);
        ObjectNode jsonResp = getMockResponse("two-good-pmids");
        ArrayNode records = (ArrayNode) jsonResp.get("records");
        assertEquals(2, records.size());

        ObjectNode recordA = (ObjectNode) records.get(0);
        NonVersionedIdSet parentA =
            resolver.readIdSet(recordA);
        log.debug("From two-good-pmids.json, parentA: " +
            parentA.dump());
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
        IdResolver resolver = new IdResolver(litIds, doi);
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
        IdResolver resolver = new IdResolver(litIds, doi);
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
        IdResolver resolver = new IdResolver(litIds, doi);
        ObjectNode jsonResp = getMockResponse("bad-response1");

        assertThrows(IOException.class, () -> {
            resolver.readIdSet(jsonResp.get("records").get(0));
        });
    }


    @Test
    public void testIdResolver_0()
        throws Exception
    {
        IdResolver resolver = new IdResolver(litIds, pmcid, mockMapper);
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
        IdResolver resolver = new IdResolver(litIds, pmcid, mockMapper);
        assertEquals(pmcid, resolver.getWantedType());

        List<RequestId> ridList =
            resolver.resolveIds("fleegle,22368089,1,26829486");
        assertEquals(4, ridList.size());

        RequestId rid0 = ridList.get(0);
        assertFalse(rid0.isWellFormed());

        RequestId rid1 = ridList.get(1);
        assertEquals("PMC3539452", rid1.getId(pmcid).getValue());

        RequestId rid2 = ridList.get(2);
        assertTrue(rid2.isWellFormed());
        assertTrue(rid2.isResolved());
        assertEquals(MaybeBoolean.FALSE, rid2.isGood());

    }



        /*

        ridList =  resolver.resolveIds("26829486,7777");

        RequestId rid0, rid1;
        IdGlob idg0, idg1;
        IdResolver resolver;

        resolver = new IdResolver();
        RequestIdList idList = null;
        try {
            idList = resolver.resolveIds("PMC3362639,Pmc3159421");
        }
        catch(Exception e) {
            fail("Got an Exception: " + e.getMessage());
        }
        assertEquals(2, idList.size());

        // Check the first ID
        rid0 = idList.get(0);
        assertEquals("pmcid", rid0.getType());
        assertEquals("PMC3362639", rid0.getRequestedValue());
        assertEquals("pmcid:PMC3362639", rid0.getId().toString());

        idg0 = rid0.getIdGlob();
        assertNotNull(idg0);
        assertFalse(idg0.isVersioned());
        assertEquals("aiid:3362639", idg0.getIdByType("aiid").toString());
        assertEquals("doi:10.1371/journal.pmed.1001226", idg0.getIdByType("doi").toString());
        assertNull(idg0.getIdByType("pmid"));

        // Check the second ID
        rid1 = idList.get(1);
        assertEquals("pmcid", rid1.getType());
        assertEquals("Pmc3159421", rid1.getRequestedValue());
        assertEquals("pmcid:PMC3159421", rid1.getId().toString());
        idg1 = rid1.getIdGlob();
        assertNotNull(idg1);
        assertFalse(idg1.isVersioned());
        assertEquals("aiid:3159421", idg1.getIdByType("aiid").toString());
        assertEquals("doi:10.4242/BalisageVol7.Maloney01", idg1.getIdByType("doi").toString());
        assertEquals("pmid:21866248", idg1.getIdByType("pmid").toString());
      */
}

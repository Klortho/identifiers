package gov.ncbi.ids.test;

import static gov.ncbi.ids.RequestId.State.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.mockito.Mockito.*;
import org.mockito.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdResolver;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.RequestId;
import gov.ncbi.ids.RequestId.MaybeBoolean;


/**
 * This uses a mocked Jackson ObjectMapper for testing the calls to the
 * external ID resolver service. The responses are given in JSON files in
 * src/test/resources/id-resolver-mock-responses/.
 */
public class TestIdResolver
{
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


    //////////////////////////////////////////////////////////////////
    // Utilities for mocking the JSON service

    // Create and initialize the real ObjectMapper.
    static final ObjectMapper realMapper = new ObjectMapper();

    /// This function reads a mocked response from a JSON file
    static JsonNode mockResolverResponse(String name)
        throws IOException
    {
        URL url = TestIdResolver.class.getClassLoader()
            .getResource("id-resolver-mock-responses/" + name + ".json");
        return realMapper.readTree(url);
    }

    /**
     * A custom ArgumentMatcher used with Mockito that checks the argument to
     * the mocked ObjectMapper's readTree() method (which is a URL), looking
     * for a part of the query string.
     */
    static class IdArgMatcher implements ArgumentMatcher<URL> {
        private String _expectedStr;
        public IdArgMatcher(String expectedStr) {
            _expectedStr = expectedStr;
        }
        @Override
        public boolean matches(URL url) {
            if (url == null || url.getQuery() == null) return false;
            return url.getQuery().matches("^.*" + _expectedStr + ".*$");
        }
        @Override
        public String toString() {
            return "[URL expected to contain " + _expectedStr + "]";
        }
    }

    /**
     * This cross-references the pattern that we look for in the query string
     * to the name of the JSON file containing the mocked response.
     */
    static final String[][] resolverNodes = new String[][] {
        new String[] { "idtype=pmid&ids=26829486,22368089", "two-good-pmids" },
        new String[] { "idtype=pmid&ids=26829486,7777", "one-good-one-bad-pmid" },
    };

    static ObjectMapper mockMapper = mock(ObjectMapper.class);

    // FIXME: this needs to be in a function; fix this.
  /*
    static {
        for (String[] pair : resolverNodes) {
            String pattern = pair[0];
            String name = pair[1];
            try {
                when(mockMapper.readTree(argThat(new IdArgMatcher(pattern))))
                    .thenReturn(mockResolverResponse(name));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
  */

    //////////////////////////////////////////////////////////////////////
    // Initialization

    @Before
    public void initialize() {
        litIds = IdDb.litIds();           // pmid, pmcid, mid, doi, aiid
        pmid = litIds.getType("pmid");
        pmcid = litIds.getType("pmcid");
        mid = litIds.getType("mid");
        doi = litIds.getType("doi");
        aiid = litIds.getType("aiid");
    }

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

    public void
    checkGroup(IdType type, Map<IdType, List<RequestId>> groups, String ...exp)
    {
        List<String> group = groups.get(type).stream()
            .map(rid -> rid.getMainCurie())
            .collect(Collectors.toList());
        assertEquals(Arrays.asList(exp), group);
    }

    @Test
    public void testGroupsToResolve()
        throws Exception
    {
        IdResolver resolver = new IdResolver(litIds, pmid);

        List<RequestId> rids;

        rids = Arrays.asList(
                new RequestId(litIds, "pmid", "34567"),    // has wanted
                new RequestId(litIds, "pmcid", "77898"),
                new RequestId(litIds, "mid", "MID7"),
                new RequestId(litIds, "pmcid", "77a898"),  // bad
                new RequestId(litIds, "pmid", "34567.5"),  // has wanted
                new RequestId(litIds, "aiid", "77898"),
                new RequestId(litIds, "pmcid", "34567"),
                new RequestId(litIds, "blech", "77898"),   // bad
                new RequestId(litIds, "pmcid", "77898.1"),
                new RequestId(litIds, "aiid", "778")
        );

        Map<IdType, List<RequestId>> groups = resolver.groupsToResolve(rids);
        System.out.println(groups);
        assertEquals(3, groups.entrySet().size());
        checkGroup(pmcid, groups,
            "pmcid:PMC77898", "pmcid:PMC34567", "pmcid:PMC77898.1");
        checkGroup(mid, groups, "mid:MID7");
        checkGroup(aiid, groups, "aiid:77898", "aiid:778");
    }

    @Test
    public void testResolverUrl()
        throws Exception
    {
        IdResolver resolver = new IdResolver(litIds, pmid);

        IdType fromType;
        List<RequestId> rids;
        URL requestURL;

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

        // FIXME: Test some versioned ones
        rids = Arrays.asList(
                new RequestId(litIds, "pmcid", "123.34"),
                new RequestId(litIds, "pmcid", "13.4"),
                new RequestId(litIds, "pmcid", "333"),
                new RequestId(litIds, "pmcid", "12")
        );
        requestURL = resolver.resolverUrl(fromType, rids);
        log.debug("===============================");
        log.debug("resolver URL: " + requestURL);
        assertTrue(requestURL.toString().endsWith(
            "idtype=pmcid&ids=PMC123.34,PMC13.4,PMC333,PMC12"));
    }

    @Ignore
    @Test
    public void testGlobbify()
        throws Exception
    {
        IdResolver resolver = new IdResolver(litIds, pmid);

        JsonNode record = mockResolverResponse("basic-one-kid");
      /*
        IdSet parent = resolver.recordFromJson((ObjectNode) record, null);
        log.debug("parent: " + parent.toString());

        // Note that the `aiid` at the top level is ignored
        assertTrue(parent.sameId(pmcid.id("PMC4734780")));
        assertTrue(parent.sameId(pmid.id("26829486")));
        assertTrue(parent.sameId(doi.id("10.1371/journal.pntd.0004413")));

        assertTrue(parent.sameWork(pmcid.id("4734780.1")));
        assertTrue(parent.sameWork(aiid.id("4734780")));
      */
    }

    @Ignore
    @Test
    public void testIdResolver()
        throws Exception
    {
        IdResolver resolver = new IdResolver(litIds, pmcid, mockMapper);
        assertEquals(pmcid, resolver.getWantedType());

    /*
        List<RequestId> ridList = resolver.resolveIds("26829486,22368089");
        assertEquals(2, ridList.size());

        RequestId rid0 = ridList.get(0);
        assert(rid0.same(pmid.id("26829485")));
        assert(rid0.same(pmcid.id("PMC4734780")));
        assert(rid0.same("pmcid:PMC4734780"));
        assert(rid0.same("PMC4734780"));
        assert(rid0.same("pmcid:4734780"));
        assert(rid0.same("pmcid:4734780"));

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
}

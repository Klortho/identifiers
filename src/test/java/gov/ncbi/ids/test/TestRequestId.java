package gov.ncbi.ids.test;

import static gov.ncbi.ids.RequestId.B.FALSE;
import static gov.ncbi.ids.RequestId.B.MAYBE;
import static gov.ncbi.ids.RequestId.B.TRUE;
import static gov.ncbi.ids.RequestId.State.GOOD;
import static gov.ncbi.ids.RequestId.State.INVALID;
import static gov.ncbi.ids.RequestId.State.NOT_WELL_FORMED;
import static gov.ncbi.ids.RequestId.State.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdSet;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.Identifier;
import gov.ncbi.ids.NonVersionedIdSet;
import gov.ncbi.ids.RequestId;
import gov.ncbi.ids.VersionedIdSet;
import gov.ncbi.ids.RequestId.B;
import gov.ncbi.ids.RequestId.State;

public class TestRequestId
{
    private static final Logger log = LoggerFactory.getLogger(TestRequestId.class);

    @Rule
    public TestName name = new TestName();

    private IdDb litIds;
    private IdType aiid;
    private IdType doi;
    private IdType mid;
    private IdType pmcid;
    private IdType pmid;

    /////////////////////////////////////////////////////////////////////////////////////
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

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // Helpers

    /**
     * Verify the state of the RequestId object.
     */
    public static void checkState(String msg, State expState, RequestId rid)
    {
        assertEquals(msg, expState, rid.getState());

        switch (expState) {
        case NOT_WELL_FORMED:
            assertFalse(msg, rid.isWellFormed());
            assertTrue(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), FALSE);
            assertNull(msg, rid.getMainId());
            assertNull(msg, rid.getMainType());
            assertNull(msg, rid.getMainValue());
            assertNull(msg, rid.getMainCurie());
            assertNull(msg, rid.getIdSet());
            break;

        case UNKNOWN:
            assertTrue(msg, rid.isWellFormed());
            assertFalse(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), MAYBE);
            assertNotNull(msg, rid.getMainId());
            break;

        case INVALID:
            assertTrue(msg, rid.isWellFormed());
            assertTrue(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), FALSE);
            assertNotNull(msg, rid.getMainId());
            break;

        case GOOD:
            assertTrue(msg, rid.isWellFormed());
            assertTrue(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), TRUE);
            assertNotNull(msg, rid.getMainId());
            break;
        }
    }

    /**
     * Test not-well-formed
     */
    @Test
    public void testNotWellFormed() {
        RequestId rid = new RequestId(litIds, "shwartz:nothing");
        checkState("testNotWellFormed: ", NOT_WELL_FORMED, rid);
    }

    @Test
    public void testUnknown() {
        RequestId rid = new RequestId(litIds, "pMC1234");
        assertEquals(UNKNOWN, rid.getState());
        log.debug("RequestId: " + rid.toString());
        assertEquals(
            "{ requested: pMC1234, " +
                "found: pmcid:PMC1234 }",
            rid.toString());
        log.debug("  dumped: " + rid.dump());
        checkState("testUnknown: ", UNKNOWN, rid);

        assertSame(litIds, rid.getIdDb());
        assertNull(rid.getRequestedType());
        assertEquals("pMC1234", rid.getRequestedValue());
        assertEquals(litIds.id("pmcid:PMC1234"), rid.getMainId());

        assertEquals(pmcid, rid.getMainType());
        assertEquals("PMC1234", rid.getMainValue());
        assertEquals("pmcid:PMC1234", rid.getMainCurie());
        assertTrue(rid.hasType(pmcid));
        assertFalse(rid.hasType(mid));

        assertEquals(litIds.id("pmcid:PMC1234"), rid.getId(pmcid));

        assertFalse(rid.isVersioned());
        assertNull(rid.getIdSet());
    }

    @Test
    public void testInvalid() {
        boolean exceptionThrown;

        RequestId rid0 = new RequestId(litIds, "pMC1234");
        rid0.resolve(null);
        checkState("testInvalid: ", INVALID, rid0);

        exceptionThrown = false;
        try {
            rid0.resolve(null);
        }
        catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        RequestId rid1 = new RequestId(litIds, "pMC1234");
        IdSet rset = (new NonVersionedIdSet(litIds))
            .add(pmid.id("123456"),
                 pmcid.id("654321"),
                 doi.id("10.13/23434.56"));

        exceptionThrown = false;
        try {
            rid1.resolve(rset);
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testGood() {
        RequestId rid = new RequestId(litIds, "pMC1234");
        IdSet rset = (new NonVersionedIdSet(litIds))
                .add(pmid.id("123456"),
                     pmcid.id("1234"),
                     doi.id("10.13/23434.56"));
        assertTrue(rset.same(rid.getMainId()));
        rid.resolve(rset);
        checkState("testGood: ", GOOD, rid);
    }
}

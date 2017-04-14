package gov.ncbi.ids.test;

import static gov.ncbi.ids.Id.IdScope.*;
import static gov.ncbi.ids.RequestId.State.*;
import static gov.ncbi.test.TestHelper.checkEqualsMethod;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.Id.IdScope;
import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdSet;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.NonVersionedIdSet;
import gov.ncbi.ids.RequestId;
import gov.ncbi.ids.RequestId.State;

public class TestRequestId
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TestRequestId.class);

    @Rule
    public TestName name = new TestName();

    private IdDb litIds;
    private IdType pmid;
    private IdType pmcid;
    private IdType mid;
    private IdType doi;
    private IdType aiid;

    /////////////////////////////////////////////////////////////////////////////////////
    // Initialization

    @Before
    public void initialize() {
        litIds = IdDb.getLiteratureIdDb();
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
            assertEquals(msg, rid.isGood(), false);
            assertNull(msg, rid.getQueryId());
            assertNull(msg, rid.getQueryIdType());
            assertNull(msg, rid.getQueryIdValue());
            assertNull(msg, rid.getMainCurie());
            assertNull(msg, rid.getIdSet());
            break;

        case UNKNOWN:
            assertTrue(msg, rid.isWellFormed());
            assertFalse(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), false);
            assertNotNull(msg, rid.getQueryId());
            break;

        case INVALID:
            assertTrue(msg, rid.isWellFormed());
            assertTrue(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), false);
            assertNotNull(msg, rid.getQueryId());
            break;

        case GOOD:
            assertTrue(msg, rid.isWellFormed());
            assertTrue(msg, rid.isResolved());
            assertEquals(msg, rid.isGood(), true);
            assertNotNull(msg, rid.getQueryId());
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
        for (IdType t : Arrays.asList(pmid, pmcid, mid, doi, aiid)) {
            assertFalse(rid.hasType(t));
            assertNull(rid.getId(t));
            assertFalse(rid.isVersioned());
        }
        assertNull(rid.getId(Arrays.asList(doi, mid, pmcid)));

        RequestId rid1 = new RequestId(litIds, "blech", "77898");
        checkState("testNotWellFormed: ", NOT_WELL_FORMED, rid1);
        for (IdType t : Arrays.asList(pmid, pmcid, mid, doi, aiid)) {
            assertFalse(rid1.hasType(t));
            assertNull(rid1.getId(t));
            assertFalse(rid1.isVersioned());
        }
    }


    @Test
    public void testUnknown() {
        RequestId rid = new RequestId(litIds, "pMC1234");
        assertEquals(UNKNOWN, rid.getState());
        log.debug("testUnknown: rid: " + rid.toString());
        assertEquals("{ query type: none, value: pMC1234 => id: pmcid:PMC1234 }",
            rid.toString());
        checkState("testUnknown: ", UNKNOWN, rid);

        assertFalse(rid.hasType(pmid));
        assertNull(rid.getId(pmid));
        assertTrue(rid.hasType(pmcid));
        assertEquals(pmcid.id("PMC1234"), rid.getId(pmcid));
        assertFalse(rid.isVersioned());

        assertSame(litIds, rid.getIdDb());
        assertNull(rid.getRequestedType());
        assertEquals("pMC1234", rid.getRequestedValue());
        assertEquals(litIds.id("pmcid:PMC1234"), rid.getQueryId());

        assertEquals(pmcid, rid.getQueryIdType());
        assertEquals("PMC1234", rid.getQueryIdValue());
        assertEquals("pmcid:PMC1234", rid.getMainCurie());

        assertTrue(rid.hasType(pmcid));
        assertEquals(pmcid.id("PMC1234"), rid.getId(pmcid));
        assertFalse(rid.hasType(mid));
        assertNull(rid.getId(mid));

        assertEquals(litIds.id("pmcid:PMC1234"), rid.getId(pmcid));

        assertFalse(rid.isVersioned());
        assertNull(rid.getIdSet());
    }

    @Test
    public void testInvalid() {
        boolean exceptionThrown;

        // Can't resolve with null.
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

        // Try to resolve, but pmcid doesn't match
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
    public void testEquals() {
        RequestId ridA = new RequestId(litIds, "PMC1234");
        assertFalse(ridA.isVersioned());

        // Not equal to null
        assertFalse(ridA.equals(null));

        // Not equal to a matching Identifier
        assertNotEquals(ridA, pmcid.id("PMC1234"));

        // Is equal to another RequestId that's created with the exact same
        // data
        RequestId ridB = new RequestId(litIds, "PMC1234");
        assertFalse(ridB.isVersioned());

        assertEquals(ridA, ridB);
        checkEqualsMethod(ridA, ridB);

        // Not equal to one created with *almost* the same data, even though
        // the mainId's are equal.
        RequestId ridC = new RequestId(litIds, "pmcid", "PMC1234");
        assertEquals(ridA.getQueryId(), ridC.getQueryId());
        assertNotEquals(ridA, ridC);
        checkEqualsMethod("Different", ridA, ridC);
    }

    @Test
    public void testSameness()
    {
        RequestId ridA = new RequestId(litIds, "PMC1234");

        // Not the same as null
        assertFalse(ridA.same(null));
        assertFalse(ridA.same(RESOURCE, null));
        assertFalse(ridA.same(EXPRESSION, null));
        assertFalse(ridA.same(WORK, null));

        // Is the same, in every scope, to an object that is equal
        RequestId ridB = new RequestId(litIds, "PMC1234");

        assertTrue(ridA.equals(ridB));
        assertTrue(ridA.same(ridB));
        assertTrue(ridA.same(RESOURCE, ridB));
        assertTrue(ridA.same(EXPRESSION, ridB));
        assertTrue(ridA.same(WORK, ridB));

        // Not the same as a non-well-formed one
        RequestId ridC = new RequestId(litIds, "shwartz:nothing");
        assertEquals(NOT_WELL_FORMED, ridC.getState());
        for (IdScope scope : Arrays.asList(RESOURCE, EXPRESSION, WORK)) {
            assertFalse(ridA.same(scope, ridC));
            assertFalse(ridC.same(scope, ridA));
        }

        // Test sameness to a resolved rid.
        RequestId ridD = new RequestId(litIds, "PMC1234");
        IdSet rset = (new NonVersionedIdSet(litIds))
                .add(pmid.id("123456"),
                     pmcid.id("1234"),
                     doi.id("10.13/23434.56"));
        ridD.resolve(rset);
        for (IdScope scope : Arrays.asList(RESOURCE, EXPRESSION, WORK)) {
            assertTrue(ridA.same(scope, ridD));
            assertTrue(ridD.same(scope, ridA));
        }
    }

    @Test
    public void testGood() {
        RequestId rid = new RequestId(litIds, "pMC1234");
        IdSet rset = (new NonVersionedIdSet(litIds))
                .add(pmid.id("123456"),
                     pmcid.id("1234"),
                     doi.id("10.13/23434.56"));
        assertTrue(rset.same(rid.getQueryId()));
        rid.resolve(rset);
        checkState("testGood: ", GOOD, rid);

        assertFalse(rid.isVersioned());

        assertTrue(rid.hasType(pmid));
        assertEquals(pmid.id("123456"), rid.getId(pmid));

        assertTrue(rid.hasType(pmcid));
        assertEquals(pmcid.id("1234"), rid.getId(pmcid));

        assertFalse(rid.hasType(mid));

        assertTrue(rid.hasType(doi));
        assertEquals(doi.id("10.13/23434.56"), rid.getId(doi));

        assertFalse(rid.hasType(aiid));
    }
}

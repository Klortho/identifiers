package gov.ncbi.ids.test;

import static gov.ncbi.ids.Id.IdScope.EXPRESSION;
import static gov.ncbi.ids.Id.IdScope.RESOURCE;
import static gov.ncbi.ids.Id.IdScope.WORK;
import static gov.ncbi.test.TestHelper.checkEqualsMethod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.Id;
import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.Identifier;

/**
 * Test the Id and Identifier classes, primarily.
 */
public class TestIdentifier
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TestIdentifier.class);

    private IdDb litIds;    // pmid, pmcid, mid, doi, aiid
    private IdType pmid;
    private IdType pmcid;
    private IdType mid;
    @SuppressWarnings("unused")
    private IdType doi;
    private IdType aiid;

    @Rule
    public TestName name = new TestName();

    @Before
    public void initialize() {
        litIds = IdDb.getLiteratureIdDb();
        pmid = litIds.getType("pmid");
        pmcid = litIds.getType("pmcid");
        mid = litIds.getType("mid");
        doi = litIds.getType("doi");
        aiid = litIds.getType("aiid");
    }

    ////////////////////////////////////////////////////////////////////////
    // Helpers

    /**
     *  Helper function to check an Identifier's type and (canonical) value.
     */
    public static void checkId(String msg, IdType expType, String expValue,
            String expCurie, boolean expIsVersioned, Identifier id)
    {
        assertEquals(msg + "; type", expType, id.getType());
        assertEquals(msg + "; value", expValue, id.getValue());
        assertEquals(msg + "; curie", expCurie, id.getCurie());
        assertEquals(msg + "; isVersioned", expIsVersioned, id.isVersioned());
    }

    /**
     *  Helper function checks that, for two Identifiers,
     * the methods equals(), sameId(), sameExpression(), and
     * sameWork() are all equivalent.
     */
    public void checkSames(Id a, Id b) {
        if (a != null) _checkSames(a, b);
        if (b != null) _checkSames(b, a);
    }

    /**
     * This implements the checkSames() function. This function is guaranteed
     * that a != null
     */
    public void _checkSames(Id a, Id b) {
        boolean aeq = a.equals(b);
        assertEquals(aeq, a.same(b));
        assertEquals(aeq, a.same(RESOURCE, b));
        assertEquals(aeq, a.same(EXPRESSION, b));
        assertEquals(aeq, a.same(WORK, b));
        assertEquals(aeq, a.sameResource(b));
        assertEquals(aeq, a.sameExpression(b));
        assertEquals(aeq, a.sameWork(b));
    }

    ////////////////////////////////////////////////////////////////////////
    // Tests

    /**
     * Test the Identifier class.
     */
    @Test
    public void testIdentifier()
    {
        Identifier id0 = pmcid.id("PMC123456");
        checkId("id0", pmcid, "PMC123456", "pmcid:PMC123456", false, id0);

        assertEquals(pmcid, id0.getType());
        assertEquals(id0.getCurie(), id0.toString());

        Identifier id1 = litIds.id(pmcid, "pmC123456");
        checkId("id1", pmcid, "PMC123456", "pmcid:PMC123456", false, id1);
        assertEquals(id0, id1);

        Identifier id2 = litIds.id("pmcid:pMc123456");
        checkId("id2", pmcid, "PMC123456", "pmcid:PMC123456", false, id2);
        assertEquals(id0, id2);
    }

    /**
     * Test constructing and querying Identifier objects.
     */
    @Test
    public void testMakeFromIdType()
    {
        Identifier id;
        String testName = name.getMethodName();

        id = pmid.id("123456");
        checkId(testName, pmid, "123456", "pmid:123456", false, id);

        id = pmid.id("123456.7");
        checkId(testName, pmid, "123456.7", "pmid:123456.7", true, id);

        id = pmcid.id("899476");
        checkId(testName, pmcid, "PMC899476", "pmcid:PMC899476", false, id);

        id = pmcid.id("Pmc3333.4");
        checkId(testName, pmcid, "PMC3333.4", "pmcid:PMC3333.4", true, id);

        Identifier id0 = pmcid.id("PMC3333.4");
        assertEquals(testName + ": PMC3333.4 identifiers equal", id, id0);

        id = mid.id("NIHMS65432");
        checkId(testName, mid, "NIHMS65432", "mid:NIHMS65432", true, id);

        id = aiid.id("899476");
        checkId(testName, aiid, "899476", "aiid:899476", true, id);

        id = pmcid.id("ABD7887466");
        assertNull(testName, id);
    }

    /**
     * Test equals and hashCode
     */
    @Test
    public void testEquals()
    {
        Identifier x = pmcid.id("778476");
        checkEqualsMethod(x);

        Identifier y = pmcid.id("PMC778476");
        assertNotSame(x, y);
        checkEqualsMethod(x, y);

        assertFalse(x.equals("PMC778476"));

        Stream.of(pmid.id("778476"), pmcid.id("778576")).forEach(id -> {
            assertFalse(x.equals(id));
            assertFalse(id.equals(x));
            assertFalse(y.equals(id));
            assertFalse(id.equals(y));
        });
    }

    /**
     * Test the sameId(), sameExpression(), and sameWork() methods
     * when applied between two Identifiers.
     */
    @Test
    public void testSames() {
        // test the test
        checkSames(null, null);

        Identifier x = pmcid.id("123456");
        checkSames(x, null);
        checkSames(null, x);

        Identifier y0 = pmcid.id("PMC778476");
        checkSames(x, y0);

        Identifier y1 = pmid.id("778476");
        checkSames(x, y1);
    }

    private class DummyIdentifier extends Identifier {
        DummyIdentifier() {
            super(pmcid, "123456", false);
        }
    }
    private class DummyId extends Id {
        @Override
        public boolean isVersioned() { return false; }
        @Override
        public boolean equals(Object other) {
            return this == other; }
        @Override
        public boolean same(IdScope scope, Id oid) { return false; }
        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    @Test
    public void testSubclass() {
        Identifier x = pmcid.id("123456");

        DummyIdentifier d0 = new DummyIdentifier();
        assertFalse(x.equals(d0));
        checkEqualsMethod(d0, x);
        checkSames(d0, x);
        checkSames(x, d0);

        DummyId d1 = new DummyId();
        assertFalse(x.equals(d1));
        checkEqualsMethod(d1, x);
        checkSames(d1, x);
        checkSames(x, d1);
    }

    @Test
    public void testToString() {
        Identifier x = pmcid.id("123456");
        assertEquals("pmcid:PMC123456", x.toString());
    }
}

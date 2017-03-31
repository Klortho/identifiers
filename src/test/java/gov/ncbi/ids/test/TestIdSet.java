package gov.ncbi.ids.test;

import static gov.ncbi.ids.Id.IdScope.EQUAL;
import static gov.ncbi.ids.Id.IdScope.EXPRESSION;
import static gov.ncbi.ids.Id.IdScope.RESOURCE;
import static gov.ncbi.ids.Id.IdScope.WORK;
import static gov.ncbi.ids.IdSet.toList;
import static gov.ncbi.testing.TestHelper.assertThrows;
import static gov.ncbi.testing.TestHelper.checkEqualsMethod;
import static gov.ncbi.testing.TestHelper.msgAppend;
import static org.hamcrest.text.StringContainsInOrder.stringContainsInOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.Id;
import gov.ncbi.ids.Id.IdScope;
import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdSet;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.Identifier;
import gov.ncbi.ids.NonVersionedIdSet;
import gov.ncbi.ids.VersionedIdSet;

public class TestIdSet
{
    private static final Logger log = LoggerFactory.getLogger(TestIdSet.class);

    @Rule
    public TestName name = new TestName();

    private IdDb litIds;
    private IdType aiid;
    private IdType doi;
    private IdType mid;
    private IdType pmcid;
    private IdType pmid;

    private NonVersionedIdSet parent;
    private VersionedIdSet kid0;
    private VersionedIdSet kid1;
    private VersionedIdSet kid2;

    // For verification purposes, when necessary
    private Identifier[] parentIds;
    private Identifier[] kid0Ids;
    private Identifier[] kid1Ids;
    private Identifier[] kid2Ids;

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

        parentIds = new Identifier[] {
            pmid.id("123456"), pmcid.id("654321"), doi.id("10.13/23434.56") };
        kid0Ids = new Identifier[] {
            pmid.id("123456.1"), pmcid.id("654321.2") };
        kid1Ids = new Identifier[] {
            pmid.id("123456.3"), mid.id("NIHMS77876") };
        kid2Ids = new Identifier[] {
            pmcid.id("654321.8"), aiid.id("654343") };

        parent = (new NonVersionedIdSet(litIds)).add(parentIds);
        kid0 = (new VersionedIdSet(parent, false)).add(kid0Ids);
        kid1 = (new VersionedIdSet(parent, true)).add(kid1Ids);
        kid2 = (new VersionedIdSet(parent, false)).add(kid2Ids);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helpers

    /**
     * For testing, compares a Stream<IdSets> with a variable-sized
     * argument list of IdSets.
     */
    public void chkSetStream(String msg, Stream<IdSet> uut, IdSet... expect) {
        String message = "Failed while checking contents of an IdSetStream:\n" + (msg == null ? "" : msg);
        List<IdSet> expected = Arrays.asList(expect);
        List<IdSet> actual = uut.collect(Collectors.toList());
        assertEquals(message, expected, actual);
    }

    /**
     * Allows comparing a Stream<Identifier> with a variable-length argument
     * list of Strings. The strings are converted into Identifiers.
     */
    public static void chkIdStream(IdDb iddb, String msg,
            Stream<Identifier> uut, String... expect)
    {
        List<Identifier> expected = iddb.idList(expect);
        List<Identifier> actual = uut.collect(Collectors.toList());

        log.debug("chkIdStream:");
        log.debug("  expected: " + expected);
        log.debug("  actual: " + actual);

        assertEquals(msg, expected, actual);
    }

    /**
     * This verifies that to Stream<Identifiers>s produce the same contents.
     */
    public static void chkIdStream(String msg, Stream<Identifier> expected, Stream<Identifier> actual)
    {
        assertEquals(msg, expected.collect(Collectors.toList()),
                          actual.collect(Collectors.toList()));
    }

    /**
     *  Make a Stream<Identifier> from an array of Strings
     */
    public Stream<Identifier> makeIdStream(String[] strings) {
        return Stream.of(strings).map(str -> {
            Identifier id = litIds.id(str);
            if (id == null) throw new RuntimeException("Bad");
            return id;
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests

    /**
     * Test the test utilities first
     */
    @Test
    public void testTestUtils()
    {
        boolean thrown;

        String[] refStrings = new String[] {
            "pmid:4456", "pmcid:8765", "mid:NIHMS89", "aiid:998"
        };
        Identifier[] refIds = new Identifier[] {
            pmid.id("4456"), pmcid.id("8765"), mid.id("NIHMS89"), aiid.id("998")
        };
        chkIdStream(litIds, null, Stream.of(refIds), refStrings);
        chkIdStream(litIds, null, makeIdStream(refStrings), refStrings);

        thrown = false;
        try {
            chkIdStream(litIds, null,
                Stream.concat(Stream.of(aiid.id("0")), Stream.of(refIds)),
                refStrings
            );
        }
        catch(AssertionError err) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    /**
     * Test the constructor and factory methods - non-versioned sets.
     */
    @Test
    public void testConstructorNoVer()
    {
        NonVersionedIdSet uut = new NonVersionedIdSet(litIds);
        log.debug(uut.dump());

        assertFalse(uut.isVersioned());
        assertEquals(0, uut.getVersions().size());
        assertNull(uut.getCurrent());

        Identifier pmidId = pmid.id("123456");
        Identifier pmcidId = pmcid.id("654321");
        Identifier doiId = doi.id("10.12/23/45");
        List<Identifier> expList = Arrays.asList(pmidId, pmcidId, doiId);

        uut.add(pmidId, pmcidId, doiId);
        log.debug(uut.dump());
        assertTrue(uut.hasType(pmid));
        assertTrue(uut.hasType(pmcid));
        assertTrue(uut.hasType(doi));
        assertFalse(uut.hasType(mid));
        assertFalse(uut.hasType(aiid));

        assertEquals(pmidId, uut.getId(pmid));
        assertEquals(pmcidId, uut.getId(pmcid));
        assertEquals(doiId, uut.getId(doi));
        assertNull(uut.getId(mid));
        assertNull(uut.getId(aiid));

        assertEquals(expList, uut.ids());
        assertEquals(expList, toList(uut.idStream()));

        // test that adding the same id again does nothing
        uut.add(pmcidId);
        assertEquals(expList, uut.ids());

        // test that you can't add a different id of the same type
        assertThrows(IllegalArgumentException.class,
            () -> uut.add(pmcid.id("87656")));

        // verify that you can't add an id with wrong version-specificity
        assertThrows(IllegalArgumentException.class,
            () -> uut.add(mid.id("NIHMS88756")));
    }

    /**
     * Test the constructor and factory methods of a version-specific IdSet.
     */
    @Test
    public void testConstructorVer()
    {
        NonVersionedIdSet parent = new NonVersionedIdSet(litIds);
        VersionedIdSet uut = new VersionedIdSet(parent, false);
        assertTrue(uut.isVersioned());
        assertSame(parent, uut.getParent());
        assertFalse(uut.isCurrent());
        assertNull(uut.getCurrent());

        Identifier pmidId = pmid.id("123456.1");
        Identifier pmcidId = pmcid.id("654321.2");
        Identifier midId = mid.id("NIHms12345");
        List<Identifier> expList = Arrays.asList(pmidId, pmcidId, midId);

        uut.add(pmidId, pmcidId, midId);
        assertTrue(uut.hasType(pmid));
        assertTrue(uut.hasType(pmcid));
        assertFalse(uut.hasType(doi));
        assertTrue(uut.hasType(mid));
        assertFalse(uut.hasType(aiid));

        assertEquals(pmidId, uut.getId(pmid));
        assertEquals(pmcidId, uut.getId(pmcid));
        assertNull(uut.getId(doi));
        assertEquals(midId, uut.getId(mid));
        assertNull(uut.getId(aiid));

        assertEquals(expList, uut.ids());
        assertEquals(expList, toList(uut.idStream()));

        // test that adding the same id again does nothing
        uut.add(pmcidId);
        assertEquals(3, toList(uut.idStream()).size());

        // test that you can't add another id of the same type
        assertThrows(IllegalArgumentException.class,
            () -> uut.add(pmcid.id("87656.3")));

        // verify that you can't add an id with wrong version-specificity
        assertThrows(IllegalArgumentException.class,
            () -> uut.add(doi.id("10.13/23434.56")));
    }

    /**
     * Test toString()
     */
    @Test
    public void testToString()
    {
        log.debug("parent: " + parent.toString());
        log.debug("kid0: " + kid0.toString());
        log.debug("kid1: " + kid1.toString());
        log.debug("kid2: " + kid2.toString());

        assertThat(parent.toString(), stringContainsInOrder(Arrays.asList(
            "pmid:123456", "pmcid:PMC654321", "10.13/23434.56")));

        log.debug("parent: " + parent.dump());
        log.debug("kid0: " + kid0.dump());
        log.debug("kid1: " + kid1.dump());
        log.debug("kid2: " + kid2.dump());
    }

    /**
     * Test some of the basic methods linking non-version-specific parents and
     * version-specific children.
     */
    @Test
    public void testParentsAndKids()
    {
        log.debug("parent.dump: " + parent.dump());
        log.debug("NonVersionedIdSet parent: " + parent.toString());
        log.debug("IdVersionSet kid0: " + kid0.toString());
        log.debug("IdVersionSet kid1: " + kid1.toString());
        log.debug("IdVersionSet kid2: " + kid2.toString());

        assertFalse(parent.isVersioned());
        assertTrue(kid0.isVersioned());
        assertTrue(kid1.isVersioned());
        assertTrue(kid2.isVersioned());

        assertSame(kid1, parent.getComplement());
        assertNull(kid0.getComplement());
        assertSame(parent, kid1.getComplement());
        assertNull(kid2.getComplement());

        assertSame(kid1, parent.getCurrent());
        assertSame(kid1, kid0.getCurrent());
        assertSame(kid1, kid1.getCurrent());
        assertSame(kid1, kid2.getCurrent());
    }

    /**
     * Test the NonVersionedIdSet::kidsStream() method.
     */
    @Test
    public void testGetKidsStream()
    {
        chkSetStream(null, parent.kidsStream(), kid1, kid2, kid0);
    }

    /**
     * Test the NonVersionedIdSet::workSetStream method, which implements
     * setStream(WORK)
     */
    @Test
    public void testWorkSetStream() {
        chkSetStream(null, parent.workSetStream(), parent, kid1, kid2, kid0);
        // Verify you can get it again
        chkSetStream(null, parent.workSetStream(), parent, kid1, kid2, kid0);
        chkSetStream(null, kid0.workSetStream(), kid0, parent, kid1, kid2);
        chkSetStream(null, kid1.workSetStream(), kid1, parent, kid2, kid0);
        chkSetStream(null, kid2.workSetStream(), kid2, parent, kid1, kid0);
    }

    /**
     * This tests making Streams of IdSets, in each of the three "scopes"
     * (RESOURCE, EXPRESSION, and WORK) for a very simple case: just one
     * non-version-specific IdSet.
     */
    @Test
    public void testSolitarySetStreams()
    {
        IdSet p = new NonVersionedIdSet(litIds);
        Identifier pmidId = pmid.id("123456");
        Identifier pmcidId = pmcid.id("654321");
        Identifier doiId = doi.id("10.12/23/45");

        //log.debug("Before: parent = " + parent);
        p.add(pmidId, pmcidId, doiId);
        //log.debug("After: parent = " + parent);

        // identity, instance, and work are all the same - there is
        // only one
        chkSetStream(null, p.setStream(), p);
        chkSetStream(null, p.setStream(RESOURCE), p);
        chkSetStream(null, p.setStream(EXPRESSION), p);
        chkSetStream(null, p.setStream(WORK), p);
        chkSetStream(null, p.resourceSetStream(), p);
        chkSetStream(null, p.expressionSetStream(), p);
        chkSetStream(null, p.workSetStream(), p);
    }

    /**
     * This tests making Streams of IdSets, in each of the three "scopes"
     * (RESOURCE, EXPRESSION, and WORK) for a cluster.
     */
    @Test
    public void testClusteredSetStreams()
    {
        chkSetStream(null, parent.setStream(), parent);
        chkSetStream(null, parent.setStream(RESOURCE), parent);
        chkSetStream(null, parent.setStream(EXPRESSION), parent, kid1);
        chkSetStream(null, parent.setStream(WORK), parent, kid1, kid2, kid0);

        chkSetStream(null, kid0.setStream(), kid0);
        chkSetStream(null, kid0.setStream(RESOURCE), kid0);
        chkSetStream(null, kid0.setStream(EXPRESSION), kid0);
        chkSetStream(null, kid0.setStream(WORK), kid0, parent, kid1, kid2);

        chkSetStream(null, kid1.setStream(), kid1);
        chkSetStream(null, kid1.setStream(RESOURCE), kid1);
        chkSetStream(null, kid1.setStream(EXPRESSION), kid1, parent);
        chkSetStream(null, kid1.setStream(WORK), kid1, parent, kid2, kid0);

        chkSetStream(null, kid2.setStream(), kid2);
        chkSetStream(null, kid2.setStream(RESOURCE), kid2);
        chkSetStream(null, kid2.setStream(EXPRESSION), kid2);
        chkSetStream(null, kid2.setStream(WORK), kid2, parent, kid1, kid0);
    }


    /**
     */
    @Test
    public void testIdStreams()
    {
        chkIdStream(litIds, null, parent.idStream(), "123456", "PMC654321", "10.13/23434.56");
        chkIdStream(litIds, null, kid0.idStream(), "123456.1",   "PMC654321.2");
        chkIdStream(litIds, null, kid1.idStream(), "123456.3",   "NIHMS77876");
        chkIdStream(litIds, null, kid2.idStream(), "PMC654321.8", "aiid:654343");

        chkIdStream(null, parent.idStream(), parent.idStream((IdType) null));
        chkIdStream(null, kid0.idStream(), kid0.idStream((IdType) null));
        chkIdStream(null, kid1.idStream(), kid1.idStream((IdType) null));
        chkIdStream(null, kid2.idStream(), kid2.idStream((IdType) null));

        chkIdStream(null, parent.idStream(), parent.idStream(RESOURCE));
        chkIdStream(null, kid0.idStream(), kid0.idStream(RESOURCE));
        chkIdStream(null, kid1.idStream(), kid1.idStream(RESOURCE));
        chkIdStream(null, kid2.idStream(), kid2.idStream(RESOURCE));

        chkIdStream(null, parent.idStream(), parent.idStream(RESOURCE, null));
        chkIdStream(null, kid0.idStream(), kid0.idStream(RESOURCE, null));
        chkIdStream(null, kid1.idStream(), kid1.idStream(RESOURCE, null));
        chkIdStream(null, kid2.idStream(), kid2.idStream(RESOURCE, null));

        chkIdStream(litIds, null, parent.idStream(EXPRESSION),
            "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56",
            "pmid:123456.3", "mid:NIHMS77876");
        chkIdStream(litIds, null, kid0.idStream(EXPRESSION),
            "pmid:123456.1", "pmcid:PMC654321.2");
        chkIdStream(litIds, null, kid1.idStream(EXPRESSION),
            "pmid:123456.3", "mid:NIHMS77876", "pmid:123456",
            "pmcid:PMC654321", "doi:10.13/23434.56");
        chkIdStream(litIds, null, kid2.idStream(EXPRESSION), "PMC654321.8", "aiid:654343");

        chkIdStream(null, parent.idStream(EXPRESSION), parent.idStream(EXPRESSION, null));
        chkIdStream(null, kid0.idStream(EXPRESSION), kid0.idStream(EXPRESSION, null));
        chkIdStream(null, kid1.idStream(EXPRESSION), kid1.idStream(EXPRESSION, null));
        chkIdStream(null, kid2.idStream(EXPRESSION), kid2.idStream(EXPRESSION, null));

        chkIdStream(litIds, null, parent.idStream(WORK),
            "123456", "PMC654321", "10.13/23434.56", "pmid:123456.3", "mid:NIHMS77876",
            "pmcid:PMC654321.8", "aiid:654343", "pmid:123456.1", "pmcid:PMC654321.2");
        chkIdStream(litIds, null, kid0.idStream(WORK), "123456.1", "PMC654321.2",
                "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56", "pmid:123456.3",
                "mid:NIHMS77876", "pmcid:PMC654321.8", "aiid:654343");
        chkIdStream(litIds, null, kid1.idStream(WORK), "123456.3", "NIHMS77876",
                "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56", "pmcid:PMC654321.8",
                "aiid:654343", "pmid:123456.1", "pmcid:PMC654321.2");
        chkIdStream(litIds, null, kid2.idStream(WORK), "PMC654321.8", "aiid:654343",
            "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56", "pmid:123456.3",
            "mid:NIHMS77876", "pmid:123456.1", "pmcid:PMC654321.2");

        chkIdStream(null, parent.idStream(WORK), parent.idStream(WORK, null));
        chkIdStream(null, kid0.idStream(WORK), kid0.idStream(WORK, null));
        chkIdStream(null, kid1.idStream(WORK), kid1.idStream(WORK, null));
        chkIdStream(null, kid2.idStream(WORK), kid2.idStream(WORK, null));
    }

    public List<Identifier> filter(IdType type, List<Identifier> list) {
        return list.stream().filter(id -> id.getType().equals(type))
            .collect(Collectors.toList());
    }

    @Test
    public void testIdLists() {

        List<Identifier> expParent = litIds.idList("123456", "PMC654321", "10.13/23434.56");
        List<Identifier> expKid0 = litIds.idList("123456.1", "PMC654321.2");
        List<Identifier> expKid1 = litIds.idList("123456.3", "NIHMS77876");
        List<Identifier> expKid2 = litIds.idList("PMC654321.8", "aiid:654343");

        assertEquals(expParent, parent.ids());
        assertEquals(expKid0, kid0.ids());
        assertEquals(expKid1, kid1.ids());
        assertEquals(expKid2, kid2.ids());

        assertEquals(expParent, parent.ids(RESOURCE));
        assertEquals(expKid0, kid0.ids(RESOURCE));
        assertEquals(expKid1, kid1.ids(RESOURCE));
        assertEquals(expKid2, kid2.ids(RESOURCE));

        assertEquals(expParent, parent.ids(RESOURCE, null));
        assertEquals(expKid0, kid0.ids(RESOURCE, null));
        assertEquals(expKid1, kid1.ids(RESOURCE, null));
        assertEquals(expKid2, kid2.ids(RESOURCE, null));

        assertEquals(filter(pmid, expParent), parent.ids(RESOURCE, pmid));
        assertEquals(filter(pmcid, expParent), parent.ids(RESOURCE, pmcid));
        assertEquals(filter(mid, expParent), parent.ids(RESOURCE, mid));
        assertEquals(filter(doi, expParent), parent.ids(RESOURCE, doi));
        assertEquals(filter(aiid, expParent), parent.ids(RESOURCE, aiid));

        assertEquals(filter(pmid, expParent), parent.ids(pmid));
        assertEquals(filter(pmcid, expParent), parent.ids(pmcid));
        assertEquals(filter(mid, expParent), parent.ids(mid));
        assertEquals(filter(doi, expParent), parent.ids(doi));
        assertEquals(filter(aiid, expParent), parent.ids(aiid));


        assertEquals(litIds.id("123456"), parent.id(RESOURCE, pmid));
        assertEquals(litIds.id("PMC654321"), parent.id(RESOURCE, pmcid));
        assertEquals(null, parent.id(RESOURCE, mid));
        assertEquals(litIds.id("10.13/23434.56"), parent.id(RESOURCE, doi));
        assertEquals(null, parent.id(RESOURCE, aiid));

        assertEquals(litIds.id("123456"), parent.id(pmid));
        assertEquals(litIds.id("PMC654321"), parent.id(pmcid));
        assertEquals(null, parent.id(mid));
        assertEquals(litIds.id("10.13/23434.56"), parent.id(doi));
        assertEquals(null, parent.id(aiid));

        assertEquals(litIds.id("123456"), parent.id());
        assertEquals(litIds.id("123456"), parent.id(RESOURCE));





        List<Identifier> expParentExpr =
            litIds.idList("pmid:123456", "pmcid:PMC654321",
                "doi:10.13/23434.56", "pmid:123456.3", "mid:NIHMS77876");
        List<Identifier> expKid0Expr =
            litIds.idList("pmid:123456.1", "pmcid:PMC654321.2");
        List<Identifier> expKid1Expr =
            litIds.idList("pmid:123456.3", "mid:NIHMS77876", "pmid:123456",
                "pmcid:PMC654321", "doi:10.13/23434.56");
        List<Identifier> expKid2Expr =
            litIds.idList("PMC654321.8", "aiid:654343");

        assertEquals(expParentExpr, parent.ids(EXPRESSION));
        assertEquals(expKid0Expr, kid0.ids(EXPRESSION));
        assertEquals(expKid1Expr, kid1.ids(EXPRESSION));
        assertEquals(expKid2Expr, kid2.ids(EXPRESSION));

        assertEquals(expParentExpr, parent.ids(EXPRESSION, null));
        assertEquals(expKid0Expr, kid0.ids(EXPRESSION, null));
        assertEquals(expKid1Expr, kid1.ids(EXPRESSION, null));
        assertEquals(expKid2Expr, kid2.ids(EXPRESSION, null));

        List<Identifier> expParentWork = litIds.idList(
            "123456", "PMC654321", "10.13/23434.56", "pmid:123456.3", "mid:NIHMS77876",
            "pmcid:PMC654321.8", "aiid:654343", "pmid:123456.1", "pmcid:PMC654321.2");

        List<Identifier> expKid0Work = litIds.idList(
                "123456.1", "PMC654321.2",
                "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56", "pmid:123456.3",
                "mid:NIHMS77876", "pmcid:PMC654321.8", "aiid:654343");

        List<Identifier> expKid1Work = litIds.idList(
                "123456.3", "NIHMS77876",
                "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56", "pmcid:PMC654321.8",
                "aiid:654343", "pmid:123456.1", "pmcid:PMC654321.2");

        List<Identifier> expKid2Work = litIds.idList(
                "PMC654321.8", "aiid:654343",
            "pmid:123456", "pmcid:PMC654321", "doi:10.13/23434.56", "pmid:123456.3",
            "mid:NIHMS77876", "pmid:123456.1", "pmcid:PMC654321.2");

        assertEquals(expParentWork, parent.ids(WORK));
        assertEquals(expKid0Work, kid0.ids(WORK));
        assertEquals(expKid1Work, kid1.ids(WORK));
        assertEquals(expKid2Work, kid2.ids(WORK));

        assertEquals(expParentWork, parent.ids(WORK, null));
        assertEquals(expKid0Work, kid0.ids(WORK, null));
        assertEquals(expKid1Work, kid1.ids(WORK, null));
        assertEquals(expKid2Work, kid2.ids(WORK, null));

        // Also test getting a list filtered by type
        assertEquals(litIds.idList("PMC654321"), parent.ids(pmcid));
        assertEquals(litIds.idList("PMC654321", "PMC654321.8", "PMC654321.2"),
            parent.ids(WORK, pmcid));
    }

    /**
     * Run some tests on equals() and the same() methods. `sameness` indicates to
     * what degree the ids should be expected to agree.
     */
    public void checkSameness(SameTestData record)
    {
        String nameA = record.nameA;
        String nameB = record.nameB;
        Id idA = record.idA;
        Id idB = record.idB;
        IdScope sameness = record.sameness;

        log.debug("Checking sameness of " + nameA + " and " + nameB);
        log.debug("  " + nameA + ": " + idA);
        log.debug("  " + nameB + ": " + idB);
        log.debug("  sameness should be: " + sameness);

        String msg = "  " + nameA + " <==> " + nameB + ": ";
        checkEqualsMethod(msgAppend(msg, "equality tests: "), idA, idB);

        for (IdScope scope : IdScope.values()) {
            boolean theSame;
            if (scope == EQUAL) {
                theSame = idA.equals(idB);
            }
            else if (scope.ordinal() <= WORK.ordinal())
            {
                theSame = idA.same(scope, idB);
                assertEquals(msgAppend(msg, "scope " + scope + " symmetric: "),
                    theSame, idB.same(scope, idA));
            }
            else {
                theSame = true;
            }
            assertEquals(msgAppend(msg, "sameness doesn't match scope " + scope + ": "),
                sameness.ordinal() <= scope.ordinal(), theSame);
        }
    }

    public class SameTestData {
        String nameA, nameB;
        Id idA, idB;
        IdScope sameness;
        SameTestData(String nameA, String nameB, Id idA, Id idB, IdScope sameness) {
            this.nameA = nameA; this.nameB = nameB;
            this.idA = idA; this.idB = idB;
            this.sameness = sameness;
        }
    }

    /**
     * Test equals()
     */
    @Test
    public void testSames() {
        // For reference, here is what the main test cluster (parent, ...) is:
        //   { pmid:123456, pmcid:PMC654321, doi:10.13/23434.56,
        //       versions: [
        //        { pmid:123456.1, pmcid:PMC654321.2 },
        //       *{ pmid:123456.3, mid:NIHMS77876 },
        //        { pmcid:PMC654321.8, aiid:654343 } ] }


        // Meet the bizarro family

        // parentz is identical
        Identifier[] parentzIds = new Identifier[] {
            pmid.id("123456"), pmcid.id("654321"), doi.id("10.13/23434.56") };
        // as is kidz0
        Identifier[] kidz0Ids = new Identifier[] {
            pmid.id("123456.1"), pmcid.id("654321.2") };
        // kidz1 shares an ID with kid1, but also adds a new one
        Identifier[] kidz1Ids = new Identifier[] {
            pmcid.id("654321.7"), mid.id("NIHMS77876") };
        // kidz2 is missing!
        // kidz3 is new:
        Identifier[] kidz3Ids = new Identifier[] {
            pmcid.id("PMC654321.9"), mid.id("NIHMS77877"), aiid.id("654344") };

        NonVersionedIdSet parentz = (new NonVersionedIdSet(litIds)).add(parentzIds);
        VersionedIdSet kidz0 = (new VersionedIdSet(parentz, false)).add(kidz0Ids);
        VersionedIdSet kidz1 = (new VersionedIdSet(parentz, true)).add(kidz1Ids);
        VersionedIdSet kidz3 = (new VersionedIdSet(parentz, false)).add(kidz3Ids);

        assertTrue(kid1.same(EXPRESSION, parent));

        SameTestData[] tests = new SameTestData[] {
            new SameTestData("parent", "parent",  parent, parent,  EQUAL),
            new SameTestData("parent", "kid0",    parent, kid0,    WORK),
            new SameTestData("parent", "kid1",    parent, kid1,    EXPRESSION),
            new SameTestData("parent", "kid2",    parent, kid2,    WORK),
            new SameTestData("parent", "parentz", parent, parentz, EQUAL),
            new SameTestData("parent", "kidz0",   parent, kidz0,   WORK),
            new SameTestData("parent", "kidz1",   parent, kidz1,   EXPRESSION),
            new SameTestData("parent", "kidz3",   parent, kidz3,   WORK),

            new SameTestData("kid0",   "parent",  kid0,   parent,  WORK),
            new SameTestData("kid0",   "kid0",    kid0,   kid0,    EQUAL),
            new SameTestData("kid0",   "kid1",    kid0,   kid1,    WORK),
            new SameTestData("kid0",   "kid2",    kid0,   kid2,    WORK),
            new SameTestData("kid0",   "parentz", kid0,   parentz, WORK),
            new SameTestData("kid0",   "kidz0",   kid0,   kidz0,   EQUAL),
            new SameTestData("kid0",   "kidz1",   kid0,   kidz1,   WORK),
            new SameTestData("kid0",   "kidz3",   kid0,   kidz3,   WORK),


            new SameTestData("kid1",   "parent",  kid1,   parent,  EXPRESSION),
            new SameTestData("kid1",   "kid0",    kid1,   kid0,    WORK),
            new SameTestData("kid1",   "kid1",    kid1,   kid1,    EQUAL),
            new SameTestData("kid1",   "kid2",    kid1,   kid2,    WORK),
            new SameTestData("kid1",   "parentz", kid1,   parentz, EXPRESSION),
            new SameTestData("kid1",   "kidz0",   kid1,   kidz0,   WORK),
            new SameTestData("kid1",   "kidz1",   kid1,   kidz1,   RESOURCE),
            new SameTestData("kid1",   "kidz3",   kid1,   kidz3,   WORK),

            new SameTestData("kid2",   "parent",  kid2,   parent,  WORK),
            new SameTestData("kid2",   "kid0",    kid2,   kid0,    WORK),
            new SameTestData("kid2",   "kid1",    kid2,   kid1,    WORK),
            new SameTestData("kid2",   "kid2",    kid2,   kid2,    EQUAL),
            new SameTestData("kid2",   "parentz", kid2,   parentz, WORK),
            new SameTestData("kid2",   "kidz0",   kid2,   kidz0,   WORK),
            new SameTestData("kid2",   "kidz1",   kid2,   kidz1,   WORK),
            new SameTestData("kid2",   "kidz3",   kid2,   kidz3,   WORK),

            new SameTestData("parentz",   "parent",  parentz,   parent,  EQUAL),
            new SameTestData("parentz",   "kid0",    parentz,   kid0,    WORK),
            new SameTestData("parentz",   "kid1",    parentz,   kid1,    EXPRESSION),
            new SameTestData("parentz",   "kid2",    parentz,   kid2,    WORK),
            new SameTestData("parentz",   "parentz", parentz,   parentz, EQUAL),
            new SameTestData("parentz",   "kidz0",   parentz,   kidz0,   WORK),
            new SameTestData("parentz",   "kidz1",   parentz,   kidz1,   EXPRESSION),
            new SameTestData("parentz",   "kidz3",   parentz,   kidz3,   WORK),

            new SameTestData("kidz0",   "parent",  kidz0,   parent,  WORK),
            new SameTestData("kidz0",   "kid0",    kidz0,   kid0,    EQUAL),
            new SameTestData("kidz0",   "kid1",    kidz0,   kid1,    WORK),
            new SameTestData("kidz0",   "kid2",    kidz0,   kid2,    WORK),
            new SameTestData("kidz0",   "parentz", kidz0,   parentz, WORK),
            new SameTestData("kidz0",   "kidz0",   kidz0,   kidz0,   EQUAL),
            new SameTestData("kidz0",   "kidz1",   kidz0,   kidz1,   WORK),
            new SameTestData("kidz0",   "kidz3",   kidz0,   kidz3,   WORK),

            new SameTestData("kidz1",   "parent",  kidz1,   parent,  EXPRESSION),
            new SameTestData("kidz1",   "kid0",    kidz1,   kid0,    WORK),
            new SameTestData("kidz1",   "kid1",    kidz1,   kid1,    RESOURCE),
            new SameTestData("kidz1",   "kid2",    kidz1,   kid2,    WORK),
            new SameTestData("kidz1",   "parentz", kidz1,   parentz, EXPRESSION),
            new SameTestData("kidz1",   "kidz0",   kidz1,   kidz0,   WORK),
            new SameTestData("kidz1",   "kidz1",   kidz1,   kidz1,   EQUAL),
            new SameTestData("kidz1",   "kidz3",   kidz1,   kidz3,   WORK),

            new SameTestData("kidz3",   "parent",  kidz3,   parent,  WORK),
            new SameTestData("kidz3",   "kid0",    kidz3,   kid0,    WORK),
            new SameTestData("kidz3",   "kid1",    kidz3,   kid1,    WORK),
            new SameTestData("kidz3",   "kid2",    kidz3,   kid2,    WORK),
            new SameTestData("kidz3",   "parentz", kidz3,   parentz, WORK),
            new SameTestData("kidz3",   "kidz0",   kidz3,   kidz0,   WORK),
            new SameTestData("kidz3",   "kidz1",   kidz3,   kidz1,   WORK),
            new SameTestData("kidz3",   "kidz3",   kidz3,   kidz3,   EQUAL)
        };
        for (SameTestData test : tests) checkSameness(test);
    }
}

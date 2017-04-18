package gov.ncbi.ids.test;

//import static org.hamcrest.MatcherAssert.assertThat;
import static gov.ncbi.ids.IdParts.Problem.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.IdDb;
import gov.ncbi.ids.IdParts;
import gov.ncbi.ids.IdType;

public class TestIdParts
{
    private static final Logger log = LoggerFactory.getLogger(TestIdParts.class);

    @Rule
    public TestName name = new TestName();

    private IdDb litIds;
    @SuppressWarnings("unused")
    private IdType pmid;
    private IdType pmcid;
    private IdType mid;
    @SuppressWarnings("unused")
    private IdType doi;
    @SuppressWarnings("unused")
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

    @Test
    public void testIdParts() {
        IdParts idp = new IdParts(litIds, mid, "NIHMS12345");
        assertFalse(idp.hasProblems());
        assertEquals(0, idp.problems.size());
        assertThat(idp.typeSpec, instanceOf(IdType.class));
        assertEquals(mid, idp.typeSpec);
        assertNull(idp.prefix);
        assertEquals(mid, idp.type);
        assertEquals("NIHMS12345", idp.npValue);

        // type mismatch
        idp = new IdParts(litIds, mid, "pmcid:1234");
        assertTrue(idp.hasProblems());
        assertEquals(1, idp.problems.size());
        assertEquals(TYPE_MISMATCH, idp.problems.get(0));
        assertThat(idp.typeSpec, instanceOf(IdType.class));
        assertEquals(mid, idp.typeSpec);
        assertEquals("pmcid", idp.prefix);
        //assertEquals(mid, idp.type);
        assertNull(idp.type);
        assertEquals("1234", idp.npValue);

        // specify the type as a string
        idp = new IdParts(litIds, "pmcid", "pmcid:1234");
        assertFalse(idp.hasProblems());
        assertEquals(0, idp.problems.size());
        assertThat(idp.typeSpec, instanceOf(String.class));
        assertEquals("pmcid", idp.typeSpec);
        assertEquals("pmcid", idp.prefix);
        assertEquals(pmcid, idp.type);
        assertEquals("1234", idp.npValue);

        // invalid type spec string
        idp = new IdParts(litIds, "fleegle", "pmid:5");
        assertTrue(idp.hasProblems());
        assertEquals(1, idp.problems.size());
        assertEquals(BAD_TYPE_SPEC, idp.problems.get(0));
        assertThat(idp.typeSpec, instanceOf(String.class));
        assertEquals("fleegle", idp.typeSpec);
        assertEquals("pmid", idp.prefix);
        //assertEquals(pmid, idp.type);
        assertNull(idp.type);
        assertEquals("5", idp.npValue);

        // invalid type prefix
        idp = new IdParts(litIds, "aiid", "aiids:56");
        assertTrue(idp.hasProblems());
        assertEquals(1, idp.problems.size());
        assertEquals(BAD_TYPE_PREFIX, idp.problems.get(0));
        assertThat(idp.typeSpec, instanceOf(String.class));
        assertEquals("aiid", idp.typeSpec);
        assertEquals("aiids", idp.prefix);
        //assertEquals(aiid, idp.type);
        assertNull(idp.type);
        assertEquals("56", idp.npValue);

        // both errors
        idp = new IdParts(litIds, "aiiid", "aiids:56");
        assertTrue(idp.hasProblems());
        assertEquals(2, idp.problems.size());
        log.debug("IdParts problems: " + idp.problems);
        assertThat(idp.problems,
            containsInAnyOrder(Arrays.asList(equalTo(BAD_TYPE_SPEC), equalTo(BAD_TYPE_PREFIX))));
        assertThat(idp.typeSpec, instanceOf(String.class));
        assertEquals("aiiid", idp.typeSpec);
        assertEquals("aiids", idp.prefix);
        assertNull(idp.type);
        assertEquals("56", idp.npValue);


        idp = new IdParts(litIds, (IdType) null, "pmcid:1234");
        assertFalse(idp.hasProblems());
        assertEquals(0, idp.problems.size());
        assertNull(idp.typeSpec);
        assertEquals("pmcid", idp.prefix);
        assertEquals(pmcid, idp.type);
        assertEquals("1234", idp.npValue);

        idp = new IdParts(litIds, "pmcid:1234");
        assertFalse(idp.hasProblems());
        assertEquals(0, idp.problems.size());
        assertNull(idp.typeSpec);
        assertEquals("pmcid", idp.prefix);
        assertEquals(pmcid, idp.type);
        assertEquals("1234", idp.npValue);
    }
}

package gov.ncbi.ids.test;

import static gov.ncbi.ids.IdParser.NOOP;
import static gov.ncbi.ids.IdParser.UPPERCASE;
import static gov.ncbi.ids.IdParser.replacer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.IdParser;
import gov.ncbi.ids.IdParser.IdMatchData;

public class TestIdParser
{
    private static final Logger log = LoggerFactory.getLogger(TestIdParser.class);

    @Rule
    public TestName name = new TestName();

    /**
     * Test the constructors and getters.
     */
    @Test
    public void testConstructor()
    {
        String regexp = "[aA][bB](\\d\\d)";
        IdParser idp0 = new IdParser(regexp, true, IdParser.NOOP);

        assertEquals(regexp, idp0.getPattern());
        assertEquals(IdParser.NOOP, idp0.getCanonicalizer());
        assertEquals(true, idp0.isVersioned());

        IdMatchData idm = idp0.match("ab11");
        assertNotNull(idm);
        assertTrue(idm.matches());
        assertEquals("ab11", idm.reMatcher.group(0));
        assertEquals("11", idm.reMatcher.group(1));

        //log.debug("idp0: " + idp0.toString());
        //assertEquals("/[aA][bB](\\d\\d)/ versioned", idp.toString());

        IdParser idp1 = new IdParser(regexp, false, UPPERCASE);
        //log.debug("idp1: " + idp1.toString());
        //assertEquals("/[aA][bB](\\d\\d)/ non-versioned", idp1.toString());
    }

    @Test
    public void testMatchers()
    {
        String pattern = "([Pp][Mm][Cc])?(\\d+)(\\.\\d+)?";
        IdParser idp0 = new IdParser(pattern, true, NOOP);

        assertEquals(pattern, idp0.getPattern());
        assertSame(IdParser.NOOP, idp0.getCanonicalizer());
        assertTrue(idp0.isVersioned());

        IdMatchData idm0 = idp0.match("fooble");
        assertFalse(idm0.matches());

        // Trying to canonicalize after no match should result in an
        // exception
        boolean exceptionThrown = false;
        try {
            @SuppressWarnings("unused")
            String c = idm0.canonicalize();
        }
        catch(IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        IdMatchData idm1 = idp0.match("pMc77868.1");
        assertTrue(idm1.matches());
        assertEquals("pMc77868.1",idm1.canonicalize());
    }

    @Test
    public void testUppercase()
    {
        IdMatchData idm;

        IdParser upper = new IdParser("([Pp][Mm][Cc])?(\\d+)(\\.\\d+)?",
                false, UPPERCASE);
        idm = upper.match("pMC321.6");
        assertTrue(idm.matches());
        assertEquals("PMC321.6", idm.canonicalize());

        idm = upper.match("wiggle");
        assertFalse(idm.matches());
        boolean exceptionThrown = false;
        try {
            idm.canonicalize();
        }
        catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testReplace() {
        boolean  exceptionThrown = false;
        try {
            @SuppressWarnings("unused")
            Function<IdMatchData, String> r0 = replacer(null);
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        IdParser idp0 = new IdParser("([Pp][Mm][Cc])?(\\d+)(\\.\\d+)?",
                false, replacer("PMC$2$3"));
        IdMatchData idm0 = idp0.match("pMC321.6");
        assertTrue(idm0.matches());
        assertEquals("PMC321.6", idm0.canonicalize());
    }

    @Test
    public void testParsers()
    {
        String tVal0 = "814";
        String tVal1 = "Pmc814";
        String tVal2 = "Pmc";
        String tVal3 = "Pc814";
        String tVal4 = "PMC814";
        String tVal5 = "PmC814.77";
        String tVal6 = "814.78";
        String tVal7 = "PMC814.88";

        String patA = "(PMC)?\\d+";
        String patB = "[Pp][Mm][Cc]\\d+";
        String patC = "(\\d+\\.\\d+)";
        String patD = "[Pp][Mm][Cc](\\d+\\.\\d+)";

        IdParser idpA = new IdParser(patA, false, NOOP);
        IdParser idpB = new IdParser(patB, false, UPPERCASE);
        IdParser idpC = new IdParser(patC, true, replacer("PMC$1"));
        IdParser idpD = new IdParser(patD, true, replacer("PMC$1"));

        class MTest {
            public String tValue;
            public IdParser tParser;
            public boolean eMatches;
            public String eCanon;
            public boolean eVer;
            public MTest(String tValue, IdParser tParser,
                boolean eMatches, String eCanon, boolean eVer)
            {
                this.tValue = tValue;
                this.tParser = tParser;
                this.eMatches = eMatches;
                this.eCanon = eCanon;
                this.eVer = eVer;
            }
        }

        List<MTest> tests = new ArrayList<> (Arrays.asList(
                    //               matches  canon   versioned
           new MTest(tVal0, idpA, true, "814", false),
           new MTest(tVal0, idpB, false, null, false),
           new MTest(tVal0, idpC, false, null, false),
           new MTest(tVal0, idpD, false, null, false),

           new MTest(tVal1, idpA, false, null, false),
           new MTest(tVal1, idpB, true, "PMC814", false),
           new MTest(tVal1, idpC, false, null, false),
           new MTest(tVal1, idpD, false, null, false),

           new MTest(tVal2, idpA, false, null, false),
           new MTest(tVal2, idpB, false, null, false),
           new MTest(tVal2, idpC, false, null, false),
           new MTest(tVal2, idpD, false, null, false),

           new MTest(tVal3, idpA, false, null, false),
           new MTest(tVal3, idpB, false, null, false),
           new MTest(tVal3, idpC, false, null, false),
           new MTest(tVal3, idpD, false, null, false),

           new MTest(tVal4, idpA, true, "PMC814", false),
           new MTest(tVal4, idpB, true, "PMC814", false),
           new MTest(tVal4, idpC, false, null, false),
           new MTest(tVal4, idpD, false, null, false),

           new MTest(tVal5, idpA, false, null, false),
           new MTest(tVal5, idpB, false, null, false),
           new MTest(tVal5, idpC, false, null, false),
           new MTest(tVal5, idpD, true, "PMC814.77", true),

           new MTest(tVal6, idpA, false, null, false),
           new MTest(tVal6, idpB, false, null, false),
           new MTest(tVal6, idpC, true, "PMC814.78", true),
           new MTest(tVal6, idpD, false, null, false),

           new MTest(tVal7, idpA, false, null, false),
           new MTest(tVal7, idpB, false, null, false),
           new MTest(tVal7, idpC, false, null, false),
           new MTest(tVal7, idpD, true, "PMC814.88", true)
        ));

        for (MTest t: tests) {
            IdMatchData idm = t.tParser.match(t.tValue);
            assertSame(idm.getParser(), t.tParser);
            assertEquals(t.eMatches, idm.matches());
            if (t.eMatches) {
                assertEquals(t.eCanon, idm.canonicalize());
                assertEquals(t.eVer, idm.isVersioned());
            }
            else {
                boolean exceptionThrown = false;
                try { idm.canonicalize(); }
                catch (IllegalStateException e) {
                    exceptionThrown = true;
                }
                assertTrue(exceptionThrown);

                exceptionThrown = false;
                try { idm.isVersioned(); }
                catch (IllegalStateException e) {
                    exceptionThrown = true;
                }
                assertTrue(exceptionThrown);
            }
        }
    }
}

package gov.ncbi.ids.test;

import static gov.ncbi.ids.IdParser.NOOP;
import static gov.ncbi.ids.IdParser.UPPERCASE;
import static gov.ncbi.ids.IdParser.replacer;
import static gov.ncbi.ids.IdType.nameValid;
import static gov.ncbi.testing.TestHelper.checkEqualsMethod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.IdParser;
import gov.ncbi.ids.IdType;
import gov.ncbi.ids.Identifier;

public class TestIdType
{
    private static final Logger log = LoggerFactory.getLogger(TestIdType.class);

    @Rule
    public TestName name = new TestName();

    /**
     * Test the IdType constructors and getters.
     */
    @Test
    public void testConstructAndGetters()
    {
        IdType pmcid;

        pmcid = new IdType("pmcid",
            new ArrayList<IdParser>(Arrays.asList(
                new IdParser("^(\\d+)$", false, replacer("PMC$1")),
                new IdParser("^(\\d+(\\.\\d+)?)$", true, replacer("PMC$1")),
                new IdParser("^([Pp][Mm][Cc]\\d+)$", false, UPPERCASE),
                new IdParser("^([Pp][Mm][Cc]\\d+(\\.\\d+)?)$", true, UPPERCASE)
        )));

        assertEquals("pmcid", pmcid.getName());
        List<IdParser> parsers = pmcid.getParsers();
        assertEquals(4, parsers.size());
        assertEquals(Pattern.compile("^(\\d+(\\.\\d+)?)$").toString(),
                parsers.get(1).getPattern().toString());

        Identifier id0 = pmcid.id("667387");
        assertNotNull(id0);
        assertEquals("PMC667387", id0.getValue());

        Identifier id1 = pmcid.id("PMC667388376");
        assertNotNull(id1);
        assertEquals("PMC667388376", id1.getValue());

        Identifier id2 = pmcid.id("pMc3");
        assertNotNull(id2);
        assertEquals("PMC3", id2.getValue());

        Identifier id3 = pmcid.id("Waddya want for nothing?");
        assertNull(id3);

        // New IdType that uses NOOP
        IdType blech = new IdType("blech",
            new ArrayList<>(Arrays.asList(
                new IdParser("\\d*", false, null)
        )));
        Identifier id4 = blech.id("8814");
        assertEquals("blech:8814", id4.getCurie());
    }

    @Test
    public void testNameValidator() {
        assertTrue(nameValid("blech"));
        assertFalse(nameValid("Foo"));
        assertFalse(nameValid("*oo"));
        assertFalse(nameValid("9oo"));
        assertFalse(nameValid("{oo"));

        assertTrue(nameValid("foo_oo"));
        assertFalse(nameValid("fo-o"));
        assertFalse(nameValid("foFo"));
        assertFalse(nameValid("foo{"));
        assertFalse(nameValid("foo-oo"));
        assertFalse(nameValid("foo\"oo"));
        assertFalse(nameValid("foo:oo"));
        assertFalse(nameValid("foo<oo"));
        assertFalse(nameValid("fo(o"));
    }

    /**
     * Trying to instantiate an IdType with an invalid
     * regular expression pattern.
     */
    @Test(expected=PatternSyntaxException.class)
    public void testBadParser0() {
        new IdParser("ab(c($de", false, NOOP);
    }


    /**
     * Can't instantiate an IdType with a bad name.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testBadName0() {
        new IdType("Foo", new ArrayList<IdParser>());
    }

    @Test
    public void testEquals() {
        IdType idt0a = new IdType("idt0", new ArrayList<IdParser>());
        IdType idt1 = new IdType("idt1", new ArrayList<IdParser>());
        IdType idt0b = new IdType("idt0", new ArrayList<IdParser>());

        assertEquals(idt0a, idt0b);
        assertNotEquals(idt0a, idt1);
        assertNotEquals(idt0b, idt1);

        checkEqualsMethod(idt0a, idt0b);
        checkEqualsMethod(idt0a, idt1);
        checkEqualsMethod(idt0b, idt1);
    }

    @Test
    public void testToString() {
        IdType idt0 = new IdType("idt0", new ArrayList<IdParser>());
        log.debug("idt0: " + idt0.toString());
        log.debug("  dump: " + idt0.dump());
        assertEquals("IdType idt0", idt0.toString());

        IdType idt1 = new IdType("pmcid",
                new ArrayList<IdParser>(Arrays.asList(
                    new IdParser("^(\\d+)$", false, replacer("PMC$1")),
                    new IdParser("^(\\d+(\\.\\d+)?)$", true, replacer("PMC$1")),
                    new IdParser("^([Pp][Mm][Cc]\\d+)$", false, UPPERCASE),
                    new IdParser("^([Pp][Mm][Cc]\\d+(\\.\\d+)?)$", true, UPPERCASE)
            )));
        log.debug("idt1: " + idt1.toString());
        log.debug("  dump: " + idt1.dump());
        assertEquals("IdType pmcid", idt1.toString());
    }
}

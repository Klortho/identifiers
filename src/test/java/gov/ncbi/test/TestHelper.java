package gov.ncbi.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic testing utility classes and functions
 */

public class TestHelper
{
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * This opens up a private method, using reflection, for unit testing.
     */
    public static Method privateMethod(String className, String methodName)
            throws ClassNotFoundException, NoSuchMethodException
    {
        Method method = Class.forName("gov.ncbi.ids." + className)
                .getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method;
    }

    /**
     * Simplifies (slightly) passing messages down a call tree, such that the
     * final displayed message is the concatenation of those seen so far.
     *
     * For example, to invoke a routine that checks an equals() method,
     *
     *     checkEqualsMethod(msgAppend(msg, "equality tests: "), idA, idB);
     */
    public static String msgAppend(String msg, String newText) {
        return (msg == null ? "" : msg) + newText;
    }


    /**
     * Helper function for checking the equals() method, given
     * a single object.
     */
    public static void checkEqualsMethod(String msg, Object a) {
        if (a == null) return;
        assertFalse(msgAppend(msg, " a != null: "), a.equals(null));
        assertTrue(msgAppend(msg, " a == a: "), a.equals(a));    // reflexive
    }

    /**
     *  @see TestHelper#checkEqualsMethod(string, object)
     */
    public static void checkEqualsMethod(Object a) {
        checkEqualsMethod(null, a);
    }




    /**
     * Helper function for checking hashCode() and equals(), given any two
     * objects.
     */
    public static void checkEqualsMethod(String msg, Object a, Object b) {
        if (a == null && b == null) return;
        checkEqualsMethod(msg, a);
        checkEqualsMethod(msg, b);
        if (a != null && b != null) {
            boolean aeqb = a.equals(b);
            assertEquals(msgAppend(msg, "symmetric: "), aeqb, b.equals(a));  // symmetric
            if (a == b) assertTrue(msgAppend(msg, "'==' implies equals: "), aeqb);
            if (aeqb) assertTrue(msgAppend(msg, "equals entails hashcode: "), a.hashCode() == b.hashCode());
        }
    }

    public static void checkEqualsMethod(Object a, Object b) {
        checkEqualsMethod(null, a, b);
    }

    /**
     * Assertion for testing that an exception gets thrown.
     */
    public static <T extends Exception> void
    assertThrows(String msg, Class<T> etype, TestLambda testLambda)
    {
        boolean thrown = false;
        try {
            testLambda.run();
        }
        catch (Exception err) {
            if (etype.isInstance(err)) thrown = true;
            else {
                fail(msgAppend(msg, "got exception of unanticipated type: ") +
                        err.getMessage());
            }
        }
        assertTrue(msgAppend(msg, "expected exception"), thrown);
    }

    /**
     * Assertion for testing that an exception gets thrown
     */
    public static <T extends Exception> void
    assertThrows(Class<T> etype, TestLambda testLambda) {
        assertThrows(null, etype, testLambda);
    }

    public interface TestLambda {
        public void run() throws Exception;
    }

}

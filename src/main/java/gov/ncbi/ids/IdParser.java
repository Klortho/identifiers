package gov.ncbi.ids;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IdParsers are immutable objects that are used to convert
 * Strings into Identifiers. Every IdType has a list of
 * IdParsers, and each IdParser has one regular expression
 * pattern, a Function to canonicalize the String,
 * and a boolean indicating whether or not the resultant Identifier
 * is version-specific.
 */
public class IdParser
{
    private static final Logger log = LoggerFactory.getLogger(IdParser.class);

    private final Pattern pattern;
    private final Function<IdMatchData, String> canonicalizer;
    private final boolean versioned;

    /**
     * This class captures the state of the attempt to match the
     * regular expression pattern to the target string. If matches()
     * returns true, that means it was successful.
     *
     * Note that an IdMatchData object implicitly stores the IdParser
     * that it belong to, giving it access to the canonicalizer
     * function, and anything else it might need.
     */
    public class IdMatchData {
        /**
         * This is the java.util.regex.Matcher that saves the state
         * of the application of the regular expression
         */
        public final Matcher reMatcher;

        /**
         * Construct with a Java regular expression Matcher.
         */
        public IdMatchData(Matcher reMatcher) {
            this.reMatcher = reMatcher;
        }

        /**
         * Returns true if the match attempt was successful.
         */
        public boolean matches() {
            return this.reMatcher.matches();
        }

        /**
         * Canonicalize the matched string
         */
        public String canonicalize() {
            if (!this.reMatcher.matches())
                throw new IllegalStateException(
                    "ID value string does not match the pattern");
            return IdParser.this
                .canonicalizer.apply(this);
        }

        /**
         * true if the Identifier created from this match will be
         * version-specific.
         */
        public boolean isVersioned() {
            if (!this.reMatcher.matches())
                throw new IllegalStateException(
                    "ID value string does not match the pattern");
            return IdParser.this.versioned;
        }

        /**
         * Return the IdParser to which this belongs.
         */
        public IdParser getParser() { return IdParser.this; }
    }

    /**
     * Constructor - canonicalize by any function.
     */
    public IdParser(String patternStr, boolean versioned,
            Function<IdMatchData, String> clizer)
    {
        this.pattern = Pattern.compile(patternStr);
        this.versioned = versioned;
        this.canonicalizer = clizer == null ? NOOP : clizer;
    }

    /**
     * Get the regular expression pattern used to match ID value strings.
     */
    public String getPattern() {
        return this.pattern.pattern();
    }

    /**
     * Get the function used to canonicalize a String.
     */
    public Function<IdMatchData, String> getCanonicalizer() {
        return this.canonicalizer;
    }

    /**
     * true if Identifiers created from this IdParser are versioned.
     */
    public boolean isVersioned() {
        return this.versioned;
    }

    /**
     * This is a canonicalizer that keeps the original as-is.
     */
    public static final Function<IdMatchData, String> NOOP =
            idm -> idm.reMatcher.group();

    /**
     * This is a canonicalizer that converts the original to uppercase
     */
    public static final Function<IdMatchData, String> UPPERCASE =
            idm -> idm.reMatcher.group().toUpperCase();

    /**
     * This creates a canonicalizer that uses a
     * regular expression replacement string to canonicalize the target.
     */
    public static final Function<IdMatchData, String>
        replacer(String replacement)
    {
        if (replacement == null)
            throw new IllegalArgumentException(
                "Need a regular expression replacement string.");
        return idm -> idm.reMatcher.replaceAll(replacement);
    }

    /**
     * Apply the pattern to a non-prefixed ID string.
     * @return a IdMatcher object
     */
    public IdMatchData match(String npValue) {
        return new IdMatchData(this.pattern.matcher(npValue));
    }

    @Override
    public String toString() {
        return "/" + pattern.toString() + "/ " +
            (this.versioned ? "" : "non-") + "versioned";
    }
}

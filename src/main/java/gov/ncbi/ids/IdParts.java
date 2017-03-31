package gov.ncbi.ids;

import static gov.ncbi.ids.IdParts.Problem.BAD_TYPE_SPEC;
import static gov.ncbi.ids.IdParts.Problem.BAD_TYPE_PREFIX;
import static gov.ncbi.ids.IdParts.Problem.TYPE_MISMATCH;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to process user-supplied ID values, that might
 * be somewhat messy. IDs can be specified as being of a certain
 * type in one of two ways: either by some independent business logic,
 * which gets resolved (outside the scope of this class) into an
 * IdType object, or with a prefix on the string value itself.
 * This class deals with the various combinations:
 *
 * - The type-specifier can be either:
 *     - null - no type was specified, or
 *     - an IdType object
 * - The value string might or might now have a type prefix.
 */
public class IdParts
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(IdParts.class);

    /**
     * Problems that can happen.
     */
    public static enum Problem {
        /**
         * The string argument specifying the type could not be resolved
         */
        BAD_TYPE_SPEC,

        /**
         * The prefix on the value string was not a valid type name.
         */
        BAD_TYPE_PREFIX,

        /**
         * The type-specifier and the prefix don't match. In this case,
         * the type-specifier takes precedence.
         */
        TYPE_MISMATCH,
    };

    // This class doesn't have getters. Everything is public and
    // (more or less) immutable.

    /**
     * List of non-fatal problems encountered.
     */
    public final List<Problem> problems;

    /**
     * The type that was specified as an independent argument (either
     * a String or an IdType) or null
     */
    public Object typeSpec;

    /**
     * The prefix as it appeared in the value string
     */
    public String prefix;

    /**
     * The designated type, reconciled among the sources, or null.
     */
    public IdType type;

    /**
     * The non-prefixed part of the value string.
     */
    public String npValue;

    //////////////////////////////////////////////////////////////////////////
    // Constructors and their friends

    /**
     * Constructor helper.
)     */
    private static void parseValue(IdParts self, IdDb iddb, IdType typeSpecObj,
            String value)
    {
        // Split the value string into prefix and non-prefixed parts
        String[] parts = value.split(":", 2);
        boolean hasPrefix = (parts.length == 2);
        self.prefix = hasPrefix ? parts[0] : null;
        self.npValue = hasPrefix ? parts[1] : value;

        // Convert the prefix (if given) into an IdType
        IdType prefixType = hasPrefix ? iddb.getType(self.prefix) : null;
        if (hasPrefix && prefixType == null)
            self.problems.add(BAD_TYPE_PREFIX);

        // See if the type was specified twice, with a mismatch
        if (typeSpecObj != null && prefixType != null &&
            !typeSpecObj.equals(prefixType))
            self.problems.add(TYPE_MISMATCH);

        // The specType takes precedence
        self.type = typeSpecObj != null ? typeSpecObj : prefixType;
    }

    /**
     * Construct without an independent type specifier.
     */
    public IdParts(IdDb iddb, String value) {
        this.problems = new ArrayList<Problem>();
        this.typeSpec = null;
        parseValue(this, iddb, null, value);
    }

    /**
     * Constructor with both an independent type specifier string and
     * a value string.
     */
    public IdParts(IdDb iddb, String typeSpec, String value) {
        this.problems = new ArrayList<Problem>();
        this.typeSpec = typeSpec;

        // Try to get the type corresponding to the string arg
        IdType typeSpecObj = typeSpec == null ? null : iddb.getType(typeSpec);
        if (typeSpec != null && typeSpecObj == null)
            problems.add(BAD_TYPE_SPEC);

        parseValue(this, iddb, typeSpecObj, value);
    }

    /**
     * Construct with the type specified by an IdType object instead of a
     * string.
     */
    public IdParts(IdDb iddb, IdType typeSpec, String value) {
        this.problems = new ArrayList<Problem>();
        this.typeSpec = typeSpec;
        parseValue(this, iddb, typeSpec, value);
    }


    /**
     *  True if there are problems with the type specifiers
     */
    public boolean hasProblems() {
        return this.problems.size() > 0;
    }
}


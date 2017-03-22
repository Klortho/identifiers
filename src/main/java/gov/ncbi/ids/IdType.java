package gov.ncbi.ids;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.ids.IdParser.IdMatchData;

/**
 * An IdType object has a name (e.g. "pmcid") and provides methods for
 * checking whether or not value strings match IDs of this type, and
 * for creating Identifier objects from those strings.
 *
 * The name is used as the prefix in a "curie" style ID string (e.g.
 * "pmcid:PMC123456"). A valid IdType name must begin with a lowercase
 * letter, and must contain only lowercase letters and numerals. In
 * all public methods that deal with ID value strings, they can
 * be given with a prefix or non-prefixed.
 *
 * An IdType object includes a list of IdParsers, each of which has
 * a regular expression pattern that is used to
 * identify valid ID value strings, data describing
 * how to canonicalize those value strings, and a boolean flag indicating
 * whether or not IDs matching that pattern are versioned.
 */

public class IdType
{
    private static final Logger log = LoggerFactory.getLogger(IdType.class);

    /**
     * The name; only lowercase letters and numbers; must begin with a letter.
     */
    private final String name;

    /**
     * The list of parsers.
     */
    private final List<IdParser> parsers;

    /**
     * Checks to see if a String is a valid name of an IdType:
     *
     * - Must start with a lowercase letter
     * - Can only contain lowercase letters, numbers, and underscores.
     */
    public static boolean nameValid(String name) {
        char c0 = name.charAt(0);
        if (c0 < 'a' || c0 > 'z') return false;
        for (char c : name.toCharArray()) {
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_')
                return false;
        }
        return true;
    }

    /**
     * Constructor.
     */
    public IdType(String name, List<IdParser> parsers)
    {
        if (!nameValid(name))
            throw new IllegalArgumentException("Invalid IdType name: " + name);
        this.name = name;
        this.parsers = parsers;
    }

    /**
     * Get the name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the List of IdParsers
     */
    public List<IdParser> getParsers() {
        return this.parsers;
    }

    /**
     * Returns a Stream of IdMatchData objects from applying the
     * patterns against the argument. This doesn't filter the stream:
     * it will not have nulls, but may have objects from unsuccessful
     * match attempts.
     */
    private Stream<IdMatchData> mdStream(String npValue)
    {
        return this.parsers.stream()
            .map(parser -> parser.match(npValue));
    }

    /**
     * Make an Identifier from match data, or null if unsuccessful.
     */
    private Function<IdMatchData, Identifier> makeId = idm -> {
        return idm == null || !idm.matches() ? null :
            new Identifier(this, idm.canonicalize(), idm.isVersioned());
    };

    /**
     * Create a Stream of Identifiers from a non-prefixed value string.
     */
    public Stream<Identifier> idStream(String npValue) {
        return mdStream(npValue)
            //.filter(matchData -> matchData.matches())
            .map(this.makeId)
            .filter(Objects::nonNull);
    }

    /**
     * Returns true if the given npValue is a valid ID of this type.
     */
    public boolean isValid(String npValue) {
        return this.mdStream(npValue)
                .anyMatch(matchData -> matchData.matches());
    }

    /**
     * Returns an Identifier from the first pattern that matches the
     * argument, or null if no patterns match.
     */
    public Identifier id(String npValue) {
        return this.idStream(npValue).findFirst().orElse(null);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    /**
     * Two IdTypes are equal if they have the same name.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof IdType)) return false;
        return this.name.equals(((IdType) obj).name);
    }

    @Override
    public String toString() {
        return "IdType " + this.name;
    }

    /**
     * This produces a more descriptive String.
     */
    public String dump() {
        String pstr = this.parsers.stream()
                .map(IdParser::toString)
                .collect(Collectors.joining("\n  "));
        return "IdType " + this.name + ":\n  " + pstr;
    }
}



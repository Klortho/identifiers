package gov.ncbi.ids;

import static gov.ncbi.ids.IdParser.NOOP;
import static gov.ncbi.ids.IdParser.UPPERCASE;
import static gov.ncbi.ids.IdParser.replacer;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * This is the main entry point into the Identifier library. You can instantiate an
 * IdDb programmatically, or from a JSON file. An IdDb comprises a set of IdTypes,
 * and provides methods for matching String values to those ID types, canonicalizing
 * them, etc.
 */
public class IdDb
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(IdDb.class);

    /**
     *  A name for this IdDb
     */
    private String name;

    /**
     *  All IdTypes, in precedence order
     */
    private ArrayList<IdType> types = new ArrayList<IdType>();

    /**
     * All the IdTypes, indexed by name.
     */
    private Map<String, IdType> byName = new HashMap<String, IdType>();

    /**
     * Construct with a name. Because the data structure has circular
     * references, it needs to be created without IdTypes first. The
     * IdTypes are then instantiated and added later.
     */
    public IdDb(String name) {
        this.name = name;
    }

    /**
     * Add an IdType to this database.
     */
    public IdDb addType(IdType type) {
        this.types.add(type);
        this.byName.put(type.getName(), type);
        return this;
    }

    /**
     * Add a list of IdTypes to this database.
     */
    public IdDb addTypes(List<IdType> types) {
        for (IdType type : types) this.addType(type);
        return this;
    }

    /**
     * Factory method to read a new IdDb from a JSON string.
     */
    public static IdDb fromJson(String jsonString)
        throws IOException
    {
        return (new IdDbJsonReader()).readIdDb(jsonString);
    }

    /**
     * Factory method to read a new IdDb from a JSON resource
     */
    public static IdDb fromJson(URL jsonUrl)
        throws JsonProcessingException, IOException
    {
        return (new IdDbJsonReader()).readIdDb(jsonUrl);
    }

    /**
     * Get the name of this ID database
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the list of IdTypes
     */
    public List<IdType> getTypes() {
        return this.types;
    }

    /**
     * Get an IdType by its name (case-insensitive).
     * @return - the IdType whose name matches the argument; null
     *   if the argument is null or if there's no match
     */
    public IdType getType(String name)
    {
        if (name == null) return null;
        return this.byName.get(name.toLowerCase());
    }

    /**
     * Look up an IdType by its name (case-insensitive). Same as getType(),
     * but this throws an IllegalArgumentException if `name` is not null and it is not
     * found.
     */
    public IdType lookupType(String name) {
        if (name == null) return null;
        IdType type = this.getType(name);
        if (type == null)
            throw new IllegalArgumentException("Bad IdType name: " + name);
        return type;
    }

    /////////////////////////////////////////////////////////////////////
    // Utility functions

    /////////////////////////////////////////////////////////////////////
    // Methods to process user-supplied ID strings

    /**
     * This helper produces a Stream of all of the IdTypes that match the
     * given ID string value. In other words, if the string value could
     * be parsed as a valid ID of a given type, then that IdType object
     * will be added to the Stream.
     */
    public Stream<IdType> typeStream(String npValue) {
        return this.types.stream()
            .filter(t -> t.isValid(npValue));
    }

    /**
     * @see IdDb#typeStream(String)
     */
    public Stream<IdType> typeStream(IdParts idparts) {
        Stream<IdType> src =
            idparts.type == null ? this.types.stream()
                                 : Stream.of(idparts.type);
        return src.filter(t -> t.isValid(idparts.npValue));
    }

    /**
     * Find the first IdType that this non-prefixed value matches
     */
    public IdType findType(String npValue) {
        return typeStream(npValue)
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns all of the IdTypes that this non-prefixed value is
     * valid for.
     */
    public List<IdType> findTypes(String npValue) {
        return typeStream(npValue)
                .collect(Collectors.toList());
    }


    /**
     * Create an Identifier object from the user-supplied IdType
     * and String..
     * This and other methods that process user-supplied values
     * each have two function signatures.
     *
     * This first form takes only a stand-alone value string,
     * that might or might not have a prefix.
     */
    public Identifier id(String value) {
        return makeId(new IdParts(this, value));
    }

    /**
     * This form of the `id` command also takes an IdType, which
     * causes the value to be interpreted as an ID of that type,
     * rather than trying type patterns in the standard order.
     */
    public Identifier id(IdType type, String value) {
        return makeId(new IdParts(this, type, value));
    }

    /**
     * Construct a new Identifier from pre-processed data.
     */
    public Identifier makeId(IdParts idparts) {
        return typeStream(idparts)
                .map(type -> type.id(idparts.npValue))
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns true if the value could be interpreted as an
     * Identifier.
     */
    public boolean isValid(String value) {
        return isValidP(new IdParts(this, value));
    }

    public boolean isValid(IdType type, String value)
    {
        return isValidP(new IdParts(this, type, value));
    }

    public boolean isValidP(IdParts idparts) {
        return typeStream(idparts).anyMatch(t -> true);
    }

    /**
     * Convert a list of strings to identifiers all in one go. If any of
     * the strings are invalid, this throws an IllegalArgumentException.
     */
    public List<Identifier> idList(String... strings) {
        return idList(null, strings);
    }

    /**
     * Convert a list of strings to Identifiers of the given type.
     */
    public List<Identifier> idList(IdType type, String... strings) {
        return Arrays.asList(strings).stream()
            .map(str -> {
                Identifier id = this.id(type, str);
                if (id == null) throw new IllegalArgumentException(
                    "Bad ID type / value: " + type + "/" + str);
                return id;
            })
            .collect(Collectors.toList());
    }


    /**
     * This static, predefined, IdDb is available to other apps. It
     * contains IdTypes used by literature resources. It is accessed
     * via a getter to make sure there are no synchronization issues
     * at startup.
     */
    private static Object _litIdsLock = new Object();
    private static IdDb _litIds = null;
    public static IdDb litIds() {
        synchronized(_litIdsLock) {
            if (_litIds != null) return _litIds;
            _litIds = (new IdDb("literature-ids"))

            // The order in which the IdTypes appear
            // determines which regular expressions get tried first. For example,
            // the string "PMC12345" matches patterns of both pmcid and mid,
            // but since pmcid appears first, it will match that type.
            .addTypes(
                new ArrayList<IdType>(Arrays.asList(
                    new IdType("pmid", new ArrayList<IdParser>(Arrays.asList(
                        new IdParser("\\d+", false, NOOP),
                        new IdParser("\\d+(\\.\\d+)?", true, NOOP)
                    ))),
                    new IdType("pmcid", new ArrayList<IdParser>(Arrays.asList(
                        new IdParser("(\\d+)", false, replacer("PMC$1")),
                        new IdParser("(\\d+(\\.\\d+)?)", true, replacer("PMC$1")),
                        new IdParser("[Pp][Mm][Cc]\\d+", false, UPPERCASE),
                        new IdParser("[Pp][Mm][Cc]\\d+(\\.\\d+)?", true, UPPERCASE)
                    ))),
                    new IdType("mid", new ArrayList<IdParser>(Arrays.asList(
                        new IdParser("[A-Za-z]+\\d+", true, UPPERCASE)
                    ))),
                    new IdType("doi", new ArrayList<IdParser>(Arrays.asList(
                        new IdParser("10\\.\\d+\\/.*", false, NOOP)
                    ))),
                    new IdType("aiid", new ArrayList<IdParser>(Arrays.asList(
                        new IdParser("\\d+", true, NOOP)
                    )))
                ))
            );
            return _litIds;
        }
    }
}

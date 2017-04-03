package gov.ncbi.ids;

import static gov.ncbi.ids.IdParser.NOOP;
import static gov.ncbi.ids.IdParser.UPPERCASE;
import static gov.ncbi.ids.IdParser.replacer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.spaceprogram.kittycache.KittyCache;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
     * The config object acts as defaults for every new IdResolver.
     */
    private Config config;

    /**
     *  All IdTypes, in precedence order
     */
    private ArrayList<IdType> types = new ArrayList<IdType>();

    /**
     * All the IdTypes, indexed by name.
     */
    private Map<String, IdType> byName = new HashMap<String, IdType>();

    /**
     * FIXME: need to write unit tests for the cache.
     *
     * If caching is enabled, the results returned from the external ID
     * resolver service are cached here. The keys of this are all of the
     * known CURIEs for the Identifiers for any given IdSet that gets
     * instantiated.
     */
    KittyCache<String, IdSet> idSetCache;


    /**
     * Construct with a name and a Config object. If `config` is null,
     * this will use the default config. Note that you can override
     * individual config values by setting system properties.
     *
     * Implementation note: Because the data structure has circular
     * references, it needs to be created without IdTypes first. The
     * IdTypes are then instantiated and added later.
     */
    public IdDb(String name, Config config) {
        this.name = name;
        if (config == null) this.config = ConfigFactory.load();
        else {
            // Validate user-supplied config
            config.checkValid(ConfigFactory.defaultReference(), "reference.conf");
            this.config = config;
        }
    }

    public IdDb(String name) {
        this(name, null);
    }

    /**
     * Add an IdType to this database. The order in which the IdTypes are
     * added is significant, because it determines the order in which the
     * regular expression will be matched against the id string value,
     * and the first match wins.
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
     * Get the Config object.
     */
    public Config getConfig() {
        return config;
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
    // Create a new IdResolver

    public IdResolver newResolver(IdType wantedType, Config config)
            throws MalformedURLException
    {
        return new IdResolver(this, wantedType, this.config.withFallback(config));
    }

    public IdResolver newResolver(IdType wantedType)
        throws MalformedURLException
    {
        return new IdResolver(this, wantedType, this.config);
    }

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
     * This is used as a lock for a thread-synchronized operation below.
     */
    private static Object _litIdsLock = new Object();

    /**
     * Get a fresh copy of the literature id database, using the default
     * config. A new copy is made
     * in order to minimize the probability of different threads interfering
     * with each other.
     * @return
     */
    public static IdDb getLiteratureIdDb() {
        return getLiteratureIdDb(null);
    }

    /**
     * Get a fresh copy of the literature id database, with some configuration
     * overrides. If `config == null`, this just uses the default config.
     */
    public static IdDb getLiteratureIdDb(Config config) {
        synchronized(_litIdsLock) {
            List<IdType> typeList = new ArrayList<IdType>(Arrays.asList(
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
            ));

            IdDb _literatureIdDb = new IdDb("literature-ids", config);
            _literatureIdDb.addTypes( typeList );
            return _literatureIdDb;
        }
    }
}

package gov.ncbi.ids;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceprogram.kittycache.KittyCache;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This class resolves IDs entered by the user, using the PMC ID Converter
 * API (http://www.ncbi.nlm.nih.gov/pmc/tools/id-converter-api/). This allows
 * the user to give us IDs in any number of forms, and we can look up the data
 * by any one of its IDs.
 *
 * Each IdResolver is instantiated with a "wanted" IdType. At request
 * time, the app calls resolveIds(), passing in a list of ID value strings.
 * This IdResolver then attempts to resolve each of the ID value strings
 * into an Identifier of the wanted type.
 */
public class IdResolver
{
    private static final Logger log = LoggerFactory.getLogger(IdResolver.class);

    public final boolean cacheEnabled;
    public final int cacheTtl;
    public final int cacheSize;
    public final URL converterUrl;
    public final String converterParams;

    private final IdDb iddb;
    private final IdType wantedType;

    /// The computed base URL of the converter service.
    public final String idConverterBase;

    /**
     * FIXME: this cache should move to IdDb.
     * If caching is enabled, the results returned from the external ID
     * resolver service are cached here. The keys of this are all of the
     * known CURIEs for the Identifiers for any given IdSet that gets
     * instantiated.
     */
    KittyCache<String, IdSet> idGlobCache;

    /// This is used to parse JSON
    private ObjectMapper mapper;

    /**
     * The JSON response from the service typically has Id fields mixed
     * in with metadata fields. Here are the known metadata field keys.
     */
    private static final List<String> nonIdFields = Arrays.asList(
        new String[] {
          "versions",
          "current",
          "live",
          "status",
          "errmsg",
          "release-date",
        }
    );

    /**
     * Create a new IdResolver object.
     * @param iddb - the IdDb in effect
     * @param wantedType - requested IDs will be resolved, if needed, to get
     *   an Identifier of this type
     * @throws MalformedURLException  If, via the Config setup, the URL to the
     *   backend service is no good.
     */
    public IdResolver(IdDb iddb, IdType wantedType)
        throws MalformedURLException
    {
        this(iddb, wantedType, null);
    }

    /**
     * Construct while passing in an ObjectMapper. This allows us to mock it
     * for unit tests.
     */
    public IdResolver(IdDb iddb, IdType wantedType, ObjectMapper mapper)
        throws MalformedURLException
    {
        Config conf = ConfigFactory.load();
        this.cacheEnabled = conf.getBoolean("ncbi-ids.cache-enabled");
        this.cacheTtl = conf.getInt("ncbi-ids.cache-ttl");
        this.cacheSize = conf.getInt("ncbi-ids.cache-size");
        this.converterUrl = new URL(conf.getString("ncbi-ids.converter-url"));
        this.converterParams = conf.getString("ncbi-ids.converter-params");

        this.iddb = iddb;
        this.wantedType = wantedType;
        this.idConverterBase = converterUrl + "?" + converterParams + "&";
        this.mapper = mapper == null ? new ObjectMapper() : mapper;

        log.debug("Instantiating idGlobCache, size = " + cacheSize +
                ", time-to-live = " + cacheTtl);
        this.idGlobCache = new KittyCache<>(cacheSize);
    }

    // For debugging
    public String getConfig() {
        return "config: {\n" +
                "  cache-enabled: " + cacheEnabled + "\n" +
                "  cache-ttl: " + cacheTtl + "\n" +
                "  cache-size: " + cacheSize + "\n" +
                "  converter-url: " + converterUrl + "\n" +
                "  converter-params: " + converterParams + "\n" +
                "}";
    }

    /**
     * Get the IdDb in use.
     */
    public IdDb getIdDb() {
        return iddb;
    }

    /**
     * Get the wanted IdType
     */
    public IdType getWantedType() {
        return wantedType;
    }

    /**
     * Resolves a comma-delimited list of IDs into a List of RequestIds.
     *
     * @param values - comma-delimited list of ID value strings, typically from
     *   a user-supplied query. Each one might or might not have a prefix. The
     *   original type of each one is determined independently.
     * @return a List of RequestIds. Best effort will be made to ensure each
     *   ID value string is resolved to an Identifier with the wantedType.
     *
     * For reference, here's the list of routines this calls:
     * -/ parseRequestIds(String, String)  - create a new list of RequestId's
     *     - new RequestId(IdDb, String, String)
     * -/ groupsToResolve(List<RequestId>)  - group them by type
     * -/ resolverUrl(IdType, List<RequestId>  - form the URL for the request
     * -  recordFromJson(ObjectNode, IdNonVersionSet)  - parse the result
     * -  findAndBind(IdType, List<RequestId>, IdSet)  - write result to list
     */
    public List<RequestId> resolveIds(String values)
            throws IOException
    {
        return resolveIds(null, values);
    }

    /**
     * Resolves a comma-delimited list of IDs into a List of RequestIds.
     *
     * @param reqType - The name of an IdType, or null. This allows the
     *   user to override the default interpretation of an ID value string. For
     *   example, if she specified "pmcid", then the value "12345" would be
     *   interpreted as "PMC12345" rather than as a pmid or an aiid.
     * @param reqValues - comma-delimited list of ID value strings, typically from
     *   a user-supplied query.
     * @return a List of RequestIds. Best effort will be made to make sure each
     *   ID value string is resolved to an Identifier with the wantedIdType.
     */
    public List<RequestId> resolveIds(String reqType, String reqValues)
            throws IOException
    {
        log.debug("Resolving IDs '" + reqValues + "'");

        // Parse the strings into a list of RequestId objects
        List<RequestId> allRids = parseRequestIds(reqType, reqValues);

        // Pick out those that need to be resolved, grouped by fromType
        Map<IdType, List<RequestId>> groups = groupsToResolve(allRids);

        // For each of those groups
        for (Map.Entry<IdType, List<RequestId>> entry : groups.entrySet()) {
            IdType fromType = entry.getKey();
            List<RequestId> gRids = entry.getValue();

            // Compute the URL to the resolver service
            URL url = resolverUrl(fromType, gRids);

            // Invoke the resolver
            ObjectNode response = (ObjectNode) mapper.readTree(url);

            String status = response.get("status").asText();
            log.debug("Status response from id resolver: " + status);
            log.debug("Parsed data tree: " + response);

            if (!status.equals("ok")) {
                log.info("Error response from ID resolver for URL " + url);
                JsonNode msg = response.get("message");
                if (msg != null)
                    log.info("Message: " + msg.asText());
            }
            else {
                // In parsing the response, we'll create IdSet objects as we go. We
                // have to then match them back to the correct entry in the
                // original list of RequestIds.

                ArrayNode records = (ArrayNode) response.get("records");
                for (JsonNode record : records) {
                    try {
                        IdSet set = readIdSet((ObjectNode) record);
                        log.debug("Constructed an id set: " + set);
                        if (set == null) continue;
                        findAndBind(fromType, gRids, set);
                    }
                    catch (IOException e) {
                        log.error(e.getMessage());
                        continue;
                    }
                }
            }
        }

        return allRids;
    }

    /**
     * Helper function to turn a type string and a comma-delimited list of
     * ID values (with or without prefixes) into a List of RequestIds.
     */
    public List<RequestId> parseRequestIds(String reqType, String reqValues)
    {
        String[] reqValArray = reqValues.split(",");
        return Arrays.asList(reqValArray).stream()
            .map(v -> new RequestId(iddb, reqType, v))
            .collect(Collectors.toList());
    }

    /**
     * This helper function creates groups of RequestIds, grouped by their
     * main types.
     *
     * Included in the output list are all of the RequestId objects that
     * are well-formed, have not been resolved, and don't have
     * the wanted type.
     *
     * The ID resolution service can take a list of IDs, but they must
     * all be of the same type; whereas the full list of RequestIds, in
     * the general case, have mixed types.
     */
    public Map<IdType, List<RequestId>> groupsToResolve(List<RequestId> rids)
    {
        // All the groups, keyed by their types
        Map<IdType, List<RequestId>> groups  = new HashMap<>();

        for (RequestId rid : rids) {
            if (rid != null && rid.isWellFormed() &&
                !rid.isResolved() && !rid.hasType(wantedType))
            {
                IdType fromType = rid.getMainType();

                List<RequestId> group = groups.get(fromType);
                if (group == null) {
                    group = new ArrayList<RequestId>();
                    groups.put(fromType, group);
                }
                group.add(rid);
            }
        }
        return groups;
    }

    /**
     * Helper function to compute the URL for a request to the resolver service.
     * This does no validating; the rids in the list should be all well-formed,
     * and of the same type.
     * @param fromType  used in the `idtype` parameter to the resolver service
     * @param rids  list of RequestIds; these must all be well-formed and
     *   not resolved, and they should all be of the same type.
     * @throws  IllegalArgumentException if it can't form a good URL
     */
    public URL resolverUrl(IdType fromType, List<RequestId> rids)
    {
        if (rids == null || rids.size() == 0)
            throw new IllegalArgumentException("No request IDs given");

        // Join the ID values for the query string
        String idsStr = rids.stream()
            .map(RequestId::getMainValue)
            .collect(Collectors.joining(","));

        try {
            String typeStr = rids.get(0).getMainType().getName();
            return new URL(idConverterBase + "idtype=" + typeStr +
                "&ids=" + idsStr);
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                "Parameters must have a problem; got malformed URL for " +
                "upstream service '" + converterUrl + "'");
        }
    }

    /////////////////////////////////////////////////////////////////////
    // Read the JSON data response

    /**
     * Helper function to throw an exception.
     */
    private void badJson(String cause)
        throws IOException
    {
        String msg = "Error processing JSON response from ID resolver: " + cause;
        throw new IOException(msg);
    }

    /**
     * Helper function that checks for and validates a boolean field in the
     * JSON response from the resolver service. When boolean values are
     * expected, either JSON boolean `true` or `false`, or strings that
     * have the exact value "true" or "false" can be used.
     * @param bnode  The JsonNode that's expected to hold a boolean value
     * @param required  If this is false, then `bnode` is optional.
     * @return `true` if the argument is a JSON boolean `true` or JSON string
     *   with value "true"; false if it is null, boolean `false` or a string
     *   with value "false".
     * @throws IOException  if the argument is not a valid boolean, as
     *   described above.
     */
    public boolean readBoolean(JsonNode bnode, boolean required)
        throws IOException
    {
        if (bnode == null) {
            if (required) badJson("Missing boolean value");
            else return false;
        }
        if (bnode.isBoolean()) return bnode.asBoolean();
        if (bnode.isTextual()) {
            String bstr = bnode.asText();
            if (bstr.equals("true")) return true;
            if (bstr.equals("false")) return false;
            badJson("Expected a boolean value 'true' or 'false'");
        }
        badJson("Expected a boolean value");
        return false;
    }

    public interface FieldHandler {
        boolean check(Map.Entry<String, JsonNode> field, IdSet set)
            throws IOException;
    }

    FieldHandler c = (field, set) -> { return true; };

    FieldHandler checkStatusField = (field, set) -> {
        if (!field.getKey().equals("status")) return false;
        JsonNode statusNode = field.getValue();
        String status = statusNode.asText();
        if (!status.equals("success"))
            badJson("ID resolver reports bad status: " + status);
        return true;
    };

    FieldHandler checkIdField = (field, set) -> {
        String key = field.getKey();
        IdType idType = set.iddb.getType(key);
        if (idType == null) return false;

        // The response includes an aiid for the parent, but that's
        // redundant, since the same aiid always also appears in a
        // version-specific child.
        if (key.equals("aiid") && !set.isVersioned()) return true;

        String value = field.getValue().asText();
        Identifier id = idType.id(value);
        if (id == null) badJson("Couldn't parse identifier of type " +
            idType + ": " + value);

        set.add(id);
        return true;
    };

    FieldHandler checkCurrentField = (field, set) -> {
        if (!field.getKey().equals("current")) return false;
        if (!set.isVersioned()) badJson("`current` field on parent node");

        boolean current = readBoolean(field.getValue(), false);
        try {
            if (current) ((VersionedIdSet) set).setIsCurrent();
        }
        catch (IllegalArgumentException e) {
            throw new IOException(
                "`current` set to true on more than one version children");
        }
        return true;
    };

    /**
     * Helper function that reads all the `idtype: idvalue` fields in a
     * JSON object, creates Identifiers and adds them to IdSets.
     */
    private void dispatchJsonFields(IdSet set, JsonNode record)
        throws IOException
    {
        Iterator<Map.Entry<String, JsonNode>> i = record.fields();
        while (i.hasNext()) {
            Map.Entry<String, JsonNode> field = i.next();
            String key = field.getKey();

            if (checkStatusField.check(field, set)) continue;
            if (checkIdField.check(field, set)) continue;
            if (checkCurrentField.check(field, set)) continue;
        }
    }

    /**
     * Helper function to create the parent IdSet object out of a single JSON
     * record from the id converter. See src/test/resources/ for examples.
     * @returns  An NonVersionedIdSet object, or null if there was a syntax error.
     */
    public NonVersionedIdSet readIdSet(JsonNode jrecord)
        throws IOException
    {
        synchronized(this) {
            ObjectNode record = (ObjectNode) jrecord;
            NonVersionedIdSet self = new NonVersionedIdSet(iddb);
            dispatchJsonFields(self, record);

            ArrayNode versionsNode = (ArrayNode) record.get("versions");
            for (JsonNode kidRecord : versionsNode) {
                readVersion((ObjectNode) kidRecord, self);
            }
            return self;
        }
    }

    public void readVersion(ObjectNode record, NonVersionedIdSet parent)
        throws IOException
    {
        VersionedIdSet kself = new VersionedIdSet(parent);
        dispatchJsonFields(kself, record);
    }

    /**
     * Helper function to find the RequestId corresponding to a new set.
     * The new IdSet was created from JSON data. If a matching
     * RequestId is found, the set is bound to it.
     */
    public RequestId findAndBind(IdType fromType, List<RequestId> rids,
            IdSet set)
    {
        Identifier globId = set.getId(fromType);
        for (RequestId rid : rids) {
            if (globId.equals(rid.getMainId())) {
                rid.resolve(set);
                return rid;
            }
        }
        return null;
    }
}

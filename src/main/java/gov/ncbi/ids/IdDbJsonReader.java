package gov.ncbi.ids;

import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES;
import static com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.databind.node.JsonNodeType.ARRAY;
import static com.fasterxml.jackson.databind.node.JsonNodeType.BOOLEAN;
import static com.fasterxml.jackson.databind.node.JsonNodeType.OBJECT;
import static com.fasterxml.jackson.databind.node.JsonNodeType.STRING;
import static gov.ncbi.ids.IdParser.NOOP;
import static gov.ncbi.ids.IdParser.UPPERCASE;
import static gov.ncbi.ids.IdParser.replacer;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gov.ncbi.ids.IdParser.IdMatchData;



public class IdDbJsonReader
{
    private static final Logger log = LoggerFactory.getLogger(IdDbJsonReader.class);

    public static final JsonParser.Feature[] jsonFeatures = {
        ALLOW_COMMENTS,
        ALLOW_NON_NUMERIC_NUMBERS,
        ALLOW_UNQUOTED_FIELD_NAMES,
        ALLOW_NUMERIC_LEADING_ZEROS,
        ALLOW_SINGLE_QUOTES,
        STRICT_DUPLICATE_DETECTION
    };

    /**
     * Jackson ObjectMapper. Note that ALLOW_TRAILING_COMMA won't
     * be available until version 2.9.
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(jsonFeatures);

    /**
     * Read an IdDb object from a JSON string
     */
    public IdDb readIdDb(String jsonString)
        throws IOException
    {
        return readIdDb(mapper.readTree(jsonString));
    }

    /**
     * Read an IdDb object from a JSON resource
     */
    public IdDb readIdDb(URL jsonUrl)
        throws IOException
    {
        System.out.println("jsonUrl: " + jsonUrl);
        return readIdDb(mapper.readTree(jsonUrl.openStream()));
    }

    /**
     * Read an IdDb from a JsonNode.
     */
    public IdDb readIdDb(JsonNode topNode)
        throws IOException
    {
        log.debug("Reading an IdDb from JsonNode: " + topNode);

        _validateNode("top node", topNode, true, OBJECT);
        ObjectNode top = (ObjectNode) topNode;

        String name = _getField(top, "name", true, STRING).asText();
        IdDb iddb = new IdDb(name);

        ArrayNode idTypesNode = (ArrayNode) _getField(top, "idTypes", true, ARRAY);
        for (JsonNode jt : idTypesNode) {
            _validateNode("id type node", jt, true, OBJECT);
            iddb.addType(_readJsonIdType(iddb, (ObjectNode) jt));
        }

        return iddb;
    }


    /**
     * Helper function to validate a JsonNode: to make sure it is not null,
     * and that it's of the expected type.
     */
    private void _validateNode(String desc, JsonNode node, boolean required,
            JsonNodeType expType)
        throws IOException
    {
        if (required && node == null)
            throw new IOException("Bad IdDb JSON data; missing " + desc);
        if (node != null && node.getNodeType() != expType)
            throw new IOException("Bad IdDb JSON data; " + desc +
                    " is of the wrong type");
    }

    /**
     * Helper function to get a value from a key in a JSON object.
     * This verifies that it exists, and checks the expected type.
     */
    private JsonNode _getField(ObjectNode parent, String key, boolean required,
            JsonNodeType expType)
        throws IOException
    {
        JsonNode f = parent.get(key);
        _validateNode("field " + key, f, required, expType);
        return f;
    }

    /**
     * Read a single IdType from a JSON object
     */
    private IdType _readJsonIdType(IdDb iddb, ObjectNode typeNode)
        throws IOException
    {
        String name = _getField(typeNode, "name", true, STRING).asText();

        ArrayNode parsersNode =
                (ArrayNode) _getField(typeNode, "parsers", true, ARRAY);
        List<IdParser> parsers = new ArrayList<>();
        for (JsonNode parserNode : parsersNode) {
            _validateNode("id type parser node", parserNode, true, OBJECT);
            parsers.add(_readJsonParser((ObjectNode) parserNode));
        }
        return new IdType(name, parsers);
    }

    /**
     * Read a single Parser from a JSON object
     */
    private IdParser _readJsonParser(ObjectNode parserNode)
        throws IOException
    {
        String pattern =
                _getField(parserNode, "pattern", true, STRING).asText();

        String canonicalize =
                _getField(parserNode, "canonicalize", true, STRING).asText();
        JsonNode rnode = _getField(parserNode, "replacement", false, STRING);
        String replacement = rnode == null ? null : rnode.asText();

        Function<IdMatchData, String> clizer =
                canonicalize.equals("NOOP") ? NOOP :
                canonicalize.equals("REPLACEMENT") ? replacer(replacement) :
                canonicalize.equals("UPPERCASE") ? UPPERCASE :
                    null;
        if (clizer == null) throw new IOException("Invalid JSON data");

        boolean versioned =
            _getField(parserNode, "isVersioned", true, BOOLEAN).asBoolean();

        return new IdParser(pattern, versioned, clizer);
    }
}

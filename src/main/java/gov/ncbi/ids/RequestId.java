package gov.ncbi.ids;

import static gov.ncbi.ids.RequestId.State.GOOD;
import static gov.ncbi.ids.RequestId.State.INVALID;
import static gov.ncbi.ids.RequestId.State.NOT_WELL_FORMED;
import static gov.ncbi.ids.RequestId.State.UNKNOWN;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This stores information about an ID as requested by the user: it
 * stores the original string the user entered, the original type specifier
 * (if there was one), and the "main" Identifier object created as the
 * result of parsing that string.
 *
 * In addition, this might store the results of invoking the external ID
 * resolution service, as an IdSet. Any RequestId object can be in one
 * of the following states.
 *
 *                      isResolved
 *            isWellFormed      isValid  Description
 *  -------------------------------------------------------------------------
 *  NOT_WELL_FORMED  F       T      F     The attempt to parse the string as
 *                                        an ID failed
 *  UNKNOWN          T       F    MAYBE   The string is well-formed, but we
 *                                        don't know whether or not it
 *                                        refers to a real resource.
 *  INVALID          T       T      F     The string is well-formed,
 *                                        but it does not refer to a real
 *                                        resource.
 *  GOOD             T       T      T     The string was parsed as a valid
 *                                        Identifier, and it refers to a
 *                                        real resource. Other
 *                                        IDs, of different types, have been
 *                                        found and are linked.
 *
 * As a note on implementation, here's another table with the values of each
 * of a few member variables in each of the states:
 *
 *                   _mainId  _resolved  _set
 *  -------------------------------------------
 *  NOT_WELL_FORMED    null       T       null
 *  UNKNOWN         Identifier    F       null
 *  INVALID         Identifier    T       null
 *  GOOD            Identifier    T       IdSet
 */

public class RequestId extends Id
{
    private static final Logger log = LoggerFactory.getLogger(RequestId.class);

    /**
     * This enum describes the overall state of the RequestId.
     */
    public enum State {
        NOT_WELL_FORMED,
        UNKNOWN,
        INVALID,
        GOOD
    }

    /**
     * Reference to the IdDb in use.
     */
    private final IdDb iddb;

    /**
     * The original type specifier, if one was in effect, else null.
     */
    private final String requestedType;

    /**
     *  The original value string, as entered by the user
     */
    private final String requestedValue;

    /**
     * The result of parsing the requestedValue, either with type specified
     * by the user or auto-determined. If this is null, then this RequestId
     * is not-well-formed.
     */
    private final Identifier mainId;

    /**
     * This will be true if the external ID resolution service has been
     * invoked for this RequestId.
     */
    private boolean resolved;

    /**
     * The results of the external ID resolution service. If _resolved is true,
     * but this is null, then this ID is invalid (does not point to a
     * real resource).
     */
    private IdSet set = null;

    /**
     * Construct from a value string, that might or might not have a prefix.
     */
    public RequestId(IdDb iddb, String value)
    {
        this(iddb, null, value);
    }

    /**
     * Construct while optionally specifying an IdType. The value string can
     * be in any non-canonical form, and might or might not have a prefix. If
     * the type and value strings can't be reconciled or parsed, then this
     * RequestId will be in the NOT_WELL_FORMED state.
     */
    public RequestId(IdDb iddb, String reqType, String reqValue)
    {
        this.iddb = iddb;
        this.requestedType = reqType;
        this.requestedValue = reqValue;

        IdParts idp = new IdParts(iddb, reqType, reqValue);
        log.trace("parts: " + idp);
        this.mainId = iddb.makeId(idp);
        log.trace("mainId: " + mainId);

        // If not-well-formed, set the "_resolved" flag to true
        this.resolved = (mainId == null);
    }


    /**
     * A couple of methods return a three-state value: either true,
     * false, or unknown (maybe).
     */
    public enum B {
        TRUE,
        FALSE,
        MAYBE
    }

   /*
    *                   _mainId  _resolved  _set
    *  -------------------------------------------
    *  NOT_WELL_FORMED    null       T       null
    *  UNKNOWN         Identifier    F       null
    *  INVALID         Identifier    T       null
    *  GOOD            Identifier    T       IdSet
    */
    public State getState() {
        return this.mainId == null ? NOT_WELL_FORMED :
               !this.resolved      ? UNKNOWN :
               this.set == null    ? INVALID :
                   GOOD;
    }

    /**
     * @return  true if the original type specifier and value string
     * were successfully parsed into an Identifier object.
     */
    public boolean isWellFormed() {
        return this.mainId != null;
    }

    /**
     * The RequestId is considered resolved if no more information
     * about it can be obtained.
     * @return  true if this is not-well-formed or if this has been
     *   subjected to an ID resolution service
     */
    public boolean isResolved() {
        return this.resolved;
    }

    /**
     * Whether or not the requested ID successfully parsed and is known to
     * point to a real resource.
     * @return  One of three states: B.TRUE, B.FALSE, or B.MAYBE. The
     *   return value will be B.MAYBE if the value string was successfully
     *   parsed into an Identifier, but it hasn't been resolved yet. In that
     *   case, it's not possible to say whether or not it points to a real
     *   resource.
     */
    public B isGood() {
        State state = getState();
        return
            state == NOT_WELL_FORMED ? B.FALSE :
            state == UNKNOWN         ? B.MAYBE :
            state == INVALID         ? B.FALSE :
                                       B.TRUE;
    }

    /**
     * Get the IdDb in use.
     */
    public IdDb getIdDb() {
        return this.iddb;
    }

    /**
     * Get the original requested type (might be null).
     */
    public String getRequestedType() {
        return this.requestedType;
    }

    /**
     * Get the original requested value.
     */
    public String getRequestedValue() {
        return this.requestedValue;
    }

    /**
     * Get the main Identifier.
     * @return null if this RequestId is NOT_WELL_FORMED.
     */
    public Identifier getMainId() {
        return this.mainId;
    }

    /**
     * Get the main IdType that was specified or inferred during the
     * instantiation.
     * @return  null if this RequestId is NOT_WELL_FORMED.
     */
    public IdType getMainType() {
        return this.mainId == null ? null : this.mainId.getType();
    }

    /**
     * Get the String value, canonicalized but without a prefix (e.g.
     * "PMC12345").
     * @return  null if this RequestId is NOT_WELL_FORMED.
     */
    public String getMainValue() {
        return this.mainId == null ? null : this.mainId.getValue();
    }

    /**
     * Get the canonicalized curie, which is a prefixed String (e.g.
     * "pmcid:PMC12345").
     * @return null if this RequestId is NOT_WELL_FORMED.
     */
    public String getMainCurie() {
        return this.mainId == null ? null : this.mainId.getCurie();
    }

    /**
     * Returns the IdSet object, which has the results of the ID
     * resolution, if it has been done.
     * @return  null unless the state is GOOD.
     */
    public IdSet getIdSet() {
        return this.set;
    }


    /**
     * Returns true if this is known to have an Identifier of the given type
     * @param type  Specify an ID type. Not null.
     */
    public boolean hasType(IdType type) {
        return this.mainId != null && (
            this.mainId.getType().equals(type) || (
                this.set != null && this.set.hasType(type)
            )
        );
    }

    /**
     * Get an Identifier, given a type.
     * @param type  Specify an ID type. Not null.
     */
    public Identifier getId(IdType type) {
        return   this.mainId == null                ? null
               : this.mainId.getType().equals(type) ? this.mainId
               : this.set == null                   ? null
                                                : this.set.getId(type);
    }

    /**
     * This function is similar, but allows you to provide a list of types. If
     * there is no Identifier of the first type, then it tries the second type,
     * until one is found; or returns null.
     */
    public Identifier getId(List<IdType> types) {
        return types.stream()
                .map(t -> this.getId(t))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns true if this RequestId is well-formed, and the Identifiers
     * for it are versioned.
     */
    @Override
    public boolean isVersioned() {
        return this.mainId != null && this.mainId.isVersioned();
    }


    /**
     * Resolve this, either into the INVALID state (if set == null) or into the
     * GOOD state.
     * @param set  an IdSet, or null. If not null, this must match the main
     *   Identifier of this RequestId.
     */
    public void resolve(IdSet set)
            throws IllegalStateException, IllegalArgumentException
    {
        if (this.resolved) throw new IllegalStateException(
            "Attempt to resolve a RequestId that has already been resolved.");
        if (set != null && !set.same(mainId)) throw new IllegalArgumentException(
            "Attempt to resolve a RequestId with a mismatching ID set.");
        //System.out.println("Setting _set to " + set);
        this.resolved = true;
        this.set = set;
    }

    //////////////////////////////////////////////////////////////////////////
    // Equals and sameness methods

    @Override
    public int hashCode() {
        return Objects.hash(
            this.requestedType,
            this.requestedValue,
            this.mainId,
            this.resolved,
            this.set
        );
    }

    /**
     * Returns true if this RequestId is known to point to the same resource
     * as a given Identifier. Note that it's possible for this to return
     * false even if they do refer to the same resource -- if this RequestId
     * hasn't yet been resolved.
     */
    public boolean same(IdScope scope, Id oid) {
        if (oid == null || this.mainId == null) return false;
        if (this.mainId.same(scope, oid)) return true;
        return this.set != null && this.set.same(scope, oid);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof RequestId)) return false;
        RequestId orid = (RequestId) other;
        return this.requestedType == orid.requestedType &&
                this.requestedValue == orid.requestedValue &&
                this.mainId == orid.mainId &&
                this.resolved == orid.resolved &&
                this.set == orid.set;
    }

    //////////////////////////////////////////////////////////////////////////
    // Display methods

    @Override
    public String toString() {
        return "{ requested: " +
            (requestedType == null ? "" : requestedType + ":") +
            requestedValue + ", found: " +
            this.getMainCurie() + " }";
    }

    public String dump() {
        String r =
            "{ " +
               "requested: { " +
                 "type: " + (requestedType == null ? "none" : requestedType) + ", " +
                 "value: " + requestedValue + " " +
               "}, " +
               "main: " + mainId + ", " +
               "equivalent: " + set + " " +
            "}";
        return r;
    }

}

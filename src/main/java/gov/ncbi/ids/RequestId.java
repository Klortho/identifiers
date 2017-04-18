package gov.ncbi.ids;

import static gov.ncbi.ids.Id.IdScope.EXPRESSION;
import static gov.ncbi.ids.RequestId.State.*;

import java.util.Arrays;
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
 * <p>
 * In addition, this might store the results of invoking the external ID
 * resolution service, as an IdSet. Any RequestId object can be in one
 * of the following states.
 *
 * <table>
 *   <tr><th></th><th>isWellFormed</th><th>isResolved</th><th>isValid</th><th>Description</th></tr>
 *   <tr><th>NOT_WELL_FORMED</th><td>F</td><td>T</td><td>F</td>
 *     <td>The attempt to parse the string as an ID failed</td></tr>
 *   <tr><th>UNKNOWN</th><td>T</td><td>F</td><td>MAYBE</td>
 *     <td>The string is well-formed, but we don't know whether or not it
 *       refers to a real resource.</td></tr>
 *   <tr><th>INVALID</th><td>T</td><td>T</td><td>F</td>
 *     <td>The string is well-formed, but it does not refer to a real resource.</td></tr>
 *   <tr><th>GOOD</th><td>T</td><td>T</td><td>T</td>
 *     <td>The string was parsed as a valid Identifier, and it refers to a
 *       real resource. Other IDs, of different types, have been
 *       found and are linked.</td></tr></table>
 *
 * <p>
 * The state is derived from the values of a few properties,
 * as listed in the following table:
 *
 * <table>
 *   <tr><th></th><th>queryId</th><th>resolved</th><th>set</th></tr>
 *   <tr><th>NOT_WELL_FORMED</th><td>null</td><td>T</td><td>null</td></tr>
 *   <tr><th>UNKNOWN</th><td>Identifier</td>  <td>F</td><td>null</td></tr>
 *   <tr><th>INVALID</th><td>Identifier</td>  <td>T</td><td>null</td></tr>
 *   <tr><th>GOOD</th><td>Identifier</td>     <td>T</td><td>IdSet</td></tr>
 * </table>
 */

public class RequestId extends Id
{
    @SuppressWarnings("unused")
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
    private final String queryType;

    /**
     *  The original value string, as entered by the user
     */
    private final String queryValue;

    /**
     * The result of parsing the queryValue, either with type specified
     * by the user or auto-determined. If this is null, then this RequestId
     * is not-well-formed.
     */
    private final Identifier queryId;

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
     * This property lets applications attach any arbitrary data object to
     * each request id, if needed.
     */
    private Object appData = null;

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
    public RequestId(IdDb iddb, String queryType, String queryValue)
    {
        this.iddb = iddb;
        this.queryType = queryType;
        this.queryValue = queryValue;

        IdParts idp = new IdParts(iddb, this.queryType, this.queryValue);
        this.queryId = iddb.makeId(idp);

        // If not-well-formed, set the "_resolved" flag to true
        this.resolved = (queryId == null);
    }


    /**
     * A couple of methods return a three-state value: either true,
     * false, or unknown (maybe).
     */
    public enum MaybeBoolean {
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
        return this.queryId == null ? NOT_WELL_FORMED :
               !this.resolved      ? UNKNOWN :
               this.set == null    ? INVALID :
                   GOOD;
    }

    /**
     * This is true if the original type specifier and value string
     * were successfully parsed into an Identifier object.
     */
    public boolean isWellFormed() {
        return this.queryId != null;
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
     * FIXME: I changed this from returning a 3-state variable to a plain bool.
     *   Need to update these docs.
     * Whether or not the requested ID successfully parsed and is known to
     * point to a real resource.
     * @return  One of three values: MaybeBoolean.TRUE, MaybeBoolean.FALSE, or
     *   MaybeBoolean.MAYBE. The return value will be MaybeBoolean.MAYBE if the
     *   value string was successfully parsed into an Identifier, but it hasn't
     *   been resolved yet. In that case, it's not possible to say whether or
     *   not it points to a real resource.
     */
    public boolean isGood() {
        State state = getState();
        return
            state == NOT_WELL_FORMED ? false :
            state == UNKNOWN         ? false :
            state == INVALID         ? false :
                                       true;
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
        return this.queryType;
    }

    /**
     * Get the original requested value.
     */
    public String getRequestedValue() {
        return this.queryValue;
    }

    /**
     * Get the IdType that was specified when this object was created.
     * Note that this is not necessarily the same as getQueryType().
     */
    public String getQueryType() {
        return this.queryType;
    }

    public String getQueryValue() {
        return this.queryValue;
    }

    /**
     * Get the main Identifier.
     * @return null if this RequestId is NOT_WELL_FORMED.
     */
    public Identifier getQueryId() {
        return this.queryId;
    }

    /**
     * Get the IdType that was specified when this object was created.
     * Note that this is not necessarily the same as getQueryType().
     */
    public IdType getQueryIdType() {
        return this.queryId == null ? null : this.queryId.getType();
    }

    /**
     * Get the String value, canonicalized but without a prefix (e.g.
     * "PMC12345").
     * @return  null if this RequestId is NOT_WELL_FORMED.
     */
    public String getQueryIdValue() {
        return this.queryId == null ? null : this.queryId.getValue();
    }

    /**
     * Get the canonicalized curie, which is a prefixed String (e.g.
     * "pmcid:PMC12345").
     * @return null if this RequestId is NOT_WELL_FORMED.
     */
    public String getMainCurie() {
        return this.queryId == null ? null : this.queryId.getCurie();
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
     * Set the application-specific data property.
     */
    public void setAppData(Object newData) {
        this.appData = newData;
    }

    /**
     * Get the application-specific data property.
     */
    public Object getAppData() {
        return this.appData;
    }

    /**
     * Returns true if this is known to have an Identifier of the given type
     * @param type  Specify an ID type. Not null.
     */
    public boolean hasType(IdType type) {
        return this.getId(type) != null;
    }

    /**
     * Get an Identifier, given a type.
     * @param type  Specify an ID type. Not null.
     */
    public Identifier getId(IdType type) {
        return   this.queryId == null                ? null
               : this.queryId.getType().equals(type) ? this.queryId
               : this.set == null                    ? null
                        : this.set.id(EXPRESSION, type);
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
     * Same as getId(List<IdType>), but accepts a variable number of arguments
     */
    public Identifier getIdFromTypes(IdType... types) {
        return this.getId(Arrays.asList(types));
    }

    /**
     * Returns true if this RequestId is well-formed, and the Identifiers
     * for it are versioned.
     */
    @Override
    public boolean isVersioned() {
        return this.queryId != null && this.queryId.isVersioned();
    }


    /**
     * Resolve this, either into the INVALID state (if set == null) or into the
     * GOOD state.
     * @param set  an IdSet, or null. If not null, this must match the main
     *   Identifier of this RequestId. If null, then the state after this is
     *   called will be INVALID.
     */
    public void resolve(IdSet set)
            throws IllegalStateException, IllegalArgumentException
    {
     /* System.out.println("----------- in RequestId.resolve -----\n" +
            ", this: " + this + ", this-curie: " +
            this.getMainCurie() +
            ", IdSet: " + set); */
        if (this.resolved) throw new IllegalStateException(
            "Attempt to resolve a RequestId that has already been resolved.");
        if (set != null && !set.same(queryId)) throw new IllegalArgumentException(
            "Attempt to resolve a RequestId with a mismatching ID set.");
        this.resolved = true;
        this.set = set;
    }

    //////////////////////////////////////////////////////////////////////////
    // Equals and sameness methods

    @Override
    public int hashCode() {
        return Objects.hash(
            this.queryType,
            this.queryValue,
            this.queryId,
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
    @Override
    public boolean same(IdScope scope, Id oid) {
        if (oid == null || this.queryId == null) return false;
        if (this.queryId.same(scope, oid)) return true;
        return this.set != null && this.set.same(scope, oid);
    }

    /**
     * This helper provides a shortcut for testing the object properties
     * for equality.
     */
    public static boolean objEquals(Object objA, Object objB) {
        return
            (objA == objB) ||
            ( (objA != null) && (objB != null) &&
              objA.equals(objB) );
    }

    /**
     * Two RequestIds are equal if they are the same in all their particulars:
     * queryType, queryValue, queryId, resolved, and set.
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof RequestId)) return false;
        RequestId orid = (RequestId) other;
        return objEquals(this.queryType, orid.queryType) &&
            objEquals(this.queryValue, orid.queryValue) &&
            objEquals(this.queryId, orid.queryId) &&
            this.resolved == orid.resolved &&
            objEquals(this.set, orid.set);
    }

    //////////////////////////////////////////////////////////////////////////
    // Display methods

    @Override
    public String toString() {
        String qType = queryType == null ? "none" : queryType;
        String qVal = queryValue == null ? "none" : queryValue;
        String qId = this.queryId == null ? "null" : this.queryId.getCurie();
        return "{ query type: " + qType + ", value: " + qVal + " => id: " +
            qId + " }";
    }

    public String dump() {
        String r =
            "{ state of this RequestId: " + this.getState() + "\n" +
               "requested: { " +
                 "type: " + (queryType == null ? "none" : queryType) + ", " +
                 "value: " + queryValue + " " +
               "}, " +
               "main: " + queryId + ", " +
               "equivalent: " + set + " " +
            "}";
        return r;
    }

}

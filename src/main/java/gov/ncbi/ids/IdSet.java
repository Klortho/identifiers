package gov.ncbi.ids;

import static gov.ncbi.ids.Id.IdScope.EQUAL;
import static gov.ncbi.ids.Id.IdScope.RESOURCE;
import static gov.ncbi.ids.Id.IdScope.EXPRESSION;
import static gov.ncbi.ids.Id.IdScope.WORK;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public abstract class IdSet extends Id
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(IdSet.class);
    protected final IdDb iddb;

    /**
     * A set of Identifiers, indexed by type, that are all
     * semantically equivalent.
     */
    private Map<IdType, Identifier> idByType;

    ////////////////////////////////////////
    // Constructors and builder methods

    /**
     * Create a new IdSet
     */
    public IdSet(IdDb iddb) {
        this.iddb = iddb;
        this.idByType = new HashMap<>();
    }

    /**
     * This method is the default implementation of add()
     */
    protected IdSet _add(Identifier...ids) {
        if (ids == null) return this;
        for (Identifier id : ids) {
            if (id == null) continue;

            // If they're trying to add the same ID twice, do nothing
            if (this.same(id)) continue;

            // If it's a different ID of the same type, that's bad
            IdType type = id.getType();
            if (this.idByType.get(type) != null)
                throw new IllegalArgumentException(
                    "IdSet already contains an ID of type " + type.getName());

            // The IDs must match in version-specificity
            if (this.isVersioned() != id.isVersioned())
                throw new IllegalArgumentException(
                    "Attempt to add a new non-version-specific Identifier " +
                    "to a version-specific IdSet.");

            // All good
            this.idByType.put(type, id);
        }
        return this;
    }

    /**
     * Add new Identifier(s) to this IdSet. This is abstract because the
     * actual return type will be the same as the subclass'. Each subclass
     * will just delegate back to the protected _add() method here.
     *
     * @param ids  A list of Identifiers.
     * @throws IllegalArgumentException if this IdSet already has a different
     *   Identifier with the same type, or if the version-specificity
     *   doesn't match.
     */
    public abstract IdSet add(Identifier... ids);

    ////////////////////////////////////////
    // Getters

    /**
     * Get the IdDb to which this belongs
     */
    public IdDb getIdDb() {
        return this.iddb;
    }

    /**
     * Get the "complement" IdSet, which is the other IdSet in
     * this cluster that refers to the same version of the resource,
     * if it exists. The complement is determined as follows:
     * - If `this` is a non-version-specific parent, the complement is
     *   `_currentVersion`.
     * - Otherwise `this` is a version-specific child, and:
     *     - If it is the current version, then the complement is the
     *       parent.
     */
    public abstract IdSet getComplement();

    /**
     * Get the current version of this IdSet
     */
    public abstract IdSet getCurrent();

    /**
     * True if this IdSet has an Identifier of the given type.
     * If type == null, this returns false.
     */
    public boolean hasType(IdType type) {
        return this.getId(type) != null;
    }

    /**
     * This gets the Identifier of the given type. Unlike id(type), though,
     * if the argument is null, this returns null.
     */
    public Identifier getId(IdType type) {
        return this.idByType.get(type);
    }

    /////////////////////////////////////////////////////////////////
    // Helpers

    /**
     * Convenience function to convert a Stream<> into a List<>
     */
    public static <T> List<T> toList(Stream<T> idStream) {
        return idStream.collect(Collectors.toList());
    }

    // Use this to create a function that can be passed to `flatMap`,
    // to map IdSets to Streams of Identifiers of the given type.
    public Function<? super IdSet, ? extends Stream<? extends Identifier>>
        withType(IdType type)
    {
        // The real list of non-null IdTypes to consider
        List<IdType> rtypes =
            type == null ? this.getIdDb().getTypes() : Arrays.asList(type);

        // This produces a function that takes an IdSet and produces a
        // Stream of non-null Identifiers.
        return set ->
            rtypes.stream()
                .map(t -> set.idByType.get(t))
                .filter(Objects::nonNull);
    }

    /////////////////////////////////////////////////////////////////
    // Get IdSets as Streams

    /**
     * Get the Stream of IdSets in the given scope
     */
    public Stream<IdSet> setStream(IdScope scope) {
        return scope == RESOURCE ? this.resourceSetStream()
             : scope == EXPRESSION ? this.expressionSetStream()
             : this.workSetStream();
    }

    /**
     * Overloading setStream(scope), with RESOURCE as the default
     */
    public Stream<IdSet> setStream() {
        return this.resourceSetStream();
    }

    /**
     * Get just this one IdSet (scope == RESOURCE) as a Stream.
     */
    public Stream<IdSet> resourceSetStream() {
        return Stream.of(this);
    }

    /**
     * Get the group of IdSets that refer to the same expression,
     * as a Stream
     */
    public Stream<IdSet> expressionSetStream() {
        IdSet comp = this.getComplement();
        return Stream.concat(
            Stream.of(this),
            comp == null ? Stream.empty() : Stream.of(comp)
        );
    }

    /**
     * Get the IdSets that refer to the same work.
     */
    public abstract Stream<IdSet> workSetStream();

    /////////////////////////////////////////////////////////////////
    // Get Identifiers as Streams

    /**
     * From the sets in the indicated scope, create a stream of the
     * Identifiers with a given type, or, if `type` == null, all types.
     */
    public Stream<Identifier> idStream(IdScope scope, IdType type) {
        return this.setStream(scope).flatMap(withType(type));
    }

    public Stream<Identifier> idStream(IdScope scope) {
        return this.idStream(scope, null);
    }

    public Stream<Identifier> idStream() {
        return this.idStream(RESOURCE, null);
    }

    public Stream<Identifier> idStream(IdType type) {
        return this.idStream(RESOURCE, type);
    }

    /////////////////////////////////////////////////////////////////
    // Lists of Identifiers

    public List<Identifier> ids(IdScope scope, IdType type) {
        return idStream(scope, type).collect(Collectors.toList());
    }

    public List<Identifier> ids(IdScope scope) {
        return this.ids(scope, null);
    }

    public List<Identifier> ids() {
        return this.ids(RESOURCE, null);
    }

    public List<Identifier> ids(IdType type) {
        return this.ids(RESOURCE, type);
    }

    /////////////////////////////////////////////////////////////////
    // Get single Identifiers

    /**
     * Returns the first matching Identifier from the set of those in the
     * indicated scope and with matching type, or null if there's no match.
     */
    public Identifier id(IdScope scope, IdType type) {
        return idStream(scope, type)
                .findFirst()
                .orElse(null);
    }

    public Identifier id(IdScope scope) {
        return this.id(scope, null);
    }

    public Identifier id() {
        return this.id(RESOURCE, null);
    }

    /**
     * Unlike getId(), if `type` is null, this returns the ID of the first
     * type that matches, with types in the order that they are added to the
     * database.
     * If `type` is not null, but there is no matching Identifier, this
     * returns null.
     */
    public Identifier id(IdType type) {
        return this.id(RESOURCE, type);
    }

    /////////////////////////////////////////////////////////////////
    // Equality and comparison methods

    @Override
    public int hashCode() {
        return Objects.hash(this.isVersioned(), this.idByType);
    }

    /**
     * `this` IdSet is equal to another iff it has the same
     * version-specificity, the same number of Identifiers,
     * and each Identifier in `this` is equal to an Identifier with
     * the same type in the other. Versioned children are
     * not considered.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IdSet)) return false;
        IdSet oset = (IdSet) other;
        return this.isVersioned() == oset.isVersioned() &&
               this.idByType.equals(oset.idByType);
    }

    /**
     * Returns true if `this` IdSet and the argument refer to
     * the same semantic resource. This works by generating a stream of
     * Identifiers, and then calling that Identifier's same() method,
     * in the same scope. If oid is also an Identifier, then it will
     * end there. Otherwise, oid will delegate back to *our* Identifier's
     * methods.
     */
    @Override
    public boolean same(IdScope scope, Id oid) {
        if (oid == null) return false;

        if (scope == EQUAL) {
            return this.equals(oid);
        }

        if (scope.ordinal() <= WORK.ordinal()) {
            return this.idStream(scope, null)
                .anyMatch(id -> oid.same(scope, id));
        }

        return true;
    }

    /////////////////////////////////////////////////////////////////
    /**
     * Render this IdSet as a string.
     */
    @Override
    public String toString() {
        return "{ " + this.myIdsToString() + " }";
    }

    /**
     * Helper for the toString() method; produces the string representation
     * of all of *this* set's IDs. Doesn't include any versioned child info.
     */
    protected String myIdsToString() {
        return this.idStream()
            .map(Identifier::toString)
            .collect(Collectors.joining(", "));
    }

    /**
     * Default implementation of dump()
     */
    public String dump() {
        return this.toString();
    }
}

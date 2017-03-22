package gov.ncbi.ids;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This stores a canonicalized, immutable identifier value. It has a
 * reference to its IdType, and a flag indicating whether or not it is
 * versioned.
 */
public class Identifier extends Id
{
    private static final Logger log = LoggerFactory.getLogger(Identifier.class);

    private final IdType type;
    private final String value;   // canonicalized, but without prefix
    private final boolean versioned;

    /**
     * Identifiers can't be constructed directly. Use one of the IdType
     * or the IdDb makeId() methods.
     *
     * This constructor is used by those classes to create a new object
     * with an already-canonicalized value string.
     */
    protected Identifier(IdType type, String value,
            boolean versioned)
    {
        this.type = type;
        this.value = value;
        this.versioned = versioned;
    }

    public IdType getType() {
        return this.type;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public boolean isVersioned() {
        return this.versioned;
    }

    public String getCurie() {
        return getType().getName() + ":" + getValue();
    }

    /**
     * To be equal, the identifiers must match type and value.
     * We implicitly assume that if they match those, then they
     * will also match _isVersionSpecific; if they don't, that's
     * an error.
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Identifier)) return false;
        Identifier id = (Identifier) other;
        return this.type.equals(id.type) && this.value.equals(id.value);
    }

    @Override
    public boolean same(IdScope scope, Id oid) {
        //System.out.println("    Identifier::same(" + scope + ", " + oid + ")");
        if (oid == null) return false;
        if (oid instanceof Identifier) return this.equals(oid);
        return oid.same(scope, this);  // punt
    }

    @Override
    public String toString() {
        return getCurie();
    }
}

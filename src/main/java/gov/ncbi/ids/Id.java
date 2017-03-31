package gov.ncbi.ids;

import static gov.ncbi.ids.Id.IdScope.EXPRESSION;
import static gov.ncbi.ids.Id.IdScope.RESOURCE;
import static gov.ncbi.ids.Id.IdScope.WORK;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Methods common to both Identifier and IdSet.
 */
public abstract class Id
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(Id.class);

    /**
     * Enumeration that specifies the scope of a request for IdSets.
     */
    public enum IdScope {
        /// This one is only used for testing
        EQUAL,
        /// Just this IdSet.
        RESOURCE,
        /// IdSets that refer to the same version at a given time
        EXPRESSION,
        /// All IdSets that refer to the same work
        WORK,
        /// Also used for testing, to indicate two ids share nothing in common
        DIFFERENT,
    }

    /**
     * Constructor
     */
    public Id() {}

    /**
     * @return true if this refers to a specific version of a resource;
     * false otherwise.
     */
    public abstract boolean isVersioned();

    /**
     * Force subclasses to implement this.
     */
    @Override
    public abstract int hashCode();

    /**
     * Subclasses must also implement equals().
     */
    @Override
    public abstract boolean equals(Object other);

    /**
     * Compares this Id with another.
     */
    public abstract boolean same(IdScope scope, Id oid);

    /**
     * Alias for `sameResource`
     */
    public boolean same(Id oid) {
        return same(RESOURCE, oid);
    }

    /**
     * @return true if this Id refers to the exact same semantic resource as
     * the argument. For example, pmid:17401604 is the same resource as
     * pmcid:PMC1868567, since they both refer to the same non-versioned
     * journal article.
     */
    public boolean sameResource(Id oid) {
        return same(RESOURCE, oid);
    }

    /**
     * @return true if this Id refers to the same expression (version) of a
     * resource as the argument.
     */
    public boolean sameExpression(Id oid) {
        return same(EXPRESSION, oid);
    }

    /**
     * @return true if this Id refers to the same work as the the argument.
     */
    public boolean sameWork(Id oid) {
        return same(WORK, oid);
    }
}

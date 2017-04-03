package gov.ncbi.ids;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Objects of this class store a group of version-specific Identifiers.
 */
public class VersionedIdSet extends IdSet
{
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(VersionedIdSet.class);

    /**
     * The non-version-specific parent
     */
    private final NonVersionedIdSet _parent;

    ////////////////////////////////////////
    // Constructors and builder methods

    /**
     * Create a new version-specific IdSet, and add this to the list
     * of the parent's kids. This form of the constructor lets you
     * specify that this is the current version, at the time it is
     * created.
     */
    public VersionedIdSet(NonVersionedIdSet parent, boolean isCurrent) {
        super(parent.iddb);
        _parent = parent;
        _parent._addVersion(this, isCurrent);
    }

    /**
     * Create a new version-specific IdSet, and add this to the list
     * of the parent's kids.
     */
    public VersionedIdSet(NonVersionedIdSet parent) {
        this(parent, false);
    }

    @Override
    public VersionedIdSet add(Identifier... ids) {
        return (VersionedIdSet) this._add(ids);
    }

    ////////////////////////////////////////
    // Getters

    /**
     * Returns true because this IdSet is version-specific.
     */
    @Override
    public boolean isVersioned() {
        return true;
    }

    /**
     * Get the non-version-specific parent IdSet
     */
    public NonVersionedIdSet getParent() {
        return _parent;
    }

    /**
     * Makes this version the current version
     */
    public void setIsCurrent() {
        this._parent.setCurrent(this);
    }

    /**
     * Returns true if this is the current version
     */
    public boolean isCurrent() {
        return this.equals(_parent.getCurrent());
    }

    /**
     * Get the complement. If this is the current version, then
     * the complement will be the parent. Otherwise, this will
     * return null.
     */
    @Override
    public IdSet getComplement() {
        return isCurrent() ? _parent : null;
    }

    /**
     * Get current
     */
    @Override
    public IdSet getCurrent() {
        return _parent.getCurrent();
    }

    ////////////////////////////////////////
    // Info about IDs

    @Override
    public Stream<IdSet> workSetStream() {
        return Stream.concat(
            Stream.of(this, _parent),
            _parent.kidsStream().filter( k -> !this.equals(k) )
        );
    }

    /////////////////////////////////////////////////////////////////
    // toString, equality, comparison methods
}

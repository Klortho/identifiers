package gov.ncbi.ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Objects of this class store a group of Identifiers that all refer to
 * the same non-version-specific resource.
 */
public class NonVersionedIdSet extends IdSet
{
    private static final Logger log = LoggerFactory.getLogger(NonVersionedIdSet.class);

    /**
     * References to the version-specific children.
     */
    private final List<VersionedIdSet> kids;

    /**
     * If this is non-version-specific, this will refer to the current
     * version
     */
    private VersionedIdSet current;

    ////////////////////////////////////////
    // Constructors and builder methods

    /**
     * Create a new non-version-specific IdSet
     */
    public NonVersionedIdSet(IdDb iddb) {
        super(iddb);
        kids = new ArrayList<VersionedIdSet>();
        current = null;
    }

    @Override
    public NonVersionedIdSet add(Identifier... ids) {
        this._add(ids);
        return this;
    }

    /**
     * Add a versioned IdSet as a child of this IdSet. No checks
     * are made to see if the argument is already in the list or not.
     * This is only called from the VersionedIdSet constructor.
     */
    protected void _addVersion(VersionedIdSet vkid, boolean isCurrent) {
        kids.add(vkid);
        if (isCurrent) current = vkid;
    }

    ////////////////////////////////////////
    // Getters

    /**
     * Returns false.
     */
    @Override
    public boolean isVersioned() {
        return false;
    }

    @Override
    public VersionedIdSet getCurrent() {
        return current;
    }

    @Override
    public IdSet getComplement() {
        return current;
    }

    public List<VersionedIdSet> getVersions() {
        return kids;
    }

    /////////////////////////////////////////////////////////////////
    // Info about IDs

    /**
     * Get all of the version-specific children as a Stream.
     * The current version is first, then the others
     * in reverse order.
     */
    public Stream<IdSet> kidsStream() {
        // Make a stream of all the kids *except* the current version, in
        // reverse order. Do this by means of an integer stream.
        int top = kids.size();
        IntStream revI = IntStream.range(0, top).map(i -> top - i - 1);
        Stream<IdSet> reverseKids = revI.mapToObj(i -> kids.get(i));

        // Put _currentVersion first, and filter out nulls.
        return Stream.concat(
            Stream.of(current),
            reverseKids.filter(kid -> (kid != current))
        ).filter(Objects::nonNull);
    }

    /**
     * This implements the getSetStream(WORK) method for
     * non-version-specific IdSets.
     */
    @Override
    public Stream<IdSet> workSetStream() {
        return Stream.concat(
            Stream.of(this),
            this.kidsStream()
        );
    }

    /////////////////////////////////////////////////////////////////
    // toString, equality, comparison methods

    /**
     * Provide more detailed info than toString()
     */
    @Override
    public String dump() {
        String kidsString = kids.stream()
            .map(kid -> (kid.isCurrent() ? "*" : "") + kid.toString())
            .collect(Collectors.joining(", "));
        return "{ " + myIdsToString() + ", versions: [ " +
            kidsString + " ] }";
    }
}

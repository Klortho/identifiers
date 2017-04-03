# Identifiers

A Java library for working with Identifiers of various resources.

***Current status:*** Almost complete. All tests working except
TestIdResolver.

## Data model

An IdSet holds a list of Identifiers that all refer to the
same resource. There are two types of resources, corresponding to two types of
Identifiers:

- Non-versioned identifiers (e.g. "PMC123456") refer to the abstract concept
  of some entity, independent of any revisions that it might undergo. This is
  analogous to a "work", as defined by the FRBR standard
  (http://www.oclc.org/research/activities/frbr.html), and this library uses
  that term.
- Versioned identifiers (e.g. "PMC123456.1") refer to a specific version of
  the entity as it existed at some point in time. This is analogous to the FRBR
  concept of "expression".

Note that at any given time, for a give work, there will be a "current" version.
So, for example, the journal article PMC123456 might have three versions, with
the current version being PMC132456.3. If so, then both
https://ncbi.nlm.nih.gov/pmc/articles/PMC123456 and
https://ncbi.nlm.nih.gov/pmc/articles/PMC123456.3 would resolve to the same
version of this article, but these identifier would not be considered equivalent,
since they still refer to different conceptual resources. The former refers
to "the *current* version", and the latter refers to "version 3".

A more complete example is the following PMC article, which has
three separate identifiers that refer to the *work*:

- PubMed ID (pmid): 17401604
- PMC ID (pmcid): PMC1868567
- DOI (doi): 10.1007/s10162-007-0081-z

There also exist sets of IDs that refer to specific versions (note that each
version can have multiple IDs). They are:

- Version 1: PMC1868567.1, mid:NIHMS20955, aiid:1868567
- Version 2: PMC1868567.2, aiid:1950588
- Version 3 (current): PMC1868567.3, aiid:2538359

Note that some types of identifiers (for example, pmcid's) have both
non-versioned (e.g. PMC1868567) and versioned (e.g. PMC1868567.2) forms.
Other types of identifiers do not have this characteristic. DOIs are always
(ostensibly) non-version-specific, whereas Manuscript IDs are
always version-specific.

As of the time of this writing, in the above example, the third version is
"current". So, both PMC1868567 and PMC1868567.3 will dereference to the same
digital object. Yet, they can't be considered equal, because
they refer to different semantic resources.

This library includes methods to determine when two identifiers are "the same",
in a number of different scopes. For completeness, they are listed here
combined with the Java equality concepts.

- `===` - identical; the two Java variables refer to the same object in memory
- `.equals()` - the two Java objects are completely interchangeable
- `.same()` - the two Ids refer to the same resource: either both are
    non-versioned identifiers, or both are versioned. For example:
    `PMC1868567.1`.same(`mid:NIHMS20955`) would be *true*.
- `.sameExpression()` - at the time this is invoked, the two Ids dereference
    to the same digital object.
- `.sameWork()` - the two Ids are both valid identifiers in the complete set
    of identifiers for a given *work*.

There are two subclasses of `IdSet`: `NonVersionedIdSet` and `VersionedIdSet`,
that store these different types of IDs, and a parent-child relationship
between them. A given `IdNonVersionedSet` can have zero-to-many
`IdVersionedSet` children.


## Testing

To run unit tests from the command line:

```
mvn test
```

To run just one specific set of tests (all the tests defined in the TestRequests
class):

```
mvn -Dtest=TestIdSet test
```

To run a single method of a test class:

```
mvn -Dtest=TestIdSet#testConstructorVer test
```

To run a test without compiling all of the other, unaffected classes and members
of the repository, specify the lifecycle phase explicitly as `surefire:test`.
(This is useful if you want to run a unit test on a class you're working on,
but there are compile-time errors in other files.)

```
mvn -Dtest=TestIdSet surefire:test
```

You can use wildcards; for example:

```
mvn '-Dtest=Test*' test
mvn -Dtest='*Transform*' test
```

See the documentation of the [Maven Surefire
plugin](http://maven.apache.org/surefire/maven-surefire-plugin/),
for more options.


### Log configuration during testing

When testing, the log is configured with src/test/resources/log4j.properties.




## Configuration

This library uses the [typesafehub
Config](https://typesafehub.github.io/config/) library for configuration,
which affords several ways of specifying configuration information.






## Javadocs

The following commands generate javadocs:

- `mvn package` - generates the javadocs, but only if all other phases pass;
  writes them to target/site/apidocs, as well as to the jar file
  target/identifiers-<ver>-javadoc.jar
- `mvn javadoc:javadoc` - generates HTML documentation in target/site/apidocs
- `mvn javadoc:jar` - generates target/identifiers-<ver>-javadoc.jar
- `mvn javadoc:test-javadoc` - generates docs for the test classes, in
  target/site/testapidocs
- `mvn javadoc:test-jar` - generates target/identifiers-<ver>-test-javadoc.jar

For more information, see:

* [Apache Maven Javadoc Plugin](https://maven.apache.org/plugins/maven-javadoc-plugin/)



### Surefire test reports

The following commands generate pretty Surefire HTML reports giving results


### Findbugs

Enter the following to bring up a GUI application to browse the results
of the findbugs tests:

```
mvn findbugs:gui
```


## Dependencies

Dependencies are declared in the *pom.xml* file, and are resolved
automatically by Maven.

Below is a list of some of the stable dependencies, along with links to
documentation, useful when doing development, and more details, where
warranted.

Several of the dependencies use a free third-party service
[Jitpack.io](https://jitpack.io/) in order to freeze specific revisions
from GitHub repositories. These are libraries that are not on Maven
Central, and Jitpack.io provides a way to ensure that we are using a
stable version. This requires adding the following to the \<repositories>
section of the pom:

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>
```


### Java

Requires Java version 8.

* Platform / library [Javadocs](http://docs.oracle.com/javase/8/docs/api/)

### PMC ID Converter API

* [Documentation](https://www.ncbi.nlm.nih.gov/pmc/tools/id-converter-api/)

### Jackson

We're using the Jackson library to read JSON objects. Here are some
handy links:

* [Home page](http://wiki.fasterxml.com/JacksonHome)
* [Data binding](https://github.com/FasterXML/jackson-databind) - includes
  tutorial on how to use it.
* [Javadocs](http://fasterxml.github.io/jackson-databind/javadoc/2.3.0/)
* [Jackson annotations](https://github.com/FasterXML/jackson-annotations) -
  how to annotate the classes that map to JSON objects

### kitty-cache

The library is on Google code
[here](https://code.google.com/p/kitty-cache/), and is also mirrored to
GitHub, at [treeder/kitty-cache](https://github.com/treeder/kitty-cache),
but it is not in Maven Central.

It is declared in the citation-exporter pom.xml, using
[JitPack.io](https://jitpack.io) to build and deploy it from a fork of the
repository on GitHub, at
[Klortho/kitty-cache](https://github.com/Klortho/kitty-cache).
(The reason for using a fork is to protect against the possibility of the
original repo being removed.)


## Public Domain notice

National Center for Biotechnology Information.

This software is a "United States Government Work" under the terms of the
United States Copyright Act.  It was written as part of the authors'
official duties as United States Government employees and thus cannot
be copyrighted.  This software is freely available to the public for
use. The National Library of Medicine and the U.S. Government have not
placed any restriction on its use or reproduction.

Although all reasonable efforts have been taken to ensure the accuracy
and reliability of the software and data, the NLM and the U.S.
Government do not and cannot warrant the performance or results that
may be obtained by using this software or data. The NLM and the U.S.
Government disclaim all warranties, express or implied, including
warranties of performance, merchantability or fitness for any
particular purpose.

Please cite NCBI in any work or product based on this material.


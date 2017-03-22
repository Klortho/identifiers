# Identifiers

A Java library for working with Identifiers of various resources.

***Current status:*** Almost complete. All tests working except
TestIdResolver.

## Data model

An IdSet holds a list of Identifiers that all refer to the
same semantically-defined resource. For example, a PMC article might
have three separate primary IDs such as the following.

- PubMed ID (pmid): 17401604
- PMC ID (pmcid): PMC1868567
- DOI (doi): 10.1007/s10162-007-0081-z

None of these three IDs refer to a specific version of the article.
Rather, they refer to the concept of this particular article
as a scientific work, without regard version.

However, there also exist IDs that do refer to specific versions. For
example, the final submitted manuscript of the above article is specified
by the following version-specific IDs:

- pmcid: PMC1868567.1
- Manuscript ID (mid): NIHMS20955

Note that PMC IDs have both non-version-specific (without a ".n" suffix)
and version specific (with suffix) forms. Other ID types do not have this
characteristic. For example, DOIs are always (ostensibly)
non-version-specific, whereas Manuscript IDs are
always version-specific.

The example article above has two other versions, that are referred to by
the following:

- second version: pmcid: PMC1868567.2
- third version: PMC1868567.3

As of the time of this writing, the third version is "current". So, at
this time, the non-version-specific ID PMC1868567 refers to the same
"expression" (in the FRBR sense) as the version-specific PMC1868567.1.
They can't be considered equal because semantically,
they refer to different resources (one refers to the "work", whereas the
other refers to the "expression").

This library includes methods to determine when two IDs are the same
in one of these scopes. So, for example, the following methods, if called on
the Identifier object for PMC1868567, would return `true`:

- sameResource(pmid:17401604)
- sameExpression(PMC1868567.3)
- sameWork(NIHMS20955)

There are two subclasses of IdSet, NonVersionedIdSet and VersionedIdSet,
that store these different types of IDs, and a parent-child relationship
between them. A given IdNonVersionedSet can have zero-to-many
IdVersionedSet children.


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
of the repository, specify the lifecycle phase explicitely as `surefire:test`.
(This is useful if you want to run a unit test on a class you're working on,
but there are other compile-time errors in other files.)

```
mvn -Dtest=TestIdSet surefire:test
```

You can use wildcards; for example:

```
mvn '-Dtest=Test*' test
mvn -Dtest='*Transform*' test
```

See documentation on the [Maven Surefire
plugin](http://maven.apache.org/surefire/maven-surefire-plugin/)
for more options.


## Configuration

### System properties

***FIXME:*** are these still relevant?

* On the command line. For example:

    ```
    mvn jetty:run -Djetty.port=9876 -Did_cache=true -Did_cache_ttl=8
    ```

### typesafehub Config

***FIXME:*** Need to document.

This library uses the [typesafehub
Config](https://typesafehub.github.io/config/) library for configuration
information.

## Maven from the command line

***FIXME:*** Include some examples


### Javadocs

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


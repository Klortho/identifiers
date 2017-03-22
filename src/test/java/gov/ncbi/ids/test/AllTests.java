package gov.ncbi.ids.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    TestIdParser.class,
    TestIdType.class,
    TestIdentifier.class,
    TestIdDb.class,
    TestIdSet.class,
    TestRequestId.class,
    TestIdResolver.class
})

public class AllTests {}

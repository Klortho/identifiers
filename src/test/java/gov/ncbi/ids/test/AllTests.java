package gov.ncbi.ids.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    TestConfig.class,
    TestIdParser.class,
    TestIdParts.class,
    TestIdType.class,
    TestIdentifier.class,
    TestIdDb.class,
    TestIdSet.class,
    TestRequestId.class,
    TestIdResolver.class
})

public class AllTests {}

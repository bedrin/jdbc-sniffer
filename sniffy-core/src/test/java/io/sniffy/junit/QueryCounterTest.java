package io.sniffy.junit;

import io.sniffy.*;
import io.sniffy.sql.SqlExpectation;
import io.sniffy.sql.WrongNumberOfRowsError;
import io.sniffy.test.Count;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.sniffy.Query.INSERT;

@NoQueriesAllowed
// TODO created a copy for new Count API
public class QueryCounterTest extends BaseTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final QueryCounter queryCounter = new QueryCounter();

    @Test
    public void testNotAllowedQueriesByDefault() {
        executeStatement();
        thrown.expect(WrongNumberOfQueriesError.class);
    }

    @Test
    @SqlExpectation(count = @Count(1))
    public void testAllowedOneQuery() {
        executeStatement();
    }

    @Test
    @Expectation(1)
    public void testAllowedOneQuery_DeprecatedApi() {
        executeStatement();
    }

    @Test
    @SqlExpectation(count = @Count(value = 5, min = 2))
    public void testAmbiguousExpectationAnnotation() {
        thrown.expect(IllegalArgumentException.class);
    }

    @Test
    @Expectation(value = 5, atLeast = 2)
    public void testAmbiguousExpectationAnnotation_DeprecatedApi() {
        thrown.expect(IllegalArgumentException.class);
    }

    @Test
    @Expectation(2)
    @NoQueriesAllowed
    public void testAmbiguousAnnotations() {
        thrown.expect(IllegalArgumentException.class);
    }

    @Test
    @Expectations({
            @Expectation(2),
            @Expectation(value = 2, threads = Threads.OTHERS)
    })
    @NoQueriesAllowed
    public void testAmbiguousAnnotations2() {
        thrown.expect(IllegalArgumentException.class);
    }

    @Test
    @NoQueriesAllowed
    public void testNotAllowedQueries() {
        executeStatement();
        thrown.expect(WrongNumberOfQueriesError.class);
    }

    @Test
    @Expectation(1)
    public void testAllowedOneQueryExecutedTwo() {
        executeStatements(2);
        thrown.expect(WrongNumberOfQueriesError.class);
    }

    @Test
    @Expectations({
            @Expectation(atMost = 1, threads = Threads.CURRENT),
            @Expectation(atMost = 1, threads = Threads.OTHERS),
    })
    public void testExpectations() {
        executeStatement();
        executeStatementInOtherThread();
    }

    @Test
    @Expectations({
            @Expectation(value = 1, query = Query.SELECT),
            @Expectation(value = 1, query = INSERT),
            @Expectation(value = 1, query = Query.UPDATE),
            @Expectation(value = 1, query = Query.DELETE),
            @Expectation(value = 1, query = Query.MERGE)
    })
    public void testDifferentQueries() {
        executeStatement(Query.SELECT);
        executeStatement(INSERT);
        executeStatement(Query.UPDATE);
        executeStatement(Query.DELETE);
        executeStatement(Query.MERGE);
    }

    @Test
    @Expectation(atLeast = 1)
    public void testAllowedMinOneQueryExecutedTwo() {
        executeStatements(2);
    }

    @Test
    @Expectation(atLeast = 2)
    public void testAllowedMinTwoQueriesExecutedOne() {
        executeStatement();
        thrown.expect(WrongNumberOfQueriesError.class);
    }

    @Test
    @Expectation(value = 2)
    public void testAllowedExactTwoQueriesExecutedTwo() {
        executeStatements(2);
    }

    @Test
    @Expectation(value = 2)
    public void testAllowedExactTwoQueriesExecutedThree() {
        executeStatements(3);
        thrown.expect(WrongNumberOfQueriesError.class);
    }

    @Test
    @SqlExpectation(rows = @Count(2))
    public void testAllowedExactTwoRows() {
        executeStatements(2, INSERT);
    }

    @Test
    @SqlExpectation(rows = @Count(2))
    public void testAllowedExactTwoRowsReturnedThree() {
        executeStatements(3, INSERT);
        thrown.expect(WrongNumberOfRowsError.class);
    }

    @Test
    @Expectation(2)
    public void testAllowedTwoQueries() {
        executeStatements(2);
    }

    @Test
    @Expectation(atLeast = 1, atMost = 3)
    public void testBetween() {
        executeStatements(2);
    }

}
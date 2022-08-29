/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static io.debezium.connector.cassandra.TestUtils.TEST_KEYSPACE_NAME;
import static io.debezium.connector.cassandra.TestUtils.TEST_TABLE_NAME;
import static io.debezium.connector.cassandra.TestUtils.runCql;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.config.Configuration;

public class CommitLogRealTimeParserTest extends AbstractCommitLogProcessorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommitLogRealTimeParserTest.class);

    @Override
    public CassandraConnectorContext generateTaskContext() throws Exception {
        Properties properties = TestUtils.generateDefaultConfigMap();
        properties.put(CassandraConnectorConfig.COMMIT_LOG_REAL_TIME_PROCESSING_ENABLED.name(), "true");
        properties.put(CassandraConnectorConfig.COMMIT_LOG_MARKED_COMPLETE_POLL_INTERVAL_IN_MS.name(), "1000");
        return generateTaskContext(Configuration.from(properties));
    }

    @Override
    public void initialiseData() throws Exception {
        createTable("CREATE TABLE IF NOT EXISTS %s.%s (a int, b int, PRIMARY KEY(a)) WITH cdc = true;");
        insertRows(3, 10);
    }

    private void insertRows(int count, int keyInc) {
        for (int i = 0; i < count; i++) {
            runCql(insertInto(TEST_KEYSPACE_NAME, TEST_TABLE_NAME)
                    .value("a", literal(i + keyInc))
                    .value("b", literal(i))
                    .build());
        }
        LOGGER.info("Inserted rows: {}", count);
    }

    @Override
    public void verifyEvents() throws Exception {
        verify(3, 10);
        insertRows(2, 20);
        verify(2, 20);
    }

    private void verify(int expectedEventsCount, int keyInc) throws InterruptedException {
        List<Event> events = queue.poll();
        int count = 0;
        int maxRetryCount = 3;
        while (events.size() != expectedEventsCount && count < maxRetryCount) {
            LOGGER.info("Sleep before polling for events, polling count: {}", count);
            Thread.sleep(2000);
            events.addAll(queue.poll());
            count += 1;
        }

        LOGGER.info("Total events received: {}", events.size());
        Assert.assertEquals("Total number of events received must be " + expectedEventsCount, expectedEventsCount, events.size());

        for (int i = 0; i < expectedEventsCount; i++) {
            Record record = (Record) events.get(i);
            Assert.assertEquals("Operation type must be insert", Record.Operation.INSERT, record.getOp());
            Assert.assertEquals("Inserted key should be " + i, record.getRowData().getPrimary().get(0).value, i + keyInc);
        }
    }

    @Override
    public void readLogs() throws Exception {
        // check to make sure there are no records in the queue to begin with
        assertEquals(queue.totalCapacity(), queue.remainingCapacity());
        String cdcLoc = DatabaseDescriptor.getCDCLogLocation();
        LOGGER.info("CDC Location: {}", cdcLoc);

        File[] commitLogs = CommitLogUtil.getIndexes(new File(cdcLoc));
        Thread.sleep(2000);

        Arrays.sort(commitLogs, (file1, file2) -> CommitLogUtil.compareCommitLogsIndexes(file1, file2));
        for (File commitLog : commitLogs) {
            LOGGER.info("Submitted the file: {}", commitLog);
            commitLogProcessor.submit(commitLog.toPath());
        }
    }

}

/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import static io.debezium.connector.cassandra.TestUtils.TEST_KEYSPACE_NAME;
import static io.debezium.connector.cassandra.TestUtils.deleteTestKeyspaceTables;
import static io.debezium.connector.cassandra.TestUtils.deleteTestOffsets;
import static io.debezium.connector.cassandra.TestUtils.runCql;
import static java.lang.String.format;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.connector.base.ChangeEventQueue;

public abstract class AbstractCommitLogProcessorTest extends EmbeddedCassandra4ConnectorTestBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCommitLogProcessorTest.class);
    public ChangeEventQueue<Event> queue;
    public CassandraConnectorContext context;
    public Cassandra4CommitLogProcessor commitLogProcessor;

    protected String tableName = "table_" + UUID.randomUUID().toString().replace("-", "");

    @Before
    public void setUp() throws Exception {
        initialiseData();
        context = generateTaskContext();
        await().forever().until(() -> context.getSchemaHolder().getKeyValueSchema(new KeyspaceTable(TEST_KEYSPACE_NAME, tableName)) != null);
        commitLogProcessor = new Cassandra4CommitLogProcessor(context);
        commitLogProcessor.initialize();
        queue = context.getQueues().get(0);
        readLogs();
    }

    @After
    public void tearDown() throws Exception {
        deleteTestOffsets(context);
        commitLogProcessor.destroy();
        deleteTestKeyspaceTables();
        context.cleanUp();
    }

    @Test
    public void test() throws Exception {
        verifyEvents();
    }

    public abstract void initialiseData() throws Exception;

    public abstract void verifyEvents() throws Exception;

    public void createTable(String query) {
        runCql(format(query, TEST_KEYSPACE_NAME, tableName));
    }

    public List<Event> getEvents() throws Exception {
        List<Event> events = queue.poll();
        assertFalse(events.isEmpty());
        return events;
    }

    public void readLogs() throws Exception {
        // check to make sure there are no records in the queue to begin with
        assertEquals(queue.totalCapacity(), queue.remainingCapacity());

        // process the logs in commit log directory
        File cdcLoc = new File(DatabaseDescriptor.getCommitLogLocation());
        File[] commitLogs = CommitLogUtil.getCommitLogs(cdcLoc);

        Cassandra4CommitLogProcessor processor = new Cassandra4CommitLogProcessor(context);

        for (File commitLog : commitLogs) {
            String newFileName = commitLog.toString().replace(".log", "_cdc.idx");
            Files.createFile(Paths.get(newFileName));
            FileWriter myWriter = new FileWriter(newFileName);
            myWriter.write("19999\nCOMPLETED");
            myWriter.close();
            LOGGER.info("Submitted the file: {}", newFileName);
            processor.submit(Paths.get(newFileName));
        }
    }
}

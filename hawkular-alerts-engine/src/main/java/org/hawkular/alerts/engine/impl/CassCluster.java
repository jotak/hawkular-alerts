/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.engine.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Map;

import org.hawkular.alerts.engine.util.TokenReplacingReader;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

/**
 * Cassandra cluster representation and session factory.
 *
 * @author Lucas Ponce
 */
public class CassCluster {
    private static final Logger log = Logger.getLogger(CassDefinitionsServiceImpl.class);
    private static final String ALERTS_CASSANDRA_PORT = "hawkular-alerts.cassandra-cql-port";
    private static final String ALERTS_CASSANDRA_PORT_ENV = "CASSANDRA_CQL_PORT";
    private static final String ALERTS_CASSANDRA_NODES = "hawkular-alerts.cassandra-nodes";
    private static final String ALERTS_CASSANDRA_NODES_ENV = "CASSANDRA_NODES";
    private static final String ALERTS_CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";
    private static final String ALERTS_CASSANDRA_RETRY_ATTEMPTS = "hawkular-alerts.cassandra-retry-attempts";

    /*
        ALERTS_CASSANDRA_RETRY_TIMEOUT defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_RETRY_TIMEOUT = "hawkular-alerts.cassandra-retry-timeout";

    /*
        ALERTS_CASSANDRA_CONNECT_TIMEOUT and ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_CONNECT_TIMEOUT = "hawkular-alerts.cassandra-connect-timeout";
    private static final String ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV = "CASSANDRA_CONNECT_TIMEOUT";

    /*
        ALERTS_CASSANDRA_READ_TIMEOUT and ALERTS_CASSANDRA_READ_TIMEOUT_ENV defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_READ_TIMEOUT = "hawkular-alerts.cassandra-read-timeout";
    private static final String ALERTS_CASSANDRA_READ_TIMEOUT_ENV = "CASSANDRA_READ_TIMEOUT";

    private Cluster cluster = null;

    private Session session = null;

    private boolean initialized = false;

    private static CassCluster instance = new CassCluster();

    private CassCluster() { }

    private void initScheme(Session session, String keyspace, boolean overwrite) throws IOException {

        if (keyspace == null) {
            keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, "hawkular_alerts");
        }

        if (log.isDebugEnabled()) {
            log.debug("Checking Schema existence for keyspace: " + keyspace);
        }

        KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);
        if (keyspaceMetadata != null) {
            if (overwrite) {
                session.execute("DROP KEYSPACE " + keyspace);
            } else {
                log.debug("Schema already exist. Skipping schema creation.");
                initialized = true;
                return;
            }
        }

        log.infof("Creating Schema for keyspace %s", keyspace);

        ImmutableMap<String, String> schemaVars = ImmutableMap.of("keyspace", keyspace);

        String updatedCQL = null;
        try (InputStream inputStream = CassCluster.class.getResourceAsStream("/hawkular-alerts-schema.cql");
             InputStreamReader reader = new InputStreamReader(inputStream)) {
            String content = CharStreams.toString(reader);

            for (String cql : content.split("(?m)^-- #.*$")) {
                if (!cql.startsWith("--")) {
                    updatedCQL = substituteVars(cql.trim(), schemaVars);
                    if (log.isDebugEnabled()) {
                        log.debug("Executing CQL:\n" + updatedCQL + "\n");
                    }
                    session.execute(updatedCQL);
                }
            }
        } catch (Exception e) {
            log.errorf("Failed schema creation: %s\nEXECUTING CQL:\n%s", e, updatedCQL);
        }
        initialized = true;

        log.infof("Done creating Schema for keyspace: " + keyspace);
    }

    private String substituteVars(String cql, Map<String, String> vars) {
        try (TokenReplacingReader reader = new TokenReplacingReader(cql, vars);
             StringWriter writer = new StringWriter()) {
            char[] buffer = new char[32768];
            int cnt;
            while ((cnt = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, cnt);
            }
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to perform variable substition on CQL", e);
        }
    }

    /**
     * Return a cached cassandra Session.
     * It generates the scheme keyspace on the first access.

     * @return A cached Session
     * @throws Exception on any issue
     */
    public static synchronized Session getSession() throws Exception {
        return getSession(false);
    }

    /**
     * Return a cached cassandra Session.
     * It generates the scheme keyspace on the first access.
     *
     * @param overwrite true will overwrite an existing keyspace at initialization
     *                  false will maintain an existing keyspace
     * @return A cached Session
     * @throws Exception on any issue
     */
    public static synchronized Session getSession(boolean overwrite) throws Exception {
        if (instance.cluster == null && instance.session == null) {
            String cqlPort = AlertProperties.getProperty(ALERTS_CASSANDRA_PORT, ALERTS_CASSANDRA_PORT_ENV, "9042");
            String nodes = AlertProperties.getProperty(ALERTS_CASSANDRA_NODES, ALERTS_CASSANDRA_NODES_ENV, "127.0.0.1");
            int attempts = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_ATTEMPTS, "5"));
            int timeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_TIMEOUT, "2000"));
            int connTimeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_CONNECT_TIMEOUT,
                    ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV,
                    String.valueOf(SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS)));
            int readTimeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_READ_TIMEOUT,
                    ALERTS_CASSANDRA_READ_TIMEOUT_ENV,
                    String.valueOf(SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS)));

            /*
                It might happen that alerts component is faster than embedded cassandra deployed in hawkular.
                We will provide a simple attempt/retry loop to avoid issues at initialization.
             */
            while(instance.session == null && !Thread.currentThread().isInterrupted() && attempts >= 0) {
                try {
                    SocketOptions socketOptions = null;
                    if (connTimeout != SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS ||
                            readTimeout != SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS) {
                        socketOptions = new SocketOptions();
                        if (connTimeout != SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS) {
                            socketOptions.setConnectTimeoutMillis(connTimeout);
                        }
                        if (readTimeout != SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS) {
                            socketOptions.setReadTimeoutMillis(readTimeout);
                        }
                    }

                    Cluster.Builder clusterBuilder = new Cluster.Builder()
                            .addContactPoints(nodes.split(","))
                            .withPort(new Integer(cqlPort))
                            .withProtocolVersion(ProtocolVersion.V3);

                    if (socketOptions != null) {
                        clusterBuilder.withSocketOptions(socketOptions);
                    }

                    instance.cluster = clusterBuilder.build();
                    instance.session = instance.cluster.connect();
                } catch (Exception e) {
                    log.warn("Could not connect to Cassandra cluster - assuming is not up yet. Cause: " +
                            ((e.getCause() == null) ? e : e.getCause()));
                    if (attempts == 0) {
                        throw e;
                    }
                }
                if (instance.session == null) {
                    log.warn("[" + attempts + "] Retrying connecting to Cassandra cluster in [" + timeout + "]ms...");
                    attempts--;
                    try {
                        Thread.sleep(timeout);
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (instance.session != null && !instance.initialized) {
                String keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, "hawkular_alerts");
                instance.initScheme(instance.session, keyspace, overwrite);
            }
        }
        if (instance.session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (instance.session != null && !instance.initialized) {
            throw new RuntimeException("Cassandra alerts keyspace is not initialized");
        }
        return instance.session;
    }

    public static void shutdown() {
        if (instance != null && instance.session != null && !instance.session.isClosed()) {
            instance.session.close();
        }
    }
}

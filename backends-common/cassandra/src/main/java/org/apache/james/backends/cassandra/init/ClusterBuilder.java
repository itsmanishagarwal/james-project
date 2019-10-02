/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.cassandra.init;

import java.util.Collection;
import java.util.Optional;

import org.apache.james.backends.cassandra.init.configuration.QueryLoggerConfiguration;
import org.apache.james.util.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.SocketOptions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ClusterBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterBuilder.class);
    private static final String DEFAULT_CLUSTER_IP = "localhost";
    public static final int DEFAULT_CASSANDRA_PORT = 9042;

    public static ClusterBuilder builder() {
        return new ClusterBuilder();
    }

    private Optional<String> username;
    private Optional<String> password;

    private Optional<Collection<Host>> servers;

    private Optional<QueryLoggerConfiguration> queryLogger;
    private Optional<Integer> readTimeoutMillis;
    private Optional<Integer> connectTimeoutMillis;
    private Optional<PoolingOptions> poolingOptions;
    private Optional<Boolean> useSsl;

    private ClusterBuilder() {
        username = Optional.empty();
        password = Optional.empty();
        useSsl = Optional.empty();

        servers = Optional.empty();

        queryLogger = Optional.empty();
        readTimeoutMillis = Optional.empty();
        connectTimeoutMillis = Optional.empty();
        poolingOptions = Optional.empty();
    }

    public ClusterBuilder username(String username) {
        return username(Optional.of(username));
    }

    public ClusterBuilder password(String password) {
        return password(Optional.of(password));
    }

    public ClusterBuilder username(Optional<String> username) {
        this.username = username;

        return this;
    }

    public ClusterBuilder password(Optional<String> password) {
        this.password = password;

        return this;
    }

    public ClusterBuilder useSsl(boolean useSsl) {
        this.useSsl = Optional.of(useSsl);

        return this;
    }

    public ClusterBuilder poolingOptions(PoolingOptions poolingOptions) {
        this.poolingOptions = Optional.of(poolingOptions);
        return this;
    }

    public ClusterBuilder poolingOptions(Optional<PoolingOptions> poolingOptions) {
        this.poolingOptions = poolingOptions;
        return this;
    }

    public ClusterBuilder servers(Host... servers) {
        this.servers = Optional.of(ImmutableList.copyOf(servers));

        return this;
    }

    public ClusterBuilder servers(Collection<Host> servers) {
        this.servers = Optional.of(servers);

        return this;
    }

    public ClusterBuilder queryLoggerConfiguration(QueryLoggerConfiguration queryLogger) {
        this.queryLogger = Optional.of(queryLogger);

        return this;
    }

    public ClusterBuilder readTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = Optional.of(readTimeoutMillis);
        return this;
    }

    public ClusterBuilder connectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = Optional.of(connectTimeoutMillis);
        return this;
    }

    public Cluster build() {
        Preconditions.checkState(username.isPresent() == password.isPresent(), "If you specify username, you must specify password");

        Cluster.Builder clusterBuilder = Cluster.builder()
            .withoutJMXReporting();
        getServers().forEach(server -> clusterBuilder
            .addContactPoint(server.getHostName())
            .withPort(server.getPort()));

        username.map(username ->
            password.map(password ->
                clusterBuilder.withCredentials(username, password)));

        clusterBuilder.withQueryOptions(queryOptions());

        SocketOptions socketOptions = new SocketOptions();
        readTimeoutMillis.ifPresent(socketOptions::setReadTimeoutMillis);
        connectTimeoutMillis.ifPresent(socketOptions::setConnectTimeoutMillis);
        clusterBuilder.withSocketOptions(socketOptions);
        poolingOptions.ifPresent(clusterBuilder::withPoolingOptions);

        useSsl.filter(b -> b).ifPresent(any -> clusterBuilder.withSSL());

        Cluster cluster = clusterBuilder.build();
        try {
            queryLogger.map(queryLoggerConfiguration ->
                cluster.register(queryLoggerConfiguration.getQueryLogger()));
            return cluster;
        } catch (Exception e) {
            cluster.close();
            throw e;
        }
    }

    private QueryOptions queryOptions() {
        return new QueryOptions()
                .setConsistencyLevel(ConsistencyLevel.QUORUM);
    }

    private Collection<Host> getServers() {
        return servers.orElse(ImmutableList.of(
            Host.from(DEFAULT_CLUSTER_IP, DEFAULT_CASSANDRA_PORT)));
    }
}

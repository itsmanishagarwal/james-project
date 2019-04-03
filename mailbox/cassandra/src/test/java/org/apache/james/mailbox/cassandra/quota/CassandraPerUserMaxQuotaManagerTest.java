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

package org.apache.james.mailbox.cassandra.quota;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraIncrementTestsPlayedRule;
import org.apache.james.backends.cassandra.DockerCassandraRestartRule;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.store.quota.GenericMaxQuotaManagerTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;

public class CassandraPerUserMaxQuotaManagerTest extends GenericMaxQuotaManagerTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    @ClassRule public static DockerCassandraRestartRule cassandraRestartRule = new DockerCassandraRestartRule();

    @Rule
    public DockerCassandraIncrementTestsPlayedRule cassandraIncrementRule = new DockerCassandraIncrementTestsPlayedRule();

    private static CassandraCluster cassandra;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(CassandraQuotaModule.MODULE, cassandraServer.getHost());
    }

    @Override
    protected MaxQuotaManager provideMaxQuotaManager() {
        return GuiceUtils.testInjector(cassandra)
            .getInstance(CassandraPerUserMaxQuotaManager.class);
    }

    @After
    public void cleanUp() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

}

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

package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;

import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropertiesProviderTest {
    private PropertiesProvider testee;

    @BeforeEach
    void setUp() {
        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());
        testee = new PropertiesProvider(fileSystem, configuration);
    }

    @Test
    void getConfigurationsShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> testee.getConfigurations())
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getConfigurationsShouldThrowWhenOnlyNotExistingFiles() {
        assertThatThrownBy(() -> testee.getConfigurations("c", "d"))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getConfigurationShouldThrowWhenNotExistingFile() {
        assertThatThrownBy(() -> testee.getConfiguration("d"))
            .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getConfigurationShouldReturnFirstExistingFile() throws Exception {
        assertThat(testee.getConfigurations("a", "b").getString("prop"))
            .isEqualTo("value1");
    }

    @Test
    void getConfigurationShouldReturnFirstExistingFileWhenAfterNonExistingFiles() throws Exception {
        assertThat(testee.getConfigurations("c", "b").getString("prop"))
            .isEqualTo("value2");
    }

    @Test
    void getConfigurationShouldLoadProperties() throws Exception {
        assertThat(testee.getConfiguration("a").getString("prop"))
            .isEqualTo("value1");
    }

    @Test
    void getConfigurationShouldLoadListValues() throws Exception {
        assertThat(testee.getConfiguration("a").getList(String.class, "keyByList"))
            .containsExactly("value1", "value2");
    }
}
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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetErrorMessageTest {
    private static final String MY_MESSAGE = "my message";

    private Mailet testee;

    @BeforeEach
    void setUp() {
        testee = new SetErrorMessage();
    }

    @Test
    void initShouldThrowWhenNoErrorMessage() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig.builder().build()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'errorMessage' needs to be specified and cannot be empty");
    }

    @Test
    void initShouldThrowOnEmptyErrorMessage() {
        assertThatThrownBy(() ->
            testee.init(FakeMailetConfig.builder()
                .setProperty("errorMessage", "")
                .build()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("'errorMessage' needs to be specified and cannot be empty");
    }

    @Test
    void serviceShouldPositionErrorMessage() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .setProperty("errorMessage", MY_MESSAGE)
            .build());

        FakeMail myMail = FakeMail.builder()
            .name("myMail")
            .build();
        testee.service(myMail);

        assertThat(myMail.getErrorMessage()).isEqualTo(MY_MESSAGE);
    }

    @Test
    void serviceShouldOverwriteErrorMessage() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .setProperty("errorMessage", MY_MESSAGE)
            .build());

        FakeMail myMail = FakeMail.builder()
            .name("myMail")
            .errorMessage("Old error message")
            .build();
        testee.service(myMail);

        assertThat(myMail.getErrorMessage()).isEqualTo(MY_MESSAGE);
    }
}
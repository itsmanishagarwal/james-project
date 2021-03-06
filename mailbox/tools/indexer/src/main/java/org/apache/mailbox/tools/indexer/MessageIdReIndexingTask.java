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
package org.apache.mailbox.tools.indexer;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageIdReIndexingTask implements Task {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageIdReIndexingTask.class);

    public static final TaskType TYPE = TaskType.of("messageId-reindexing");

    public static class Factory {
        private ReIndexerPerformer reIndexerPerformer;
        private final MessageId.Factory messageIdFactory;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer, MessageId.Factory messageIdFactory) {
            this.reIndexerPerformer = reIndexerPerformer;
            this.messageIdFactory = messageIdFactory;
        }

        public MessageIdReIndexingTask create(MessageIdReindexingTaskDTO dto) {
            MessageId messageId = messageIdFactory.fromString(dto.getMessageId());
            return new MessageIdReIndexingTask(reIndexerPerformer, messageId);
        }
    }

    public static final class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final MessageId messageId;
        private final Instant timestamp;

        AdditionalInformation(MessageId messageId, Instant timestamp) {
            this.messageId = messageId;
            this.timestamp = timestamp;
        }

        public String getMessageId() {
            return messageId.serialize();
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }


    private ReIndexerPerformer reIndexerPerformer;
    private final MessageId messageId;
    private final AdditionalInformation additionalInformation;

    MessageIdReIndexingTask(ReIndexerPerformer reIndexerPerformer, MessageId messageId) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.messageId = messageId;
        this.additionalInformation = new AdditionalInformation(messageId, Clock.systemUTC().instant());
    }

    @Override
    public Result run() {
        return reIndexerPerformer.handleMessageIdReindexing(messageId);
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    MessageId getMessageId() {
        return messageId;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }
}

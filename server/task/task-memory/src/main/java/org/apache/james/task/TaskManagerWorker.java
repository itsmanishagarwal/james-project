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
package org.apache.james.task;

import java.io.Closeable;
import java.util.Optional;

import reactor.core.publisher.Mono;

public interface TaskManagerWorker extends Closeable {

    interface Listener {
        void started(TaskId taskId);

        void completed(TaskId taskId, Task.Result result, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation);

        void failed(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation, String errorMessage, Throwable t);

        void failed(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation, Throwable t);

        void failed(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation);

        void cancelled(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation);

        void updated(TaskId taskId, TaskExecutionDetails.AdditionalInformation additionalInformation);
    }

    Mono<Task.Result> executeTask(TaskWithId taskWithId);

    void cancelTask(TaskId taskId);

    void fail(TaskId taskId, Optional<TaskExecutionDetails.AdditionalInformation> additionalInformation, String errorMessage, Throwable reason);
}

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

package org.apache.james.jmap.event;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.draft.model.message.view.MessageFullView;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.parboiled.common.ImmutableList;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ComputeMessageFastViewProjectionListener implements MailboxListener.GroupMailboxListener {
    public static class ComputeMessageFastViewProjectionListenerGroup extends Group {

    }

    static final Group GROUP = new ComputeMessageFastViewProjectionListenerGroup();

    private final MessageIdManager messageIdManager;
    private final MessageFastViewProjection messageFastViewProjection;
    private final SessionProvider sessionProvider;
    private final MessageFullViewFactory messageFullViewFactory;

    @Inject
    public ComputeMessageFastViewProjectionListener(SessionProvider sessionProvider, MessageIdManager messageIdManager,
                                                    MessageFastViewProjection messageFastViewProjection,
                                                    MessageFullViewFactory messageFullViewFactory) {
        this.sessionProvider = sessionProvider;
        this.messageIdManager = messageIdManager;
        this.messageFastViewProjection = messageFastViewProjection;
        this.messageFullViewFactory = messageFullViewFactory;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public void event(Event event) throws MailboxException {
        if (event instanceof Added) {
            MailboxSession session = sessionProvider.createSystemSession(event.getUsername());
            handleAddedEvent((Added) event, session);
        }
    }

    private void handleAddedEvent(Added addedEvent, MailboxSession session) throws MailboxException {
        Flux.fromIterable(messageIdManager.getMessages(addedEvent.getMessageIds(), FetchGroup.BODY_CONTENT, session))
            .flatMap(Throwing.function(messageResult -> Mono.fromCallable(
                () -> Pair.of(messageResult.getMessageId(), computeFastViewPrecomputedProperties(messageResult)))
                    .subscribeOn(Schedulers.parallel())))
            .publishOn(Schedulers.boundedElastic())
            .flatMap(message -> messageFastViewProjection.store(message.getKey(), message.getValue()))
            .then()
            .block();
    }

    @VisibleForTesting
    MessageFastViewPrecomputedProperties computeFastViewPrecomputedProperties(MessageResult messageResult) throws MailboxException, IOException {
        MessageFullView message = messageFullViewFactory.fromMessageResults(ImmutableList.of(messageResult));

        return MessageFastViewPrecomputedProperties.builder()
            .preview(Preview.from(message.getPreview().getValue()))
            .hasAttachment(message.isHasAttachment())
            .build();
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class ComputeMessageFastViewProjectionListenerTest {
    private static final Username BOB = Username.of("bob");
    private static final Preview PREVIEW = Preview.from("blabla bloblo");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_OTHER_BOX_PATH = MailboxPath.forUser(BOB, "otherBox");
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_PREVIEW = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW)
        .noAttachments()
        .build();
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_EMPTY = MessageFastViewPrecomputedProperties.builder()
        .preview(Preview.from(""))
        .noAttachments()
        .build();
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_PREVIEW_HAS_ATTACHMENT = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW)
        .hasAttachment()
        .build();
    private static final MessageFastViewPrecomputedProperties PRECOMPUTED_PROPERTIES_HAS_ATTACHMENT = MessageFastViewPrecomputedProperties.builder()
        .preview(Preview.from(""))
        .hasAttachment()
        .build();

    MessageFastViewProjection messageFastViewProjection;
    MessageFullViewFactory messageFullViewFactory;
    MailboxSession mailboxSession;
    StoreMailboxManager mailboxManager;

    MessageManager inboxMessageManager;
    MessageManager otherBoxMessageManager;
    ComputeMessageFastViewProjectionListener listener;
    MessageIdManager messageIdManager;
    MemoryEventDeadLetters eventDeadLetters;

    @BeforeEach
    void setup() throws Exception {
        eventDeadLetters = new MemoryEventDeadLetters();
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()), RetryBackoffConfiguration.DEFAULT, eventDeadLetters))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();
        messageIdManager = spy(resources.getMessageIdManager());

        messageFastViewProjection = new MemoryMessageFastViewProjection(new NoopMetricFactory());

        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();

        messageFullViewFactory = new MessageFullViewFactory(resources.getBlobManager(), messageContentExtractor, htmlTextExtractor, messageIdManager, messageFastViewProjection);

        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");

        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, FakeAuthorizator.defaultReject());

        listener = spy(new ComputeMessageFastViewProjectionListener(sessionProvider, messageIdManager,
            messageFastViewProjection, messageFullViewFactory));

        resources.getEventBus().register(listener);

        mailboxSession = MailboxSessionUtil.create(BOB);

        MailboxId inboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, mailboxSession).get();
        inboxMessageManager = mailboxManager.getMailbox(inboxId, mailboxSession);

        MailboxId otherBoxId = mailboxManager.createMailbox(BOB_OTHER_BOX_PATH, mailboxSession).get();
        otherBoxMessageManager = mailboxManager.getMailbox(otherBoxId, mailboxSession);
    }

    @Test
    void deserializeMailboxAnnotationListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.jmap.event.ComputeMessageFastViewProjectionListener$ComputeMessageFastViewProjectionListenerGroup"))
            .isEqualTo(new ComputeMessageFastViewProjectionListener.ComputeMessageFastViewProjectionListenerGroup());
    }

    @Test
    void shouldStorePreviewWithNoAttachmentsWhenBodyMessageNotEmptyAndNoAttachments() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
    }

    @Test
    void shouldStoreEmptyPreviewWithNoAttachmentsWhenEmptyBodyMessageAndNoAttachments() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(emptyMessage()),
            mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_EMPTY);
    }

    @Test
    void shouldStorePreviewWithHasAttachmentWhenBodyMessageNotEmptyAndHasAttachment() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("fullMessage.eml")),
            mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW_HAS_ATTACHMENT);
    }

    @Test
    void shouldStoreEmptyPreviewWithHasAttachmentWhenEmptyBodyMessageAndHasAttachment() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("emptyBodyMessageWithOneAttachment.eml")),
            mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_HAS_ATTACHMENT);
    }

    @Test
    void shouldStoreMultiplePreviewsWhenMultipleMessagesAdded() throws Exception {
        ComposedMessageId composedId1 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        ComposedMessageId composedId2 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(emptyMessage()),
            mailboxSession);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Mono.from(messageFastViewProjection.retrieve(composedId1.getMessageId())).block())
                .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
            softly.assertThat(Mono.from(messageFastViewProjection.retrieve(composedId2.getMessageId())).block())
                .isEqualTo(PRECOMPUTED_PROPERTIES_EMPTY);
        });
    }

    @Test
    void shouldKeepPreviewWhenMovingMessage() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.moveMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        MessageResult result = otherBoxMessageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).next();
        assertThat(Mono.from(messageFastViewProjection.retrieve(result.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
    }

    @Test
    void shouldKeepPreviewWhenCopyingMessage() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.copyMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        MessageResult result = otherBoxMessageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).next();
        assertThat(Mono.from(messageFastViewProjection.retrieve(result.getMessageId())).block())
            .isEqualTo(PRECOMPUTED_PROPERTIES_PREVIEW);
    }

    @Test
    void shouldStoreEventInDeadLettersWhenComputeFastViewPrecomputedPropertiesException() throws Exception {
        doThrow(new IOException())
            .when(listener)
            .computeFastViewPrecomputedProperties(any());

        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        assertThat(eventDeadLetters.failedIds(ComputeMessageFastViewProjectionListener.GROUP).collectList().block())
            .hasSize(1);
    }

    @Test
    void shouldStoreEventInDeadLettersWhenGetMessagesException() throws Exception {
        doThrow(new MailboxException())
            .when(messageIdManager)
            .getMessages(any(), any(), any());

        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        assertThat(eventDeadLetters.failedIds(ComputeMessageFastViewProjectionListener.GROUP).collectList().block())
            .hasSize(1);
    }

    private Message previewMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("Preview message")
            .setBody(PREVIEW.getValue(), StandardCharsets.UTF_8)
            .build();
    }

    private Message emptyMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("Empty message")
            .setBody("", StandardCharsets.UTF_8)
            .build();
    }
}

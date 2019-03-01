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

package org.apache.james.vault;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mime4j.MimeIOException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

class DeletedMessageConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageConverter.class);

    DeletedMessage convert(DeletedMessageVaultHook.DeletedMessageMailboxContext deletedMessageMailboxContext, org.apache.james.mailbox.store.mail.model.Message message, ZonedDateTime deletionDate) throws IOException {
        Preconditions.checkNotNull(deletedMessageMailboxContext);
        Preconditions.checkNotNull(message);

        Optional<Message> mimeMessage = parseMessage(message);

        return DeletedMessage.builder()
            .messageId(deletedMessageMailboxContext.getMessageId())
            .originMailboxes(deletedMessageMailboxContext.getOwnerMailboxes())
            .user(retrieveOwner(deletedMessageMailboxContext))
            .deliveryDate(retrieveDeliveryDate(mimeMessage, message))
            .deletionDate(deletionDate)
            .sender(retrieveSender(mimeMessage))
            .recipients(retrieveRecipients(mimeMessage))
            .hasAttachment(!message.getAttachments().isEmpty())
            .subject(mimeMessage.map(Message::getSubject))
            .build();
    }

    private Optional<Message> parseMessage(org.apache.james.mailbox.store.mail.model.Message message) throws IOException {
        DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
        messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        try {
            return Optional.ofNullable(messageBuilder.parseMessage(message.getFullContent()));
        } catch (MimeIOException e) {
            LOGGER.warn("Can not parse the message {}", message.getMessageId(), e);
            return Optional.empty();
        }
    }

    private User retrieveOwner(DeletedMessageVaultHook.DeletedMessageMailboxContext deletedMessageMailboxContext) {
        Preconditions.checkNotNull(deletedMessageMailboxContext.getOwner(), "Deleted mail is missing owner");
        return deletedMessageMailboxContext.getOwner();
    }

    private ZonedDateTime retrieveDeliveryDate(Optional<Message> mimeMessage, org.apache.james.mailbox.store.mail.model.Message message) {
        return mimeMessage.map(Message::getDate)
            .map(Date::toInstant)
            .map(instant -> ZonedDateTime.ofInstant(instant, ZoneOffset.UTC))
            .orElse(ZonedDateTime.ofInstant(message.getInternalDate().toInstant(), ZoneOffset.UTC));
    }

    private MaybeSender retrieveSender(Optional<Message> mimeMessage) {
        return mimeMessage
            .map(Message::getSender)
            .map(Mailbox::getAddress)
            .map(MaybeSender::getMailSender)
            .orElse(MaybeSender.nullSender());
    }

    private List<MailAddress> retrieveRecipients(Optional<Message> message) {
        return StreamUtils.flatten(combineRecipients(message)
            .map(AddressList::flatten)
            .flatMap(MailboxList::stream)
            .map(Mailbox::getAddress)
            .map(this::retrieveAddress))
            .collect(Guavate.toImmutableList());
    }

    private Stream<MailAddress> retrieveAddress(String address) {
        try {
            return Stream.of(new MailAddress(address));
        } catch (AddressException e) {
            LOGGER.warn("Can not create the mailAddress from {}", address, e);
            return Stream.of();
        }
    }

    private Stream<AddressList> combineRecipients(Optional<Message> message) {
        return message.map(mimeMessage -> Stream.of(mimeMessage.getTo(),
                    mimeMessage.getCc(),
                    mimeMessage.getBcc())
                .filter(Objects::nonNull))
            .orElse(Stream.of());
    }
}

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
package org.apache.james.mailbox.backup;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.core.User;
import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ZipAssert.EntryChecks;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.inmemory.MemoryMailboxManagerProvider;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.reactivestreams.Publisher;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import com.github.fge.lambdas.Throwing;
import reactor.core.publisher.Mono;

@ExtendWith(TemporaryFolderExtension.class)
class DefaultMailboxBackupTest implements MailboxMessageFixture {

    private static final User USER1 = User.fromUsername(ManagerTestResources.USER);
    private static final MailboxPath MAILBOX_PATH_USER1_MAILBOX1 = MailboxPath.forUser(ManagerTestResources.USER, MAILBOX_1_NAME);
    private static final MailboxPath MAILBOX_PATH_OTHER_USER_MAILBOX1 = MailboxPath.forUser(ManagerTestResources.OTHER_USER, MAILBOX_OTHER_USER_NAME);
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final HashSet<PreDeletionHook> PRE_DELETION_HOOKS = new HashSet<>();

    private final ArchiveService archiveService = new Zipper();

    private MailboxManager mailboxManager;
    private BlobStore store;
    private File destination;
    private DefaultMailboxBackup backup;

    @BeforeEach
    void beforeEach(TemporaryFolderExtension.TemporaryFolder temporaryFolder) throws Exception {
        destination = File.createTempFile("backup-test", ".zip", temporaryFolder.getTempDir());
        mailboxManager = MemoryMailboxManagerProvider.provideMailboxManager(PRE_DELETION_HOOKS);
        store = new MemoryBlobStore(new HashBlobId.Factory());
        backup = new DefaultMailboxBackup(mailboxManager, archiveService, store);
    }

    private void readFromStoreAndCopyInFile(BlobId blobId) throws Exception {
        InputStream content = store.read(BLOB_ID_FACTORY.from(blobId.asString()));
        try (OutputStream out = new FileOutputStream(destination)) {
            IOUtils.copy(content, out);
        }
    }

    private void createMailBoxWithMessage(MailboxSession session, MailboxPath mailboxPath, MailboxMessage... messages) throws Exception {
        MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session).get();
        Arrays.stream(messages).forEach(Throwing.consumer(message ->
                mailboxManager.getMailbox(mailboxId, session).appendMessage(MessageManager.AppendCommand.from(message.getFullContent()), session)
            )
        );
    }

    private ZipAssert assertThatZipFromIdContainsOnly(Publisher<BlobId> blobIdPublisher, EntryChecks... onlyEntriesMatching) throws Exception {
        BlobId blobId = Mono.from(blobIdPublisher).block();
        readFromStoreAndCopyInFile(blobId);

        try (ZipFile zipFile = new ZipFile(destination)) {
            return ZipAssert.assertThatZip(zipFile).containsOnlyEntriesMatching(onlyEntriesMatching);
        }
    }

    @Test
    void doBackupWithoutMailboxShouldStoreEmptyBackup() throws Exception {
        Publisher<BlobId> res = backup.backupAccount(USER1);

        assertThatZipFromIdContainsOnly(res);
    }

    @Test
    void doBackupWithoutMessageShouldStoreAnArchiveWithOnlyOneEntry() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ManagerTestResources.USER);
        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1);

        Publisher<BlobId> res = backup.backupAccount(USER1);

        assertThatZipFromIdContainsOnly(res, EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory());
    }

    @Test
    void doBackupWithOneMessageShouldStoreAnArchiveWithTwoEntries() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ManagerTestResources.USER);
        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1, MESSAGE_1);

        Publisher<BlobId> res = backup.backupAccount(USER1);

        assertThatZipFromIdContainsOnly(res,
            EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
            EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
        );
    }

    @Test
    void doBackupShouldOnlyArchiveTheMailboxOfTheUser() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ManagerTestResources.USER);
        MailboxSession otherSession = mailboxManager.createSystemSession(ManagerTestResources.OTHER_USER);

        createMailBoxWithMessage(session, MAILBOX_PATH_USER1_MAILBOX1, MESSAGE_1);
        createMailBoxWithMessage(otherSession, MAILBOX_PATH_OTHER_USER_MAILBOX1, MESSAGE_1_OTHER_USER);

        Publisher<BlobId> res = backup.backupAccount(USER1);

        assertThatZipFromIdContainsOnly(res,
            EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
            EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
        );
    }
}

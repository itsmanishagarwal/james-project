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

package org.apache.james.jmap.methods.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapCommonRequests.deleteMessages;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapCommonRequests.listMessageIdsForAccount;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.LOCALHOST_IP;
import static org.apache.james.jmap.TestingConstants.calmlyAwait;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.linshare.LinshareFixture.USER_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.ExportRequest;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.linshare.LinshareExtension;
import org.apache.james.linshare.client.LinshareAPI;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.JmapGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;

public abstract class LinshareBlobExportMechanismIntegrationTest {
    private static final String HOMER = "homer@" + DOMAIN;
    private static final String BART = "bart@" + DOMAIN;
    private static final String HOMER_PASSWORD = "homerPassword";
    private static final String BART_PASSWORD = "bartPassword";
    private static final ConditionFactory WAIT_TEN_SECONDS = calmlyAwait.atMost(Duration.TEN_SECONDS);
    private static final String SUBJECT = "This mail will be restored from the vault!!";
    private static final String MATCH_ALL_QUERY = "{" +
        "\"combinator\": \"and\"," +
        "\"criteria\": []" +
        "}";
    private static final ExportRequest EXPORT_ALL_HOMER_MESSAGES_TO_USER_1 = ExportRequest
        .userExportFrom(HOMER)
        .exportTo(USER_1.getUsername())
        .query(MATCH_ALL_QUERY);

    private AccessToken homerAccessToken;
    private AccessToken bartAccessToken;
    private GuiceJamesServer jmapServer;
    private RequestSpecification webAdminApi;
    private RequestSpecification fakeSmtpRequestSpecification;
    private LinshareAPI user1LinshareAPI;

    @RegisterExtension
    private IMAPMessageReader imapMessageReader = new IMAPMessageReader();

    @BeforeEach
    void setup(GuiceJamesServer jmapServer) throws Throwable {
        this.jmapServer = jmapServer;

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(HOMER, HOMER_PASSWORD)
            .addUser(BART, BART_PASSWORD);

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
            .build();
        RestAssured.defaultParser = Parser.JSON;

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

        MailboxProbe mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, HOMER, DefaultMailboxes.INBOX);

        homerAccessToken = authenticateJamesUser(baseUri(jmapServer), HOMER, HOMER_PASSWORD);
        bartAccessToken = authenticateJamesUser(baseUri(jmapServer), BART, BART_PASSWORD);
        user1LinshareAPI = new LinshareExtension().getAPIFor(USER_1);

        fakeSmtpRequestSpecification = given(new LinshareExtension().getLinshare().fakeSmtpRequestSpecification());
    }

    @Test
    void exportShouldShareTheDocumentViaLinShareWhenJmapDelete() {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        exportVaultContent(EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        assertThat(user1LinshareAPI.receivedShares())
            .hasSize(1)
            .allSatisfy(receivedShare -> assertThat(receivedShare.getDocument().getName()).endsWith(".zip"))
            .allSatisfy(receivedShare -> assertThat(receivedShare.getSender().getMail()).isEqualTo(USER_1.getUsername()));
    }

    @Test
    void exportShouldShareTheDocumentViaLinShareWhenImapDelete() throws Exception {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        exportVaultContent(EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        assertThat(user1LinshareAPI.receivedShares())
            .hasSize(1)
            .allSatisfy(receivedShare -> assertThat(receivedShare.getDocument().getName()).endsWith(".zip"))
            .allSatisfy(receivedShare -> assertThat(receivedShare.getSender().getMail()).isEqualTo(USER_1.getUsername()));
    }

    @Test
    void exportShouldSendAnEmailToShareeWhenJmapDelete() {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        homerDeletesMessages(listMessageIdsForAccount(homerAccessToken));
        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        exportVaultContent(EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        WAIT_TEN_SECONDS.untilAsserted(
            () -> fakeSmtpRequestSpecification
                .get("/api/email")
            .then()
                .body("", hasSize(2)));

        fakeSmtpRequestSpecification
            .get("/api/email")
        .then()
            .body("[1].subject", containsString("John Doe has shared a file with you"))
            .body("[1].to", hasItem(USER_1.getUsername()));
    }

    @Test
    void exportShouldSendAnEmailToShareeWhenImapDelete() throws Exception {
        bartSendMessageToHomer();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 1);

        imapMessageReader.connect(LOCALHOST_IP, jmapServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(HOMER, HOMER_PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .setFlagsForAllMessagesInMailbox("\\Deleted");
        imapMessageReader.expunge();

        WAIT_TEN_SECONDS.until(() -> listMessageIdsForAccount(homerAccessToken).size() == 0);

        exportVaultContent(EXPORT_ALL_HOMER_MESSAGES_TO_USER_1);

        WAIT_TEN_SECONDS.untilAsserted(
                () -> fakeSmtpRequestSpecification
                    .get("/api/email")
                .then()
                    .body("", hasSize(2)));

        fakeSmtpRequestSpecification
            .get("/api/email")
        .then()
            .body("[1].subject", containsString("John Doe has shared a file with you"))
            .body("[1].to", hasItem(USER_1.getUsername()));
    }

    private void bartSendMessageToHomer() {
        String messageCreationId = "creationId";
        String outboxId = getOutboxId(bartAccessToken);
        String textBody = "You got mail!";
        String requestBody = "[" +
            "  [" +
            "    \"setMessages\"," +
            "    {" +
            "      \"create\": { \"" + messageCreationId  + "\" : {" +
            "        \"from\": { \"name\": \"user2\", \"email\": \"" + BART + "\"}," +
            "        \"to\": [{ \"name\": \"user1\", \"email\": \"" + HOMER + "\"}]," +
            "        \"subject\": \"" + SUBJECT + "\"," +
            "        \"textBody\": \"" + textBody + "\"," +
            "        \"htmlBody\": \"Test <b>body</b>, HTML version\"," +
            "        \"mailboxIds\": [\"" + outboxId + "\"] " +
            "      }}" +
            "    }," +
            "    \"#0\"" +
            "  ]" +
            "]";

        with()
            .header("Authorization", bartAccessToken.serialize())
            .body(requestBody)
            .post("/jmap")
        .then()
            .extract()
            .body()
            .path(ARGUMENTS + ".created." + messageCreationId + ".id");
    }

    private void homerDeletesMessages(List<String> idsToDestroy) {
        deleteMessages(homerAccessToken, idsToDestroy);
    }

    private void exportVaultContent(ExportRequest exportRequest) {
        String taskId =
            webAdminApi.with()
                .queryParam("action", "export")
                .queryParam("exportTo", exportRequest.getSharee())
                .body(exportRequest.getMatchingQuery())
                .post("/deletedMessages/users/" + exportRequest.getUserExportFrom())
            .jsonPath()
                .get("taskId");

        webAdminApi.with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));
    }
}

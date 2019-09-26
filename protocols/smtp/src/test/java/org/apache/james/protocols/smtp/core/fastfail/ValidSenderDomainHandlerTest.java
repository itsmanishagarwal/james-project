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

package org.apache.james.protocols.smtp.core.fastfail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Test;

public class ValidSenderDomainHandlerTest {
    
    private ValidSenderDomainHandler createHandler() {
        return new ValidSenderDomainHandler() {
            @Override
            protected boolean hasMXRecord(SMTPSession session, String domain) {
                if (domain.equals("test.james.apache.org")) {
                    return true;
                }
                return false;
            }
        };
    }
    
    private SMTPSession setupMockedSession(final MailAddress sender) {
        return new BaseFakeSMTPSession() {
            HashMap<String,Object> map = new HashMap<>();

            @Override
            public Map<String,Object> getState() {

                map.put(SMTPSession.SENDER, MaybeSender.of(sender));

                return map;
            }

            @Override
            public boolean isRelayingAllowed() {
                return false;
            }

            @Override
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    throw new UnsupportedOperationException();

                } else {
                    if (value == null) {
                        return getState().remove(key);
                    } else {
                        return getState().put(key, value);
                    }
                }
            }

            @Override
            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    throw new UnsupportedOperationException();
                } else {
                    return getState().get(key);
                }
            }

        };
    }
    
    
    // Test for JAMES-580
    @Test
    public void testNullSenderNotReject() {
        ValidSenderDomainHandler handler = createHandler();
        HookReturnCode response = handler.doMail(setupMockedSession(null), MaybeSender.nullSender()).getResult();
        
        assertThat(HookReturnCode.declined()).describedAs("Not blocked cause its a nullsender").isEqualTo(response);
    }

    @Test
    public void testInvalidSenderDomainReject() throws Exception {
        ValidSenderDomainHandler handler = createHandler();
        SMTPSession session = setupMockedSession(new MailAddress("invalid@invalid"));
        MaybeSender sender = (MaybeSender) session.getAttachment(SMTPSession.SENDER, State.Transaction);
        HookReturnCode response = handler.doMail(session, sender).getResult();
        
        assertThat(HookReturnCode.deny()).describedAs("Blocked cause we use reject action").isEqualTo(response);
    }
}

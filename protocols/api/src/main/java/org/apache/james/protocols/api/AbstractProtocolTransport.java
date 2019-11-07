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

package org.apache.james.protocols.api;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Abstract base class for {@link ProtocolTransport} implementation which already takes care of all the complex
 * stuff when handling {@link Response}'s.
 */
public abstract class AbstractProtocolTransport implements ProtocolTransport {
    private static final String CRLF = "\r\n";

    // TODO: Should we limit the size ?
    private final Queue<Response> responses = new LinkedBlockingQueue<>();
    private volatile boolean isAsync = false;
    
    @Override
    public final void writeResponse(Response response, ProtocolSession session) {
        // if we already in asynchrnous mode we simply enqueue the response
        // we do this synchronously because we may have a dequeuer thread working on
        // isAsync and responses.
        boolean enqueued = false;
        synchronized (this) {
            if (isAsync == true) {
                responses.offer(response);
                enqueued = true;
            }
        }

        if (!enqueued) {
            writeResponseToClient(response, session);
        }
    }
    
    /**
     * Write the {@link Response} to the client
     * 
     * @param response
     * @param session
     */
    protected void writeResponseToClient(Response response, ProtocolSession session) {
        if (response != null) {
            boolean startTLS = false;
            if (response instanceof StartTlsResponse) {
                if (isStartTLSSupported()) {
                    startTLS = true;
                } else {
                    // StartTls is not supported by this transport, so throw a exception
                    throw new UnsupportedOperationException("StartTls is not supported by this ProtocolTransport implementation");
                }
            }

            if (response instanceof StreamResponse) {
                writeToClient(toBytes(response), session, false);
                writeToClient(((StreamResponse) response).getStream(), session, startTLS);
            } else {
                writeToClient(toBytes(response), session, startTLS);
            }
            // reset state on starttls
            if (startTLS) {
                session.resetState();
            }
            
            if (response.isEndSession()) {
                // close the channel if needed after the message was written out
                close();
            }
        }
    }

    /**
     * Take the {@link Response} and encode it to a <code>byte</code> array
     * 
     * @param response
     * @return bytes
     */
    protected static byte[] toBytes(Response response) {
        StringBuilder builder = new StringBuilder();
        List<CharSequence> lines = response.getLines();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i < lines.size()) {
                builder.append(CRLF);
            }
        }
        try {
            return builder.toString().getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("No US-ASCII ?");
        }
    }

    /**
     * Write the given <code>byte's</code> to the remote peer
     * 
     * @param bytes    the bytes to write 
     * @param session  the {@link ProtocolSession} for the write request
     * @param startTLS true if startTLS should be started after the bytes were written to the client
     */
    protected abstract void writeToClient(byte[] bytes, ProtocolSession session, boolean startTLS);
    
    /**
     * Write the given {@link InputStream} to the remote peer
     * 
     * @param in       the {@link InputStream} which should be written back to the client
     * @param session  the {@link ProtocolSession} for the write request
     * @param startTLS true if startTLS should be started after the {@link InputStream} was written to the client
     */
    protected abstract void writeToClient(InputStream in, ProtocolSession session, boolean startTLS);
    
    /**
     * Close the Transport
     */
    protected abstract void close();
}


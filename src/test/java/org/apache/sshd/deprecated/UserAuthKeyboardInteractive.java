/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.deprecated;

import java.io.IOException;
import java.util.Arrays;

import org.apache.sshd.client.auth.UserInteraction;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.util.buffer.Buffer;

import static org.apache.sshd.common.SshConstants.SSH_MSG_USERAUTH_FAILURE;
import static org.apache.sshd.common.SshConstants.SSH_MSG_USERAUTH_INFO_REQUEST;
import static org.apache.sshd.common.SshConstants.SSH_MSG_USERAUTH_INFO_RESPONSE;
import static org.apache.sshd.common.SshConstants.SSH_MSG_USERAUTH_SUCCESS;

/**
 * Userauth with keyboard-interactive method.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 * @author <a href="mailto:j.kapitza@schwarze-allianz.de">Jens Kapitza</a>
 */
public class UserAuthKeyboardInteractive extends AbstractUserAuth {

    private final String password;

    public UserAuthKeyboardInteractive(ClientSession session, String service, String password) {
        super(session, service);
        this.password = password;
    }

    @Override
    public Result next(Buffer buffer) throws IOException {
        if (buffer == null) {
            log.debug("Send SSH_MSG_USERAUTH_REQUEST for password");
            buffer = session.createBuffer(SshConstants.SSH_MSG_USERAUTH_REQUEST);
            buffer.putString(session.getUsername());
            buffer.putString(service);
            buffer.putString("keyboard-interactive");
            buffer.putString("");
            buffer.putString("");
            session.writePacket(buffer);
            return Result.Continued;
        } else {
            int cmd = buffer.getUByte();
            switch (cmd) {
                case SSH_MSG_USERAUTH_INFO_REQUEST:
                    log.debug("Received SSH_MSG_USERAUTH_INFO_REQUEST");
                    String name = buffer.getString();
                    String instruction = buffer.getString();
                    String language_tag = buffer.getString();
                    log.info("Received {} {} {}", new Object[]{name, instruction, language_tag});
                    int num = buffer.getInt();
                    String[] prompt = new String[num];
                    boolean[] echo = new boolean[num];
                    for (int i = 0; i < num; i++) {
                        prompt[i] = buffer.getString();
                        echo[i] = buffer.getBoolean();
                    }
                    log.debug("Promt: {}", Arrays.toString(prompt));
                    log.debug("Echo: {}", echo);

                    String[] rep = null;
                    if (num == 0) {
                        rep = new String[0];
                    } else if (num == 1 && password != null && !echo[0] && prompt[0].toLowerCase().startsWith("password:")) {
                        rep = new String[]{password};
                    } else {
                        UserInteraction ui = session.getFactoryManager().getUserInteraction();
                        if (ui != null) {
                            String dest = session.getUsername() + "@" + session.getIoSession().getRemoteAddress().toString();
                            rep = ui.interactive(dest, name, instruction, language_tag, prompt, echo);
                        }
                    }
                    if (rep == null) {
                        return Result.Failure;
                    }

                    buffer = session.createBuffer(SSH_MSG_USERAUTH_INFO_RESPONSE);
                    buffer.putInt(rep.length);
                    for (String r : rep) {
                        buffer.putString(r);
                    }
                    session.writePacket(buffer);
                    return Result.Continued;
                case SSH_MSG_USERAUTH_SUCCESS:
                    log.debug("Received SSH_MSG_USERAUTH_SUCCESS");
                    return Result.Success;
                case SSH_MSG_USERAUTH_FAILURE:
                    log.debug("Received SSH_MSG_USERAUTH_FAILURE");
                    return Result.Failure;
                default:
                    log.debug("Received unknown packet {}", Integer.valueOf(cmd));
                    return Result.Continued;
            }
        }
    }

}
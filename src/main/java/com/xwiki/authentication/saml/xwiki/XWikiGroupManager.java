/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.authentication.saml.xwiki;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReferenceResolver;

public class XWikiGroupManager {
    private static final Logger LOG = LoggerFactory.getLogger(XWikiGroupManager.class);
    private final DocumentReferenceResolver<String> groupResolver;

    public XWikiGroupManager(DocumentReferenceResolver<String> groupResolver) {
        this.groupResolver = groupResolver;
    }

    public synchronized void addUserToGroup(String xwikiUserName, String groupName, XWikiContext context) throws XWikiException {
        new XWikiGroupManagerWithContext(context).addUserToGroup(xwikiUserName, groupName);
    }
    public synchronized void removeUserFromGroup(String xwikiUserName, String groupName, XWikiContext context) throws XWikiException {
        new XWikiGroupManagerWithContext(context).removeUserFromGroup(xwikiUserName, groupName, context);
    }

    class XWikiGroupManagerWithContext {
        private final XWikiContext context;

        XWikiGroupManagerWithContext(XWikiContext context) {
            this.context = context;
        }

        private Group getGroupDocument(String groupName, XWikiContext context) throws XWikiException {
            return new Group(groupName, context, groupResolver);
        }

        protected synchronized void addUserToGroup(String xwikiUserName, String groupName) throws XWikiException {
            try {
                if (groupName.trim().isEmpty()) {
                    LOG.warn("Tried to add user [{}] to group with empty name. Ignoring", xwikiUserName);
                    return;
                }
                LOG.debug("Adding user [{}] to xwiki group [{}]", xwikiUserName, groupName);
                final Group group = getGroupDocument(groupName, context);

                if (group.hasMember(xwikiUserName)) {
                    LOG.warn("User [{}] already exist in group [{}]", xwikiUserName, group.groupName());
                    return;
                }

                group.addMember(xwikiUserName);

                if (group.isNew())
                    group.setupNewGroupDocument();

                group.save();

                LOG.debug("Finished adding user [{}] to xwiki group [{}]", xwikiUserName, groupName);
            } catch (XWikiException e) {
                LOG.error("Failed to add user [{}] to group [{}]", xwikiUserName, groupName, e);
                throw e;
            }
        }

        protected synchronized void removeUserFromGroup(String xwikiUserName, String groupName, XWikiContext context) throws XWikiException {
            try {
                if (groupName.trim().isEmpty()) {
                    LOG.warn("Tried to remove user [{}] from empty group. Ignoring", xwikiUserName);
                    return;
                }
                final Group group = getGroupDocument(groupName, context);

                group.removeUser(xwikiUserName);

                group.save();

            } catch (XWikiException e) {
                LOG.error("Failed to remove a user from a group [{}] group: [{}]", xwikiUserName, groupName, e);
                throw e;
            }
        }
    }

}

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
package com.xwiki.authentication.saml;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.user.api.XWikiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import static com.xwiki.authentication.saml.SamlAuthenticator.PROFILE_PARENT;

public class AuthenticatedUserHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticatedUserHandler.class);
    private XWikiContext context;
    private DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver;

    public AuthenticatedUserHandler(XWikiContext context, DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver) {
        this.context = context;
        this.currentMixedDocumentReferenceResolver = currentMixedDocumentReferenceResolver;
    }

    public XWikiUser handle(String samlUserName) {
        LOG.debug("User [{}] already logged in the session.", samlUserName);
        return new XWikiUser(getUserReferenceForName(samlUserName), context.isMainWiki());
    }

    private DocumentReference getUserReferenceForName(String validUserName) {
        return this.currentMixedDocumentReferenceResolver.resolve(validUserName, PROFILE_PARENT);
    }
}

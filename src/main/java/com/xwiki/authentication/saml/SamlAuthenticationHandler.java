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

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xwiki.authentication.saml.samlauth.Saml2XWikiAttributes;
import com.xwiki.authentication.saml.samlauth.SamlAuthConfig;
import com.xwiki.authentication.saml.samlauth.SamlXwikiAttributesExtractor;
import com.xwiki.authentication.saml.xwiki.XWikiGroupManager;
import com.xwiki.authentication.saml.xwiki.XWikiUserGroupSynchronizer;
import com.xwiki.authentication.saml.xwiki.XWikiUserManager;
import com.xwiki.authentication.saml.onelogin.OneLoginAuth;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_UNKNOWN;
import static com.xpn.xwiki.XWikiException.MODULE_XWIKI_PLUGINS;

public class SamlAuthenticationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SamlAuthenticationHandler.class);

    private final Saml2Settings samlSettings;
    private final SamlAuthConfig authConfig;
    private final XWikiGroupManager groupManager;
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer;
    private final OneLoginAuth loginAuthFactory;
    private final XWikiContext context;
    private final XWikiUserManager xWikiUserManager;
    private final SamlXwikiAttributesExtractor attributesExtractor;


    public SamlAuthenticationHandler(XWikiContext context,
                                     OneLoginAuth loginAuthFactory,
                                     Saml2Settings samlSettings,
                                     SamlAuthConfig authConfig,
                                     XWikiGroupManager groupManager,
                                     EntityReferenceSerializer<String> compactStringEntityReferenceSerializer,
                                     DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver) {
        this.context = context;
        this.loginAuthFactory = loginAuthFactory;
        this.samlSettings = samlSettings;
        this.authConfig = authConfig;
        this.groupManager = groupManager;
        this.compactStringEntityReferenceSerializer = compactStringEntityReferenceSerializer;
        this.xWikiUserManager = new XWikiUserManager(authConfig,
                compactStringEntityReferenceSerializer,
                currentMixedDocumentReferenceResolver);
        this.attributesExtractor = new SamlXwikiAttributesExtractor(authConfig);
    }

    public XWikiUser handle() throws XWikiException {
        try {
            final Auth auth = loginAuthFactory.produce(samlSettings, context.getRequest(), context.getResponse());
            auth.processResponse();

            if (auth.isAuthenticated()) {
                final Saml2XWikiAttributes attributes = attributesExtractor.extractXWikiAttributesFromSaml(auth);
                return setupAuthenticatedUser(attributes);
            }

            LOG.error(String.format("Saml authentication failed %s", auth.getLastErrorReason()), auth.getLastValidationException());
            return null;
        } catch (com.onelogin.saml2.exception.Error e) {
            LOG.error("Saml authentication failed due to configuration issues", e);
            throw new XWikiException(MODULE_XWIKI_PLUGINS, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Saml authentication failed due to unexpected exception", e);
            throw new XWikiException(MODULE_XWIKI_PLUGINS, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        }
    }

     private XWikiUser setupAuthenticatedUser(Saml2XWikiAttributes attributes) throws IOException, XWikiException {
        final DocumentReference userReference = xWikiUserManager.getOrCreateUserIfNeeded(context, attributes);

        new XWikiUserGroupSynchronizer(groupManager, context).syncUserGroups(userReference, attributes);

        addUserToTheSession(userReference);
        redirectToOriginalRequestedUrl();

        LOG.info("User [{}] authentication complete", attributes.nameID);
        return new XWikiUser(userReference, context.isMainWiki());
     }

    private void addUserToTheSession(DocumentReference userReference) {
        LOG.debug("Setting authentication in session for user [{}]", userReference);
        context.getRequest().getSession().setAttribute(authConfig.authFieldName,
                this.compactStringEntityReferenceSerializer.serialize(userReference));
    }

    private void redirectToOriginalRequestedUrl() throws IOException {
        final String originalSourceUrl = (String) context.getRequest().getSession().getAttribute(XWikiSAML20Authenticator.ORIGINAL_URL_SESSION_KEY);
        LOG.debug("Adding redirection header to [{}], since we got a successful authentication", originalSourceUrl);
        context.getResponse().sendRedirect(originalSourceUrl);
        context.setFinished(true);
    }
}

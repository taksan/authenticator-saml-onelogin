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
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiUser;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_UNKNOWN;
import static com.xpn.xwiki.XWikiException.MODULE_XWIKI_PLUGINS;
import static com.xwiki.authentication.saml.SamlAuthenticator.ORIGINAL_URL_SESSION_KEY;
import static com.xwiki.authentication.saml.SamlAuthenticator.PROFILE_PARENT;

public class SamlAuthenticationResponseHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SamlAuthenticationResponseHandler.class);
    public static final EntityReference SAML_XCLASS = new EntityReference("SAMLAuthClass", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));
    public static final String PROPERTY_TO_STORE_SAML_MANAGED_GROUPS = "SamlManagedGroups";
    public static final EntityReference USER_XCLASS = PROFILE_PARENT;

    private final Saml2Settings samlSettings;
    private final XwikiAuthConfig authConfig;
    private final XWikiGroupManager groupManager;
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer;
    private final OneLoginAuth loginAuthFactory;
    private final XWikiContext context;
    private final XWikiUserManager xWikiUserManager;
    private final SamlXwikiAttributesExtractor attributesExtractor;


    public SamlAuthenticationResponseHandler(XWikiContext context,
                                             OneLoginAuth loginAuthFactory,
                                             Saml2Settings samlSettings,
                                             XwikiAuthConfig authConfig,
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
                 final Saml2XwikiAttributes attributes = attributesExtractor.extractXWikiAttributesFromSaml(auth);
                return setupAuthenticatedUser(attributes);
            }

            LOG.info("Saml authentication failed {}", auth.getLastErrorReason(), auth.getLastValidationException());
            return null;
        } catch (com.onelogin.saml2.exception.Error e) {
            LOG.error("Saml authentication failed due to configuration issues", e);
            throw new XWikiException(MODULE_XWIKI_PLUGINS, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Saml authentication failed due to unexpected exception", e);
            throw new XWikiException(MODULE_XWIKI_PLUGINS, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        }
    }

     private XWikiUser setupAuthenticatedUser(Saml2XwikiAttributes attributes) throws IOException, XWikiException {
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
        final String originalSourceUrl = (String) context.getRequest().getSession().getAttribute(ORIGINAL_URL_SESSION_KEY);
        LOG.debug("Will redirect to [{}] after successful authentication", originalSourceUrl);
        context.getResponse().sendRedirect(originalSourceUrl);
        context.setFinished(true);
    }
}

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
package com.xwiki.authentication.saml.samlauth;

import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xwiki.authentication.saml.AuthenticatedUserHandler;
import com.xwiki.authentication.saml.NonAuthenticatedAccessHandler;
import com.xwiki.authentication.saml.SamlAuthenticationHandler;
import com.xwiki.authentication.saml.xwiki.XWikiGroupManager;
import com.xwiki.authentication.saml.function.SupplierWithException;
import com.xwiki.authentication.saml.onelogin.OneLoginAuth;
import java.util.Optional;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

public class SamlAuthenticator {
    public static final EntityReference PROFILE_PARENT = new EntityReference("XWikiUsers", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));
    private final Saml2Settings samlSettings;
    private final SamlAuthConfig authConfig;
    private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver;
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer;
    private final OneLoginAuth loginAuthFactory;

    private final XWikiGroupManager groupManager;

    public SamlAuthenticator(SamlAuthConfig authConfig,
                             DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver,
                             EntityReferenceSerializer<String> compactStringEntityReferenceSerializer,
                             OneLoginAuth loginAuthFactory,
                             XWikiGroupManager groupManager) {
        this.groupManager = groupManager;
        this.authConfig = authConfig;
        this.currentMixedDocumentReferenceResolver = currentMixedDocumentReferenceResolver;
        this.compactStringEntityReferenceSerializer = compactStringEntityReferenceSerializer;
        this.loginAuthFactory = loginAuthFactory;
        this.samlSettings = buildSamlSettings();
    }

    private Saml2Settings buildSamlSettings() {
        final java.util.Properties settings = new java.util.Properties();
        settings.put("onelogin.saml2.strict", true);
        settings.put("onelogin.saml2.sp.entityid", authConfig.entityId);
        settings.put("onelogin.saml2.sp.assertion_consumer_service.url", authConfig.assertionConsumerServiceUrl);
        settings.put("onelogin.saml2.sp.nameidformat", authConfig.nameIdFormat);
        settings.put("onelogin.saml2.idp.entityid", authConfig.idpEntityId);
        settings.put("onelogin.saml2.idp.single_sign_on_service.url", authConfig.idpSingleSignOnUrl);
        settings.put("onelogin.saml2.idp.x509cert", authConfig.x509Certificate);
        return new SettingsBuilder().fromProperties(settings).build();
    }

    public XWikiUser checkAuth(XWikiContext context,
                               SupplierWithException<XWikiUser, XWikiException> defaultAuthHandler)
            throws XWikiException {
        final Optional<String> samlUserName = getSAMLAuthenticatedUserFromSession(context);
        if (samlUserName.isPresent())
            return new AuthenticatedUserHandler(context, currentMixedDocumentReferenceResolver).handle(samlUserName.get());

        if (isSamlAuthentication(context))
            return new SamlAuthenticationHandler(
                    context,
                    loginAuthFactory,
                    samlSettings,
                    authConfig,
                    groupManager,
                    compactStringEntityReferenceSerializer,
                    currentMixedDocumentReferenceResolver)
                    .handle();

        return new NonAuthenticatedAccessHandler(context,loginAuthFactory,samlSettings).handle(defaultAuthHandler);
    }

    private boolean isSamlAuthentication(XWikiContext context) {
        return extractSamlResponse(context) != null;
    }

    private String extractSamlResponse(XWikiContext context) {
        return context.getRequest().getParameter("SAMLResponse");
    }

    public Optional<String> getSAMLAuthenticatedUserFromSession(XWikiContext context) {
        return Optional.ofNullable((String)context.getRequest().getSession(true).getAttribute(authConfig.authFieldName));
    }
}

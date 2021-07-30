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

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.authentication.saml.onelogin.OneLoginAuthImpl;
import com.xwiki.authentication.saml.samlauth.SamlAuthConfig;
import com.xwiki.authentication.saml.samlauth.SamlAuthenticator;
import com.xwiki.authentication.saml.xwiki.XWikiGroupManager;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.EntityType;
import org.xwiki.model.ModelConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

public class XWikiSAML20Authenticator extends XWikiAuthServiceImpl{
    public static final String ORIGINAL_URL_SESSION_KEY = "saml20_url";
    private static final Logger LOG = LoggerFactory.getLogger(XWikiSAML20Authenticator.class);

    @SuppressWarnings("deprecation")
    private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver =
        Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, "currentmixed");

    @SuppressWarnings("deprecation")
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer =
        Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, "compactwiki");

    @SuppressWarnings("deprecation")
    private final ModelConfiguration defaultReference = Utils.getComponent(ModelConfiguration.class);

    @SuppressWarnings("deprecation")
    private final ConfigurationSource configurationSource = Utils.getComponent(ConfigurationSource.class, "xwikicfg");

    private final SamlAuthenticator authenticator;

    public XWikiSAML20Authenticator() {
        SamlAuthConfig authConfig = SamlAuthConfig.from(configurationSource);
        authenticator = new SamlAuthenticator(
                authConfig,
                currentMixedDocumentReferenceResolver,
                compactStringEntityReferenceSerializer,
                new OneLoginAuthImpl(),
                new XWikiGroupManager(currentMixedDocumentReferenceResolver));
    }

    @Override
    public void showLogin(XWikiContext context) throws XWikiException
    {
        final XWikiRequest request = context.getRequest();

        // Remember the requested URL, so we can return to it afterwards
        final String sourceUrl = getSourceUrl(context, request);

        request.getSession().setAttribute(ORIGINAL_URL_SESSION_KEY, sourceUrl);
        LOG.debug("Invoked showLogin");
    }

    @Override
    public XWikiUser checkAuth(XWikiContext context) throws XWikiException{
        return authenticator.checkAuth(context, () -> super.checkAuth(context));
    }

    @Override
    public XWikiUser checkAuth(String username, String password, String rememberMe, XWikiContext context)
        throws XWikiException
    {
        LOG.debug("Invoked checkAuth(String username, String password, String rememberMe, XWikiContext context)");
        // We can't validate a password, so we either forward to the default authenticator or return the cached auth
        final Optional<String> auth = authenticator.getSAMLAuthenticatedUserFromSession(context);

        if (auth.isPresent())
            return checkAuth(context);
        else
            return super.checkAuth(context);
    }

    private String getSourceUrl(XWikiContext context, XWikiRequest request) throws XWikiException {
        final String sourceUrl = request.getParameter("xredirect");
        if (sourceUrl != null)
            return request.getContextPath() + "/" + sourceUrl;

        if (context.getAction().startsWith("login"))
            return context.getWiki().getURL(new DocumentReference(context.getWikiId(),
                    this.defaultReference.getDefaultReferenceValue(EntityType.SPACE),
                    this.defaultReference.getDefaultReferenceValue(EntityType.DOCUMENT)), "view", context);

        return XWiki.getRequestURL(request).toString();
    }

}
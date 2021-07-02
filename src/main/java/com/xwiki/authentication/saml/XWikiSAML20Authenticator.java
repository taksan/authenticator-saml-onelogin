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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.EntityType;
import org.xwiki.model.ModelConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

/**
 * Authentication using a SAML 2.0 server. The following parameters are needed for customizing its behavior in xwiki.cfg:
 * <dl>
 * <dt>xwiki.authentication.saml2.idp.single_sign_on_service.url</dt>
 * <dd>The url to the IdP authentication URL</dd>
 * <dd>required; example: {@code  https://accounts.google.com/o/saml2/idp?idpid=}</dd>
 * <dt>xwiki.authentication.saml2.sp.entityid</dt>
 * <dd>An identifier for the application that will be also configured on the IdP. Can be any unique string</dd>
 * <dd>required</dd>
 * <dt>xwiki.authentication.saml2.idp.x509cert</dt>
 * <dd>The IdP certificate. For multiple lines, add \ at the line break</dd>
 * <dd>required</dd>
 * <dt>xwiki.authentication.saml2.sp.assertion_consumer_service.url</dt>
 * <dd>The ACS link (link to the login submit page)</dd>
 * <dd>required; https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin</dd>
 * <dt>xwiki.authentication.saml2.idp.entityid</dt>
 * <dd>service provider issuer or entityid url</dd>
 * <dd>required; example: {@code https://accounts.google.com/o/saml2?idpid}</dd>
 * <dt>xwiki.authentication.saml2.sp.nameidformat</dt>
 * <dd>the {@code nameidformat} used as a login key</dd>
 * <dd>optional; example: {@code urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress}</dd>
 * <dt>xwiki.authentication.saml2.fields_mapping</dt>
 * <dd>mapping between {@code XWikiUsers} fields and SAML fields</dd>
 * <dd>optional; default value: {@code email=email,first_name=firstName,last_name=lastName}</dd>
 * <dt>xwiki.authentication.saml2.auth_field</dt>
 * <dd>the name of the attribute used to cache the authentication result in the current session</dd>
 * <dd>optional; default value: {@code saml_user}</dd>
 * <dt>xwiki.authentication.saml2.xwiki_user_rule</dt>
 * <dd>list of fields to use for generating an XWiki username</dd>
 * <dd>optional; default value: {@code first_name,last_name}</dd>
 * <dt>xwiki.authentication.saml2.xwiki_user_rule_capitalize</dt>
 * <dd>capitalize each field value when generating the username</dd>
 * <dd>optional; default value: {@code true}; any other value is treated as {@code false}</dd>
 * <dd>xwiki.authentication.saml2.defaultGroupForNewUsers</dd>
 * <dd>The group where new users will be added</dd>
 * <dt>optional; default value {@code XWiki.SamlUsers}</dt>
 * </dl>
 *
 * @version $Id$
 */
public class XWikiSAML20Authenticator extends XWikiAuthServiceImpl
{
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

    private final XwikiAuthConfig authConfig;

    private final SamlAuthenticator authenticator;

    public XWikiSAML20Authenticator() {
        authConfig = XwikiAuthConfig.from(configurationSource);
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

        request.getSession().setAttribute(SamlAuthenticator.ORIGINAL_URL_SESSION_KEY, sourceUrl);
        LOG.debug("Invoked showLogin");
    }

    @Override
    public XWikiUser checkAuth(XWikiContext context) throws XWikiException
    {
        return authenticator.checkAuth(context, () -> super.checkAuth(context));
    }

    @Override
    public XWikiUser checkAuth(String username, String password, String rememberMe, XWikiContext context)
        throws XWikiException
    {
        LOG.debug("Invoked checkAuth(String username, String password, String rememberMe, XWikiContext context)");
        // We can't validate a password, so we either forward to the default authenticator or return the cached auth
        final String auth = authenticator.getSAMLAuthenticatedUserFromSession(context);

        if (StringUtils.isEmpty(auth))
            // No SAML authentication, try standard authentication
            return super.checkAuth(context);
        else
            return checkAuth(context);
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
package com.xwiki.authentication.saml;

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.exception.SettingsException;
import com.onelogin.saml2.settings.Saml2Settings;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import com.xwiki.authentication.saml.function.SupplierWithException;
import com.xwiki.authentication.saml.onelogin.OneLoginAuth;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_UNKNOWN;
import static com.xpn.xwiki.XWikiException.MODULE_XWIKI;
import static java.util.Arrays.asList;

public class NonAuthenticatedAccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NonAuthenticatedAccessHandler.class);
    private static final List<String> allowedActions = asList("login", "skin", "ssx", "logout", "loginsubmit");
    private final XWikiContext context;
    private final OneLoginAuth loginAuthFactory;
    private final Saml2Settings samlSettings;

    public NonAuthenticatedAccessHandler(XWikiContext context, OneLoginAuth loginAuthFactory, Saml2Settings samlSettings) {
        this.context = context;
        this.loginAuthFactory = loginAuthFactory;
        this.samlSettings = samlSettings;
    }

    public XWikiUser handle(SupplierWithException<XWikiUser,XWikiException> defaultAuthHandler)
            throws XWikiException {
        if (isActionAllowedForAnonymousUsers())
            return defaultAuthHandler.execute();

        if (isUsernamePresentInCookie())
            return defaultAuthHandler.execute();

        return startSamlAuthentication();
    }

    private boolean isActionAllowedForAnonymousUsers() {
        return allowedActions.contains(context.getAction());
    }

    private boolean isUsernamePresentInCookie() {
        return context.getRequest().getCookie("username") != null;
    }

    private XWikiUser startSamlAuthentication() throws XWikiException {
        final XWikiRequest request = context.getRequest();
        final XWikiResponse response = context.getResponse();
        final Auth auth;
        try {
            auth = loginAuthFactory.produce(this.samlSettings, request, response);
            LOG.debug("SAML 2.0 Authentication redirection started");
            auth.login(XWiki.getRequestURL(request).toString());
        } catch (SettingsException | IOException e) {
            throw new XWikiException(MODULE_XWIKI, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        }
        return null;
    }
}

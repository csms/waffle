/**
 * Waffle (https://github.com/dblock/waffle)
 *
 * Copyright (c) 2010 - 2014 Application Security, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Application Security, Inc.
 */
package waffle.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;

import javax.security.auth.Subject;
import javax.servlet.ServletException;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import waffle.mock.MockWindowsAuthProvider;
import waffle.mock.MockWindowsIdentity;
import waffle.mock.http.SimpleFilterChain;
import waffle.mock.http.SimpleFilterConfig;
import waffle.mock.http.SimpleHttpRequest;
import waffle.mock.http.SimpleHttpResponse;
import waffle.windows.auth.IWindowsCredentialsHandle;
import waffle.windows.auth.PrincipalFormat;
import waffle.windows.auth.impl.WindowsAccountImpl;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;
import waffle.windows.auth.impl.WindowsCredentialsHandleImpl;
import waffle.windows.auth.impl.WindowsSecurityContextImpl;

import com.google.common.io.BaseEncoding;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Secur32.EXTENDED_NAME_FORMAT;
import com.sun.jna.platform.win32.Secur32Util;
import com.sun.jna.platform.win32.Sspi;
import com.sun.jna.platform.win32.Sspi.SecBufferDesc;

/**
 * Waffle Tomcat Security Filter Tests
 * 
 * @author dblock[at]dblock[dot]org
 */
public class NegotiateSecurityFilterTests {

    private static final String     NEGOTIATE = "Negotiate";
    private static final String     NTLM      = "NTLM";

    private NegotiateSecurityFilter filter;

    @Before
    public void setUp() throws ServletException {
        this.filter = new NegotiateSecurityFilter();
        this.filter.setAuth(new WindowsAuthProviderImpl());
        this.filter.init(null);
    }

    @After
    public void tearDown() {
        this.filter.destroy();
    }

    @Test
    public void testChallengeGET() throws IOException, ServletException {
        SimpleHttpRequest request = new SimpleHttpRequest();
        request.setMethod("GET");
        SimpleHttpResponse response = new SimpleHttpResponse();
        this.filter.doFilter(request, response, null);
        String[] wwwAuthenticates = response.getHeaderValues("WWW-Authenticate");
        assertEquals(3, wwwAuthenticates.length);
        assertEquals(NEGOTIATE, wwwAuthenticates[0]);
        assertEquals(NTLM, wwwAuthenticates[1]);
        assertTrue(wwwAuthenticates[2].startsWith("Basic realm=\""));
        assertEquals(2, response.getHeaderNamesSize());
        assertEquals("keep-alive", response.getHeader("Connection"));
        assertEquals(401, response.getStatus());
    }

    @Test
    public void testChallengePOST() throws IOException, ServletException {
        String securityPackage = NEGOTIATE;
        IWindowsCredentialsHandle clientCredentials = null;
        WindowsSecurityContextImpl clientContext = null;
        try {
            // client credentials handle
            clientCredentials = WindowsCredentialsHandleImpl.getCurrent(securityPackage);
            clientCredentials.initialize();
            // initial client security context
            clientContext = new WindowsSecurityContextImpl();
            clientContext.setPrincipalName(WindowsAccountImpl.getCurrentUsername());
            clientContext.setCredentialsHandle(clientCredentials.getHandle());
            clientContext.setSecurityPackage(securityPackage);
            clientContext.initialize(null, null, WindowsAccountImpl.getCurrentUsername());
            SimpleHttpRequest request = new SimpleHttpRequest();
            request.setMethod("POST");
            request.setContentLength(0);
            String clientToken = BaseEncoding.base64().encode(clientContext.getToken());
            request.addHeader("Authorization", securityPackage + " " + clientToken);
            SimpleHttpResponse response = new SimpleHttpResponse();
            this.filter.doFilter(request, response, null);
            assertTrue(response.getHeader("WWW-Authenticate").startsWith(securityPackage + " "));
            assertEquals("keep-alive", response.getHeader("Connection"));
            assertEquals(2, response.getHeaderNamesSize());
            assertEquals(401, response.getStatus());
        } finally {
            if (clientContext != null) {
                clientContext.dispose();
            }
            if (clientCredentials != null) {
                clientCredentials.dispose();
            }
        }
    }

    @Test
    public void testNegotiate() throws IOException, ServletException {
        String securityPackage = NEGOTIATE;
        // client credentials handle
        IWindowsCredentialsHandle clientCredentials = null;
        WindowsSecurityContextImpl clientContext = null;
        // role will contain both Everyone and SID
        this.filter.setRoleFormat("both");
        try {
            // client credentials handle
            clientCredentials = WindowsCredentialsHandleImpl.getCurrent(securityPackage);
            clientCredentials.initialize();
            // initial client security context
            clientContext = new WindowsSecurityContextImpl();
            clientContext.setPrincipalName(WindowsAccountImpl.getCurrentUsername());
            clientContext.setCredentialsHandle(clientCredentials.getHandle());
            clientContext.setSecurityPackage(securityPackage);
            clientContext.initialize(null, null, WindowsAccountImpl.getCurrentUsername());
            // filter chain
            SimpleFilterChain filterChain = new SimpleFilterChain();
            // negotiate
            boolean authenticated = false;
            SimpleHttpRequest request = new SimpleHttpRequest();
            while (true) {
                String clientToken = BaseEncoding.base64().encode(clientContext.getToken());
                request.addHeader("Authorization", securityPackage + " " + clientToken);

                SimpleHttpResponse response = new SimpleHttpResponse();
                this.filter.doFilter(request, response, filterChain);

                Subject subject = (Subject) request.getSession().getAttribute("javax.security.auth.subject");
                authenticated = (subject != null && subject.getPrincipals().size() > 0);

                if (authenticated) {
                    Assertions.assertThat(response.getHeaderNamesSize()).isGreaterThanOrEqualTo(0);
                    break;
                }

                assertTrue(response.getHeader("WWW-Authenticate").startsWith(securityPackage + " "));
                assertEquals("keep-alive", response.getHeader("Connection"));
                assertEquals(2, response.getHeaderNamesSize());
                assertEquals(401, response.getStatus());
                String continueToken = response.getHeader("WWW-Authenticate").substring(securityPackage.length() + 1);
                byte[] continueTokenBytes = BaseEncoding.base64().decode(continueToken);
                Assertions.assertThat(continueTokenBytes.length).isGreaterThan(0);
                SecBufferDesc continueTokenBuffer = new SecBufferDesc(Sspi.SECBUFFER_TOKEN, continueTokenBytes);
                clientContext.initialize(clientContext.getHandle(), continueTokenBuffer, "localhost");
            }
            assertTrue(authenticated);
            assertTrue(filterChain.getRequest() instanceof NegotiateRequestWrapper);
            assertTrue(filterChain.getResponse() instanceof SimpleHttpResponse);
            NegotiateRequestWrapper wrappedRequest = (NegotiateRequestWrapper) filterChain.getRequest();
            assertEquals(NEGOTIATE.toUpperCase(), wrappedRequest.getAuthType());
            assertEquals(Secur32Util.getUserNameEx(EXTENDED_NAME_FORMAT.NameSamCompatible),
                    wrappedRequest.getRemoteUser());
            assertTrue(wrappedRequest.getUserPrincipal() instanceof WindowsPrincipal);
            String everyoneGroupName = Advapi32Util.getAccountBySid("S-1-1-0").name;
            assertTrue(wrappedRequest.isUserInRole(everyoneGroupName));
            assertTrue(wrappedRequest.isUserInRole("S-1-1-0"));
        } finally {
            if (clientContext != null) {
                clientContext.dispose();
            }
            if (clientCredentials != null) {
                clientCredentials.dispose();
            }
        }
    }

    @Test
    public void testNegotiatePreviousAuthWithWindowsPrincipal() throws IOException, ServletException {
        MockWindowsIdentity mockWindowsIdentity = new MockWindowsIdentity("user", new ArrayList<String>());
        SimpleHttpRequest request = new SimpleHttpRequest();
        WindowsPrincipal windowsPrincipal = new WindowsPrincipal(mockWindowsIdentity);
        request.setUserPrincipal(windowsPrincipal);
        SimpleFilterChain filterChain = new SimpleFilterChain();
        SimpleHttpResponse response = new SimpleHttpResponse();
        this.filter.doFilter(request, response, filterChain);
        assertTrue(filterChain.getRequest() instanceof NegotiateRequestWrapper);
        NegotiateRequestWrapper wrappedRequest = (NegotiateRequestWrapper) filterChain.getRequest();
        assertTrue(wrappedRequest.getUserPrincipal() instanceof WindowsPrincipal);
        assertEquals(windowsPrincipal, wrappedRequest.getUserPrincipal());
    }

    @Test
    public void testChallengeNTLMPOST() throws IOException, ServletException {
        MockWindowsIdentity mockWindowsIdentity = new MockWindowsIdentity("user", new ArrayList<String>());
        SimpleHttpRequest request = new SimpleHttpRequest();
        WindowsPrincipal windowsPrincipal = new WindowsPrincipal(mockWindowsIdentity);
        request.setUserPrincipal(windowsPrincipal);
        request.setMethod("POST");
        request.setContentLength(0);
        request.addHeader("Authorization", "NTLM TlRMTVNTUAABAAAABzIAAAYABgArAAAACwALACAAAABXT1JLU1RBVElPTkRPTUFJTg==");
        SimpleFilterChain filterChain = new SimpleFilterChain();
        SimpleHttpResponse response = new SimpleHttpResponse();
        this.filter.doFilter(request, response, filterChain);
        assertEquals(401, response.getStatus());
        String[] wwwAuthenticates = response.getHeaderValues("WWW-Authenticate");
        assertEquals(1, wwwAuthenticates.length);
        assertTrue(wwwAuthenticates[0].startsWith("NTLM "));
        assertEquals(2, response.getHeaderNamesSize());
        assertEquals("keep-alive", response.getHeader("Connection"));
        assertEquals(401, response.getStatus());
    }

    @Test
    public void testChallengeNTLMPUT() throws IOException, ServletException {
        MockWindowsIdentity mockWindowsIdentity = new MockWindowsIdentity("user", new ArrayList<String>());
        SimpleHttpRequest request = new SimpleHttpRequest();
        WindowsPrincipal windowsPrincipal = new WindowsPrincipal(mockWindowsIdentity);
        request.setUserPrincipal(windowsPrincipal);
        request.setMethod("PUT");
        request.setContentLength(0);
        request.addHeader("Authorization", "NTLM TlRMTVNTUAABAAAABzIAAAYABgArAAAACwALACAAAABXT1JLU1RBVElPTkRPTUFJTg==");
        SimpleFilterChain filterChain = new SimpleFilterChain();
        SimpleHttpResponse response = new SimpleHttpResponse();
        this.filter.doFilter(request, response, filterChain);
        assertEquals(401, response.getStatus());
        String[] wwwAuthenticates = response.getHeaderValues("WWW-Authenticate");
        assertEquals(1, wwwAuthenticates.length);
        assertTrue(wwwAuthenticates[0].startsWith("NTLM "));
        assertEquals(2, response.getHeaderNamesSize());
        assertEquals("keep-alive", response.getHeader("Connection"));
        assertEquals(401, response.getStatus());
    }

    @Test
    public void testInitBasicSecurityFilterProvider() throws ServletException {
        SimpleFilterConfig filterConfig = new SimpleFilterConfig();
        filterConfig.setParameter("principalFormat", "sid");
        filterConfig.setParameter("roleFormat", "none");
        filterConfig.setParameter("allowGuestLogin", "true");
        filterConfig.setParameter("securityFilterProviders", "waffle.servlet.spi.BasicSecurityFilterProvider\n");
        filterConfig.setParameter("waffle.servlet.spi.BasicSecurityFilterProvider/realm", "DemoRealm");
        filterConfig.setParameter("authProvider", MockWindowsAuthProvider.class.getName());
        this.filter.init(filterConfig);
        assertEquals(this.filter.getPrincipalFormat(), PrincipalFormat.SID);
        assertEquals(this.filter.getRoleFormat(), PrincipalFormat.NONE);
        assertTrue(this.filter.isAllowGuestLogin());
        assertEquals(1, this.filter.getProviders().size());
        assertTrue(this.filter.getAuth() instanceof MockWindowsAuthProvider);
    }

    @Test
    public void testInitTwoSecurityFilterProviders() throws ServletException {
        // make sure that providers can be specified separated by any kind of space
        SimpleFilterConfig filterConfig = new SimpleFilterConfig();
        filterConfig.setParameter("securityFilterProviders", "waffle.servlet.spi.BasicSecurityFilterProvider\n"
                + "waffle.servlet.spi.NegotiateSecurityFilterProvider waffle.servlet.spi.BasicSecurityFilterProvider");
        this.filter.init(filterConfig);
        assertEquals(3, this.filter.getProviders().size());
    }

    @Test
    public void testInitNegotiateSecurityFilterProvider() throws ServletException {
        SimpleFilterConfig filterConfig = new SimpleFilterConfig();
        filterConfig.setParameter("securityFilterProviders", "waffle.servlet.spi.NegotiateSecurityFilterProvider\n");
        filterConfig.setParameter("waffle.servlet.spi.NegotiateSecurityFilterProvider/protocols",
                "NTLM\nNegotiate NTLM");
        this.filter.init(filterConfig);
        assertEquals(this.filter.getPrincipalFormat(), PrincipalFormat.FQN);
        assertEquals(this.filter.getRoleFormat(), PrincipalFormat.FQN);
        assertTrue(this.filter.isAllowGuestLogin());
        assertEquals(1, this.filter.getProviders().size());
    }

    @Test
    public void testInitNegotiateSecurityFilterProviderInvalidProtocol() {
        SimpleFilterConfig filterConfig = new SimpleFilterConfig();
        filterConfig.setParameter("securityFilterProviders", "waffle.servlet.spi.NegotiateSecurityFilterProvider\n");
        filterConfig.setParameter("waffle.servlet.spi.NegotiateSecurityFilterProvider/protocols", "INVALID");
        try {
            this.filter.init(filterConfig);
            fail("expected ServletException");
        } catch (ServletException e) {
            assertEquals("java.lang.RuntimeException: Unsupported protocol: INVALID", e.getMessage());
        }
    }

    @Test
    public void testInitInvalidParameter() {
        try {
            SimpleFilterConfig filterConfig = new SimpleFilterConfig();
            filterConfig.setParameter("invalidParameter", "random");
            this.filter.init(filterConfig);
            fail("expected ServletException");
        } catch (ServletException e) {
            assertEquals("Invalid parameter: invalidParameter", e.getMessage());
        }
    }

    @Test
    public void testInitInvalidClassInParameter() {
        try {
            SimpleFilterConfig filterConfig = new SimpleFilterConfig();
            filterConfig.setParameter("invalidClass/invalidParameter", "random");
            this.filter.init(filterConfig);
            fail("expected ServletException");
        } catch (ServletException e) {
            assertEquals("java.lang.ClassNotFoundException: invalidClass", e.getMessage());
        }
    }
}

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

import java.io.IOException;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;

import waffle.mock.http.*;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import waffle.mock.MockWindowsAuthProvider;
import waffle.windows.auth.impl.WindowsAccountImpl;

/**
 * Waffle Tomcat Security Filter Tests
 * 
 * @author dblock[at]dblock[dot]org
 */
public class BasicSecurityFilterTests {

    private NegotiateSecurityFilter filter;

    @Before
    public void setUp() throws ServletException {
        this.filter = new NegotiateSecurityFilter();
        this.filter.setAuth(new MockWindowsAuthProvider());
        this.filter.init(null);
    }

    @After
    public void tearDown() {
        this.filter.destroy();
    }

    @Test
    public void testBasicAuth() throws IOException, ServletException {
        final SimpleHttpRequest request = new SimpleHttpRequest();
        request.setMethod("GET");

        final String userHeaderValue = WindowsAccountImpl.getCurrentUsername() + ":password";
        final String basicAuthHeader = "Basic "
                + BaseEncoding.base64().encode(userHeaderValue.getBytes(Charsets.UTF_8));
        request.addHeader("Authorization", basicAuthHeader);

        final SimpleHttpResponse response = new SimpleHttpResponse();
        final FilterChain filterChain = new SimpleFilterChain();
        this.filter.doFilter(request, response, filterChain);
        final Subject subject = (Subject) request.getSession().getAttribute("javax.security.auth.subject");
        Assert.assertNotNull(subject);
        Assertions.assertThat(subject.getPrincipals().size()).isGreaterThan(0);
    }
}

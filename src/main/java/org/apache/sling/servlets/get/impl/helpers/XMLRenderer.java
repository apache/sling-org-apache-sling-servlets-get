/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.servlets.get.impl.helpers;

import javax.jcr.Node;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;
import org.xml.sax.ContentHandler;

/**
 * The <code>XMLRendererServlet</code> renders the current resource in XML
 * on behalf of the {@link org.apache.sling.servlets.get.impl.DefaultGetServlet}.
 *
 * At the moment only JCR nodes can be rendered as XML.
 */
public class XMLRenderer implements Renderer {

    private static final String SYSVIEW = "sysview";
    private static final String DOCVIEW = "docview";

    @Override
    public void render(SlingJakartaHttpServletRequest req, SlingJakartaHttpServletResponse resp) throws IOException {
        final Resource r = req.getResource();

        if (ResourceUtil.isNonExistingResource(r)) {
            throw new ResourceNotFoundException("No data to render.");
        }

        resp.setContentType(req.getResponseContentType());
        resp.setCharacterEncoding("UTF-8");

        // are we included?
        final boolean isIncluded = req.getAttribute(SlingConstants.ATTR_REQUEST_JAKARTA_SERVLET) != null;

        try {
            final Node node = r.adaptTo(Node.class);
            if (node != null) {
                try {
                    if (req.getRequestPathInfo().getSelectorString() == null
                            || req.getRequestPathInfo().getSelectorString().equals(DOCVIEW)) {
                        // check if response is adaptable to a content handler
                        final ContentHandler ch = resp.adaptTo(ContentHandler.class);
                        if (ch == null) {
                            node.getSession().exportDocumentView(node.getPath(), resp.getOutputStream(), false, false);
                        } else {
                            node.getSession().exportDocumentView(node.getPath(), ch, false, false);
                        }
                    } else if (req.getRequestPathInfo().getSelectorString().equals(SYSVIEW)) {
                        // check if response is adaptable to a content handler
                        final ContentHandler ch = resp.adaptTo(ContentHandler.class);
                        if (ch == null) {
                            node.getSession().exportSystemView(node.getPath(), resp.getOutputStream(), false, false);
                        } else {
                            node.getSession().exportSystemView(node.getPath(), ch, false, false);
                        }
                    } else {
                        resp.sendError(HttpServletResponse.SC_NO_CONTENT); // NO Content
                    }
                } catch (Exception e) {
                    throw new ServletException("Unable to export resource as xml: " + r, e);
                }
            } else {
                if (!isIncluded) {
                    resp.sendError(HttpServletResponse.SC_NO_CONTENT); // NO Content
                }
            }
        } catch (final Throwable t) {
            // if the JCR api is not available, we get here
            if (!isIncluded) {
                resp.sendError(HttpServletResponse.SC_NO_CONTENT); // NO Content
            }
        }
    }
}

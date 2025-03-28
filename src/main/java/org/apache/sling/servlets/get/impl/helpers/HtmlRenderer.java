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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;
import org.owasp.encoder.Encode;

/**
 * The <code>HtmlRendererServlet</code> renders the current resource in HTML
 * on behalf of the {@link org.apache.sling.servlets.get.impl.DefaultGetServlet}.
 */
public class HtmlRenderer implements Renderer {

    public static final HtmlRenderer INSTANCE = new HtmlRenderer();

    public void render(final SlingJakartaHttpServletRequest req, final SlingJakartaHttpServletResponse resp)
            throws IOException {
        final Resource r = req.getResource();

        if (ResourceUtil.isNonExistingResource(r)) {
            throw new ResourceNotFoundException("No data to render.");
        }

        resp.setContentType(req.getResponseContentType());
        resp.setCharacterEncoding("UTF-8");

        final PrintWriter pw = resp.getWriter();

        final boolean isIncluded = req.getAttribute(SlingConstants.ATTR_REQUEST_JAKARTA_SERVLET) != null;

        @SuppressWarnings({"rawtypes"})
        final Map map = r.adaptTo(Map.class);
        if (map != null) {
            printProlog(pw, isIncluded);
            printResourceInfo(pw, r);
            render(pw, r, map);
            printEpilog(pw, isIncluded);
        } else if (r.adaptTo(String.class) != null) {
            printProlog(pw, isIncluded);
            printResourceInfo(pw, r);
            render(pw, r, r.adaptTo(String.class));
            printEpilog(pw, isIncluded);
        } else if (r.adaptTo(String[].class) != null) {
            printProlog(pw, isIncluded);
            printResourceInfo(pw, r);
            render(pw, r, r.adaptTo(String[].class));
            printEpilog(pw, isIncluded);
        } else {
            if (!isIncluded) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT); // NO Content
            }
        }
    }

    private void printProlog(final PrintWriter pw, final boolean isIncluded) {
        if (!isIncluded) {
            pw.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            pw.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\"");
            pw.println("    \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
            pw.println("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">");
            pw.println("<head><meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" /></head>");
            pw.println("<body>");
        }
    }

    private void printEpilog(final PrintWriter pw, final boolean isIncluded) {
        if (!isIncluded) {
            pw.println("</body></html>");
        }
    }

    private void printResourceInfo(final PrintWriter pw, final Resource r) {
        pw.print("<h1>Resource dumped by ");
        pw.print(Encode.forHtmlContent(getClass().getSimpleName()));
        pw.println("</h1>");

        pw.print("<p>Resource path: <b>");
        pw.print(Encode.forHtmlContent(r.getPath()));
        pw.println("</b></p>");

        pw.print("<p>Resource metadata: <b>");
        pw.print(Encode.forHtmlContent(String.valueOf(r.getResourceMetadata())));
        pw.println("</b></p>");

        pw.print("<p>Resource type: <b>");
        pw.print(Encode.forHtmlContent(r.getResourceType()));
        pw.println("</b></p>");

        String resourceSuperType = r.getResourceResolver().getParentResourceType(r);
        if (resourceSuperType == null) {
            resourceSuperType = "-";
        }
        pw.print("<p>Resource super type: <b>");
        pw.print(Encode.forHtmlContent(resourceSuperType));
        pw.println("</b></p>");
    }

    @SuppressWarnings({"rawtypes"})
    private void render(final PrintWriter pw, final Resource r, final Map<?, ?> map) {
        pw.println("<h2>Resource properties</h2>");
        pw.println("<p>");
        for (final Map.Entry p : map.entrySet()) {
            printPropertyValue(pw, p.getKey().toString(), p.getValue());
            pw.println();
        }
        pw.println("</p>");
    }

    private void render(final PrintWriter pw, final Resource r, final String value) {
        printPropertyValue(pw, "Resource Value", value);
    }

    private void render(final PrintWriter pw, final Resource r, final String[] values) {
        for (final String value : values) {
            printPropertyValue(pw, "Resource Value", value);
        }
    }

    private void printPropertyValue(final PrintWriter pw, final String name, final Object value) {

        pw.print(Encode.forHtmlContent(name));
        pw.print(": <b>");

        if (value.getClass().isArray()) {
            Object[] values = (Object[]) value;
            pw.print('[');
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    pw.print(", ");
                }
                pw.print(Encode.forHtmlContent(values[i].toString()));
            }
            pw.print(']');
        } else {
            pw.print(Encode.forHtmlContent(value.toString()));
        }

        pw.print("</b><br />");
    }
}

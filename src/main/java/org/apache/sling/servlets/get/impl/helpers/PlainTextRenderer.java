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
import java.util.Iterator;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;

/**
 * The <code>PlainTextRendererServlet</code> renders the current resource in
 * plain text on behalf of the
 * {@link org.apache.sling.servlets.get.impl.DefaultGetServlet}.
 */
public class PlainTextRenderer implements Renderer {

    public void render(SlingJakartaHttpServletRequest req, SlingJakartaHttpServletResponse resp) throws IOException {
        final Resource r = req.getResource();
        if (ResourceUtil.isNonExistingResource(r)) {
            throw new ResourceNotFoundException("No data to render.");
        }

        /*
         * TODO if(srd != null) { renderSyntheticResource(req, resp, srd);
         * return; }
         */

        resp.setContentType(req.getResponseContentType());
        resp.setCharacterEncoding("UTF-8");

        final PrintWriter pw = resp.getWriter();
        @SuppressWarnings("unchecked")
        final Map<String, Object> map = r.adaptTo(Map.class);
        if (map != null) {
            dump(pw, r, map);
        } else if (r.adaptTo(String.class) != null) {
            printPropertyValue(pw, r.getName(), r.adaptTo(String.class), false);
        } else if (r.adaptTo(String[].class) != null) {
            printPropertyValue(pw, r.getName(), r.adaptTo(String[].class), false);
        } else {
            resp.sendError(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    /** Render synthetic resource */
    /*
     * TODO private void renderSyntheticResource(SlingJakartaHttpServlet
     * req,SlingJakartaHttpServletResponse resp,SyntheticResourceData data) throws
     * IOException { resp.setContentType(responseContentType);
     * resp.getOutputStream().write(data.toString().getBytes()); }
     */

    /**
     * Dumps the information about the provided resource to a {@link PrintWriter}.
     * @param pw the PrintWriter
     * @param r the resource
     * @param map the resource's properties
     */
    protected void dump(PrintWriter pw, Resource r, Map<String, Object> map) {
        pw.println("** Resource dumped by " + getClass().getSimpleName() + "**");
        pw.println("Resource path:" + r.getPath());
        pw.println("Resource metadata: " + r.getResourceMetadata());
        pw.println("Resource type: " + r.getResourceType());

        String resourceSuperType = r.getResourceResolver().getParentResourceType(r);
        if (resourceSuperType == null) {
            resourceSuperType = "-";
        }
        pw.println("Resource super type: " + resourceSuperType);

        pw.println("\n** Resource properties **");
        final Iterator<Map.Entry<String, Object>> pi = map.entrySet().iterator();
        while (pi.hasNext()) {
            final Map.Entry<String, Object> p = pi.next();
            printPropertyValue(pw, p.getKey(), p.getValue(), true);
            pw.println();
        }
    }

    protected void printPropertyValue(PrintWriter pw, String name, Object value, boolean includeName) {

        if (includeName) {
            pw.print(name + ": ");
        }

        if (value.getClass().isArray()) {
            final Object[] values = (Object[]) value;
            pw.print('[');
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    pw.print(", ");
                }
                pw.print(values[i].toString());
            }
            pw.print(']');
        } else {
            pw.print(value.toString());
        }
    }
}

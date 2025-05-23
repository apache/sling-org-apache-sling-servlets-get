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
package org.apache.sling.servlets.get.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.SlingJakartaSafeMethodsServlet;
import org.apache.sling.servlets.get.impl.helpers.HeadServletResponse;
import org.apache.sling.servlets.get.impl.helpers.HtmlRenderer;
import org.apache.sling.servlets.get.impl.helpers.JsonRenderer;
import org.apache.sling.servlets.get.impl.helpers.PlainTextRenderer;
import org.apache.sling.servlets.get.impl.helpers.Renderer;
import org.apache.sling.servlets.get.impl.helpers.StreamRenderer;
import org.apache.sling.servlets.get.impl.helpers.XMLRenderer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A SlingSafeMethodsServlet that renders the current Resource as simple HTML
 */
@Component(
        service = Servlet.class,
        name = "org.apache.sling.servlets.get.DefaultGetServlet",
        property = {
            "service.description=Default GET Servlet",
            "service.vendor=The Apache Software Foundation",

            // Use this as a default servlet for Sling
            "sling.servlet.resourceTypes=sling/servlet/default",
            "sling.servlet.prefix:Integer=-1",

            // Generic handler for all get requests
            "sling.servlet.methods=GET",
            "sling.servlet.methods=HEAD"
        })
@Designate(ocd = DefaultGetServlet.Config.class)
public class DefaultGetServlet extends SlingJakartaSafeMethodsServlet {

    private static final long serialVersionUID = -2714152339750885354L;

    @ObjectClassDefinition(
            name = "Apache Sling GET Servlet",
            description = "The Sling GET servlet is registered as the default servlet to handle GET requests.")
    public @interface Config {

        @AttributeDefinition(
                name = "Extension Aliases",
                description = "The aliases can be used to map several extensions to a "
                        + "single servlet. This works irrespective if the renderer for the target extension is enabled or not. "
                        + "For instance \"xml:pdf,rtf\" maps the extensions \".pdf\" and "
                        + "\".rtf\" to the servlet helper handling the \".xml\" extension.")
        String[] aliases();

        @AttributeDefinition(
                name = "Auto Index",
                description = "Controls whether a simple directory index is rendered for "
                        + "a directory request. A directory request is a request to a resource with a "
                        + "trailing slash (/) character, for example http://host/apps/. If none of the "
                        + "index resources exists, the default GET servlet may automatically render an "
                        + "index listing of the child resources if this option is checked, which is the "
                        + "default. If this option is not checked, the request to the resource is "
                        + "forbidden and results in a status 403/FORBIDDEN. This configuration "
                        + "corresponds to the \"Index\" option of the Options directive of Apache HTTP "
                        + "Server (httpd).")
        boolean index() default false;

        @AttributeDefinition(
                name = "Index Resources",
                description = "List of child resources to be considered for rendering  "
                        + "the index of a \"directory\". The default value is [ \"index\", \"index.html\" ].  "
                        + "Each entry in the list is checked and the first entry found is included to  "
                        + "render the index. If an entry is selected, which has not extension (for  "
                        + "example the \"index\" resource), the extension \".html\" is appended for the  "
                        + "inclusion to indicate the desired text/html rendering. If the resource name  "
                        + "has an extension (as in \"index.html\"), no additional extension is appended  "
                        + "for the inclusion. This configuration corresponds to the <DirectoryIndex>  "
                        + "directive of Apache HTTP Server (httpd).")
        String[] index_files() default {"index", "index.html"};

        @AttributeDefinition(
                name = "Enable HTML",
                description =
                        "Whether the renderer for HTML of the default GET servlet is enabled for extension \"html\" or not.")
        boolean enable_html() default true;

        @AttributeDefinition(
                name = "Enable JSON",
                description =
                        "Whether the renderer for JSON of the default GET servlet is enabled for extension \"json\" or not.")
        boolean enable_json() default true;

        @AttributeDefinition(
                name = "Enable Plain Text",
                description =
                        "Whether the renderer for plain text of the default GET servlet is enabled for extension \"txt\" or not.")
        boolean enable_txt() default true;

        @AttributeDefinition(
                name = "Enable XML",
                description =
                        "Whether the renderer for XML of the default GET servlet is enabled for extension \"xml\" or not.")
        boolean enable_xml() default true;

        @AttributeDefinition(
                name = "JSON Max results",
                description = "The maximum number of resources that should "
                        + "be returned when doing a node.5.json or node.infinity.json. In JSON terms "
                        + "this basically means the number of Objects to return. Default value is "
                        + "200.")
        int json_maximumresults() default 200;

        @AttributeDefinition(
                name = "Legacy ECMA date format",
                description = "Enable legacy Sling ECMA format for dates")
        boolean ecmaSuport() default true;
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, Renderer> rendererMap = new HashMap<>();

    private int jsonMaximumResults;

    /** Additional aliases. */
    private String[] aliases;

    /** Whether to support automatic index rendering */
    private boolean index;

    /** The names of index rendering children */
    private String[] indexFiles;

    private boolean enableHtml;

    private boolean enableTxt;

    private boolean enableJson;

    private boolean enableXml;

    private boolean enableEcmaSupport;

    public static final String EXT_HTML = "html";

    public static final String EXT_TXT = "txt";

    public static final String EXT_JSON = "json";

    public static final String EXT_XML = "xml";

    public static final String EXT_RES = "res";

    @Activate
    protected void activate(Config cfg) {
        this.aliases = cfg.aliases();
        this.index = cfg.index();
        this.indexFiles = cfg.index_files();
        if (this.indexFiles == null) {
            this.indexFiles = new String[0];
        }

        this.enableHtml = cfg.enable_html();
        this.enableTxt = cfg.enable_txt();
        this.enableJson = cfg.enable_json();
        this.enableXml = cfg.enable_xml();
        this.jsonMaximumResults = cfg.json_maximumresults();
        this.enableEcmaSupport = cfg.ecmaSuport();
        if (enableEcmaSupport) {
            logger.info("Legacy ECMA format is enabled");
        }
    }

    @Deactivate
    protected void deactivate() {
        this.aliases = null;
        this.index = false;
        this.indexFiles = null;
    }

    private Renderer getDefaultRenderer(final String type) {
        Renderer renderer = null;
        if (EXT_RES.equals(type)) {
            renderer = new StreamRenderer(index, indexFiles, getServletContext());
        } else if (EXT_HTML.equals(type)) {
            renderer = HtmlRenderer.INSTANCE;
        } else if (EXT_TXT.equals(type)) {
            renderer = new PlainTextRenderer();
        } else if (EXT_JSON.equals(type)) {
            renderer = new JsonRenderer(jsonMaximumResults, enableEcmaSupport);
        } else if (EXT_XML.equals(type)) {
            renderer = new XMLRenderer();
        }
        return renderer;
    }

    @Override
    public void init() throws ServletException {
        super.init();

        // use the Renderer for rendering EXT_RES as the
        // streamer renderer
        Renderer streamRenderer = getDefaultRenderer(EXT_RES);

        rendererMap.put(null, streamRenderer);

        rendererMap.put(EXT_RES, streamRenderer);

        if (enableHtml) {
            rendererMap.put(EXT_HTML, getDefaultRenderer(EXT_HTML));
        }

        if (enableTxt) {
            rendererMap.put(EXT_TXT, getDefaultRenderer(EXT_TXT));
        }

        if (enableJson) {
            rendererMap.put(EXT_JSON, getDefaultRenderer(EXT_JSON));
        }

        if (enableXml) {
            rendererMap.put(EXT_XML, getDefaultRenderer(EXT_XML));
        }

        // check additional aliases
        if (this.aliases != null) {
            for (final String m : aliases) {
                final int pos = m.indexOf(':');
                if (pos != -1) {
                    final String type = m.substring(0, pos);
                    Renderer renderer = rendererMap.get(type);
                    if (renderer == null) {
                        renderer = getDefaultRenderer(type);
                    }
                    if (renderer != null) {
                        final String extensions = m.substring(pos + 1);
                        final StringTokenizer st = new StringTokenizer(extensions, ",");
                        while (st.hasMoreTokens()) {
                            final String ext = st.nextToken();
                            rendererMap.put(ext, renderer);
                        }
                    } else {
                        logger.warn("Unable to enable renderer alias(es) for {} - type not supported", m);
                    }
                }
            }
        }
    }

    /**
     * @throws ResourceNotFoundException if the resource of the request is a non
     *             existing resource.
     */
    @Override
    protected void doGet(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
            throws ServletException, IOException {

        // cannot handle the request for missing resources
        if (ResourceUtil.isNonExistingResource(request.getResource())) {
            throw new ResourceNotFoundException(request.getResource().getPath(), "No resource found");
        }

        Renderer renderer;
        String ext = request.getRequestPathInfo().getExtension();
        renderer = rendererMap.get(ext);

        // fail if we should not just stream or we cannot support the ext.
        if (renderer == null) {
            request.getRequestProgressTracker().log("No renderer for extension " + ext);
            // if this is an included request, sendError() would fail
            // as the response is already committed, in this case we just
            // do nothing (but log an error message)
            if (response.isCommitted() || request.getAttribute(SlingConstants.ATTR_REQUEST_JAKARTA_SERVLET) != null) {
                logger.error("No renderer for extension {}, cannot render resource {}", ext, request.getResource());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }

        request.getRequestProgressTracker()
                .log("Using " + renderer.getClass().getName() + " to render for extension=" + ext);
        renderer.render(request, response);
    }

    @Override
    protected void doHead(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
            throws ServletException, IOException {

        response = new HeadServletResponse(response);
        doGet(request, response);
    }

    @Override
    public void destroy() {
        rendererMap.clear();
        super.destroy();
    }
}

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
package org.apache.sling.servlets.get.impl.util;

import java.util.Iterator;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

public class JsonToText {
    /** Rendering options */
    public static class Options {
        int indent;
        private boolean indentIsPositive;
        int initialIndent;
        boolean arraysForChildren;

        public static final String DEFAULT_CHILDREN_KEY = "__children__";
        public static final String DEFAULT_CHILD_NAME_KEY = "__name__";

        String childrenKey = DEFAULT_CHILDREN_KEY;
        String childNameKey = DEFAULT_CHILD_NAME_KEY;

        /** Clients use JSONRenderer.options() to create objects */
        private Options() {}

        Options(Options opt) {
            this.indent = opt.indent;
            this.indentIsPositive = opt.indentIsPositive;
            this.initialIndent = opt.initialIndent;
            this.arraysForChildren = opt.arraysForChildren;
        }

        public Options withIndent(int n) {
            indent = n;
            indentIsPositive = indent > 0;
            return this;
        }

        public Options withInitialIndent(int n) {
            initialIndent = n;
            return this;
        }

        public Options withArraysForChildren(boolean b) {
            arraysForChildren = b;
            return this;
        }

        public Options withChildNameKey(String key) {
            childNameKey = key;
            return this;
        }

        public Options withChildrenKey(String key) {
            childrenKey = key;
            return this;
        }

        boolean hasIndent() {
            return indentIsPositive;
        }
    }

    /** Return an Options object with default values */
    public Options options() {
        return new Options();
    }

    /** Write N spaces to sb for indentation */
    private void indent(StringBuilder sb, int howMuch) {
        for (int i = 0; i < howMuch; i++) {
            sb.append(' ');
        }
    }

    /** Quote the supplied string for JSON */
    private String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char b;
        char c = 0;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 2);
        String t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    if (b == '<') {
                        sb.append('\\');
                    }
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** Make a JSON String of an Object value, with rendering options
     */
    private String valueToString(JsonValue value, Options opt) {
        if (value instanceof JsonObject) {
            return prettyPrint((JsonObject) value, opt);
        } else if (value instanceof JsonArray) {
            return prettyPrint((JsonArray) value, opt);
        }
        return value.toString();
    }

    /** Decide whether o must be skipped and added to a, when rendering a JSONObject */
    private boolean skipChildObject(JsonArrayBuilder a, Options opt, String key, Object value) {
        if (opt.arraysForChildren && (value instanceof JsonObject)) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(opt.childNameKey, key);
            for (Map.Entry<String, JsonValue> entry : ((JsonObject) value).entrySet()) {
                builder.add(entry.getKey(), entry.getValue());
            }
            a.add(builder);
            return true;
        }
        return false;
    }

    /**
     * Make a prettyprinted JSON text of this JSONObject.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     * @return a printable, displayable, transmittable
     *  representation of the object, beginning
     *  with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *  with <code>}</code>&nbsp;<small>(right brace)</small>.
     * @throws IllegalArgumentException If the object contains an invalid number.
     */
    public String prettyPrint(JsonObject jo, Options opt) {
        int n = jo.size();
        if (n == 0) {
            return "{}";
        }
        final JsonArrayBuilder children = Json.createArrayBuilder();
        Iterator<String> keys = jo.keySet().iterator();
        StringBuilder sb = new StringBuilder("{");
        int newindent = opt.initialIndent + opt.indent;
        String o;
        if (n == 1) {
            o = keys.next();
            final JsonValue v = jo.get(o);
            if (!skipChildObject(children, opt, o, v)) {
                sb.append(quote(o));
                sb.append(": ");
                sb.append(valueToString(v, opt));
            }
        } else {
            while (keys.hasNext()) {
                o = keys.next();
                final JsonValue v = jo.get(o);
                if (skipChildObject(children, opt, o, v)) {
                    continue;
                }
                if (sb.length() > 1) {
                    sb.append(",\n");
                } else {
                    sb.append('\n');
                }
                indent(sb, newindent);
                sb.append(quote(o.toString()));
                sb.append(": ");
                sb.append(valueToString(v, options().withIndent(opt.indent).withInitialIndent(newindent)));
            }
            if (sb.length() > 1) {
                sb.append('\n');
                indent(sb, newindent);
            }
        }

        /** Render children if any were skipped (in "children in arrays" mode) */
        JsonArray childrenArray = children.build();
        if (childrenArray.size() > 0) {
            if (sb.length() > 1) {
                sb.append(",\n");
            } else {
                sb.append('\n');
            }
            final Options childOpt = new Options(opt);
            childOpt.withInitialIndent(childOpt.initialIndent + newindent);
            indent(sb, childOpt.initialIndent);
            sb.append(quote(opt.childrenKey)).append(":");
            sb.append(prettyPrint(childrenArray, childOpt));
        }

        sb.append('}');
        return sb.toString();
    }

    /** Pretty-print a JSONArray */
    public String prettyPrint(JsonArray ja, Options opt) {
        int len = ja.size();
        if (len == 0) {
            return "[]";
        }
        int i;
        StringBuilder sb = new StringBuilder("[");
        if (len == 1) {
            sb.append(valueToString(ja.get(0), opt));
        } else {
            final int newindent = opt.initialIndent + opt.indent;
            if (opt.hasIndent()) {
                sb.append('\n');
            }
            for (i = 0; i < len; i += 1) {
                if (i > 0) {
                    sb.append(',');
                    if (opt.hasIndent()) {
                        sb.append('\n');
                    }
                }
                indent(sb, newindent);
                sb.append(valueToString(ja.get(i), opt));
            }
            if (opt.hasIndent()) {
                sb.append('\n');
            }
            indent(sb, opt.initialIndent);
        }
        sb.append(']');
        return sb.toString();
    }
}

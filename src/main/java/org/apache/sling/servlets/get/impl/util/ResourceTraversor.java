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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.apache.sling.api.request.RecursionTooDeepException;
import org.apache.sling.api.resource.Resource;

public class ResourceTraversor
{
    Map<Resource, List<Resource>> tree = new HashMap<>();

    private long count;

    private long maxResources;

    private final int maxRecursionLevels;

    private final JsonObjectBuilder startObject;

    private LinkedList<Resource> currentQueue;

    private LinkedList<Resource> nextQueue;

    private final Resource startResource;

	private boolean ecmaSupport;

    /** Create a ResourceTraversor, optionally limiting recursion and total number of resources
     * @param levels recursion levels limit, -1 means no limit
     * @param maxResources maximum number of resources to collect, ignored if levels == 1
     * @param resource the root resource to traverse
     */
    public ResourceTraversor(final int levels, final long maxResources, final Resource resource, boolean ecmaSupport) {
        this.maxResources = maxResources;
        this.maxRecursionLevels = levels;
        this.startResource = resource;
        currentQueue = new LinkedList<>();
        nextQueue = new LinkedList<>();
        this.ecmaSupport = ecmaSupport;
        this.startObject = this.adapt(resource);
    }

    /**
     * Recursive descent from startResource, collecting JSONObjects into
     * startObject. Throws a RecursionTooDeepException if the maximum number of
     * nodes is reached on a "deep" traversal (where "deep" === level greater
     * than 1).
     *
     * @return -1 if everything went fine, a positive value when the resource
     *            has more child nodes then allowed.
     * @throws RecursionTooDeepException
     */
    public int collectResources() throws RecursionTooDeepException {
        return collectChildren(startResource, 0);
    }

    /**
     * @param resource
     * @param currentLevel
     * @throws JSONException
     */
    private int collectChildren(final Resource resource, int currentLevel) {

        if (maxRecursionLevels == -1 || currentLevel < maxRecursionLevels) {
            final Iterator<Resource> children = resource.listChildren();
            List<Resource> childTree = tree.get(resource);
            if (childTree == null)
            {
                childTree = new ArrayList<>();
                tree.put(resource, childTree);
            }
            
            while (children.hasNext()) {
                count++;
                final Resource child = children.next();
                // SLING-2320: always allow enumeration of one's children;
                // DOS-limitation is for deeper traversals.
                if (count > maxResources && maxRecursionLevels != 1) {
                    return currentLevel;
                }
                nextQueue.addLast(child);
                childTree.add(child);
            }
        }

        // do processing only at first level to avoid unnecessary recursion
        if (currentLevel > 0) {
            return -1;
        }

        while (!currentQueue.isEmpty() || !nextQueue.isEmpty()) {
            if (currentQueue.isEmpty()) {
                currentLevel++;
                currentQueue = nextQueue;
                nextQueue = new LinkedList<>();
            }
            final int maxLevel = collectChildren(currentQueue.removeFirst(), currentLevel);
            if ( maxLevel != -1 ) {
                return maxLevel;
            }
        }
        return -1;
    }

    /**
     * Adapt a Resource to a JSON Object.
     *
     * @param resource The resource to adapt.
     * @return The JSON representation of the Resource
     * @throws JSONException
     */
    private JsonObjectBuilder adapt(final Resource resource) {
        return new JsonObjectCreator(resource,ecmaSupport).create();
    }

    public JsonObject getJSONObject() {
        return addChildren(startResource,startObject).build();
    }

    private JsonObjectBuilder addChildren(Resource resource,JsonObjectBuilder builder) {
    	List<Resource> children = tree.get(resource);
    	
        if (children != null)
        {
        	for (Resource child:children) {
                builder.add(child.getName(), addChildren(child, adapt(child)));
            }
        }

        return builder;
    }
}

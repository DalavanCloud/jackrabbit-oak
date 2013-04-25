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
package org.apache.jackrabbit.oak.plugins.index.nodetype;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.plugins.index.property.PropertyIndexLookup;
import org.apache.jackrabbit.oak.spi.query.Cursor;
import org.apache.jackrabbit.oak.spi.query.Cursors;
import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.query.QueryIndex;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * <code>NodeTypeIndex</code> implements a {@link QueryIndex} using
 * {@link PropertyIndexLookup}s on <code>jcr:primaryType</code> and
 * <code>jcr:mixinTypes</code> to evaluate a node type restriction on
 * {@link Filter}. The cost for this index is the sum of the costs of the
 * {@link PropertyIndexLookup} for queries on <code>jcr:primaryType</code> and
 * <code>jcr:mixinTypes</code>.
 */
class NodeTypeIndex implements QueryIndex, JcrConstants {

    @Override
    public double getCost(Filter filter, NodeState root) {
        if (!hasNodeTypeRestriction(filter)) {
            // this is not an appropriate index if the filter
            // doesn't have a node type restriction
            return Double.POSITIVE_INFINITY;
        }
        NodeTypeIndexLookup lookup = new NodeTypeIndexLookup(root);
        if (lookup.isIndexed(filter.getPath())) {
            return lookup.getCost(filter);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public Cursor query(Filter filter, NodeState root) {
        NodeTypeIndexLookup lookup = new NodeTypeIndexLookup(root);
        if (!hasNodeTypeRestriction(filter) || !lookup.isIndexed(filter.getPath())) {
            throw new IllegalStateException(
                    "NodeType index is used even when no index is available for filter " + filter);
        }
        return Cursors.newPathCursorDistinct(lookup.query(filter));
    }
    
    @Override
    public String getPlan(Filter filter, NodeState root) {
        return filter.toString();
    }

    @Override
    public String getIndexName() {
        return "nodeType";
    }
    
    //----------------------------< internal >----------------------------------

    private static boolean hasNodeTypeRestriction(Filter filter) {
        return !filter.matchesAllTypes();
    }

}

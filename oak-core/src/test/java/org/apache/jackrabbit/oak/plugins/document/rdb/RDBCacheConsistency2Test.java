/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.document.rdb;

import static org.apache.jackrabbit.oak.plugins.document.Collection.NODES;
import static org.apache.jackrabbit.oak.plugins.document.util.Utils.getIdFromPath;
import static org.apache.jackrabbit.oak.plugins.document.util.Utils.getKeyLowerLimit;
import static org.apache.jackrabbit.oak.plugins.document.util.Utils.getKeyUpperLimit;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.apache.jackrabbit.oak.plugins.document.AbstractRDBConnectionTest;
import org.apache.jackrabbit.oak.plugins.document.Document;
import org.apache.jackrabbit.oak.plugins.document.DocumentMK;
import org.apache.jackrabbit.oak.plugins.document.DocumentStore;
import org.apache.jackrabbit.oak.plugins.document.NodeDocument;
import org.apache.jackrabbit.oak.plugins.document.Revision;
import org.apache.jackrabbit.oak.plugins.document.UpdateOp;
import org.apache.jackrabbit.oak.plugins.document.util.Utils;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class RDBCacheConsistency2Test extends AbstractRDBConnectionTest {

    private static final long CACHE_SIZE = 128 * 1024;

    private static final int NUM_NODES = 50;

    private DocumentStore ds;

    private AtomicLong counter = new AtomicLong();

    @Override
    protected DocumentMK.Builder newBuilder(DataSource db) throws Exception {
        String prefix = "T" + Long.toHexString(System.currentTimeMillis());
        RDBOptions opt = new RDBOptions().tablePrefix(prefix).dropTablesOnClose(true);
        return new DocumentMK.Builder().clock(getTestClock())
                .memoryCacheSize(CACHE_SIZE)
                .setRDBConnection(dataSource, opt);
    }

    @Before
    public void before() {
        ds = mk.getDocumentStore();
    }

    /**
     * Perform concurrent update and query operations and check if the document
     * cache is consistent afterwards. See OAK-7101.
     */
    @Test
    public void cacheUpdate() throws Exception {
        Revision r = newRevision();
        List<UpdateOp> ops = Lists.newArrayList();
        List<String> ids = Lists.newArrayList();
        for (int i = 0; i < NUM_NODES; i++) {
            String id = Utils.getIdFromPath("/node-" + i);
            ids.add(id);
            UpdateOp op = new UpdateOp(id, true);
            op.set(Document.ID, id);
            NodeDocument.setLastRev(op, r);
            ops.add(op);
        }
        ds.remove(NODES, ids);
        ds.create(NODES, ops);

        for (int i = 0; i < 1000; i++) {
            Thread q = new Thread(new Runnable() {
                @Override
                public void run() {
                    queryDocuments();
                }
            });
            Thread u = new Thread(new Runnable() {
                @Override
                public void run() {
                    updateDocuments();
                }
            });
            q.start();
            u.start();
            q.join();
            u.join();
            for (int j = 0; j < NUM_NODES; j++) {
                NodeDocument doc = ds.getIfCached(NODES, Utils.getIdFromPath("/node-" + j));
                if (doc != null) {
                    assertEquals("Unexpected revision timestamp for " + doc.getId(),
                            counter.get(), doc.getLastRev().get(1).getTimestamp());
                }
            }
        }
    }

    private Revision newRevision() {
        return new Revision(counter.incrementAndGet(), 0, 1);
    }

    private void queryDocuments() {
        ds.query(NODES, getKeyLowerLimit("/"), getKeyUpperLimit("/"), 100);
    }

    private void updateDocuments() {
        UpdateOp op = new UpdateOp("foo", false);
        Revision r = newRevision();
        NodeDocument.setLastRev(op, r);
        List<UpdateOp> ops = Lists.newArrayList();
        for (int i = 0; i < NUM_NODES; i++) {
            ops.add(op.shallowCopy(getIdFromPath("/node-" + i)));
        }
        ds.createOrUpdate(NODES, ops);
    }
}

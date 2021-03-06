/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.examples;

import org.apache.atlas.AtlasClient;
import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.model.instance.AtlasClassification;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.lineage.AtlasLineageInfo;
import org.apache.atlas.model.lineage.AtlasLineageInfo.LineageDirection;
import org.apache.atlas.model.lineage.AtlasLineageInfo.LineageRelation;
import org.apache.atlas.web.integration.BaseResourceIT;
import org.codehaus.jettison.json.JSONException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

public class QuickStartV2IT extends BaseResourceIT {

    @BeforeClass
    public void runQuickStart() throws Exception {
        super.setUp();
        QuickStartV2.runQuickstart(new String[]{}, new String[]{"admin", "admin"});
    }

    @Test
    public void testDBIsAdded() throws Exception {
        AtlasEntity db = getDB(QuickStartV2.SALES_DB);
        Map<String, Object> dbAttributes = db.getAttributes();
        assertEquals(QuickStartV2.SALES_DB, dbAttributes.get("name"));
        assertEquals("sales database", dbAttributes.get("description"));
    }

    private AtlasEntity getDB(String dbName) throws AtlasServiceException, JSONException {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("name", dbName);
        AtlasEntity dbEntity = atlasClientV2.getEntityByAttribute(QuickStartV2.DATABASE_TYPE, attributes).getEntity();
        return dbEntity;
    }

    @Test
    public void testTablesAreAdded() throws AtlasServiceException, JSONException {
        AtlasEntity table = getTable(QuickStart.SALES_FACT_TABLE);
        verifySimpleTableAttributes(table);

        verifyDBIsLinkedToTable(table);

        verifyColumnsAreAddedToTable(table);

        verifyTrait(table);
    }

    private AtlasEntity getTable(String tableName) throws AtlasServiceException {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(AtlasClient.REFERENCEABLE_ATTRIBUTE_NAME, tableName);
        AtlasEntity tableEntity = atlasClientV2.getEntityByAttribute(QuickStartV2.TABLE_TYPE, attributes).getEntity();
        return tableEntity;
    }

    private AtlasEntity getProcess(String processName) throws AtlasServiceException {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(AtlasClient.REFERENCEABLE_ATTRIBUTE_NAME, processName);
        AtlasEntity processEntity = atlasClientV2.getEntityByAttribute(QuickStartV2.LOAD_PROCESS_TYPE, attributes).getEntity();
        return processEntity;
    }


    private void verifyTrait(AtlasEntity table) throws AtlasServiceException {
        AtlasClassification.AtlasClassifications classfications = atlasClientV2.getClassifications(table.getGuid());
        List<AtlasClassification> traits = classfications.getList();
        assertNotNull(traits.get(0).getTypeName());
    }

    private void verifyColumnsAreAddedToTable(AtlasEntity table) throws JSONException {
        Map<String, Object> tableAttributes = table.getAttributes();
        List<Map> columns = (List<Map>) tableAttributes.get("columns");
        assertEquals(4, columns.size());

        for (Map colMap : columns) {
            String colGuid = (String) colMap.get("guid");
            assertNotNull(UUID.fromString(colGuid));
        }
    }

    private void verifyDBIsLinkedToTable(AtlasEntity table) throws AtlasServiceException, JSONException {
        AtlasEntity db = getDB(QuickStartV2.SALES_DB);
        Map<String, Object> tableAttributes = table.getAttributes();
        Map dbFromTable = (Map) tableAttributes.get("db");
        assertEquals(db.getGuid(), dbFromTable.get("guid"));
    }

    private void verifySimpleTableAttributes(AtlasEntity table) throws JSONException {
        Map<String, Object> tableAttributes = table.getAttributes();
        assertEquals(QuickStartV2.SALES_FACT_TABLE, tableAttributes.get("name"));
        assertEquals("sales fact table", tableAttributes.get("description"));
    }

    @Test
    public void testProcessIsAdded() throws AtlasServiceException, JSONException {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(AtlasClient.REFERENCEABLE_ATTRIBUTE_NAME, QuickStartV2.LOAD_SALES_DAILY_PROCESS);
        AtlasEntity loadProcess = atlasClientV2.getEntityByAttribute(QuickStartV2.LOAD_PROCESS_TYPE, attributes).getEntity();

        Map loadProcessAttribs = loadProcess.getAttributes();
        assertEquals(QuickStartV2.LOAD_SALES_DAILY_PROCESS, loadProcessAttribs.get(AtlasClient.NAME));
        assertEquals("hive query for daily summary", loadProcessAttribs.get("description"));

        List inputs = (List) loadProcessAttribs.get("inputs");
        List outputs = (List) loadProcessAttribs.get("outputs");
        assertEquals(2, inputs.size());

        String salesFactTableId = getTableId(QuickStartV2.SALES_FACT_TABLE);
        String timeDimTableId = getTableId(QuickStartV2.TIME_DIM_TABLE);
        String salesFactDailyMVId = getTableId(QuickStartV2.SALES_FACT_DAILY_MV_TABLE);

        assertEquals(salesFactTableId, ((Map) inputs.get(0)).get("guid"));
        assertEquals(timeDimTableId, ((Map) inputs.get(1)).get("guid"));
        assertEquals(salesFactDailyMVId, ((Map) outputs.get(0)).get("guid"));
    }

    private String getTableId(String tableName) throws AtlasServiceException {
        return getTable(tableName).getGuid();
    }

    private String getProcessId(String processName) throws AtlasServiceException {
        return getProcess(processName).getGuid();
    }

    @Test
    public void testLineageIsMaintained() throws AtlasServiceException, JSONException {
        String salesFactTableId      = getTableId(QuickStartV2.SALES_FACT_TABLE);
        String timeDimTableId        = getTableId(QuickStartV2.TIME_DIM_TABLE);
        String salesFactDailyMVId    = getTableId(QuickStartV2.SALES_FACT_DAILY_MV_TABLE);
        String salesFactMonthlyMvId  = getTableId(QuickStartV2.SALES_FACT_MONTHLY_MV_TABLE);
        String salesDailyProcessId   = getProcessId(QuickStartV2.LOAD_SALES_DAILY_PROCESS);
        String salesMonthlyProcessId = getProcessId(QuickStartV2.LOAD_SALES_MONTHLY_PROCESS);

        AtlasLineageInfo inputLineage = atlasClientV2.getLineageInfo(salesFactDailyMVId, LineageDirection.BOTH, 0);
        List<LineageRelation> relations = new ArrayList<>(inputLineage.getRelations());
        Map<String, AtlasEntityHeader> entityMap = inputLineage.getGuidEntityMap();

        assertEquals(relations.size(), 5);
        assertEquals(entityMap.size(), 6);

        assertTrue(entityMap.containsKey(salesFactTableId));
        assertTrue(entityMap.containsKey(timeDimTableId));
        assertTrue(entityMap.containsKey(salesFactDailyMVId));
        assertTrue(entityMap.containsKey(salesDailyProcessId));
        assertTrue(entityMap.containsKey(salesFactMonthlyMvId));
        assertTrue(entityMap.containsKey(salesMonthlyProcessId));
    }

    @Test
    public void testViewIsAdded() throws AtlasServiceException, JSONException {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(AtlasClient.REFERENCEABLE_ATTRIBUTE_NAME, QuickStartV2.PRODUCT_DIM_VIEW);
        AtlasEntity view = atlasClientV2.getEntityByAttribute(QuickStartV2.VIEW_TYPE, attributes).getEntity();
        Map<String, Object> viewAttributes = view.getAttributes();
        assertEquals(QuickStartV2.PRODUCT_DIM_VIEW, viewAttributes.get(AtlasClient.NAME));

        String productDimId = getTable(QuickStartV2.PRODUCT_DIM_TABLE).getGuid();
        List inputTables = (List) viewAttributes.get("inputTables");
        Map inputTablesMap = (Map) inputTables.get(0);
        assertEquals(productDimId, inputTablesMap.get("guid"));
    }
}

/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ext.exasol.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.DBCFeatureNotSupportedException;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCRemoteInstance;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;

import java.sql.SQLException;

/**
 * ExasolExecutionContext
 */
public class ExasolExecutionContext extends JDBCExecutionContext implements DBCExecutionContextDefaults<DBSCatalog, ExasolSchema> {
    private static final Log log = Log.getLog(ExasolExecutionContext.class);

    private static final String GET_CURRENT_SCHEMA = "SELECT CURRENT_SCHEMA";
    private static final String SET_CURRENT_SCHEMA = "OPEN SCHEMA \"%s\"";

    private String activeSchemaName;

    ExasolExecutionContext(@NotNull JDBCRemoteInstance instance, String purpose) {
        super(instance, purpose);
    }

    @NotNull
    @Override
    public ExasolDataSource getDataSource() {
        return (ExasolDataSource) super.getDataSource();
    }

    @Nullable
    @Override
    public DBCExecutionContextDefaults getContextDefaults() {
        return this;
    }

    public String getActiveSchemaName() {
        return activeSchemaName;
    }

    @Override
    public DBSCatalog getDefaultCatalog() {
        return null;
    }

    @Override
    public ExasolSchema getDefaultSchema() {
        return activeSchemaName == null ? null : getDataSource().getSchemaCache().getCachedObject(activeSchemaName);
    }

    @Override
    public boolean supportsCatalogChange() {
        return false;
    }

    @Override
    public boolean supportsSchemaChange() {
        return true;
    }

    @Override
    public void setDefaultCatalog(DBRProgressMonitor monitor, DBSCatalog catalog, ExasolSchema schema) throws DBCException {
        throw new DBCFeatureNotSupportedException();
    }

    @Override
    public void setDefaultSchema(DBRProgressMonitor monitor, ExasolSchema schema) throws DBCException {
        final ExasolSchema oldSelectedEntity = getDefaultSchema();
        if (schema == null || oldSelectedEntity == schema) {
            return;
        }
        setCurrentSchema(monitor, schema);
        activeSchemaName = schema.getName();

        // Send notifications
        DBUtils.fireObjectSelectionChange(oldSelectedEntity, schema);
    }

    @Override
    public boolean refreshDefaults(DBRProgressMonitor monitor, boolean useBootstrapSettings) throws DBException {
        // Check default active schema
        try (JDBCSession session = openSession(monitor, DBCExecutionPurpose.META, "Query active schema")) {
            // Get active schema
            this.activeSchemaName = determineActiveSchema(session);
        } catch (Exception e) {
            throw new DBCException(e, getDataSource());
        }

        return true;
    }

    void setCurrentSchema(DBRProgressMonitor monitor, ExasolSchema object) throws DBCException {
        if (object == null) {
            log.debug("Null current schema");
            return;
        }
        try (JDBCSession session = openSession(monitor, DBCExecutionPurpose.UTIL, "Set active schema")) {
            JDBCUtils.executeSQL(session, String.format(SET_CURRENT_SCHEMA, object.getName()));
            this.activeSchemaName = object.getName();
        } catch (SQLException e) {
            throw new DBCException(e, getDataSource());
        }
    }

    private String determineActiveSchema(JDBCSession session)
        throws SQLException
    {
        // First try to get active schema from special register 'CURRENT SCHEMA'
        String defSchema = JDBCUtils.queryString(session, GET_CURRENT_SCHEMA);
        if (defSchema == null) {
            return null;
        }

        return defSchema.trim();
    }


}

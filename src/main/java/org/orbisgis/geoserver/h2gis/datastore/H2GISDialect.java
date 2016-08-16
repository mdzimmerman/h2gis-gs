/*
 * h2gis-gs is an extension to geoserver to connect H2GIS a spatial library 
 * that brings spatial support to the H2 database engine.
 *
 * h2gis-gs  is distributed under GPL 3 license. It is produced by the DECIDE
 * team of the Lab-STICC laboratory <http://www.labsticc.fr/> CNRS UMR 6285.
 *
 * Copyright (C) 2015-2016 Lab-STICC (CNRS UMR 6285)
 *
 * h2gis-gs  is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2gis-gs  is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2gis-gs. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.orbisgis.geoserver.h2gis.datastore;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.factory.Hints;
import org.geotools.jdbc.BasicSQLDialect;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.referencing.CRS;
import org.h2.value.ValueGeometry;
import org.h2gis.utilities.SFSUtilities;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

/**
 * Dialect to transform from to geotools feature model 
 *
 * @author Erwan Bocher, CNRS Atelier SIG
 * @author Nicolas Fortin, CNRS Atelier SIG
 * 
 */
public class H2GISDialect extends BasicSQLDialect {

    final static WKTReader wKTReader = new WKTReader();
    private static final Map<String,Class> TYPE_TO_CLASS = new HashMap<String,Class>();
    private static final Map<Class, String> CLASS_TO_TYPE = new HashMap<Class, String>();
    
    static{
            TYPE_TO_CLASS.put("GEOMETRY", Geometry.class);
            TYPE_TO_CLASS.put("POINT", Point.class);
            TYPE_TO_CLASS.put("POINTM", Point.class);
            TYPE_TO_CLASS.put("LINESTRING", LineString.class);
            TYPE_TO_CLASS.put("LINESTRINGM", LineString.class);
            TYPE_TO_CLASS.put("POLYGON", Polygon.class);
            TYPE_TO_CLASS.put("POLYGONM", Polygon.class);
            TYPE_TO_CLASS.put("MULTIPOINT", MultiPoint.class);            
            TYPE_TO_CLASS.put("MULTIPOINTM", MultiPoint.class);
            TYPE_TO_CLASS.put("MULTILINESTRING", MultiLineString.class);
            TYPE_TO_CLASS.put("MULTILINESTRINGM", MultiLineString.class);
            TYPE_TO_CLASS.put("MULTIPOLYGON", MultiPolygon.class);
            TYPE_TO_CLASS.put("MULTIPOLYGONM", MultiPolygon.class);
            TYPE_TO_CLASS.put("GEOMETRYCOLLECTION", GeometryCollection.class);
            TYPE_TO_CLASS.put("GEOMETRYCOLLECTIONM", GeometryCollection.class);
            TYPE_TO_CLASS.put("GEOGRAPHY", Geometry.class);    
            
            CLASS_TO_TYPE.put(Geometry.class, "GEOMETRY");
            CLASS_TO_TYPE.put(Point.class, "POINT");
            CLASS_TO_TYPE.put(LineString.class, "LINESTRING");
            CLASS_TO_TYPE.put(Polygon.class, "POLYGON");
            CLASS_TO_TYPE.put(MultiPoint.class, "MULTIPOINT");
            CLASS_TO_TYPE.put(MultiLineString.class, "MULTILINESTRING");
            CLASS_TO_TYPE.put(MultiPolygon.class, "MULTIPOLYGON");
            CLASS_TO_TYPE.put(GeometryCollection.class, "GEOMETRYCOLLECTION");
            CLASS_TO_TYPE.put(LinearRing.class, "LINEARRING");        
    };
    
    boolean functionEncodingEnabled = true;    
    
    boolean simplifyEnabled = true;
    
    @Override
    public boolean isAggregatedSortSupported(String function) {
       return "distinct".equalsIgnoreCase(function);
    }

    /**
     *
     * @param dataStore
     */
    public H2GISDialect(JDBCDataStore dataStore) {
        super(dataStore);
    }

    /**
     *
     * @return
     */
    public boolean isFunctionEncodingEnabled() {
        return functionEncodingEnabled;
    }

    @Override
    public void initializeConnection(Connection cx) throws SQLException {
        super.initializeConnection(cx);
    }

    @Override
    public boolean includeTable(String schemaName, String tableName,
            Connection cx) throws SQLException {
        if (tableName.equalsIgnoreCase("geometry_columns")) {
            return false;
        } else if (tableName.toLowerCase().startsWith("spatial_ref_sys")) {
            return false;
        } 
        return true;
    }
    

    @Override
    public Geometry decodeGeometryValue(GeometryDescriptor descriptor,
            ResultSet rs, String column, GeometryFactory factory, Connection cx)
            throws IOException, SQLException {
        Geometry geom = ValueGeometry.get(rs.getBytes(column)).getGeometry();
        if (geom == null) {
            return null;
        }
        return geom;
    }    
    

    @Override
    public void encodeGeometryColumn(GeometryDescriptor gatt, String prefix, int srid,
            StringBuffer sql) {
        encodeGeometryColumn(gatt, prefix, srid, null, sql);
    }

    @Override
    public void encodeGeometryColumn(GeometryDescriptor gatt, String prefix, int srid, Hints hints,
            StringBuffer sql) {

        boolean force2D = hints != null && hints.containsKey(Hints.FEATURE_2D)
                && Boolean.TRUE.equals(hints.get(Hints.FEATURE_2D));

        if (force2D) {
            sql.append("ST_AsBinary(ST_Force2D(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append("))");
        } else {
            sql.append("ST_AsBinary(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append(")");
        }        
    }    
    

    @Override
    public void encodeGeometryEnvelope(String tableName, String geometryColumn,
            StringBuffer sql) {
        sql.append("ST_AsText(");
        sql.append("ST_Extent(\"").append(geometryColumn).append("\"::geometry))");
    }    
   

    @Override
    public Envelope decodeGeometryEnvelope(ResultSet rs, int column,
            Connection cx) throws SQLException, IOException {
        try {
            String envelope = rs.getString(column);
            if (envelope != null){
                return wKTReader.read(envelope).getEnvelopeInternal();
            }
            else{
                // empty one
                return new Envelope();
            }
        } catch (ParseException e) {
            throw new IOException(
                    "Cannot create the bounding box", e);
        }
    }

    @Override
    public Class<?> getMapping(ResultSet columnMetaData, Connection cx)
            throws SQLException {
        String typeName = columnMetaData.getString("TYPE_NAME");

        if ("uuid".equalsIgnoreCase(typeName)) {
            return UUID.class;
        }

        if ("geometry".equalsIgnoreCase(typeName)) {
            return getGeometryClass(columnMetaData, cx);
        } else {
            return null;
        }
    }   
    
    @Override
    public Integer getGeometrySRID(String schemaName, String tableName,
            String columnName, Connection cx) throws SQLException {

        // first attempt, try with the geometry metadata
        Statement statement = null;
        ResultSet result = null;
        int srid = 0;
        try {
            if (schemaName == null)
                schemaName = "PUBLIC";
                        
            
            // try geometry_columns
            try {
                String sqlStatement = "SELECT SRID FROM GEOMETRY_COLUMNS WHERE " //
                        + "F_TABLE_SCHEMA = '" + schemaName + "' " //
                        + "AND F_TABLE_NAME = '" + tableName + "' " //
                        + "AND F_GEOMETRY_COLUMN = '" + columnName + "'";
    
                LOGGER.log(Level.FINE, "Geometry srid check; {0} ", sqlStatement);
                statement = cx.createStatement();
                result = statement.executeQuery(sqlStatement);
    
                if (result.next()) {
                    srid = result.getInt(1);
                }
            } catch(SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve information about " 
                        + schemaName + "." + tableName + "."  + columnName 
                        + " from the geometry_columns table, checking the first geometry instead", e);
            } finally {
                dataStore.closeSafe(result);
            }
            
            // fall back on inspection of the first geometry, assuming uniform srid (fair assumption
            // an unpredictable srid makes the table un-queriable)
            if(srid == 0) {
                String sqlStatement = "SELECT ST_SRID(\"" + columnName + "\") " +
                               "FROM \"" + schemaName + "\".\"" + tableName + "\" " +
                               "WHERE \"" + columnName + "\" IS NOT NULL " +
                               "LIMIT 1";
                result = statement.executeQuery(sqlStatement);
                if (result.next()) {
                    srid = result.getInt(1);
                }
            }
        } finally {
            dataStore.closeSafe(result);
            dataStore.closeSafe(statement);
        }

        return srid;
    }
    
    @Override
    public int getGeometryDimension(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {
        //first attempt, try with the geometry metadata
        Statement statement = null;
        ResultSet result = null;
        int dimension = 0;
        try {
            if (schemaName == null){
                schemaName = "PUBLIC";
            }
            
            // try geometry_columns
            try {
                String sqlStatement = "SELECT COORD_DIMENSION FROM GEOMETRY_COLUMNS WHERE " //
                        + "F_TABLE_SCHEMA = '" + schemaName + "' " //
                        + "AND F_TABLE_NAME = '" + tableName + "' " //
                        + "AND F_GEOMETRY_COLUMN = '" + columnName + "'";
    
                LOGGER.log(Level.FINE, "Geometry srid check; {0} ", sqlStatement);
                statement = cx.createStatement();
                result = statement.executeQuery(sqlStatement);
    
                if (result.next()) {
                    dimension = result.getInt(1);
                }
            } catch(SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve information about " 
                        + schemaName + "." + tableName + "."  + columnName 
                        + " from the geometry_columns table, checking the first geometry instead", e);
            } finally {
                dataStore.closeSafe(result);
            }
            
            // fall back on inspection of the first geometry, assuming uniform srid (fair assumption
            // an unpredictable srid makes the table un-queriable)
            if(dimension == 0) {
                String sqlStatement = "SELECT ST_DIMENSION(\"" + columnName + "\") " +
                               "FROM \"" + schemaName + "\".\"" + tableName + "\" " +
                               "WHERE " + columnName + " IS NOT NULL " +
                               "LIMIT 1";
                result = statement.executeQuery(sqlStatement);
                if (result.next()) {
                    dimension = result.getInt(1);
                }
            }
        } finally {
            dataStore.closeSafe(result);
            dataStore.closeSafe(statement);
        }

        return dimension;
    }

    @Override
    public String getSequenceForColumn(String schemaName, String tableName,
            String columnName, Connection cx) throws SQLException {

        String sequenceName = tableName + "_" + columnName + "_SEQUENCE";

        //sequence names have to be upper case to select values from them
        sequenceName = sequenceName.toUpperCase();
        Statement st = cx.createStatement();
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM INFORMATION_SCHEMA.SEQUENCES ");
            sql.append("WHERE SEQUENCE_NAME = '").append(sequenceName).append("'");

            dataStore.getLogger().fine(sql.toString());
            ResultSet rs = st.executeQuery(sql.toString());
            try {
                if (rs.next()) {
                    return sequenceName;
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }

    @Override
    public Object getNextSequenceValue(String schemaName, String sequenceName,
            Connection cx) throws SQLException {
        Statement st = cx.createStatement();
        try {
            String sql = "SELECT nextval('" + sequenceName + "')";

            dataStore.getLogger().fine(sql);
            ResultSet rs = st.executeQuery(sql);
            try {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }    
    
    @Override
    public Object getLastAutoGeneratedValue(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {
        
        Statement st = cx.createStatement();
        try {
            String sql = "SELECT lastval()";
            dataStore.getLogger().fine( sql);
            
            ResultSet rs = st.executeQuery( sql);
            try {
                if ( rs.next() ) {
                    return rs.getLong(1);
                }
            } 
            finally {
                dataStore.closeSafe(rs);
            }
        }
        finally {
            dataStore.closeSafe(st);
        }

        return null;
    }
    
    @Override
    public void registerClassToSqlMappings(Map<Class<?>, Integer> mappings) {
        super.registerClassToSqlMappings(mappings);
        // jdbc metadata for geom columns reports DATA_TYPE=1111=Types.OTHER
        mappings.put(Geometry.class, Types.OTHER);
        mappings.put(UUID.class, Types.OTHER);
    }

    @Override
    public void registerSqlTypeNameToClassMappings(
            Map<String, Class<?>> mappings) {
        super.registerSqlTypeNameToClassMappings(mappings);

        mappings.put("geometry", Geometry.class);
        mappings.put("text", String.class);
        mappings.put("int8", Long.class);
        mappings.put("int4", Integer.class);
        mappings.put("bool", Boolean.class);
        mappings.put("character", String.class);
        mappings.put("float8", Double.class);
        mappings.put("int", Integer.class);
        mappings.put("float4", Float.class);
        mappings.put("int2", Short.class);
        mappings.put("time", Time.class);
        mappings.put("timestamp", Timestamp.class);
        mappings.put("timestamptz", Timestamp.class);
        mappings.put("uuid", UUID.class);
        mappings.put("date", Date.class);
    }
    
    @Override
    public void registerSqlTypeToSqlTypeNameOverrides(
            Map<Integer, String> overrides) {
        overrides.put(Types.VARCHAR, "VARCHAR");
        overrides.put(Types.BOOLEAN, "BOOL");
    }

    @Override
    public String getGeometryTypeName(Integer type) {
        return "geometry";
    }

    @Override
    public void encodePrimaryKey(String column, StringBuffer sql) {
        encodeColumnName(column, sql);
        sql.append(" SERIAL PRIMARY KEY");
    }

    /**
     * Creates GEOMETRY_COLUMN registrations and spatial indexes for all
     * geometry columns
     * @param schemaName
     * @param featureType
     * @param cx
     * @throws java.sql.SQLException
     */
    @Override
    public void postCreateTable(String schemaName,
            SimpleFeatureType featureType, Connection cx) throws SQLException {
        schemaName = schemaName != null ? schemaName : "PUBLIC"; 
        String tableName = featureType.getName().getLocalPart();
        
        Statement st = null;
        try {
            st = cx.createStatement();

            // register all geometry columns in the database
            for (AttributeDescriptor att : featureType
                    .getAttributeDescriptors()) {
                if (att instanceof GeometryDescriptor) {
                    GeometryDescriptor gd = (GeometryDescriptor) att;
                    // lookup or reverse engineer the srid
                    int srid = 0;
                    if (gd.getUserData().get(JDBCDataStore.JDBC_NATIVE_SRID) != null) {
                        srid = (Integer) gd.getUserData().get(
                                JDBCDataStore.JDBC_NATIVE_SRID);
                    } else if (gd.getCoordinateReferenceSystem() != null) {
                        try {
                            Integer result = CRS.lookupEpsgCode(gd
                                    .getCoordinateReferenceSystem(), true);
                            if (result != null) {
                                srid = result;
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Error looking up the "
                                    + "epsg code for metadata "
                                    + "insertion, assuming 0", e);
                        }
                    }

                    // setup the dimension according to the geometry hints
                    int dimensions = 2;
                    if(gd.getUserData().get(Hints.COORDINATE_DIMENSION) != null) {
                        dimensions = (Integer) gd.getUserData().get(Hints.COORDINATE_DIMENSION);
                    }

                    // grab the geometry type
                    String geomType = CLASS_TO_TYPE.get(gd.getType().getBinding());
                    if (geomType == null) {
                        geomType = "GEOMETRY";
                    }

                    String sql = null;
                    if (dimensions > 3) {
                        throw new IllegalArgumentException("H2GIS only supports geometries with 2 and 3 dimensions, current value: " + dimensions);
                    }

                    sql = "ALTER TABLE \"" + schemaName + "\".\"" + tableName + "\" "
                            + "ALTER COLUMN \"" + gd.getLocalName() + "\" "
                            + "TYPE " + geomType + ";";
                            //" check st_srid(\"" + gd.getLocalName() +"\")="+ srid + ";";

                    LOGGER.fine(sql);
                    st.execute(sql);
                    
                    
                    // add a spatial index to the table
                    sql = 
                    "CREATE SPATIAL INDEX \"spatial_" + tableName // 
                            + "_" + gd.getLocalName().toLowerCase() + "\""// 
                            + " ON " //
                            + "\"" + schemaName + "\"" // 
                            + "." //
                            + "\"" + tableName + "\"" //
                            + " (" //
                            + "\"" + gd.getLocalName() + "\"" //
                            + ")";
                    LOGGER.fine(sql);
                    st.execute(sql);
                }
            }
            if (!cx.getAutoCommit()) {
                cx.commit();
            }
         } finally {
            dataStore.closeSafe(st);
        }
    }
    
    @Override
    public void postDropTable(String schemaName, SimpleFeatureType featureType, Connection cx)
            throws SQLException {
        //Nothing todo it's a view and a view is not editable.
    }

    @Override
    public void encodeGeometryValue(Geometry value, int dimension, int srid, StringBuffer sql)
            throws IOException {
    	if (value == null || value.isEmpty()) {
            sql.append("NULL");
        } else {
            if (value instanceof LinearRing) {
                //postgis does not handle linear rings, convert to just a line string
                value = value.getFactory().createLineString(((LinearRing) value).getCoordinateSequence());
            }            
            WKTWriter writer = new WKTWriter(dimension);
            String wkt = writer.write(value);
            sql.append("ST_GeomFromText('").append(wkt).append("', ").append(srid).append(")");
        }
    }
    

    @Override
    public FilterToSQL createFilterToSQL() {
        H2GISFilterToSQL sql = new H2GISFilterToSQL(this);
        sql.setFunctionEncodingEnabled(functionEncodingEnabled);
        return sql;
    }
    
    @Override
    public boolean isLimitOffsetSupported() {
        return true;
    }
    
    @Override
    public void applyLimitOffset(StringBuffer sql, int limit, int offset) {
        if(limit >= 0 && limit < Integer.MAX_VALUE) {
            sql.append(" LIMIT ").append(limit);
            if(offset > 0) {
                sql.append(" OFFSET ").append(offset);
            }
        } else if(offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }
    
    @Override
    public void encodeValue(Object value, Class type, StringBuffer sql) {
        if (byte[].class == type) {
            byte[] b = (byte[]) value;
            if (value != null) {
                //encode as hex string
                sql.append("'");
                for (int i = 0; i < b.length; i++) {
                    sql.append(Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1));
                }
                sql.append("'");
            } else {
                sql.append("NULL");
            }
        } else {
            super.encodeValue(value, type, sql);
        }

    }
    
    @Override
    public int getDefaultVarcharSize(){
        return -1;
    }

    @Override
    public String[] getDesiredTablesType() {
        return new String[]{"TABLE", "VIEW", "TABLE LINK", "EXTERNAL"};
    }

    /**
     * Return the corresponding Geometry.class
     *
     * @param columnMetaData
     * @param cx
     * @return
     */
    private Class<?> getGeometryClass(ResultSet columnMetaData, Connection cx) throws SQLException {
        StringBuilder sb = new StringBuilder("AND f_geometry_column  = '");
        sb.append(columnMetaData.getString("COLUMN_NAME"));
        sb.append("'");

        PreparedStatement ps = SFSUtilities.prepareInformationSchemaStatement(cx, columnMetaData.getString("TABLE_CAT"), columnMetaData.getString("TABLE_SCHEM"), columnMetaData.getString("TABLE_NAME"), "geometry_columns", sb.toString());
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            String gType = null;
            if (rs.next()) {
                gType = rs.getString("TYPE");
            }
            if (gType == null) {
                return Geometry.class;
            } else {
                Class geometryClass = TYPE_TO_CLASS.get(gType);
                if (geometryClass == null) {
                    geometryClass = Geometry.class;
                }
                return geometryClass;
            }

        } finally {
            dataStore.closeSafe(rs);
            dataStore.closeSafe(ps);
        }
    }
    
    public boolean isSimplifyEnabled() {
        return simplifyEnabled;
    }

    /**
     * Enables/disables usage of ST_Simplify geometry wrapping when 
     * the Query contains a geometry simplification hint
     * 
     * @param simplifyEnabled
     */
    public void setSimplifyEnabled(boolean simplifyEnabled) {
        this.simplifyEnabled = simplifyEnabled;
    }
    
    @Override
    protected void addSupportedHints(Set<Hints.Key> hints) {    
        if(isSimplifyEnabled()) {
            hints.add(Hints.GEOMETRY_SIMPLIFICATION);
        }
    }    
    
    /**
     * @param functionEncodingEnabled
     * @see h2GISDataStoreFactory#ENCODE_FUNCTIONS
     */
    public void setFunctionEncodingEnabled(boolean functionEncodingEnabled) {
        this.functionEncodingEnabled = functionEncodingEnabled;
    }

    @Override
    public void encodeGeometryColumnSimplified(GeometryDescriptor gatt, String prefix, int srid, StringBuffer sql, Double distance) {
        if (!isSimplifyEnabled()) {
            super.encodeGeometryColumnSimplified(gatt, prefix, srid, sql, distance);
        } else {
            sql.append("ST_ASBinary(ST_Simplify(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append(", ").append(distance).append("))");
        }
    }
    
}

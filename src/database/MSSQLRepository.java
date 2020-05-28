package database;

import resource.DBNode;
import resource.data.Row;
import resource.enums.AttributeType;
import resource.enums.ConstraintType;
import resource.implementation.Attribute;
import resource.implementation.AttributeConstraint;
import resource.implementation.Entity;
import resource.implementation.InformationResource;
import utils.Constants;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleToIntFunction;

public class MSSQLRepository implements Repository{

    private Connection conn;

    public MSSQLRepository() {}

    private void initConnection() throws SQLException, ClassNotFoundException {
        Class.forName("net.sourceforge.jtds.jdbc.Driver");
        conn = DriverManager.getConnection("jdbc:jtds:sqlserver://" + Constants.MSSQL_IP + "/" + Constants.MSSQL_DATABASE,
                                        Constants.MSSQL_USERNAME,
                                        Constants.MSSQL_PASSWORD);
    }

    private void closeConnection() {
        try {
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            conn = null;
        }
    }

    @Override
    public DBNode getSchema() {

        try {
            this.initConnection();

            DatabaseMetaData metaData = conn.getMetaData();
            InformationResource ir = new InformationResource("Baza");

            String tableType[] = {"TABLE"};
            ResultSet tables = metaData.getTables(conn.getCatalog(), "dbo", null, tableType);

            while (tables.next()) {

                String tableName = tables.getString("TABLE_NAME");
                if (tableName.equals("sysdiagrams")) continue;
                Entity newTable = new Entity(tableName, ir);
                ir.addChild(newTable);

                // System.out.println(tableName);

                ResultSet columns = metaData.getColumns(conn.getCatalog(), null, tableName, null);

                while (columns.next()) {

                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    //System.out.println(columnName + " " + columnType);
                    int columnSize = Integer.parseInt(columns.getString("COLUMN_SIZE"));

                    ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName);
                    ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);
                    String isNullable = columns.getString("IS_NULLABLE");
                    String hasDefault = columns.getString("COLUMN_DEF");

                    Attribute attribute = new Attribute(columnName, newTable, AttributeType.valueOf(columnType.toUpperCase()), columnSize);

                    while (primaryKeys.next()) {
                        String pkColumnName = primaryKeys.getString("COLUMN_NAME");
                        if (pkColumnName.equals(attribute.toString())) {
                            AttributeConstraint ac = new AttributeConstraint(pkColumnName, attribute, ConstraintType.PRIMARY_KEY);
                            attribute.addChild(ac);
                            newTable.addPrimaryKey(ac);
                        }
                    }

                    while (foreignKeys.next()) {
                        String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                        String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                        String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");

                        if (fkColumnName.equals(attribute.toString())) {
                            AttributeConstraint ac = new AttributeConstraint(pkColumnName, attribute, ConstraintType.FOREIGN_KEY);
                            attribute.addChild(ac);
                            newTable.addForeignKey(ac);
                        }
                    }

                    if (isNullable.equals("NO")) {
                        AttributeConstraint ac = new AttributeConstraint(ConstraintType.NOT_NULL.toString(), attribute, ConstraintType.NOT_NULL);
                        attribute.addChild(ac);
                    }

                    if (hasDefault != null) {
                        AttributeConstraint ac = new AttributeConstraint(ConstraintType.DEFAULT_VALUE.toString(), attribute, ConstraintType.DEFAULT_VALUE);
                        attribute.addChild(ac);
                    }

                    newTable.addChild(attribute);
                }

            }

            // kreiranje relacija

            for (DBNode i: ir.getChildren())
                if (i instanceof Entity) {
                    Entity ie = (Entity) i;
                    for (DBNode j: ir.getChildren())
                        if (j instanceof Entity) {
                            Entity je = (Entity) j;
                            if (ie.inRelation(je) || je.inRelation(ie)) {
                                ie.addRelation(je);
                                je.addRelation(ie);
                            }
                        }
                }

            return ir;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.closeConnection();
        }

        return null;
    }

    @Override
    public List<Row> get(String from) {

        List<Row> rows = new ArrayList<>();

        try {
            this.initConnection();

            String query = "SELECT * FROM " + from;
            PreparedStatement ps = conn.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Row row = new Row();
                row.setName(from);

                ResultSetMetaData rsmd = rs.getMetaData();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    row.addField(rsmd.getColumnName(i), rs.getString(i));
                }

                rows.add(row);

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.closeConnection();
        }

        return rows;
    }

    @Override
    public boolean insert(Entity entity, ArrayList<String> newValues) {

        try {
            this.initConnection();

            StringBuilder query = new StringBuilder();
            query.append("INSERT INTO ").append(entity.getName()).append(" VALUES (");

            for (int i = 0; i < newValues.size(); i++) {
                if (i > 0)
                    query.append(", ");

                if (newValues.get(i).equals("")) {
                    query.append("NULL");
                    continue;
                }

                AttributeType at =((Attribute) entity.getChildren().get(i)).getType();

                if (at == AttributeType.CHAR || at == AttributeType.VARCHAR || at == AttributeType.NVARCHAR || at == AttributeType.DATE || at == AttributeType.DATETIME) {
                    query.append("'").append(newValues.get(i)).append("'");
                    continue;
                }

                query.append(newValues.get(i));
            }

            query.append(")");

            System.out.println(query.toString());

            PreparedStatement ps = conn.prepareStatement(query.toString());
            ps.execute();

        } catch (Exception e) {
            //e.printStackTrace();
            return  false;
        } finally {
            this.closeConnection();
        }

        return true;
    }

    @Override
    public boolean update(Entity entity, ArrayList<String> newValues, Row row) {

        try {
            this.initConnection();

            ArrayList<AttributeConstraint> primaryKeys = entity.getPrimaryKeys();

            StringBuilder query = new StringBuilder();
            query.append("UPDATE ").append(entity.getName()).append(" SET ");

            int first = 0;

            for (int i = 0; i < newValues.size(); i++) {
                if (newValues.get(i).equals("")) {
                    continue;
                }
                if (first > 0)
                    query.append(", ");
                first++;

                query.append(((Attribute) entity.getChildren().get(i)).getName()).append(" = ");

                AttributeType at =((Attribute) entity.getChildren().get(i)).getType();

                if (at == AttributeType.CHAR || at == AttributeType.VARCHAR || at == AttributeType.NVARCHAR || at == AttributeType.DATE || at == AttributeType.DATETIME) {
                    query.append("'").append(newValues.get(i)).append("'");
                    continue;
                }

                query.append(newValues.get(i));
            }

            query.append(" WHERE ");

            first = 0;

            for (AttributeConstraint ac:primaryKeys) {
                if (first > 0)
                    query.append("AND ");
                first++;
                query.append(ac.getName()).append(" = ");
                AttributeType at = ((Attribute) ac.getParent()).getType();

                if (at == AttributeType.CHAR || at == AttributeType.VARCHAR || at == AttributeType.NVARCHAR || at == AttributeType.DATE || at == AttributeType.DATETIME) {
                    query.append("'").append(row.getFields().get(ac.getName())).append("'");
                    continue;
                }

                query.append(row.getFields().get(ac.getName()));
            }

            System.out.println(query.toString());

            PreparedStatement ps = conn.prepareStatement(query.toString());
            ps.execute();

        } catch (Exception e) {
            //e.printStackTrace();
            return  false;
        } finally {
            this.closeConnection();
        }

        return true;
    }

    @Override
    public void delete(Entity entity, ArrayList<String> attributeNames, ArrayList<String> attributeValues) {

        try {
            this.initConnection();

            StringBuilder query = new StringBuilder();
            query.append("DELETE FROM ").append(entity.getName()).append(" WHERE ");

            for (int i = 0; i < attributeNames.size(); i++) {

                if (isNumeric(attributeValues.get(i))) {
                    query.append(attributeNames.get(i)).append("=").append(attributeValues.get(i));
                } else {
                    query.append(attributeNames.get(i)).append(" LIKE '").append(attributeValues.get(i)).append("'");
                }

                if (i != attributeNames.size() - 1) {
                    query.append(" AND ");
                }
            }

            System.out.println(query.toString());
            PreparedStatement ps = conn.prepareStatement(query.toString());
            ps.execute();
            System.out.println("Success");

        } catch (Exception e) {
            System.out.println("Neuspesno");
        } finally {
            this.closeConnection();
        }

    }

    private boolean isNumeric(String num) {
        if (num == null) {
            return false;
        }
        try {
            double d = Double.parseDouble(num);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

}

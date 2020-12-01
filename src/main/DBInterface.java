package main;

import arc.util.Time;

import java.sql.*;
import java.util.HashMap;


public class DBInterface {
    public String table;
    public String key;
    public Connection conn = null;
    public HashMap<String, HashMap<String, Object>> entries = new HashMap<String, HashMap<String, Object>>();

    public boolean locked = false;
    public boolean multipleUsers = false;

    public DBInterface(String database){
        this(database, false);
    }

    public DBInterface(String database, boolean multipleUsers){
        this.table = database;
        this.multipleUsers = multipleUsers;
        this.locked = multipleUsers;
    }

    public void connect(String db){
        // SQLite connection string
        String url = "jdbc:sqlite:" + db;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("Connected to database successfully");
        // Initialise primaryKey
        setKey();
    }

    public void connect(Connection c){
        conn = c;
        setKey();
    }

    private void setKey(){
        String sql = "pragma table_info('" + table + "');";
        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);
            key = rs.getString(2);
            rs.close();
        } catch (SQLException ignored) {
        }
    }

    public boolean hasRow(String key){
        String sql;
        sql = "SELECT " + this.key + " FROM " + table + " WHERE " + this.key + " = '" + key + "'";
        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            // loop through the result set
            boolean isThere = rs.getString(this.key).length() != 0;
            rs.close();
            return isThere;
        } catch (SQLException ignored) {
        }
        return false;
    }

    public void addRow(String key){
        String sql = "INSERT INTO " + this.table + "(" + this.key + ") VALUES('" + key + "')";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadRow(String key){
        HashMap<String, Object> vals = new HashMap<String, Object>();

        if(!hasRow(key)) addRow(key);

        String sql = "SELECT * FROM " + this.table + " where " + this.key + " = '" + key + "'";

        Statement stmt;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            ResultSetMetaData rsmd = rs.getMetaData();
            for(int i = 1; i <= rsmd.getColumnCount(); i++){ // ONE INDEXED? REALLY?
                vals.put(rsmd.getColumnName(i),rs.getObject(rsmd.getColumnName(i)));
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        entries.put(key, vals);
        if(multipleUsers){
            locked = false;
            Time.runTask(60, () -> {locked = true;}); // 1 second delay
        }

    }
    public void saveRow(String key){
        saveRow(key, true);
    }

    public void saveRow(String key, boolean drop){
        try {
            HashMap<String, Object> vals = entries.get(key);
            String sql = "UPDATE " + this.table + " SET ";
            try {

                int c = 0;
                for (Object _key : vals.keySet()) {
                    if (vals.get(_key) == null) {
                        continue;
                    }
                    if (c > 0) {
                        sql += ",";
                    }
                    c++;
                    if (vals.get(_key) instanceof String) {
                        sql += _key + " = '" + vals.get(_key) + "'";
                    } else if (vals.get(_key) instanceof Boolean) {
                        sql += _key + " = " + ((boolean) vals.get(_key) ? 1 : 0);
                    } else {
                        sql += _key + " = " + vals.get(_key);
                    }

                }
                sql += " WHERE " + this.key + " = '" + key + "'";

                Statement stmt = conn.createStatement();
                stmt.executeUpdate(sql);
                stmt.close();
            } catch (SQLException e) {
                System.out.println("Query: " + sql);
                e.printStackTrace();
            }

            if(drop) entries.remove(key);
        }catch(NullPointerException e){
            e.printStackTrace();
        }

    }

    public ResultSet customQuery(String query){
        try {
            return conn.prepareStatement(query).executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void customUpdate(String update){
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(update);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Large scale modifications

    public void setColumn(String col, Object value){
        // Sets an entire column to the provided value
        try {
            String sql = "UPDATE " + this.table + " SET " + col + " = ";
            if (value instanceof String) {
                sql += "'" + value + "'";
            } else if (value instanceof Boolean) {
                sql += ((boolean) value ? 1 : 0);
            } else {
                sql += value;
            }
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void safePut(String primaryKey, String key, Object value){
        this.safePut(primaryKey, key, value, false);
    }

    public void safePut(String primaryKey, String key, Object value, boolean overwrite){
        if(locked && !overwrite){
            throw new RuntimeException("Accessing locked entries");
        }else{
            this.entries.get(primaryKey).put(key, value);
        }
    }

    public Object safeGet(String primaryKey, String key){
        return this.entries.get(primaryKey).get(key);

    }
}

package dao;

import logger.Logger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

public abstract class ImportServiceDao implements ImportService {
    protected JdbcTemplate template;
    private JdbcTemplate mysqlTemplate;

    public void setTemplate(DataSource dataSource, DataSource mysqlDataSource) {
        template = new JdbcTemplate(dataSource);
        mysqlTemplate = new JdbcTemplate(mysqlDataSource);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public void readData(boolean dropOldTable) {
        Map<String, String> tablesEncode = getTablesEncode();
        for (String table : tablesEncode.keySet()) {
            //删除原有数据表
            if (dropOldTable)
                dropTable(table);
            //设置编码
            try {
                template.getDataSource().getConnection().setClientInfo("charset", tablesEncode.get(table));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            readTable(table);
        }

    }

    private void readTable(String table) {
        SqlRowSet res = template.queryForRowSet("SELECT * FROM " + table);
        SqlRowSetMetaData metaData = res.getMetaData();

        int length = metaData.getColumnCount();
        String[] names = metaData.getColumnNames();
        String[] values = new String[length];

        //创建表
        createIfNotExists(table, names);

        //未预处理的SQL插入语句
        String sql = getInsertSQL(table, names);
        while (res.next()) {
            for (int i = 0; i < length; i++) {
                values[i] = res.getString(names[i]);
            }
            if (update(sql, values))
                Logger.add_data_sql(sql, values);
            else Logger.add_dup_data_sql(sql, values);
        }

    }

    private String getInsertSQL(String table, String[] names) {
        StringBuilder insertSQL = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();

        insertSQL.append("INSERT INTO ").append(table).append(" (");
        placeholders.append("VALUES (");
        //设置占位符
        for (String name : names) {
            insertSQL.append(name).append(", ");
            placeholders.append("?, ");
        }
        insertSQL.delete(insertSQL.length() - 2, insertSQL.length()).append(") ");
        placeholders.delete(placeholders.length() - 2, placeholders.length()).append(");");
        insertSQL.append(placeholders);
        return insertSQL.toString();
    }

    private void createIfNotExists(String table, String[] names) {
        StringBuilder createSQL = new StringBuilder();
        StringBuilder primaryKey = new StringBuilder();
        createSQL.append("CREATE TABLE IF NOT EXISTS ").append(table).append("(");
        for (String name : names) {
            createSQL.append(name).append(" VARCHAR(20) NOT NULL, ");
            primaryKey.append(name).append(", ");
        }
        createSQL.append("PRIMARY KEY (").append(primaryKey).delete(createSQL.length() - 2, createSQL.length()).append("));");
        createTable(createSQL.toString());
    }

    private void dropTable(String table) {
        try {
            mysqlTemplate.update("DROP TABLE " + table);
        } catch (Exception ignored) {

        }

    }

    private boolean update(String sql, String[] strings) {
        try {
            mysqlTemplate.update(sql, (Object[]) strings);
            return true;
        } catch (DuplicateKeyException e) {
            return false;

        }

    }

    private void createTable(String sql) {
        mysqlTemplate.update(sql);
    }

    protected abstract Map<String, String> getTablesEncode();
}

package software.aws.aurora.dsql.dbeaver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.postgresql.PostgreDataSourceProvider;
import org.jkiss.dbeaver.ext.postgresql.PostgreUtils;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDataSource;
import org.jkiss.dbeaver.ext.postgresql.model.impls.PostgreServerType;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceProvider;
import org.jkiss.dbeaver.model.DBPDataSourceURLProvider;
import org.jkiss.dbeaver.model.DatabaseURL;
import org.jkiss.dbeaver.model.access.DBAAuthModel;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.connection.DBPDriverConfigurationType;
import org.jkiss.dbeaver.model.preferences.DBPPropertyDescriptor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

public class DSQLDataSourceProvider implements DBPDataSourceProvider<PostgreDataSource> {

    private final PostgreDataSourceProvider postgreDataSourceProvider = new PostgreDataSourceProvider();

    @Override
    public void init(DBPPlatform platform) {
        postgreDataSourceProvider.init(platform);
    }

    @Override
    public Class<? extends PostgreDataSource> getDataSourceClass() {
        return PostgreDataSource.class;
    }

    @Override
    public DBPPropertyDescriptor[] getConnectionProperties(
            DBRProgressMonitor monitor,
            DBPDriver driver,
            DBPDataSourceContainer dataSourceContainer,
            DBPConnectionConfiguration connectionInfo
    ) throws DBException {
        return postgreDataSourceProvider.getConnectionProperties(
                monitor,
                driver,
                dataSourceContainer,
                connectionInfo
        );
    }

    /**
     * Compatibility overload used by DBeaver 24.3.5 through 26.0.x.
     */
    public DBPPropertyDescriptor[] getConnectionProperties(
            DBRProgressMonitor monitor,
            DBPDriver driver,
            DBPConnectionConfiguration connectionInfo
    ) throws DBException {
        try {
            Method method = PostgreDataSourceProvider.class.getMethod(
                    "getConnectionProperties",
                    DBRProgressMonitor.class,
                    DBPDriver.class,
                    DBPConnectionConfiguration.class
            );
            return (DBPPropertyDescriptor[]) method.invoke(
                    postgreDataSourceProvider,
                    monitor,
                    driver,
                    connectionInfo
            );
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof DBException dbException) {
                throw dbException;
            }
            throw new DBException("Unable to read PostgreSQL driver properties", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new DBException("Unable to read PostgreSQL driver properties", e);
        }
    }

    @Override
    public long getFeatures() {
        return FEATURE_CATALOGS | FEATURE_SCHEMAS;
    }

    @Override
    public PostgreDataSource openDataSource(
            DBRProgressMonitor monitor,
            DBPDataSourceContainer container
    ) throws DBException {
        return new PostgreDataSource(monitor, container);
    }

    @Override
    public String getConnectionURL(
            DBPDriver driver,
            DBPConnectionConfiguration connectionInfo
    ) throws DBException {
        DBPConnectionConfiguration configToUse = connectionInfo;
        String databaseName = connectionInfo.getDatabaseName();

        if (databaseName != null && databaseName.contains("/")) {
            configToUse = new DBPConnectionConfiguration(connectionInfo);
            configToUse.setDatabaseName(databaseName.replace("/", "%2F"));
        }

        DBAAuthModel<?> authModel = configToUse.getAuthModel();

        if (authModel instanceof DBPDataSourceURLProvider sourceURLProvider) {
            String connectionURL = sourceURLProvider.getConnectionURL(driver, configToUse);
            if (CommonUtils.isNotEmpty(connectionURL)) {
                return connectionURL;
            }
        }

        if (configToUse.getConfigurationType() == DBPDriverConfigurationType.URL) {
            return configToUse.getUrl();
        }

        PostgreServerType serverType = PostgreUtils.getServerType(driver);

        if (serverType.supportsCustomConnectionURL()) {
            return DatabaseURL.generateUrlByTemplate(driver, configToUse);
        }

        StringBuilder url = new StringBuilder("jdbc:aws-dsql:postgresql://");
        String hostName = configToUse.getHostName();
        if (!CommonUtils.isEmpty(hostName)) {
            url.append(hostName);
        } else {
            // Handle missing hostname appropriately
            throw new IllegalArgumentException("Hostname is required for DSQL connection");
        }

        if (!CommonUtils.isEmpty(configToUse.getHostPort())) {
            url.append(":").append(configToUse.getHostPort());
        }

        url.append("/");

        if (!CommonUtils.isEmpty(configToUse.getDatabaseName())) {
            url.append(configToUse.getDatabaseName());
        }
        return url.toString();
    }

    /**
     * Compatibility method used by DBeaver 24.3.5 through 26.0.x.
     */
    public boolean providesDriverClasses() {
        return true;
    }

    @Override
    public boolean providesDriverClasses(DBPDriver driver) {
        return true;
    }
}

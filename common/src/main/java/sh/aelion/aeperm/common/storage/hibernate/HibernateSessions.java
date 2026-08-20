package sh.aelion.aeperm.common.storage.hibernate;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import sh.aelion.aeperm.common.storage.entity.GroupEntity;
import sh.aelion.aeperm.common.storage.entity.GroupNodeEntity;
import sh.aelion.aeperm.common.storage.entity.HistoryEntity;
import sh.aelion.aeperm.common.storage.entity.UserEntity;
import sh.aelion.aeperm.common.storage.entity.UserNodeEntity;
import sh.aelion.aeperm.common.storage.entity.UserTempGroupEntity;
import sh.aelion.sql.Dialect;

import javax.sql.DataSource;

public final class HibernateSessions {

    private HibernateSessions() {
    }

    public static SessionFactory create(DataSource dataSource, Dialect dialect) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                .applySetting(AvailableSettings.DIALECT, dialect == Dialect.POSTGRES
                        ? PostgreSQLDialect.class.getName()
                        : MariaDBDialect.class.getName())
                .applySetting(AvailableSettings.STATEMENT_BATCH_SIZE, 50)
                .applySetting(AvailableSettings.ORDER_INSERTS, true)
                .applySetting(AvailableSettings.ORDER_UPDATES, true)
                .applySetting(AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, 32)
                .applySetting(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, true)
                .applySetting(AvailableSettings.PREFERRED_UUID_JDBC_TYPE, dialect == Dialect.POSTGRES ? "UUID" : "CHAR")
                .applySetting(AvailableSettings.SHOW_SQL, false)
                .build();
        try {
            return new MetadataSources(registry)
                    .addAnnotatedClass(UserEntity.class)
                    .addAnnotatedClass(GroupEntity.class)
                    .addAnnotatedClass(UserTempGroupEntity.class)
                    .addAnnotatedClass(UserNodeEntity.class)
                    .addAnnotatedClass(GroupNodeEntity.class)
                    .addAnnotatedClass(HistoryEntity.class)
                    .buildMetadata()
                    .buildSessionFactory();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }
}

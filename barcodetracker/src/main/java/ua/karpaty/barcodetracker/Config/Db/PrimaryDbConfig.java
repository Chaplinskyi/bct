package ua.karpaty.barcodetracker.Config.Db;

// (Імпорти залишаються ті самі, що й минулого разу)
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import ua.karpaty.barcodetracker.Entity.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager",
        basePackages = {"ua.karpaty.barcodetracker.Repository"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "ua\\.karpaty\\.barcodetracker\\.Repository\\.Frozen\\..*"
        )
)
public class PrimaryDbConfig {

    // 1. Властивості для ОСНОВНОЇ БД
    @Primary
    @Bean
    // === ЗМІНА ТУТ: Читаємо 'spring.datasource', а не 'spring.datasource.primary' ===
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    // 2. ОСНОВНЕ джерело даних (без змін)
    @Primary
    @Bean
    public DataSource primaryDataSource(@Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    // 3. ОСНОВНИЙ EntityManager (без змін, він все ще використовує .packages(...))
    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource) {

        return builder
                .dataSource(dataSource)
                .packages(
                        Barcode.class,
                        ImportBatch.class,
                        LocationHistory.class,
                        MaterialMaster.class,
                        StatusHistory.class,
                        User.class
                )
                .persistenceUnit("primary")
                .build();
    }

    // 4. ОСНОВНИЙ менеджер транзакцій (без змін)
    @Primary
    @Bean
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
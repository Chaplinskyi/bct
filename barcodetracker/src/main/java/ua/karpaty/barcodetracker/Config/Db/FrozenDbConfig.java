package ua.karpaty.barcodetracker.Config.Db;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "frozenEntityManagerFactory", // Важливо
        transactionManagerRef = "frozenTransactionManager",   // Важливо
        basePackages = {"ua.karpaty.barcodetracker.Repository.Frozen"} // Сканувати репозиторії ТІЛЬКИ в цій папці
)
public class FrozenDbConfig {

    // 1. Налаштовуємо властивості для "замороженої" БД
    @Bean
    @ConfigurationProperties("spring.datasource.frozen")
    public DataSourceProperties frozenDataSourceProperties() {
        return new DataSourceProperties();
    }

    // 2. Створюємо саме джерело даних (DataSource)
    @Bean
    public DataSource frozenDataSource(@Qualifier("frozenDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    // 3. Створюємо EntityManager, який буде працювати з нашими "замороженими" Entity
    @Bean
    public LocalContainerEntityManagerFactoryBean frozenEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("frozenDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("ua.karpaty.barcodetracker.Entity.Frozen") // Сканувати Entity ТІЛЬКИ в цій папці
                .persistenceUnit("frozen")
                .build();
    }

    // 4. Створюємо менеджер транзакцій для цієї БД
    @Bean
    public PlatformTransactionManager frozenTransactionManager(
            @Qualifier("frozenEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
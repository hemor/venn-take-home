package com.emmanuel.venn;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DatabaseCredentialsTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsWithTheConfiguredLogin() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getUserName()).isEqualToIgnoringCase("user");
        }
    }
}

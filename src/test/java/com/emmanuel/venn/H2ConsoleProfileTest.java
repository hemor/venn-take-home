package com.emmanuel.venn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

class H2ConsoleProfileTest {

    @Nested
    @SpringBootTest
    class WithoutProfile {

        @Autowired
        ApplicationContext context;

        @Test
        void consoleIsNotExposedByDefault() {
            assertThat(context.getBeanNamesForType(ServletRegistrationBean.class)).doesNotContain("h2Console");
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    class WithDevProfile {

        @Autowired
        ApplicationContext context;

        @Test
        void consoleIsExposedInTheDevProfile() {
            assertThat(context.getBeanNamesForType(ServletRegistrationBean.class)).contains("h2Console");
        }
    }
}

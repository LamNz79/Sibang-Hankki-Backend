package com.sibang.hankki.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DemoOwnerSeedConfigurationTest {

    @Test
    void onlyDemoProfileAddsTheDemoFlywayLocation() throws IOException {
        String defaultProperties = resource("application.properties");
        String demoProperties = resource("application-demo.properties");
        String demoSeed = resource("db/demo/R__seed_prototype_owner.sql");

        assertThat(defaultProperties).doesNotContain("classpath:db/demo");
        assertThat(demoProperties)
                .contains("classpath:db/migration")
                .contains("classpath:db/demo");
        assertThat(demoSeed)
                .contains("'owner'", "'OWNER'", "'ACTIVE'", "'00000000-0000-0000-0000-000000000002'", "'$2a$")
                .doesNotContain("demo-owner-password");
    }

    private String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}

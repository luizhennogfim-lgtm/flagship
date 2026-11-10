package io.flagship.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void removesAccentsAndSymbols() {
        assertThat(SlugGenerator.slugify("Acme Ltda. São Paulo!")).isEqualTo("acme-ltda-sao-paulo");
    }

    @Test
    void fallsBackWhenNothingUsableRemains() {
        assertThat(SlugGenerator.slugify("!!!")).isEqualTo("org");
    }

    @Test
    void addsSuffixWhenSlugIsTaken() {
        String slug = SlugGenerator.unique("Acme", Set.of("acme")::contains);

        assertThat(slug).startsWith("acme-").isNotEqualTo("acme");
    }
}

package com.example.batch.sample.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.context.annotation.Bean;

import com.example.batch.core.item.ValidatingItemProcessorBase;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * {@link ValidatingItemProcessorBase} 派生の具象型を {@code @StepScope} の Bean にする書き方を検出するルールのテスト。
 *
 * <p>{@code process()} は final のため、Bean メソッドの戻り値が具象型だとスコーププロキシがクラスのプロキシになり、
 * 実行時に NullPointerException になる。戻り値は {@code ItemProcessor<I, O>} にする（docs/architecture.md §13）。</p>
 */
class StepScopeBeanRuleTest {

    private static final ArchRule RULE = noMethods()
            .that().areAnnotatedWith(Bean.class)
            .and().areAnnotatedWith(StepScope.class)
            .should().haveRawReturnType(assignableTo(ValidatingItemProcessorBase.class))
            .because("process() が final のため、具象型のスコーププロキシは NullPointerException になる。"
                    + "戻り値は ItemProcessor<I, O> にする");

    @Test
    void StepScopeのBeanメソッドがValidatingItemProcessorBase派生の具象型を返さない() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.batch");

        RULE.check(classes);
    }

    @Test
    void 派生の具象型を返すStepScopeのBeanメソッド＿違反として検出される() {
        var classes = new ClassFileImporter().importClasses(ViolatingConfig.class);

        assertThatThrownBy(() -> RULE.check(classes)).isInstanceOf(AssertionError.class);
    }

    @Test
    void ItemProcessor型を返すStepScopeのBeanメソッド＿違反として検出されない() {
        var classes = new ClassFileImporter().importClasses(CompliantConfig.class);

        RULE.check(classes);
    }

    /** 違反の見本（テスト専用）。 */
    static class ViolatingConfig {
        @Bean
        @StepScope
        SampleProcessor sampleProcessor() {
            return new SampleProcessor();
        }
    }

    /** 正しい書き方の見本（テスト専用）。 */
    static class CompliantConfig {
        @Bean
        @StepScope
        ItemProcessor<String, String> sampleProcessor() {
            return new SampleProcessor();
        }
    }

    /** {@link ValidatingItemProcessorBase} を継承した見本の Processor。 */
    static class SampleProcessor extends ValidatingItemProcessorBase<String, String> {
        SampleProcessor() {
            super(null);
        }

        @Override
        protected String doProcess(String item) {
            return item;
        }
    }
}

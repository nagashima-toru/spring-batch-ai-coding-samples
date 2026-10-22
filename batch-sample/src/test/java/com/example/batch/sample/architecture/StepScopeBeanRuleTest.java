package com.example.batch.sample.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
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
 * {@link ValidatingItemProcessorBase} 派生を {@code @StepScope} の Bean にする書き方を検出するルールのテスト。
 *
 * <p>{@code process()} は final のため、スコーププロキシがクラスのプロキシになると、
 * 実行時に未初期化のプロキシ本体で実行されて NullPointerException になる。戻り値は
 * {@code ItemProcessor<I, O>} にする（docs/architecture.md §13）。</p>
 *
 * <p>検出するもの：</p>
 * <ul>
 *   <li>{@code @Bean} かつ {@code @StepScope} のメソッドの戻り値が、派生の具象型になっているもの</li>
 *   <li>派生クラスそのものに、クラスレベルの {@code @StepScope} が付いているもの
 *       （既定の {@code proxyMode} がクラスのプロキシのため）</li>
 * </ul>
 *
 * <p>どちらのルールも {@code allowEmptyShould} を無効にし、検査対象が1件も取り込めなかった時は
 * 成功にせず失敗させる。パッケージの指定の誤りなどで、何も検査せずに成功することを防ぐため。</p>
 *
 * <p>次は検出の対象外とする。</p>
 * <ul>
 *   <li>{@code ValidatingItemProcessorBase} 以外の、final の public メソッドを持つ基底クラス
 *       （一般の final メソッドは判定できない。docs/decisions.md の
 *       「{@code @StepScope} で生成する Bean のクラスは final にせず、継承元の public メソッドも final にしない」で防ぐ）</li>
 *   <li>XML などアノテーション以外で定義した Bean</li>
 * </ul>
 */
class StepScopeBeanRuleTest {

    private static final ArchRule RULE = noMethods()
            .that().areAnnotatedWith(Bean.class)
            .and().areAnnotatedWith(StepScope.class)
            .should().haveRawReturnType(assignableTo(ValidatingItemProcessorBase.class))
            .because("process() が final のため、具象型のスコーププロキシは NullPointerException になる。"
                    + "戻り値は ItemProcessor<I, O> にする")
            .allowEmptyShould(false);

    private static final ArchRule CLASS_RULE = noClasses()
            .that().areAssignableTo(ValidatingItemProcessorBase.class)
            .should().beAnnotatedWith(StepScope.class)
            .because("クラスレベルの @StepScope は既定の proxyMode がクラスのプロキシのため、"
                    + "final の process() が未初期化のプロキシ本体で実行され NullPointerException になる")
            .allowEmptyShould(false);

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

    @Test
    void StepScopeのクラスにValidatingItemProcessorBase派生が無い() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.batch");

        CLASS_RULE.check(classes);
    }

    @Test
    void クラスにStepScopeを付けた派生クラス＿違反として検出される() {
        var classes = new ClassFileImporter().importClasses(StepScopedProcessor.class);

        assertThatThrownBy(() -> CLASS_RULE.check(classes)).isInstanceOf(AssertionError.class);
    }

    @Test
    void 検査対象が取り込めなかった場合＿何も検査せずに成功しない() {
        var classes = new ClassFileImporter().importClasses(NoBeanConfig.class);

        assertThatThrownBy(() -> RULE.check(classes)).isInstanceOf(AssertionError.class);
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

    /** {@code @Bean} メソッドを持たない見本（テスト専用）。 */
    static class NoBeanConfig {
        String noBean() {
            return "";
        }
    }

    /** クラスレベルの {@code @StepScope} を付けた見本の Processor（テスト専用）。 */
    @StepScope
    static class StepScopedProcessor extends ValidatingItemProcessorBase<String, String> {
        StepScopedProcessor() {
            super(null);
        }

        @Override
        protected String doProcess(String item) {
            return item;
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

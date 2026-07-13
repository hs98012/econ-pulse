package com.econpulse.term.application;

import com.econpulse.term.api.dto.TermCreateRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "econpulse.seed.enabled", havingValue = "true")
public class LocalTermSeedRunner implements CommandLineRunner {

    private final EconomicTermService economicTermService;
    private final ConfigurableApplicationContext applicationContext;
    private final boolean exitAfterRun;

    public LocalTermSeedRunner(
            EconomicTermService economicTermService,
            ConfigurableApplicationContext applicationContext,
            @Value("${econpulse.seed.exit-after-run:false}") boolean exitAfterRun
    ) {
        this.economicTermService = economicTermService;
        this.applicationContext = applicationContext;
        this.exitAfterRun = exitAfterRun;
    }

    @Override
    public void run(String... args) {
        seedTerms();

        if (exitAfterRun) {
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }

    private void seedTerms() {
        for (TermCreateRequest request : sampleTerms()) {
            try {
                economicTermService.create(request);
                log.info("Seeded local economic term: {}", request.name());
            } catch (DuplicateTermNameException exception) {
                log.info("Skipped existing local economic term: {}", request.name());
            }
        }
    }

    private List<TermCreateRequest> sampleTerms() {
        return List.of(
                new TermCreateRequest("기준금리", "중앙은행이 금융시장에 적용하는 기준이 되는 금리", List.of("정책금리", "base rate")),
                new TermCreateRequest("환율", "한 나라 통화와 다른 나라 통화의 교환 비율", List.of("외환시세", "exchange rate")),
                new TermCreateRequest("물가상승률", "상품과 서비스의 전반적인 가격 수준이 오른 비율", List.of("인플레이션율", "inflation rate")),
                new TermCreateRequest("국내총생산", "일정 기간 한 나라 안에서 생산된 최종 재화와 서비스의 시장 가치", List.of("GDP", "gross domestic product")),
                new TermCreateRequest("소비자물가지수", "가계가 구입하는 상품과 서비스 가격 변동을 나타내는 지수", List.of("CPI", "consumer price index")),
                new TermCreateRequest("양적완화", "중앙은행이 자산을 매입해 시중 유동성을 공급하는 통화정책", List.of("QE", "quantitative easing")),
                new TermCreateRequest("채권", "정부나 기업이 자금을 빌리며 발행하는 채무 증서", List.of("bond", "fixed income")),
                new TermCreateRequest("주가수익비율", "주가를 주당순이익으로 나눈 투자 지표", List.of("PER", "price earnings ratio")),
                new TermCreateRequest("경기침체", "경제 활동이 전반적으로 위축되는 상태", List.of("불황", "recession")),
                new TermCreateRequest("무역수지", "상품 수출액과 수입액의 차이", List.of("trade balance", "수출입차"))
        );
    }
}

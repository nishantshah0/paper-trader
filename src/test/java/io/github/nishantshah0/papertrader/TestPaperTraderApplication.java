package io.github.nishantshah0.papertrader;

import org.springframework.boot.SpringApplication;

public class TestPaperTraderApplication {

    public static void main(String[] args) {
        SpringApplication.from(PaperTraderApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

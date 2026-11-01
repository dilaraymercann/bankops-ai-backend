package com.bankops;

import org.springframework.boot.SpringApplication;

public class TestBankOpsApplication {

	public static void main(String[] args) {
		SpringApplication.from(BankOpsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

package com.example.pcaExamAnalyze;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: boots the full Spring context on the in-memory H2 'dev' profile
 * so it runs without a live Supabase connection.
 */
@SpringBootTest
@ActiveProfiles("test")
class PcaExamAnalyzeApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.example.pcaExamAnalyze.config;
import com.example.pcaExamAnalyze.service.ClassPackageService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class ClassPackageSeed implements ApplicationRunner {
    private final ClassPackageService service;
    public ClassPackageSeed(ClassPackageService service) { this.service = service; }
    @Override public void run(ApplicationArguments args) throws Exception { service.seed(); }
}

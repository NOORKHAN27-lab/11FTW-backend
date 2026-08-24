package com.elevenftw.service;

import com.elevenftw.dto.CreateReportRequest;
import com.elevenftw.entity.Report;
import com.elevenftw.entity.User;
import com.elevenftw.exception.NotFoundException;
import com.elevenftw.repository.ReportRepository;
import com.elevenftw.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void fileReport(Long reporterId, CreateReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reason(request.reason())
                .details(request.details())
                .build();
        reportRepository.save(report);
    }
}

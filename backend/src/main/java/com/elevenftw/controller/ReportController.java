package com.elevenftw.controller;

import com.elevenftw.dto.CreateReportRequest;
import com.elevenftw.dto.MessageResponse;
import com.elevenftw.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse fileReport(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateReportRequest request
    ) {
        reportService.fileReport(userId, request);
        return new MessageResponse("Thanks — we've received your report and will take a look.");
    }
}

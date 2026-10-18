package com.jev.filter.controller;

import com.jev.filter.dto.FilterRequest;
import com.jev.filter.dto.FilterResponse;
import com.jev.filter.service.FilterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/filter")
public class FilterController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterController.class);

    private final FilterService filterService;

    public FilterController(FilterService filterService) {
        this.filterService = filterService;
    }

    @PostMapping
    public FilterResponse filter(@Valid @RequestBody FilterRequest request) {
        LOGGER.info("Live chat received | author={}", request.author());
        return filterService.filter(request);
    }
}

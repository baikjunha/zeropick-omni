package com.zeropick.possyncservice.controller;

import com.zeropick.possyncservice.status.SyncStatusTracker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/pos-sync-service")
public class SyncStatusController {

    private final SyncStatusTracker tracker;

    public SyncStatusController(SyncStatusTracker tracker) {
        this.tracker = tracker;
    }

    /** 재고 동기화 현황 — 관리자 화면이 폴링한다. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return tracker.snapshot();
    }
}

package com.zeropick.commerceservice.web;

import com.zeropick.commerceservice.dto.CommerceDtos.LoginRequest;
import com.zeropick.commerceservice.dto.CommerceDtos.LoginResponse;
import com.zeropick.commerceservice.dto.CommerceDtos.MemberCreate;
import com.zeropick.commerceservice.dto.CommerceDtos.MemberResponse;
import com.zeropick.commerceservice.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/commerce-service/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public ResponseEntity<MemberResponse> join(@Valid @RequestBody MemberCreate req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.join(req));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return memberService.login(req);
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long id) {
        return memberService.get(id);
    }
}

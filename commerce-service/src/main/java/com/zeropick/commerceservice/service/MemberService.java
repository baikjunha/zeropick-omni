package com.zeropick.commerceservice.service;

import com.zeropick.commerceservice.dto.CommerceDtos.LoginRequest;
import com.zeropick.commerceservice.dto.CommerceDtos.LoginResponse;
import com.zeropick.commerceservice.dto.CommerceDtos.MemberCreate;
import com.zeropick.commerceservice.dto.CommerceDtos.MemberResponse;
import com.zeropick.commerceservice.entity.Member;
import com.zeropick.commerceservice.repository.MemberRepository;
import com.zeropick.commerceservice.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MemberResponse join(MemberCreate req) {
        if (memberRepository.existsByEmail(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다");
        }
        Member member = memberRepository.save(
                new Member(req.email(), passwordEncoder.encode(req.password()), req.name()));
        return MemberResponse.of(member);
    }

    public LoginResponse login(LoginRequest req) {
        Member member = memberRepository.findByEmail(req.email())
                .filter(m -> passwordEncoder.matches(req.password(), m.getPassword()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED",
                        "이메일 또는 비밀번호가 올바르지 않습니다"));
        return new LoginResponse(member.getId(), member.getName(), null);
    }

    public MemberResponse get(Long id) {
        return memberRepository.findById(id)
                .map(MemberResponse::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "없는 회원입니다"));
    }
}

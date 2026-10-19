package com.chatapp.controller;

import com.chatapp.dto.AddMemberRequest;
import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.CreateGroupRequest;
import com.chatapp.dto.MemberResponse;
import com.chatapp.dto.StartConversationRequest;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.service.ConversationService;
import com.chatapp.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMemberRepository memberRepository;
    private final NotificationService notificationService;

    public ConversationController(ConversationService conversationService,
                                  ConversationMemberRepository memberRepository,
                                  NotificationService notificationService) {
        this.conversationService = conversationService;
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return conversationService.listForUser(principal.getUsername());
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal UserDetails principal) {
        return conversationService.get(id, principal.getUsername());
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> startDirect(@Valid @RequestBody StartConversationRequest request,
                                                            @AuthenticationPrincipal UserDetails principal) {
        ConversationResponse response =
                conversationService.startDirectConversation(principal.getUsername(), request.username());
        notifyMembers(response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                            @AuthenticationPrincipal UserDetails principal) {
        ConversationResponse response =
                conversationService.createGroup(principal.getUsername(), request.name(), request.usernames());
        notifyMembers(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/members")
    public ConversationResponse addMember(@PathVariable("id") Long id,
                                          @Valid @RequestBody AddMemberRequest request,
                                          @AuthenticationPrincipal UserDetails principal) {
        ConversationResponse response =
                conversationService.addMember(id, principal.getUsername(), request.username());
        notifyMembers(response);
        return response;
    }

    @DeleteMapping("/{id}/members/{username}")
    public ResponseEntity<Void> removeMember(@PathVariable("id") Long id,
                                             @PathVariable("username") String username,
                                             @AuthenticationPrincipal UserDetails principal) {
        List<String> before = memberRepository.findUsernamesByConversationId(id);
        conversationService.removeMember(id, principal.getUsername(), username);

        // the removed person loses the chat, everyone else just refreshes
        List<String> remaining = before.stream()
                .filter(name -> !name.equalsIgnoreCase(username))
                .toList();
        notificationService.pushConversationEvent("REMOVED", id, List.of(username));
        notificationService.pushConversationEvent("CHANGED", id, remaining);
        return ResponseEntity.noContent().build();
    }

    // tells every member their conversation list changed, so it shows up / refreshes live
    private void notifyMembers(ConversationResponse response) {
        List<String> usernames = response.members().stream().map(MemberResponse::username).toList();
        notificationService.pushConversationEvent("CHANGED", response.id(), usernames);
    }
}

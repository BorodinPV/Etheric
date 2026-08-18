package com.etheric.service;

import com.etheric.model.MembershipAssignmentView;
import com.etheric.repository.ClientRepository;
import com.etheric.repository.UserClientMembershipRepository;
import com.etheric.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class UserClientMembershipService {

    @Inject
    UserClientMembershipRepository membershipRepository;

    @Inject
    ClientRepository clientRepository;

    @Inject
    UserRepository userRepository;

    public Uni<Boolean> isMember(String userId, String clientId) {
        if (userId == null || userId.isBlank()) {
            return Uni.createFrom().item(false);
        }
        try {
            return membershipRepository.isMember(UUID.fromString(userId), clientId);
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(false);
        }
    }

    public Uni<List<MembershipAssignmentView>> listClientsForUser(UUID userId) {
        return membershipRepository.findClientIdsForUser(userId)
                .flatMap(assignedIds -> {
                    Set<String> assigned = new HashSet<>(assignedIds);
                    return clientRepository.findAllClients()
                            .map(clients -> clients.stream()
                                    .map(client -> new MembershipAssignmentView(
                                            client.clientId,
                                            client.clientName + " (" + client.clientId + ")",
                                            assigned.contains(client.clientId)))
                                    .toList());
                });
    }

    public Uni<List<MembershipAssignmentView>> listUsersForClient(String clientId) {
        return membershipRepository.findUserIdsForClient(clientId)
                .flatMap(assignedIds -> {
                    Set<UUID> assigned = new HashSet<>(assignedIds);
                    return userRepository.findAllUsers()
                            .map(users -> users.stream()
                                    .map(user -> new MembershipAssignmentView(
                                            user.id.toString(),
                                            user.username + (user.email != null ? " (" + user.email + ")" : ""),
                                            assigned.contains(user.id)))
                                    .toList());
                });
    }

    public Uni<AdminServiceResult<Void>> replaceUserClients(UUID userId, List<String> clientIds) {
        List<String> normalized = normalizeClientIds(clientIds);
        return validateAllClientsExist(normalized).flatMap(error -> {
            if (error != null) {
                return Uni.createFrom().item(AdminServiceResult.badRequest("invalid_request", error));
            }
            return membershipRepository.replaceUserClients(userId, normalized)
                    .replaceWith(AdminServiceResult.ok(null));
        });
    }

    public Uni<AdminServiceResult<Void>> replaceClientUsers(String clientId, List<UUID> userIds) {
        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item(AdminServiceResult.notFound("not_found", "Client not found"));
            }
            List<UUID> normalized = userIds == null ? List.of() : userIds.stream().distinct().toList();
            return membershipRepository.replaceClientUsers(clientId, normalized)
                    .replaceWith(AdminServiceResult.ok(null));
        });
    }

    public Uni<Void> ensureMembership(UUID userId, String clientId) {
        return membershipRepository.ensureMembership(userId, clientId);
    }

    private static List<String> normalizeClientIds(List<String> clientIds) {
        List<String> normalized = new ArrayList<>();
        if (clientIds == null) {
            return normalized;
        }
        for (String clientId : clientIds) {
            if (clientId != null && !clientId.isBlank()) {
                normalized.add(clientId.trim());
            }
        }
        return normalized.stream().distinct().toList();
    }

    private Uni<String> validateAllClientsExist(List<String> clientIds) {
        if (clientIds.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return validateClientAt(clientIds, 0);
    }

    private Uni<String> validateClientAt(List<String> clientIds, int index) {
        if (index >= clientIds.size()) {
            return Uni.createFrom().nullItem();
        }
        String clientId = clientIds.get(index);
        return clientRepository.findByClientId(clientId).flatMap(opt -> {
            if (opt.isEmpty()) {
                return Uni.createFrom().item("Unknown client_id: " + clientId);
            }
            return validateClientAt(clientIds, index + 1);
        });
    }
}

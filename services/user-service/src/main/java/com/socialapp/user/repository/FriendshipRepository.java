package com.socialapp.user.repository;

import com.socialapp.user.entity.Friendship;
import com.socialapp.user.entity.FriendshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    Optional<Friendship> findByRequesterIdAndAddresseeId(String requesterId, String addresseeId);

    boolean existsByRequesterIdAndAddresseeId(String requesterId, String addresseeId);

    List<Friendship> findByRequesterIdAndStatus(String requesterId, FriendshipStatus status);

    List<Friendship> findByAddresseeIdAndStatus(String addresseeId, FriendshipStatus status);

    Page<Friendship> findByAddresseeIdAndStatus(String addresseeId, FriendshipStatus status, Pageable pageable);

    Optional<Friendship> findByRequesterIdAndAddresseeIdAndStatus(String requesterId, String addresseeId, FriendshipStatus status);

    @Query("select f from Friendship f where f.status = com.socialapp.user.entity.FriendshipStatus.ACCEPTED "
            + "and (f.requesterId = :userId or f.addresseeId = :userId)")
    Page<Friendship> findAcceptedFriendships(@Param("userId") String userId, Pageable pageable);

    @Query("select f from Friendship f where f.status = com.socialapp.user.entity.FriendshipStatus.ACCEPTED "
            + "and (f.requesterId = :userId or f.addresseeId = :userId)")
    List<Friendship> findAcceptedFriendships(@Param("userId") String userId);

    @Query("select f from Friendship f where f.status = com.socialapp.user.entity.FriendshipStatus.ACCEPTED "
            + "and ((f.requesterId = :userA and f.addresseeId = :userB) or (f.requesterId = :userB and f.addresseeId = :userA))")
    Optional<Friendship> findAcceptedBetween(@Param("userA") String userA, @Param("userB") String userB);

    @Query("select f from Friendship f where (f.requesterId = :userA and f.addresseeId = :userB) "
            + "or (f.requesterId = :userB and f.addresseeId = :userA)")
    Optional<Friendship> findAnyBetween(@Param("userA") String userA, @Param("userB") String userB);

    @Query("select f from Friendship f where f.status = com.socialapp.user.entity.FriendshipStatus.PENDING "
            + "and (f.requesterId = :userId or f.addresseeId = :userId)")
    List<Friendship> findPendingInvolving(@Param("userId") String userId);
}

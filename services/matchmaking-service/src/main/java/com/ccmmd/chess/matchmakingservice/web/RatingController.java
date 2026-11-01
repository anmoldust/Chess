package com.ccmmd.chess.matchmakingservice.web;

import com.ccmmd.chess.matchmakingservice.dto.RatingChangeDto;
import com.ccmmd.chess.matchmakingservice.dto.RatingDto;
import com.ccmmd.chess.matchmakingservice.service.RatingService;
import com.ccmmd.chess.platform.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingService ratings;

    public RatingController(RatingService ratings) {
        this.ratings = ratings;
    }

    @GetMapping("/me")
    public RatingDto me(Authentication authentication) {
        return ratings.get(requireSignedIn(CurrentUser.id(authentication)));
    }

    @GetMapping("/leaderboard")
    public List<RatingDto> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        return ratings.leaderboard(limit);
    }

    @GetMapping("/{userId}")
    public RatingDto byUser(@PathVariable String userId) {
        return ratings.get(userId);
    }

    /** A player's rating movements, for the progression list on a profile. */
    @GetMapping("/{userId}/history")
    public List<RatingChangeDto> history(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        return ratings.history(userId, limit);
    }

    private static String requireSignedIn(String userId) {
        if (userId == null) {
            throw new AccessDeniedException("Sign in to see your rating");
        }
        return userId;
    }
}

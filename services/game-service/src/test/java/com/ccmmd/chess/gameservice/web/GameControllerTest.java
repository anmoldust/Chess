package com.ccmmd.chess.gameservice.web;

import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.service.GameEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identity comes from a verified JWT here, so the tests authenticate with a real
 * {@code JwtAuthenticationToken} rather than a mock user — which is what the service reads.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Captures broadcasts so a test can assert a mutation reached the game's topic. */
    @MockBean
    private GameEventPublisher publisher;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String userId, String name) {
        return jwt().jwt(builder -> builder.subject(userId).claim("name", name));
    }

    private String createGame(String userId, String mode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games")
                        .with(asUser(userId, "Player " + userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"" + mode + "\",\"preferredColor\":\"WHITE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("creating a game without a token returns 401")
    void anonymousCreateRejected() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"HUMAN_VS_HUMAN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token holder can create a game, and it is seated on their subject")
    void createGame() throws Exception {
        mockMvc.perform(post("/api/games")
                        .with(asUser("user-rajat", "Rajat"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"HUMAN_VS_HUMAN\",\"preferredColor\":\"WHITE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_OPPONENT"))
                .andExpect(jsonPath("$.whitePlayer").value("Rajat"))
                .andExpect(jsonPath("$.whiteUserId").value("user-rajat"));
    }

    @Test
    @DisplayName("anyone may read a game, so play can be watched")
    void anonymousCanReadGame() throws Exception {
        String id = createGame("user-spec", "HUMAN_VS_HUMAN");

        mockMvc.perform(get("/api/games/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("an illegal move returns 422 problem detail")
    void illegalMoveReturns422() throws Exception {
        String id = createGame("user-mover", "HUMAN_VS_AI");

        mockMvc.perform(post("/api/games/" + id + "/move")
                        .with(asUser("user-mover", "Mover"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"e2\",\"to\":\"e5\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Illegal move"));
    }

    @Test
    @DisplayName("moving out of turn returns 403")
    void outOfTurnMoveReturns403() throws Exception {
        String id = createGame("user-white", "HUMAN_VS_HUMAN");

        mockMvc.perform(post("/api/games/" + id + "/join").with(asUser("user-black", "Black")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/" + id + "/move")
                        .with(asUser("user-black", "Black"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"e7\",\"to\":\"e5\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unknown game returns 404")
    void unknownGameReturns404() throws Exception {
        mockMvc.perform(get("/api/games/nope")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("open games can be listed without a token")
    void listOpenGamesIsPublic() throws Exception {
        createGame("user-lister", "HUMAN_VS_HUMAN");

        mockMvc.perform(get("/api/games").param("status", GameStatus.WAITING_FOR_OPPONENT.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING_FOR_OPPONENT"));
    }

    @Test
    @DisplayName("a REST draw offer is broadcast, so the opponent sees it without refreshing")
    void drawOfferIsBroadcast() throws Exception {
        String id = createGame("user-white", "HUMAN_VS_HUMAN");
        mockMvc.perform(post("/api/games/" + id + "/join").with(asUser("user-black", "Black")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/" + id + "/draw").with(asUser("user-white", "White")))
                .andExpect(status().isOk());

        ArgumentCaptor<GameDto> sent = ArgumentCaptor.forClass(GameDto.class);
        verify(publisher, atLeastOnce()).gameUpdated(sent.capture());
        assertThat(sent.getAllValues()).anyMatch(game -> game.drawOfferBy() != null);
    }

    @Test
    @DisplayName("a takeback request is broadcast, and accepting it undoes the move")
    void takebackIsBroadcastAndUndoes() throws Exception {
        String id = createGame("user-white", "HUMAN_VS_HUMAN");
        mockMvc.perform(post("/api/games/" + id + "/join").with(asUser("user-black", "Black")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/games/" + id + "/move")
                        .with(asUser("user-white", "White"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"e2\",\"to\":\"e4\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/" + id + "/takeback").with(asUser("user-white", "White")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.takebackOfferBy").value("WHITE"));

        ArgumentCaptor<GameDto> sent = ArgumentCaptor.forClass(GameDto.class);
        verify(publisher, atLeastOnce()).gameUpdated(sent.capture());
        assertThat(sent.getAllValues()).anyMatch(game -> game.takebackOfferBy() != null);

        mockMvc.perform(post("/api/games/" + id + "/takeback").with(asUser("user-black", "Black")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves").isEmpty());
    }
}

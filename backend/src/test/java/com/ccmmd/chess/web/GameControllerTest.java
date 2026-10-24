package com.ccmmd.chess.web;

import com.ccmmd.chess.domain.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession register(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"" + name + "\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createGame(MockHttpSession session, String mode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"" + mode + "\",\"preferredColor\":\"WHITE\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("registering creates an account and establishes a session")
    void registerCreatesAccount() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new-player@example.com\",\"password\":\"password123\",\"displayName\":\"New\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("new-player@example.com"))
                .andExpect(jsonPath("$.displayName").value("New"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("a weak or invalid registration is rejected")
    void weakRegistrationRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    @DisplayName("a duplicate email is rejected with 409")
    void duplicateEmailRejected() throws Exception {
        register("dupe@example.com", "Dupe");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"password\":\"password123\",\"displayName\":\"Other\"}")
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("login with the wrong password returns 401")
    void badLoginRejected() throws Exception {
        register("login-check@example.com", "Login");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login-check@example.com\",\"password\":\"wrong-password\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("me reports anonymous before login and the account after")
    void meReflectsAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        MockHttpSession session = register("who-am-i@example.com", "Who");
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("who-am-i@example.com"));
    }

    @Test
    @DisplayName("mutating without a session returns 401")
    void anonymousCreateRejected() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"HUMAN_VS_HUMAN\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("mutating without a CSRF token is rejected")
    void missingCsrfRejected() throws Exception {
        MockHttpSession session = register("csrf-check@example.com", "Csrf");
        mockMvc.perform(post("/api/games")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"HUMAN_VS_HUMAN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an authenticated user can create a game")
    void createGameAuthenticated() throws Exception {
        MockHttpSession session = register("creator@example.com", "Creator");
        mockMvc.perform(post("/api/games")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"HUMAN_VS_HUMAN\",\"preferredColor\":\"WHITE\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_OPPONENT"))
                .andExpect(jsonPath("$.whitePlayer").value("Creator"))
                .andExpect(jsonPath("$.whiteUserId").exists());
    }

    @Test
    @DisplayName("an illegal move returns 422 problem detail")
    void illegalMoveReturns422() throws Exception {
        MockHttpSession session = register("mover@example.com", "Mover");
        String id = createGame(session, "HUMAN_VS_AI");

        mockMvc.perform(post("/api/games/" + id + "/move")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"e2\",\"to\":\"e5\"}")
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.title").value("Illegal move"));
    }

    @Test
    @DisplayName("moving out of turn in a two-player game returns 403")
    void outOfTurnMoveReturns403() throws Exception {
        MockHttpSession white = register("white-side@example.com", "White");
        MockHttpSession black = register("black-side@example.com", "Black");
        String id = createGame(white, "HUMAN_VS_HUMAN");

        mockMvc.perform(post("/api/games/" + id + "/join").session(black).with(csrf()))
                .andExpect(status().isOk());

        // White is to move; black may not.
        mockMvc.perform(post("/api/games/" + id + "/move")
                        .session(black)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"e7\",\"to\":\"e5\"}")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anyone may read a game, so play can be watched")
    void anonymousCanReadGame() throws Exception {
        MockHttpSession session = register("spectate@example.com", "Spec");
        String id = createGame(session, "HUMAN_VS_HUMAN");
        mockMvc.perform(get("/api/games/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("an unknown game returns 404")
    void unknownGameReturns404() throws Exception {
        mockMvc.perform(get("/api/games/nope")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("listed games can be filtered by status")
    void listGamesFilteredByStatus() throws Exception {
        MockHttpSession session = register("lister@example.com", "Lister");
        createGame(session, "HUMAN_VS_HUMAN");

        mockMvc.perform(get("/api/games").param("status", GameStatus.WAITING_FOR_OPPONENT.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING_FOR_OPPONENT"));
    }

    @Test
    @DisplayName("logout clears the session")
    void logoutClearsSession() throws Exception {
        MockHttpSession session = register("bye@example.com", "Bye");
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }
}

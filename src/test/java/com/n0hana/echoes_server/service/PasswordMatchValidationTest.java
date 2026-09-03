package com.n0hana.echoes_server.service;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.n0hana.echoes_server.controller.AuthController;

@SpringBootTest
@AutoConfigureMockMvc    
@ActiveProfiles("test")
public class PasswordMatchValidationTest {

    @Autowired
    private AuthController authController;
    
    @Autowired
    private MockMvc mockMvc;


    @Test
    public void passwordAndConfirmPasswordMatch() throws Exception {
        this.mockMvc.perform(
            MockMvcRequestBuilders
                .post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{ " +  
                        "\"name\":\"enrico\", " + 
                        "\"email\":\"enrico8@teste.com\", " + 
                        "\"password\":\"Enrico@123\", " + 
                        "\"confirmPassword\":\"Enrico@123\"" + 
                    "}"
                )
            ).andExpect(status().isOk());
    }

    @Test
    public void passwordAndConfirmPasswordDontMatch() throws Exception {
        this.mockMvc.perform(
            MockMvcRequestBuilders
                .post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{ " +  
                        "\"name\":\"enrico\", " + 
                        "\"email\":\"enrico8@teste.com\", " + 
                        "\"password\":\"Enrico@123\", " + 
                        "\"confirmPassword\":\"Enrico@321\"" + 
                    "}"
                )
            ).andExpect(status().is(400));
    }
    
}

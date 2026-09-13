package com.n0hana.echoes_server.user;

import java.util.UUID;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("MANAGER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Manager extends User {

    private UUID institutionId;

    @Override
    public UserRole getUserRole() {
        return UserRole.MANAGER;
    }
}

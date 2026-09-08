package com.n0hana.echoes_server.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.user.UserRepository;

@Service
public class AuthService implements UserDetailsService {

  @Autowired
  public UserRepository userRepository;

  /**
   * Carrega dados do usuário pelo {@code UserModel.email}.
   *
   * @param username - Email do usuário.
   * */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return userRepository.findUserByEmail(username);
  }


}

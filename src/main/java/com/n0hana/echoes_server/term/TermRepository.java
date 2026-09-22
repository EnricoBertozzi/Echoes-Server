package com.n0hana.echoes_server.term;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.term.model.TermModel;

@Repository
public interface TermRepository extends JpaRepository<TermModel, Long> {
}

package com.n0hana.echoes_server.animal.dto;

import com.n0hana.echoes_server.animal.model.ScenarioModel;

/**
 * ScenarioDTO
 */
public record ScenarioDTO(
		String name,
		String description,
		String audioUrl) {

	public ScenarioModel toModel() {
		return ScenarioModel.builder()
				.name(name)
				.description(description)
				.audioUrl(audioUrl)
				.build();
	}
}
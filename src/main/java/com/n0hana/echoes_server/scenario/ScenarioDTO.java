package com.n0hana.echoes_server.scenario;

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
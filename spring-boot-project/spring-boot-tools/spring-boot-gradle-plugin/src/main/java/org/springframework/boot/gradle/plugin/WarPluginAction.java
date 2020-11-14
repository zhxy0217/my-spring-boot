/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.gradle.plugin;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.TaskProvider;

import org.springframework.boot.gradle.tasks.bundling.BootWar;

/**
 * {@link Action} that is executed in response to the {@link WarPlugin} being applied.
 *
 * @author Andy Wilkinson
 * @author Scott Frederick
 */
class WarPluginAction implements PluginApplicationAction {

	private final SinglePublishedArtifact singlePublishedArtifact;

	WarPluginAction(SinglePublishedArtifact singlePublishedArtifact) {
		this.singlePublishedArtifact = singlePublishedArtifact;
	}

	@Override
	public Class<? extends Plugin<? extends Project>> getPluginClass() {
		return WarPlugin.class;
	}

	@Override
	public void execute(Project project) {
		disableWarTask(project);
		TaskProvider<BootWar> bootWar = configureBootWarTask(project);
		configureArtifactPublication(bootWar);
	}

	private void disableWarTask(Project project) {
		project.getTasks().named(WarPlugin.WAR_TASK_NAME).configure((war) -> war.setEnabled(false));
	}

	private TaskProvider<BootWar> configureBootWarTask(Project project) {
		return project.getTasks().register(SpringBootPlugin.BOOT_WAR_TASK_NAME, BootWar.class, (bootWar) -> {
			bootWar.setGroup(BasePlugin.BUILD_GROUP);
			bootWar.setDescription("Assembles an executable war archive containing webapp"
					+ " content, and the main classes and their dependencies.");
			bootWar.providedClasspath(providedRuntimeConfiguration(project));
			Configuration developmentOnly = project.getConfigurations()
					.getByName(SpringBootPlugin.DEVELOPMENT_ONLY_CONFIGURATION_NAME);
			Configuration productionRuntimeClasspath = project.getConfigurations()
					.getByName(SpringBootPlugin.PRODUCTION_RUNTIME_CLASSPATH_NAME);
			bootWar.setClasspath(bootWar.getClasspath().minus((developmentOnly.minus(productionRuntimeClasspath))));
			bootWar.conventionMapping("mainClassName", new MainClassConvention(project, bootWar::getClasspath));
		});
	}

	private FileCollection providedRuntimeConfiguration(Project project) {
		ConfigurationContainer configurations = project.getConfigurations();
		return configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME);
	}

	private void configureArtifactPublication(TaskProvider<BootWar> bootWar) {
		LazyPublishArtifact artifact = new LazyPublishArtifact(bootWar);
		this.singlePublishedArtifact.addCandidate(artifact);
	}

}

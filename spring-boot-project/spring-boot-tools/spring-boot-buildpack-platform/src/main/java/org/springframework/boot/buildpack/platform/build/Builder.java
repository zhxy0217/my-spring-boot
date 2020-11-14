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

package org.springframework.boot.buildpack.platform.build;

import java.io.IOException;
import java.util.function.Consumer;

import org.springframework.boot.buildpack.platform.build.BuilderMetadata.Stack;
import org.springframework.boot.buildpack.platform.docker.DockerApi;
import org.springframework.boot.buildpack.platform.docker.TotalProgressEvent;
import org.springframework.boot.buildpack.platform.docker.TotalProgressPullListener;
import org.springframework.boot.buildpack.platform.docker.UpdateListener;
import org.springframework.boot.buildpack.platform.docker.transport.DockerEngineException;
import org.springframework.boot.buildpack.platform.docker.type.Image;
import org.springframework.boot.buildpack.platform.docker.type.ImageReference;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Central API for running buildpack operations.
 *
 * @author Phillip Webb
 * @author Scott Frederick
 * @since 2.3.0
 */
public class Builder {

	private final BuildLog log;

	private final DockerApi docker;

	public Builder() {
		this(BuildLog.toSystemOut());
	}

	public Builder(BuildLog log) {
		this(log, new DockerApi());
	}

	Builder(BuildLog log, DockerApi docker) {
		Assert.notNull(log, "Log must not be null");
		this.log = log;
		this.docker = docker;
	}

	public void build(BuildRequest request) throws DockerEngineException, IOException {
		Assert.notNull(request, "Request must not be null");
		this.log.start(request);
		Image builderImage = pullBuilder(request);
		BuilderMetadata builderMetadata = BuilderMetadata.fromImage(builderImage);
		BuildOwner buildOwner = BuildOwner.fromEnv(builderImage.getConfig().getEnv());
		request = determineRunImage(request, builderImage, builderMetadata.getStack());
		EphemeralBuilder builder = new EphemeralBuilder(buildOwner, builderImage, builderMetadata, request.getCreator(),
				request.getEnv());
		this.docker.image().load(builder.getArchive(), UpdateListener.none());
		try {
			executeLifecycle(request, builder);
		}
		finally {
			this.docker.image().remove(builder.getName(), true);
		}
	}

	private Image pullBuilder(BuildRequest request) throws IOException {
		ImageReference builderImageReference = request.getBuilder();
		Consumer<TotalProgressEvent> progressConsumer = this.log.pullingBuilder(request, builderImageReference);
		TotalProgressPullListener listener = new TotalProgressPullListener(progressConsumer);
		Image builderImage = this.docker.image().pull(builderImageReference, listener);
		this.log.pulledBuilder(request, builderImage);
		return builderImage;
	}

	private BuildRequest determineRunImage(BuildRequest request, Image builderImage, Stack builderStack)
			throws IOException {
		if (request.getRunImage() == null) {
			ImageReference runImage = getRunImageReferenceForStack(builderStack);
			request = request.withRunImage(runImage);
		}
		Image runImage = pullRunImage(request);
		assertStackIdsMatch(runImage, builderImage);
		return request;
	}

	private ImageReference getRunImageReferenceForStack(Stack stack) {
		String name = stack.getRunImage().getImage();
		Assert.state(StringUtils.hasText(name), "Run image must be specified in the builder image stack");
		return ImageReference.of(name).inTaggedOrDigestForm();
	}

	private Image pullRunImage(BuildRequest request) throws IOException {
		ImageReference runImage = request.getRunImage();
		Consumer<TotalProgressEvent> progressConsumer = this.log.pullingRunImage(request, runImage);
		TotalProgressPullListener listener = new TotalProgressPullListener(progressConsumer);
		Image image = this.docker.image().pull(runImage, listener);
		this.log.pulledRunImage(request, image);
		return image;
	}

	private void assertStackIdsMatch(Image runImage, Image builderImage) {
		StackId runImageStackId = StackId.fromImage(runImage);
		StackId builderImageStackId = StackId.fromImage(builderImage);
		Assert.state(runImageStackId.equals(builderImageStackId),
				"Run image stack '" + runImageStackId + "' does not match builder stack '" + builderImageStackId + "'");
	}

	private void executeLifecycle(BuildRequest request, EphemeralBuilder builder) throws IOException {
		try (Lifecycle lifecycle = new Lifecycle(this.log, this.docker, request, builder)) {
			lifecycle.execute();
		}
	}

}

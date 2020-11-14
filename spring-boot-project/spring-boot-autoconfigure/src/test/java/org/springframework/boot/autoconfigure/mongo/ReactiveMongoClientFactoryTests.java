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

package org.springframework.boot.autoconfigure.mongo;

import java.util.List;

import com.mongodb.MongoClientSettings;
import com.mongodb.internal.async.client.AsyncMongoClient;
import com.mongodb.reactivestreams.client.MongoClient;

import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for {@link ReactiveMongoClientFactory}.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 * @author Scott Frederick
 */
class ReactiveMongoClientFactoryTests extends MongoClientFactorySupportTests<MongoClient> {

	@Override
	protected MongoClient createMongoClient(MongoProperties properties, Environment environment,
			List<MongoClientSettingsBuilderCustomizer> customizers, MongoClientSettings settings) {
		return new ReactiveMongoClientFactory(properties, environment, customizers).createMongoClient(settings);
	}

	@Override
	protected MongoClientSettings getClientSettings(MongoClient client) {
		AsyncMongoClient wrapped = (AsyncMongoClient) ReflectionTestUtils.getField(client, "wrapped");
		return (MongoClientSettings) ReflectionTestUtils.getField(wrapped, "settings");
	}

}

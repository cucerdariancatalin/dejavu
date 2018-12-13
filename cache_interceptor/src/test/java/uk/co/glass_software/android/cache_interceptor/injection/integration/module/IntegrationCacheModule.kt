/*
 * Copyright (C) 2017 Glass Software Ltd
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package uk.co.glass_software.android.cache_interceptor.injection.integration.module

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import dagger.Module
import dagger.Provides
import uk.co.glass_software.android.cache_interceptor.configuration.CacheConfiguration
import uk.co.glass_software.android.cache_interceptor.injection.module.BaseCacheModule
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.error.ApiError
import javax.inject.Singleton

@Module
internal class IntegrationCacheModule(configuration: CacheConfiguration<ApiError>)
    : BaseCacheModule<ApiError>(configuration) {

    @Provides
    @Singleton
    override fun provideSqlOpenHelper(context: Context,
                                      callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper =
            FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                            .name(DATABASE_NAME)
                            .callback(callback)
                            .build()
            )

}

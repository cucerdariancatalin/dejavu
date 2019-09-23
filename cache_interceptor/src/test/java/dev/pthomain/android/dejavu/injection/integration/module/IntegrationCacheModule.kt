/*
 *
 *  Copyright (C) 2017 Pierre Thomain
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package dev.pthomain.android.dejavu.injection.integration.module

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import dagger.Module
import dagger.Provides
import dev.pthomain.android.dejavu.configuration.CacheConfiguration
import dev.pthomain.android.dejavu.injection.module.BaseCacheModule
import dev.pthomain.android.dejavu.injection.module.CacheModule
import dev.pthomain.android.dejavu.interceptors.internal.error.Glitch
import java.util.*
import javax.inject.Singleton

@Module
internal class IntegrationCacheModule(configuration: CacheConfiguration<Glitch>)
    : BaseCacheModule<Glitch>(configuration) {

    val NOW = Date(1234L)

    @Provides
    @Singleton
    override fun provideDateFactory() = object : CacheModule.Function1<Long?, Date> {
        override fun get(t1: Long?) = if (t1 == null) NOW else Date(t1)
    }

    @Provides
    @Singleton
    override fun provideSqlOpenHelper(context: Context,
                                      callback: SupportSQLiteOpenHelper.Callback?): SupportSQLiteOpenHelper? =
            FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                            .name(DATABASE_NAME)
                            .callback(callback!!)
                            .build()
            )

}

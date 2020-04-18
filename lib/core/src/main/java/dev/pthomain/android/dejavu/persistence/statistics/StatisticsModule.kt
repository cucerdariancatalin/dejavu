/*
 *
 *  Copyright (C) 2017-2020 Pierre Thomain
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

package dev.pthomain.android.dejavu.persistence.statistics

import dagger.Module
import dagger.Provides
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import io.reactivex.Single
import javax.inject.Singleton

@Module
abstract class StatisticsModule<E>
        where E : Throwable,
              E : NetworkErrorPredicate {

    @Provides
    @Singleton
    fun provideStatisticsCompiler(): StatisticsCompiler =
            object : StatisticsCompiler { //FIXME
                override fun getStatistics() = Single.error<CacheStatistics>(
                        IllegalStateException("Cache statistics are not supported at the moment")
                )
            }

}

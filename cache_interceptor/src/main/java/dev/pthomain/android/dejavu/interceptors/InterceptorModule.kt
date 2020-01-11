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

package dev.pthomain.android.dejavu.interceptors

import android.content.Context
import dagger.Module
import dagger.Provides
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration
import dev.pthomain.android.dejavu.injection.Function1
import dev.pthomain.android.dejavu.injection.Function3
import dev.pthomain.android.dejavu.interceptors.cache.CacheInterceptor
import dev.pthomain.android.dejavu.interceptors.cache.CacheManager
import dev.pthomain.android.dejavu.interceptors.cache.instruction.Operation.Remote.Cache
import dev.pthomain.android.dejavu.interceptors.cache.metadata.CacheMetadata
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.CacheToken
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.Hasher
import dev.pthomain.android.dejavu.interceptors.error.ErrorInterceptor
import dev.pthomain.android.dejavu.interceptors.error.error.NetworkErrorPredicate
import dev.pthomain.android.dejavu.interceptors.network.NetworkInterceptor
import dev.pthomain.android.dejavu.interceptors.response.EmptyResponseWrapperFactory
import dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
import io.reactivex.subjects.PublishSubject
import java.util.*
import javax.inject.Singleton

@Module
internal abstract class InterceptorModule<E>
        where E : Exception,
              E : NetworkErrorPredicate {

    @Provides
    @Singleton
    fun provideErrorInterceptorFactory(configuration: DejaVuConfiguration<E>,
                                       context: Context,
                                       emptyResponseWrapperFactory: EmptyResponseWrapperFactory<E>,
                                       logger: Logger,
                                       dateFactory: Function1<Long?, Date>) =
            object : Function1<CacheToken, ErrorInterceptor<E>> {
                override fun get(t1: CacheToken) = ErrorInterceptor(
                        context,
                        configuration.errorFactory,
                        emptyResponseWrapperFactory,
                        logger,
                        dateFactory::get,
                        t1
                )
            }

    @Provides
    @Singleton
    fun provideNetworkInterceptorFactory(context: Context,
                                         logger: Logger,
                                         dateFactory: Function1<Long?, Date>) =
            object : Function3<ErrorInterceptor<E>, Cache?, Long, NetworkInterceptor<E>> {
                override fun get(t1: ErrorInterceptor<E>, t2: Cache?, t3: Long) = NetworkInterceptor(
                        context,
                        logger,
                        t1,
                        dateFactory::get,
                        t2,
                        t3
                )
            }

    @Provides
    @Singleton
    fun provideCacheInterceptorFactory(dateFactory: Function1<Long?, Date>,
                                       cacheManager: CacheManager<E>) =
            object : Function3<ErrorInterceptor<E>, CacheToken, Long, CacheInterceptor<E>> {
                override fun get(t1: ErrorInterceptor<E>, t2: CacheToken, t3: Long) = CacheInterceptor(
                        t1,
                        cacheManager,
                        dateFactory::get,
                        t2,
                        t3
                )
            }

    @Provides
    @Singleton
    fun provideResponseInterceptor(configuration: DejaVuConfiguration<E>,
                                   logger: Logger,
                                   dateFactory: Function1<Long?, Date>,
                                   metadataSubject: PublishSubject<CacheMetadata<E>>,
                                   emptyResponseWrapperFactory: EmptyResponseWrapperFactory<E>) =
            object : Function3<CacheToken, Boolean, Long, ResponseInterceptor<E>> {
                override fun get(t1: CacheToken,
                                 t2: Boolean,
                                 t3: Long) = ResponseInterceptor(
                        logger,
                        dateFactory::get,
                        emptyResponseWrapperFactory,
                        configuration,
                        metadataSubject,
                        t1,
                        t2,
                        t3
                )
            }

    @Provides
    @Singleton
    fun provideEmptyResponseFactory(configuration: DejaVuConfiguration<E>) =
            EmptyResponseWrapperFactory(configuration.errorFactory)


    @Provides
    @Singleton
    fun provideOkHttpHeaderInterceptor() =
            HeaderInterceptor()

    @Provides
    @Singleton
    fun provideDejaVuInterceptorFactory(hasher: Hasher,
                                        configuration: DejaVuConfiguration<E>,
                                        dateFactory: Function1<Long?, Date>,
                                        networkInterceptorFactory: Function3<ErrorInterceptor<E>, Cache?, Long, NetworkInterceptor<E>>,
                                        errorInterceptorFactory: Function1<CacheToken, ErrorInterceptor<E>>,
                                        cacheInterceptorFactory: Function3<ErrorInterceptor<E>, CacheToken, Long, CacheInterceptor<E>>,
                                        responseInterceptorFactory: Function3<CacheToken, Boolean, Long, ResponseInterceptor<E>>) =
            DejaVuInterceptor.Factory(
                    hasher,
                    dateFactory::get,
                    errorInterceptorFactory::get,
                    networkInterceptorFactory::get,
                    cacheInterceptorFactory::get,
                    responseInterceptorFactory::get,
                    configuration
            )

}

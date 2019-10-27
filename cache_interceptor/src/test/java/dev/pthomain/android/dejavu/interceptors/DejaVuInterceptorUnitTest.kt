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

import com.nhaarman.mockitokotlin2.*
import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration
import dev.pthomain.android.dejavu.configuration.instruction.Operation
import dev.pthomain.android.dejavu.configuration.instruction.Operation.Expiring
import dev.pthomain.android.dejavu.configuration.instruction.Operation.Type.DO_NOT_CACHE
import dev.pthomain.android.dejavu.interceptors.cache.CacheInterceptor
import dev.pthomain.android.dejavu.interceptors.cache.metadata.RequestMetadata
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.CacheToken
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.Hasher
import dev.pthomain.android.dejavu.interceptors.error.ErrorInterceptor
import dev.pthomain.android.dejavu.interceptors.error.ResponseWrapper
import dev.pthomain.android.dejavu.interceptors.error.glitch.Glitch
import dev.pthomain.android.dejavu.interceptors.network.NetworkInterceptor
import dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
import dev.pthomain.android.dejavu.retrofit.annotations.AnnotationProcessor.RxType
import dev.pthomain.android.dejavu.retrofit.annotations.AnnotationProcessor.RxType.*
import dev.pthomain.android.dejavu.test.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import org.junit.Test
import java.util.*

class DejaVuInterceptorUnitTest {

    private val start = 1234L
    private val mockDateFactory: (Long?) -> Date = { Date(start) }

    private lateinit var mockNetworkInterceptorFactory: (ErrorInterceptor<Glitch>, CacheToken, Long) -> NetworkInterceptor<Glitch>
    private lateinit var mockErrorInterceptorFactory: (CacheToken) -> ErrorInterceptor<Glitch>
    private lateinit var mockCacheInterceptorFactory: (ErrorInterceptor<Glitch>, CacheToken, Long) -> CacheInterceptor<Glitch>
    private lateinit var mockResponseInterceptorFactory: (CacheToken, RxType, Long) -> ResponseInterceptor<Glitch>
    private lateinit var mockConfiguration: DejaVuConfiguration<Glitch>
    private lateinit var mockHasher: Hasher
    private lateinit var mockRequestMetadata: RequestMetadata.Plain
    private lateinit var mockValidHashedMetadata: RequestMetadata.Hashed.Valid
    private lateinit var mockInvalidHashedMetadata: RequestMetadata.Hashed.Invalid
    private lateinit var mockNetworkInterceptor: NetworkInterceptor<Glitch>
    private lateinit var mockErrorInterceptor: ErrorInterceptor<Glitch>
    private lateinit var mockCacheInterceptor: CacheInterceptor<Glitch>
    private lateinit var mockResponseInterceptor: ResponseInterceptor<Glitch>
    private lateinit var mockInstructionToken: CacheToken
    private lateinit var mockUpstreamObservable: Observable<Any>
    private lateinit var mockNetworkObservable: Observable<ResponseWrapper<Glitch>>
    private lateinit var mockCacheResponseObservable: Observable<ResponseWrapper<Glitch>>
    private lateinit var mockResponseObservable: Observable<Any>
    private lateinit var mockHashingErrorObservable: Observable<Any>
    private lateinit var errorTokenCaptor: KArgumentCaptor<CacheToken>
    private lateinit var cacheCacheTokenCaptor: KArgumentCaptor<CacheToken>
    private lateinit var networkCacheTokenCaptor: KArgumentCaptor<CacheToken>
    private lateinit var responseTokenCaptor: KArgumentCaptor<CacheToken>

    private val mockException = IllegalStateException("test")

    private fun setUp(operation: Operation,
                      rxType: RxType,
                      isHashingSuccess: Boolean): DejaVuInterceptor<Glitch> {
        mockErrorInterceptorFactory = mock()
        mockCacheInterceptorFactory = mock()
        mockResponseInterceptorFactory = mock()
        mockNetworkInterceptorFactory = mock()
        mockConfiguration = mock()
        mockHasher = mock()

        mockErrorInterceptor = mock()
        mockCacheInterceptor = mock()
        mockResponseInterceptor = mock()
        mockNetworkInterceptor = mock()

        mockRequestMetadata = defaultRequestMetadata()
        mockValidHashedMetadata = mock()
        mockInvalidHashedMetadata = mock()

        mockInstructionToken = instructionToken(operation)

        mockUpstreamObservable = mock()
        mockNetworkObservable = mock()
        mockCacheResponseObservable = mock()
        mockResponseObservable = mock()
        mockHashingErrorObservable = Observable.error(mockException)

        whenever(mockHasher.hash(eq(mockRequestMetadata))).thenReturn(mock())

        errorTokenCaptor = argumentCaptor()
        cacheCacheTokenCaptor = argumentCaptor()
        networkCacheTokenCaptor = argumentCaptor()
        responseTokenCaptor = argumentCaptor()

        whenever(mockErrorInterceptorFactory.invoke(
                errorTokenCaptor.capture()
        )).thenReturn(mockErrorInterceptor)

        whenever(mockNetworkInterceptorFactory.invoke(
                eq(mockErrorInterceptor),
                networkCacheTokenCaptor.capture(),
                eq(start)
        )).thenReturn(mockNetworkInterceptor)

        whenever(mockCacheInterceptorFactory.invoke(
                eq(mockErrorInterceptor),
                cacheCacheTokenCaptor.capture(),
                eq(start)
        )).thenReturn(mockCacheInterceptor)

        whenever(mockResponseInterceptorFactory.invoke(
                responseTokenCaptor.capture(),
                eq(rxType),
                eq(start)
        )).thenReturn(mockResponseInterceptor)

        if (isHashingSuccess) {
            whenever(mockUpstreamObservable.compose(eq(mockNetworkInterceptor))).thenReturn(mockNetworkObservable)
            whenever(mockNetworkObservable.compose(eq(mockCacheInterceptor))).thenReturn(mockCacheResponseObservable)
            whenever(mockCacheResponseObservable.compose(eq(mockResponseInterceptor))).thenReturn(mockResponseObservable)
        } else {
            whenever(mockNetworkInterceptor.apply(eq(mockHashingErrorObservable))).thenReturn(mockNetworkObservable)
            whenever(mockNetworkObservable.compose(eq(mockCacheInterceptor))).thenReturn(mockCacheResponseObservable)
            whenever(mockCacheResponseObservable.compose(eq(mockResponseInterceptor))).thenReturn(mockHashingErrorObservable)
        }

        whenever(mockHasher.hash(mockRequestMetadata)).thenReturn(
                ifElse(isHashingSuccess, mockValidHashedMetadata, mockInvalidHashedMetadata)
        )

        whenever(mockConfiguration.compress).thenReturn(true)
        whenever(mockConfiguration.encrypt).thenReturn(true)

        return DejaVuInterceptor(
                operation,
                mockRequestMetadata,
                mockConfiguration,
                mockHasher,
                mockDateFactory,
                { mockHashingErrorObservable },
                mockErrorInterceptorFactory,
                mockNetworkInterceptorFactory,
                mockCacheInterceptorFactory,
                mockResponseInterceptorFactory
        )
    }

    @Test
    fun testApplyObservable() {
        testApply(OBSERVABLE)
    }

    @Test
    fun testApplySingle() {
        testApply(SINGLE)
    }

    @Test
    fun testApplyCompletable() {
        testApply(COMPLETABLE)
    }

    private fun testApply(rxType: RxType) {
        operationSequence { operation ->
            trueFalseSequence { isCacheEnabled ->
                trueFalseSequence { isHashingSuccess ->

                    val target = setUp(
                            operation,
                            rxType,
                            isHashingSuccess
                    )
                    val testObserver = TestObserver<Any>()

                    val context = "Operation = $operation," +
                            "\nisCacheEnabled = $isCacheEnabled," +
                            "\nisHashingSuccess = $isHashingSuccess"

                    val mockSingle = mock<Single<Any>>()
                    whenever(mockSingle.toObservable()).thenReturn(mockUpstreamObservable)
                    whenever(mockResponseObservable.firstOrError()).thenReturn(mockSingle)

                    val mockCompletable = mock<Completable>()
                    whenever(mockCompletable.toObservable<Any>()).thenReturn(mockUpstreamObservable)
                    whenever(mockResponseObservable.ignoreElements()).thenReturn(mockCompletable)

                    when (rxType) {
                        OBSERVABLE -> target.apply(mockUpstreamObservable).subscribe(testObserver)
                        SINGLE -> target.apply(mockSingle).subscribe(testObserver)
                        COMPLETABLE -> target.apply(mockCompletable).subscribe(testObserver)
                    }

                    val errorToken = errorTokenCaptor.firstValue
                    val cacheCacheToken = cacheCacheTokenCaptor.firstValue
                    val networkCacheToken = networkCacheTokenCaptor.firstValue
                    val responseToken = responseTokenCaptor.firstValue

                    if (!isHashingSuccess) {
                        assertTrueWithContext(
                                testObserver.errorCount() == 1,
                                "A hashing error should have been emitted"
                        )

                        assertTrueWithContext(
                                testObserver.errors().first() == mockException,
                                "The wrong exception was emitted"
                        )
                    } else {
                        assertEqualsWithContext(
                                cacheCacheToken,
                                networkCacheToken,
                                "Cache tokens for cache and network interceptors didn't match",
                                context
                        )

                        assertEqualsWithContext(
                                errorToken,
                                cacheCacheToken,
                                "Error token and cache token should be the same",
                                context
                        )

                        assertEqualsWithContext(
                                cacheCacheToken,
                                responseToken,
                                "Response token and cache token should be the same",
                                context
                        )

                        if (!isCacheEnabled) {
                            assertTrueWithContext(
                                    errorToken.instruction.operation.type == DO_NOT_CACHE,
                                    "Cache token should be DO_NOT_CACHE when isCacheEnabled == false",
                                    context
                            )
                        }

                        assertEqualsWithContext(
                                mockValidHashedMetadata,
                                errorToken.instruction.requestMetadata,
                                "Request metadata didn't match",
                                context
                        )

                        if (operation is Expiring) {
                            if (operation.compress == null) {
                                assertTrueWithContext(
                                        errorToken.isCompressed,
                                        "Token value for isCompressed should be true",
                                        context
                                )
                            } else {
                                assertEqualsWithContext(
                                        operation.compress,
                                        errorToken.isCompressed,
                                        "Token value for isCompressed didn't match operation's value",
                                        context
                                )
                            }

                            if (operation.encrypt == null) {
                                assertTrueWithContext(
                                        errorToken.isEncrypted,
                                        "Token value for isEncrypted should be true",
                                        context
                                )
                            } else {
                                assertEqualsWithContext(
                                        operation.encrypt,
                                        errorToken.isEncrypted,
                                        "Token value for isEncrypted didn't match operation's value",
                                        context
                                )
                            }
                        } else {
                            assertTrueWithContext(
                                    errorToken.isCompressed,
                                    "Token value for isCompressed should be true",
                                    context

                            )
                            assertTrueWithContext(
                                    errorToken.isEncrypted,
                                    "Token value for isEncrypted should be true",
                                    context
                            )
                        }
                    }
                }
            }
        }
    }
}
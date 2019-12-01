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

package dev.pthomain.android.dejavu.retrofit

import com.nhaarman.mockitokotlin2.*
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.boilerplate.core.utils.rx.observable
import dev.pthomain.android.dejavu.DejaVu.Companion.DejaVuHeader
import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration
import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration.Companion.CachePredicate
import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration.Companion.DefaultCachePredicate
import dev.pthomain.android.dejavu.interceptors.DejaVuInterceptor
import dev.pthomain.android.dejavu.interceptors.RxType.*
import dev.pthomain.android.dejavu.interceptors.cache.instruction.DejaVuCall
import dev.pthomain.android.dejavu.interceptors.cache.instruction.Operation
import dev.pthomain.android.dejavu.interceptors.cache.instruction.Operation.Cache
import dev.pthomain.android.dejavu.interceptors.cache.instruction.OperationSerialiser
import dev.pthomain.android.dejavu.interceptors.cache.metadata.RequestMetadata
import dev.pthomain.android.dejavu.interceptors.cache.metadata.RequestMetadata.Companion.DEFAULT_URL
import dev.pthomain.android.dejavu.interceptors.error.ResponseWrapper
import dev.pthomain.android.dejavu.interceptors.error.glitch.Glitch
import dev.pthomain.android.dejavu.test.assertEqualsWithContext
import dev.pthomain.android.dejavu.test.assertTrueWithContext
import dev.pthomain.android.dejavu.test.network.model.TestResponse
import dev.pthomain.android.dejavu.test.verifyWithContext
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.CallAdapter
import java.lang.reflect.Type

class RetrofitCallAdapterUnitTest {

    private lateinit var mockDejaVuFactory: DejaVuInterceptor.Factory<Glitch>
    private lateinit var mockLogger: Logger
    private lateinit var mockRxCallAdapter: CallAdapter<Any, Any>
    private lateinit var mockCall: Call<Any>
    private lateinit var mockOperationSerialiser: OperationSerialiser
    private lateinit var mockRequest: Request
    private lateinit var mockDejaVuTransformer: DejaVuInterceptor<Glitch>
    private lateinit var mockTestResponse: TestResponse
    private lateinit var requestMetadata: RequestMetadata.Plain
    private lateinit var mockRequestBodyConverter: (Request) -> String?
    private lateinit var configuration: DejaVuConfiguration<Glitch>

    private val responseClass = TestResponse::class.java
    private val mockMethodDescription = "mockMethodDescription"
    private val mockHeader = "mockHeader"
    private val mockOperation = Cache()

    @Before
    fun setUp() {
        mockDejaVuFactory = mock()
        mockLogger = mock()
        mockRxCallAdapter = mock()
        mockCall = mock()
        mockOperationSerialiser = mock()
        mockRequest = mock()
        mockDejaVuTransformer = mock()
        mockTestResponse = mock()
        mockRequestBodyConverter = mock()
    }

    //TODO test validateInstruction()

    private fun getTarget(hasOperation: Boolean,
                          hasHeader: Boolean,
                          cachePredicate: (metadata: RequestMetadata) -> Operation?,
                          isHeaderDeserialisationSuccess: Boolean,
                          isHeaderDeserialisationException: Boolean): RetrofitCallAdapter<Glitch> {
        whenever(mockCall.request()).thenReturn(mockRequest)

        if (hasHeader) {
            whenever(mockRequest.header(eq(DejaVuHeader))).thenReturn(mockHeader)
            whenever(mockOperationSerialiser.deserialise(eq(mockHeader))).apply {
                if (isHeaderDeserialisationException)
                    thenThrow(RuntimeException("error"))
                else
                    thenReturn(if (isHeaderDeserialisationSuccess) mockOperation else null)
            }
        }

        configuration = DejaVuConfiguration(
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                true,
                mock(),
                cachePredicate
        )

        return RetrofitCallAdapter(
                configuration,
                responseClass,
                mockDejaVuFactory,
                mockOperationSerialiser,
                mockRequestBodyConverter,
                mockLogger,
                mockMethodDescription,
                if (hasOperation) mockOperation else null,
                mockRxCallAdapter
        )
    }

    private fun testAdapt(hasOperation: Boolean,
                          hasHeader: Boolean,
                          cachePredicate: (metadata: RequestMetadata) -> Operation?,
                          isHeaderDeserialisationSuccess: Boolean,
                          isHeaderDeserialisationException: Boolean) {
        sequenceOf(
                null,
                OBSERVABLE,
                SINGLE,
                OPERATION
        ).forEach { rxType ->
            setUp() //reset mocks

            val target = getTarget(
                    hasOperation,
                    hasHeader,
                    cachePredicate,
                    isHeaderDeserialisationSuccess,
                    isHeaderDeserialisationException
            )

            val mockResponseWrapper = mock<ResponseWrapper<Glitch>>()

            val rxCall = when (rxType) {
                OBSERVABLE -> Observable.just(mockTestResponse)
                SINGLE -> Single.just(mockTestResponse)
                OPERATION -> DejaVuCall.Resolved<TestResponse, Glitch>(mockResponseWrapper.observable(), Glitch::class.java)
                else -> mockTestResponse
            }

            whenever(mockRxCallAdapter.adapt(eq(mockCall))).thenReturn(rxCall)

            val mockUrl = mock<HttpUrl>()
            whenever(mockRequest.url()).thenReturn(mockUrl)
            whenever(mockUrl.toString()).thenReturn(DEFAULT_URL)

            val mockBodyString = "body"
            val mockBody = mock<RequestBody>()
            whenever(mockRequest.body()).thenReturn(mockBody)
            whenever(mockBody.toString()).thenReturn(mockBodyString)

            whenever(mockRequestBodyConverter.invoke(eq(mockRequest))).thenReturn(mockBodyString)

            requestMetadata = RequestMetadata.Plain(
                    responseClass,
                    DEFAULT_URL,
                    mockBodyString
            )

            val hasDefaultAdaptation = cachePredicate(requestMetadata) != null
            val usesDefaultAdaptation = hasDefaultAdaptation && !hasHeader && !hasOperation

            if (rxType != null
                    && (hasOperation
                            || (hasHeader && isHeaderDeserialisationSuccess)
                            || hasDefaultAdaptation)) {

                whenever(mockDejaVuFactory.create(
                        any(),
                        eq(requestMetadata)
                )).thenReturn(mockDejaVuTransformer)

                when (rxType) {
                    OBSERVABLE -> whenever(mockDejaVuTransformer.apply(eq(rxCall as Observable<Any>))).thenReturn(rxCall.map { it })
                    SINGLE -> whenever(mockDejaVuTransformer.apply(eq(rxCall as Single<Any>))).thenReturn(rxCall.map { it })
                    OPERATION -> whenever(mockDejaVuTransformer.apply(eq(rxCall as DejaVuCall<TestResponse>))).thenReturn(rxCall as Observable<Any>)
                }
            }

            val actualAdapted = target.adapt(mockCall)

            val context = "For rxType == $rxType"

            if (rxType == null) {
                verify(mockDejaVuFactory, never()).create(
                        any(),
                        any()
                )

                assertEqualsWithContext(
                        mockTestResponse,
                        actualAdapted,
                        "Adapted value should be the mocked TestResponse",
                        context
                )
            } else if ((hasHeader && isHeaderDeserialisationSuccess) || hasOperation || usesDefaultAdaptation) {
                val argumentCaptor = argumentCaptor<Operation>()
                verifyWithContext(
                        mockDejaVuFactory,
                        "$context: DejaVuFactory should have been called with the default CacheInstruction, using the cache predicate"
                ).create(
                        argumentCaptor.capture(),
                        eq(requestMetadata)
                )

                val subContext = "$context: Returned cache predicate CacheInstruction was incorrect"
                val capturedOperation = argumentCaptor.firstValue as Cache

                if (usesDefaultAdaptation) {
                    assertEqualsWithContext(
                            false,
                            capturedOperation.isFreshOnly(),
                            "freshOnly didn't match",
                            subContext
                    )
                } else {
                    val expectedOperation = when {
                        hasHeader && isHeaderDeserialisationSuccess -> mockOperation
                        hasOperation -> mockOperation
                        else -> null
                    }

                    assertEqualsWithContext(
                            expectedOperation,
                            capturedOperation,
                            subContext
                    )
                }

                when (rxType) {
                    OBSERVABLE -> assertTrueWithContext(
                            Observable::class.java.isAssignableFrom(actualAdapted.javaClass),
                            "Adapted result should be of type Observable",
                            context
                    )

                    SINGLE -> assertTrueWithContext(
                            Single::class.java.isAssignableFrom(actualAdapted.javaClass),
                            "Adapted result should be of type Single",
                            context
                    )

                    OPERATION -> assertTrueWithContext(
                            Completable::class.java.isAssignableFrom(actualAdapted.javaClass),
                            "Adapted result should be of type Completable",
                            context
                    )
                }
            } else {
                assertEqualsWithContext(
                        rxCall,
                        actualAdapted,
                        "The given call should not have been adapted",
                        context
                )
            }
        }
    }

    @Test
    fun testResponseType() {
        val mockResponseType = mock<Type>()
        whenever(mockRxCallAdapter.responseType()).thenReturn(mockResponseType)

        assertEqualsWithContext(
                mockResponseType,
                getTarget(
                        false,
                        false,
                        CachePredicate.Inactive,
                        false,
                        false
                ).responseType(),
                "Response type didn't match"
        )
    }

    @Test
    fun testAdaptWithNoInstructionAndHeader() {
        testAdapt(
                false,
                true,
                CachePredicate.Inactive,
                true,
                false
        )
    }

    @Test
    fun testAdaptWithNoInstructionAndHeaderDeserialisationReturnsNull() {
        testAdapt(
                false,
                true,
                CachePredicate.Inactive,
                false,
                false
        )
    }

    @Test
    fun testAdaptWithNoInstructionAndHeaderDeserialisationThrowsException() {
        testAdapt(
                false,
                true,
                CachePredicate.Inactive,
                false,
                true
        )
    }

    @Test
    fun testAdaptWithInstructionAndNoHeader() {
        testAdapt(
                true,
                false,
                CachePredicate.Inactive,
                true,
                false
        )
    }

    @Test
    fun testAdaptWithInstructionAndHeaderDeserialisationReturnsNull() {
        testAdapt(
                true,
                true,
                CachePredicate.Inactive,
                false,
                false
        )
    }

    @Test
    fun testAdaptWithInstructionAndHeaderDeserialisationThrowsException() {
        testAdapt(
                true,
                true,
                CachePredicate.Inactive,
                false,
                true
        )
    }

    //TODO loop
    @Test
    fun testAdaptWithNoInstructionAndNoHeaderAndCacheByDefaultTrue() {
        testAdapt(
                false,
                false,
                CachePredicate.CacheAll,
                false,
                false
        )
    }

    //TODO use loop for this
    @Test
    fun testAdaptWithNoInstructionAndNoHeaderAndCacheByDefaultFalse() {
        testAdapt(
                false,
                false,
                CachePredicate.CacheAll,
                false,
                false
        )
    }

}
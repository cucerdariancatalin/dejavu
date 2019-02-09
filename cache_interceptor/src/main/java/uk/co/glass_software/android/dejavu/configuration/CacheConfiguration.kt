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

package uk.co.glass_software.android.dejavu.configuration

import android.content.Context
import android.os.Looper
import com.google.gson.Gson
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.android.schedulers.AndroidSchedulers
import uk.co.glass_software.android.boilerplate.utils.log.Logger
import uk.co.glass_software.android.dejavu.DejaVu
import uk.co.glass_software.android.dejavu.injection.component.CacheComponent

data class CacheConfiguration<E> internal constructor(val context: Context,
                                                      val logger: Logger,
                                                      val errorFactory: ErrorFactory<E>,
                                                      val gson: Gson,
                                                      val isCacheEnabled: Boolean,
                                                      val encrypt: Boolean,
                                                      val compress: Boolean,
                                                      val mergeOnNextOnError: Boolean,
                                                      val allowNonFinalForSingle: Boolean,
                                                      val requestTimeOutInSeconds: Int,
                                                      val connectivityTimeoutInMillis: Long,
                                                      val cacheDurationInMillis: Long,
                                                      val cacheAllByDefault: Boolean)
        where E : Exception,
              E : NetworkErrorProvider {

    companion object {

        internal fun <E> builder(errorFactory: ErrorFactory<E>,
                                 componentProvider: (CacheConfiguration<E>) -> CacheComponent<E>)
                where E : Exception,
                      E : NetworkErrorProvider =
                Builder(errorFactory, componentProvider)

    }

    class Builder<E> internal constructor(
            private val errorFactory: ErrorFactory<E>,
            private val componentProvider: (CacheConfiguration<E>) -> CacheComponent<E>
    ) where E : Exception,
            E : NetworkErrorProvider {

        private var logger: Logger? = null
        private var gson: Gson? = null

        private var requestTimeOutInSeconds: Int = 15
        private var connectivityTimeoutInMillis: Long = 0L
        private var cacheDurationInMillis: Long = 60 * 60 * 1000 //1h

        private var isCacheEnabled = true
        private var compressData: Boolean = false
        private var encryptData: Boolean = false
        private var mergeOnNextOnError: Boolean = false
        private var allowNonFinalForSingle: Boolean = false
        private var cacheAllByDefault: Boolean = true

        /**
         * Disables log output (default log output is only enabled in DEBUG mode).
         */
        fun noLog() = logger(getSilentLogger())

        private fun getSilentLogger(): Logger {
            return object : Logger {
                override fun d(tagOrCaller: Any, message: String) = Unit
                override fun e(tagOrCaller: Any, message: String) = Unit
                override fun e(tagOrCaller: Any, t: Throwable, message: String?) = Unit
            }
        }

        /**
         * Sets custom logger.
         */
        fun logger(logger: Logger) = apply { this.logger = logger }

        /**
         * Sets custom Gson implementation.
         */
        fun gson(gson: Gson) = apply { this.gson = gson }

        /**
         * Sets network call timeout in seconds globally (default is 15s).
         */
        fun requestTimeOutInSeconds(requestTimeOutInSeconds: Int) = apply { this.requestTimeOutInSeconds = requestTimeOutInSeconds }

        /**
         *  Sets the maximum time to wait for the network connectivity to become available to return an online response (does not apply to cached responses)
         */
        fun connectivityTimeoutInMillis(connectivityTimeoutInMillis: Long) = apply { this.connectivityTimeoutInMillis = connectivityTimeoutInMillis }

        /**
         * Sets the global cache duration in milliseconds (used by default for all calls with no specific directive,
         * see @Cache::durationInMillis for call-specific directive).
         */
        fun cacheDurationInMillis(cacheDurationInMillis: Long) = apply { this.cacheDurationInMillis = cacheDurationInMillis }

        /**
         * Enables or disables cache globally, regardless of individual call setup.
         * Error handling is still executing and errors will be delivered in 2 possible ways:
         *
         * - as metadata on the response if the response implements CacheMetadata.Holder and
         * the mergeOnNextOnError directive is set to true for the call.
         *
         * - using the default RxJava error mechanism otherwise.
         */
        fun setCacheEnabled(isCacheEnabled: Boolean) = apply { this.isCacheEnabled = isCacheEnabled }

        /**
         * Sets the data compression globally (used by default for all calls with no specific directive,
         * see @Cache::compress for call-specific directive).
         */
        fun compressData(compressData: Boolean) = apply { this.compressData = compressData }

        /**
         * Sets the data encryption globally (used by default for all calls with no specific directive,
         * see @Cache::encrypt for call-specific directive).
         */
        fun encryptData(encryptData: Boolean) = apply { this.encryptData = encryptData }

        /**
         * Sets response/error merging globally (used by default for all calls with no specific directive,
         * see @Cache::mergeOnNextOnError for call-specific directive).
         *
         * When set to true, errors will be added as metadata to any call implementing
         * the CacheMetadata.Holder interface. This means onError(t:Throwable) will never be called.
         *
         * Instead if an error occurs, an empty response is returned with the exception available as
         * metadata. Special care must be taken to check if the response metadata contains an error
         * before attempting to read any of its fields.
         *
         * When used by mistake on a call returning a response that does not implement
         * CacheMetadata.Holder, this directive is ignored and the exception is delivered using the
         * default RxJava mechanism which may cause a crash if no uncaught error handler
         * is set and the onError(t:Throwable) callback is not provided.
         */
        fun mergeOnNextOnError(mergeOnNextOnError: Boolean) = apply { this.mergeOnNextOnError = mergeOnNextOnError }

        /**
         * Allows Singles to return non-final responses. This means the call terminates earlier with
         * the risk that the returned data might be STALE. The REFRESH call will still happen in
         * the background but the result of it won't be delivered. Instead it will be available
         * for the next call. By default, Singles will only return responses with final status.
         * The 'filterFinal' directive on the cache instruction will take precedence if set.
         *
         * @see uk.co.glass_software.android.dejavu.interceptors.internal.cache.token.CacheStatus
         * @see uk.co.glass_software.android.dejavu.configuration.CacheInstruction.Operation.Expiring.filterFinal
         */
        fun allowNonFinalForSingle(allowNonFinalForSingle: Boolean) = apply { this.allowNonFinalForSingle = allowNonFinalForSingle }

        /**
         * Sets data caching globally (used by default for all calls with no annotation) using
         * the default global values set here. As for any global directive, it is overridden by
         * call-specific values.
         */
        fun cacheAllByDefault(cacheAllByDefault: Boolean) = apply { this.cacheAllByDefault = cacheAllByDefault }

        /**
         * Returns an instance of DejaVu.
         */
        fun build(context: Context): DejaVu<E> {
            val logger = logger ?: getSilentLogger()

            RxAndroidPlugins.setInitMainThreadSchedulerHandler {
                AndroidSchedulers.from(Looper.getMainLooper(), true)
            }

            return DejaVu(
                    componentProvider(
                            CacheConfiguration(
                                    context.applicationContext,
                                    logger,
                                    errorFactory,
                                    gson ?: Gson(),
                                    isCacheEnabled,
                                    encryptData,
                                    compressData,
                                    mergeOnNextOnError,
                                    allowNonFinalForSingle,
                                    requestTimeOutInSeconds,
                                    connectivityTimeoutInMillis,
                                    cacheDurationInMillis,
                                    cacheAllByDefault
                            ).also { logger.d(this, "DejaVu set up with the following configuration: $it") }
                    )
            )
        }
    }

}
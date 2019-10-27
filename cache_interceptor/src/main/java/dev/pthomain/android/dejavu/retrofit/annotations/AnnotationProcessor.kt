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

package dev.pthomain.android.dejavu.retrofit.annotations

import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration
import dev.pthomain.android.dejavu.configuration.error.NetworkErrorPredicate
import dev.pthomain.android.dejavu.configuration.instruction.CacheInstruction
import dev.pthomain.android.dejavu.configuration.instruction.Operation
import dev.pthomain.android.dejavu.configuration.instruction.Operation.Type.*
import dev.pthomain.android.dejavu.retrofit.annotations.CacheException.Type.ANNOTATION
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single

/**
 * Processes Retrofit annotations and generates a CacheInstruction if needed.
 *
 * @see CacheInstruction
 */
internal class AnnotationProcessor<E>(private val dejaVuConfiguration: DejaVuConfiguration<E>)
        where  E : Exception,
               E : NetworkErrorPredicate {

    private val logger = dejaVuConfiguration.logger

    /**
     * Processes the annotations on the Retrofit call and tries to convert them to a CacheInstruction
     * if applicable.
     *
     * @param annotations the calls annotations as provided by the Retrofit call adapter.
     * @param rxType the type of RxJava operation for this call
     * @param responseClass the target response class
     *
     * @return the processed cache operation if applicable
     */
    @Throws(CacheException::class)
    fun process(annotations: Array<Annotation>,
                rxType: RxType,
                responseClass: Class<*>): Operation? {
        var operation: Operation? = null

        annotations.forEach { annotation ->
            when (annotation) {
                is Cache -> CACHE
                is DoNotCache -> DO_NOT_CACHE
                is Invalidate -> INVALIDATE
                is Refresh -> REFRESH
                is Offline -> OFFLINE
                is Clear -> CLEAR
                else -> null
            }?.let {
                operation = getOperation(
                        operation,
                        rxType,
                        responseClass,
                        it,
                        annotation
                )
            }
        }

        return operation
    }

    @Throws(CacheException::class)
    private fun CacheException.logAndThrow() {
        logger.e(this, this)
        throw this
    }

    /**
     * Returns a cache operation for the given annotation and checks for duplicate annotations
     * on the same call.
     *
     * @param currentOperation the previous existing annotation if found, to detect duplicates
     * @param rxType the RxJava type
     * @param responseClass the targeted response class for this call
     * @param foundOperation the operation type associated with the given annotation
     * @param annotation the annotation being processed
     * @return the processed cache operation for the given annotation
     */
    @Throws(CacheException::class)
    private fun getOperation(currentOperation: Operation?,
                             rxType: RxType,
                             responseClass: Class<*>,
                             foundOperation: Operation.Type,
                             annotation: Annotation): Operation? {
        if (currentOperation != null) {
            CacheException(
                    ANNOTATION,
                    "More than one cache annotation defined for method returning"
                            + " ${rxType.getTypedName(responseClass)}, found ${foundOperation.annotationName}"
                            + " after existing annotation ${currentOperation.type.annotationName}."
                            + " Only one annotation can be used for this method."
            ).logAndThrow()
        }

        return with(annotation) {
            when (this) {
                is Cache -> Operation.Expiring.Cache(
                        durationInMillis.swapWhenDefaultLong(dejaVuConfiguration.cacheDurationInMillis),
                        connectivityTimeoutInMillis.swapWhenDefaultLong(dejaVuConfiguration.connectivityTimeoutInMillis),
                        freshOnly,
                        mergeOnNextOnError.value,
                        encrypt.value,
                        compress.value
                )

                is Refresh -> Operation.Expiring.Refresh(
                        durationInMillis.swapWhenDefaultLong(dejaVuConfiguration.cacheDurationInMillis), //FIXME re-use value used for Cache if available (leave this to -1 and let DatabaseManager deal with it)
                        connectivityTimeoutInMillis.swapWhenDefaultLong(dejaVuConfiguration.connectivityTimeoutInMillis),
                        freshOnly,
                        mergeOnNextOnError.value
                )

                is Offline -> Operation.Expiring.Offline(
                        freshOnly,
                        mergeOnNextOnError.value
                )

                is Invalidate -> Operation.Invalidate(
                        typeToInvalidate.java.swapWhenDefaultClass()
                )

                is Clear -> Operation.Clear(
                        typeToClear.java.swapWhenDefaultClass(),
                        clearStaleEntriesOnly
                )

                is DoNotCache -> Operation.DoNotCache

                else -> null
            }
        }
    }

    /**
     * Represents a RxJava type
     */
    enum class RxType(val rxClass: Class<*>) {
        OBSERVABLE(Observable::class.java),
        SINGLE(Single::class.java),
        COMPLETABLE(Completable::class.java);

        /**
         * @return a String representation of the typed Rx object
         */
        fun getTypedName(responseClass: Class<*>) =
                if (this == COMPLETABLE) rxClass.simpleName
                else "${rxClass.simpleName}<${responseClass.simpleName}>"
    }

}

fun <T, S : T> T?.swapWhen(ifTrue: S?,
                           condition: (T?) -> Boolean?) =
        if (condition(this) == true) ifTrue else this

private fun Long.swapWhenDefaultLong(ifDefault: Long) =
        swapWhen(ifDefault) { it == -1L }

private fun Class<*>.swapWhenDefaultClass() =
        swapWhen(null) { it == Any::class.java }

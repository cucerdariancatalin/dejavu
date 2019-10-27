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

package dev.pthomain.android.dejavu.utils

fun <T : Any> T?.isAnyNullable(predicate: (Any?) -> Boolean,
                               vararg values: Any?): Boolean {
    if (this != null) {
        values.forEach {
            if (predicate(it)) return true
        }
    }
    return false
}

fun <T : Any> T.isAny(predicate: (Any) -> Boolean,
                      vararg values: Any) =
        isAnyNullable(
                { it != null && predicate(it) },
                values
        )

fun <T : Any> T.isAnyInstance(vararg classes: Class<*>) =
        isAny(
                { this::class.java.isAssignableFrom(it::class.java) },
                classes
        )

fun <T : Any> T?.swapLambdaWhen(condition: Boolean?,
                                ifTrue: (T?) -> T?): T? =
        swapLambdaWhen({ condition }, ifTrue)

fun <T : Any> T?.swapLambdaWhen(condition: (T?) -> Boolean?,
                                ifTrue: (T?) -> T?): T? =
        if (condition(this) == true) ifTrue(this) else this

fun <T : Any> T?.swapValueWhen(ifTrue: T?,
                               condition: (T?) -> Boolean?): T? =
        swapLambdaWhen(condition) { ifTrue }

fun Long?.swapWhenDefaultLong(ifDefault: Long) =
        swapValueWhen(ifDefault) { it == null || it == -1L }!!

fun Class<*>.swapWhenDefaultClass() =
        swapValueWhen(null) { it == Any::class.java }!!

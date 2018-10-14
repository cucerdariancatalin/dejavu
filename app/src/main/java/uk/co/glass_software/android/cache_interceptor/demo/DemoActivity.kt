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

package uk.co.glass_software.android.cache_interceptor.demo

import android.content.Context
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ExpandableListView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.multidex.MultiDex
import uk.co.glass_software.android.boilerplate.utils.lambda.Callback1
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction.Operation.*
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction.Operation.Expiring.*
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction.Operation.Type.*
import uk.co.glass_software.android.cache_interceptor.demo.DemoMvpContract.*
import uk.co.glass_software.android.cache_interceptor.demo.injection.DemoViewModule
import uk.co.glass_software.android.cache_interceptor.demo.model.CatFactResponse
import uk.co.glass_software.android.cache_interceptor.demo.presenter.CompositePresenter.Method
import uk.co.glass_software.android.cache_interceptor.demo.presenter.CompositePresenter.Method.*


internal class DemoActivity
    : AppCompatActivity(),
        DemoMvpView,
        (String) -> Unit {

    private lateinit var listAdapter: ExpandableListAdapter

    private val loadButton by lazy { findViewById<View>(R.id.load_button)!! }
    private val refreshButton by lazy { findViewById<View>(R.id.refresh_button)!! }
    private val clearButton by lazy { findViewById<View>(R.id.clear_button)!! }
    private val offlineButton by lazy { findViewById<View>(R.id.offline_button)!! }
    private val invalidateButton by lazy { findViewById<View>(R.id.invalidate_button)!! }
    private val gitHubButton by lazy { findViewById<View>(R.id.github)!! }

    private val retrofitAnnotationRadio by lazy { findViewById<View>(R.id.radio_button_retrofit_annotation)!! }
    private val retrofitHeaderRadio by lazy { findViewById<View>(R.id.radio_button_retrofit_header)!! }
    private val volleyRadio by lazy { findViewById<View>(R.id.radio_button_volley)!! }

    private val freshOnlyCheckBox by lazy { findViewById<CheckBox>(R.id.checkbox_fresh_only)!! }
    private val compressCheckBox by lazy { findViewById<CheckBox>(R.id.checkbox_compress)!! }
    private val encryptCheckBox by lazy { findViewById<CheckBox>(R.id.checkbox_encrypt)!! }

    private val listView by lazy { findViewById<ExpandableListView>(R.id.list)!! }

    private var encrypt: Boolean = false
    private var compress: Boolean = false
    private var freshOnly: Boolean = false

    private var instructionType: CacheInstruction.Operation.Type = CACHE

    private lateinit var presenter: DemoPresenter
    private lateinit var presenterSwitcher: Callback1<Method>

    override fun getPresenter() = presenter

    override fun initialiseComponent() = DaggerDemoMvpContract_DemoViewComponent
            .builder()
            .demoViewModule(DemoViewModule(this, this))
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreateComponent(savedInstanceState)
    }

    override fun onComponentReady(component: DemoViewComponent) {
        this.presenter = component.presenter()
        this.presenterSwitcher = component.presenterSwitcher()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun invoke(p1: String) {
        listAdapter.log(p1)
    }

    override fun onCreateMvpView(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)

        loadButton.setOnClickListener { loadCatFact(false) }
        refreshButton.setOnClickListener { loadCatFact(true) }
        clearButton.setOnClickListener { clearEntries() }
        offlineButton.setOnClickListener { offline() }
        invalidateButton.setOnClickListener { invalidate() }

        retrofitAnnotationRadio.setOnClickListener { presenterSwitcher(RETROFIT_ANNOTATION) }
        retrofitHeaderRadio.setOnClickListener { presenterSwitcher(RETROFIT_HEADER) }
        volleyRadio.setOnClickListener { presenterSwitcher(VOLLEY) }
        gitHubButton.setOnClickListener { openGithub() }

        freshOnlyCheckBox.setOnCheckedChangeListener { _, isChecked -> freshOnly = isChecked }
        compressCheckBox.setOnCheckedChangeListener { _, isChecked -> compress = isChecked }
        encryptCheckBox.setOnCheckedChangeListener { _, isChecked -> encrypt = isChecked }

        listAdapter = ExpandableListAdapter(this)
        listView.setAdapter(listAdapter)

        listAdapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onInvalidated() {
                onChanged()
            }

            override fun onChanged() {
                for (x in 0 until listAdapter.groupCount) {
                    listView.expandGroup(x)
                }
            }
        })
    }

    private fun loadCatFact(isRefresh: Boolean) {
        instructionType = if (isRefresh) REFRESH else CACHE

        presenter.loadCatFact(
                isRefresh,
                encrypt,
                compress,
                freshOnly
        )
    }

    private fun clearEntries() {
        instructionType = CLEAR
        presenter.clearEntries()
    }

    private fun offline() {
        instructionType = OFFLINE
        presenter.offline(freshOnly)
    }

    private fun invalidate() {
        instructionType = INVALIDATE
        presenter.invalidate()
    }

    private fun getInstruction(): CacheInstruction {
        val configuration = presenter.configuration

        val operation = when (instructionType) {
            CACHE -> Cache(
                    configuration.cacheDurationInMillis,
                    freshOnly,
                    configuration.mergeOnNextOnError,
                    encrypt,
                    compress,
                    false
            )
            REFRESH -> Refresh(
                    configuration.cacheDurationInMillis,
                    freshOnly,
                    configuration.mergeOnNextOnError,
                    false
            )
            DO_NOT_CACHE -> DoNotCache
            INVALIDATE -> Invalidate
            OFFLINE -> Offline(
                    freshOnly,
                    configuration.mergeOnNextOnError
            )
            CLEAR,
            CLEAR_ALL -> Clear(clearOldEntriesOnly = false)
        }

        return CacheInstruction(
                CatFactResponse::class.java,
                operation
        )
    }

    override fun showCatFact(response: CatFactResponse) {
        listAdapter.showCatFact(response)
    }

    override fun onCallStarted() {
        listView.post {
            setButtonsEnabled(false)
            listAdapter.onStart(getInstruction())
        }
    }

    override fun onCallComplete() {
        listView.post {
            setButtonsEnabled(true)
            listAdapter.onComplete()
        }
    }

    private fun setButtonsEnabled(isEnabled: Boolean) {
        loadButton.isEnabled = isEnabled
        refreshButton.isEnabled = isEnabled
        clearButton.isEnabled = isEnabled
        invalidateButton.isEnabled = isEnabled
        offlineButton.isEnabled = isEnabled
    }

    private fun openGithub() {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this, Uri.parse("https://github.com/pthomain/RxCacheInterceptor"))
    }

}
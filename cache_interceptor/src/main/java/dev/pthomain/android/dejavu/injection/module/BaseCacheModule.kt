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

package dev.pthomain.android.dejavu.injection.module

import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.Module
import dagger.Provides
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.configuration.CacheConfiguration
import dev.pthomain.android.dejavu.configuration.CacheInstruction
import dev.pthomain.android.dejavu.configuration.CacheInstructionSerialiser
import dev.pthomain.android.dejavu.configuration.NetworkErrorPredicate
import dev.pthomain.android.dejavu.injection.module.CacheModule.*
import dev.pthomain.android.dejavu.interceptors.DejaVuInterceptor
import dev.pthomain.android.dejavu.interceptors.cache.CacheInterceptor
import dev.pthomain.android.dejavu.interceptors.cache.CacheManager
import dev.pthomain.android.dejavu.interceptors.cache.CacheMetadataManager
import dev.pthomain.android.dejavu.interceptors.cache.metadata.CacheMetadata
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.CacheToken
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManagerFactory
import dev.pthomain.android.dejavu.interceptors.cache.persistence.database.DatabasePersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.database.SqlOpenHelperCallback
import dev.pthomain.android.dejavu.interceptors.cache.persistence.file.FileNameSerialiser
import dev.pthomain.android.dejavu.interceptors.cache.persistence.file.FilePersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.memory.MemoryPersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.statistics.StatisticsCompiler
import dev.pthomain.android.dejavu.interceptors.cache.persistence.statistics.database.DatabaseStatisticsCompiler
import dev.pthomain.android.dejavu.interceptors.cache.persistence.statistics.file.FileStatisticsCompiler
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.Hasher
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.SerialisationManager
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.decoration.compression.CompressionSerialisationDecorator
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.decoration.encryption.EncryptionSerialisationDecorator
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.decoration.file.FileSerialisationDecorator
import dev.pthomain.android.dejavu.interceptors.error.ErrorInterceptor
import dev.pthomain.android.dejavu.interceptors.network.NetworkInterceptor
import dev.pthomain.android.dejavu.interceptors.response.EmptyResponseFactory
import dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
import dev.pthomain.android.dejavu.retrofit.ProcessingErrorAdapter
import dev.pthomain.android.dejavu.retrofit.RequestBodyConverter
import dev.pthomain.android.dejavu.retrofit.RetrofitCallAdapter
import dev.pthomain.android.dejavu.retrofit.RetrofitCallAdapterFactory
import dev.pthomain.android.dejavu.retrofit.annotations.AnnotationProcessor
import dev.pthomain.android.mumbo.base.EncryptionManager
import io.reactivex.subjects.PublishSubject
import org.iq80.snappy.Snappy
import retrofit2.CallAdapter
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*
import javax.inject.Singleton

@Module
internal abstract class BaseCacheModule<E>(
        protected val configuration: CacheConfiguration<E>
) : CacheModule<E>
        where E : Exception,
              E : NetworkErrorPredicate {

    companion object {
        const val DATABASE_NAME = "dejavu.db"
        const val DATABASE_VERSION = 1
    }

    @Provides
    @Singleton
    override fun provideContext() =
            configuration.context

    @Provides
    @Singleton
    override fun provideConfiguration() =
            configuration

    @Provides
    @Singleton
    override fun provideLogger() =
            configuration.logger

    @Provides
    @Singleton
    override fun provideSerialiser() =
            configuration.serialiser

    @Provides
    @Singleton
    override fun provideEncryptionManager() =
            configuration.encryptionManager

    @Provides
    @Singleton
    override fun provideFileNameSerialiser() =
            FileNameSerialiser()

    @Provides
    @Singleton
    override fun provideCompresser() = object : Function1<ByteArray, ByteArray> {
        override fun get(t1: ByteArray) = Snappy.compress(t1)
    }

    @Provides
    @Singleton
    override fun provideUncompresser() = object : Function3<ByteArray, Int, Int, ByteArray> {
        override fun get(t1: ByteArray, t2: Int, t3: Int) = Snappy.uncompress(t1, t2, t3)
    }

    @Provides
    @Singleton
    override fun provideByteToStringConverter() = object : Function1<ByteArray, String> {
        override fun get(t1: ByteArray) = String(t1)
    }

    @Provides
    @Singleton
    override fun provideHasher(uriParser: Function1<String, Uri>) =
            Hasher.Factory(
                    configuration.logger,
                    uriParser::get
            ).create()

    @Provides
    @Singleton
    override fun provideFileSerialisationDecorator(byteToStringConverter: Function1<ByteArray, String>) =
            FileSerialisationDecorator<E>(byteToStringConverter::get)

    @Provides
    @Singleton
    override fun provideCompressionSerialisationDecorator(logger: Logger,
                                                          compresser: Function1<ByteArray, ByteArray>,
                                                          uncompresser: Function3<ByteArray, Int, Int, ByteArray>) =
            CompressionSerialisationDecorator<E>(
                    logger,
                    compresser::get,
                    uncompresser::get
            )

    @Provides
    @Singleton
    override fun provideEncryptionSerialisationDecorator(encryptionManager: EncryptionManager) =
            EncryptionSerialisationDecorator<E>(encryptionManager)

    @Provides
    @Singleton
    override fun provideSerialisationManagerFactory(byteToStringConverter: Function1<ByteArray, String>,
                                                    fileSerialisationDecorator: FileSerialisationDecorator<E>,
                                                    compressionSerialisationDecorator: CompressionSerialisationDecorator<E>,
                                                    encryptionSerialisationDecorator: EncryptionSerialisationDecorator<E>) =
            SerialisationManager.Factory(
                    configuration.serialiser,
                    byteToStringConverter::get,
                    fileSerialisationDecorator,
                    compressionSerialisationDecorator,
                    encryptionSerialisationDecorator
            )

    @Provides
    @Singleton
    override fun providePersistenceManager(databasePersistenceManagerFactory: DatabasePersistenceManager.Factory<E>?,
                                           filePersistenceManagerFactory: FilePersistenceManager.Factory<E>,
                                           memoryPersistenceManagerFactory: MemoryPersistenceManager.Factory<E>): PersistenceManager<E> =
            configuration.persistenceManagerPicker
                    ?.invoke(
                            PersistenceManagerFactory(
                                    filePersistenceManagerFactory,
                                    databasePersistenceManagerFactory,
                                    memoryPersistenceManagerFactory
                            )
                    ) ?: databasePersistenceManagerFactory!!.create()

    @Provides
    @Singleton
    override fun provideMemoryPersistenceManagerFactory(hasher: Hasher,
                                                        serialisationManagerFactory: SerialisationManager.Factory<E>,
                                                        dateFactory: Function1<Long?, Date>,
                                                        fileNameSerialiser: FileNameSerialiser) =
            MemoryPersistenceManager.Factory(
                    hasher,
                    configuration,
                    dateFactory::get,
                    fileNameSerialiser,
                    serialisationManagerFactory
            )

    @Provides
    @Singleton
    override fun provideFilePersistenceManagerFactory(hasher: Hasher,
                                                      serialisationManagerFactory: SerialisationManager.Factory<E>,
                                                      dateFactory: Function1<Long?, Date>,
                                                      fileNameSerialiser: FileNameSerialiser) =
            FilePersistenceManager.Factory(
                    hasher,
                    configuration,
                    serialisationManagerFactory,
                    dateFactory::get,
                    fileNameSerialiser
            )

    @Provides
    @Singleton
    override fun provideDatabasePersistenceManagerFactory(hasher: Hasher,
                                                          database: SupportSQLiteDatabase?,
                                                          dateFactory: Function1<Long?, Date>,
                                                          serialisationManagerFactory: SerialisationManager.Factory<E>) =
            if (database != null)
                DatabasePersistenceManager.Factory(
                        database,
                        hasher,
                        serialisationManagerFactory,
                        configuration,
                        dateFactory::get,
                        ::mapToContentValues
                )
            else null

    @Provides
    @Singleton
    override fun provideSqlOpenHelperCallback(): SupportSQLiteOpenHelper.Callback? =
            if (configuration.useDatabase) SqlOpenHelperCallback(DATABASE_VERSION)
            else null

    @Provides
    @Singleton
    @Synchronized
    override fun provideDatabase(sqlOpenHelper: SupportSQLiteOpenHelper?) =
            sqlOpenHelper?.writableDatabase

    @Provides
    @Singleton
    override fun provideDatabaseStatisticsCompiler(database: SupportSQLiteDatabase?,
                                                   dateFactory: Function1<Long?, Date>) =
            database?.let {
                DatabaseStatisticsCompiler(
                        configuration,
                        configuration.logger,
                        dateFactory::get,
                        it
                )
            }

    @Provides
    @Singleton
    override fun provideFileStatisticsCompiler(fileNameSerialiser: FileNameSerialiser,
                                               persistenceManager: PersistenceManager<E>,
                                               dateFactory: Function1<Long?, Date>) =
            (persistenceManager as? FilePersistenceManager<E>)?.let {
                FileStatisticsCompiler(
                        configuration,
                        it.cacheDirectory,
                        ::File,
                        { BufferedInputStream(FileInputStream(it)) },
                        dateFactory::get,
                        fileNameSerialiser
                )
            }

    @Provides
    @Singleton
    override fun provideStatisticsCompiler(fileStatisticsCompiler: FileStatisticsCompiler?,
                                           databaseStatisticsCompiler: DatabaseStatisticsCompiler?): StatisticsCompiler =
            databaseStatisticsCompiler ?: fileStatisticsCompiler!!

    @Provides
    @Singleton
    override fun provideCacheMetadataManager(persistenceManager: PersistenceManager<E>,
                                             dateFactory: Function1<Long?, Date>,
                                             emptyResponseFactory: EmptyResponseFactory<E>) =
            CacheMetadataManager(
                    configuration.errorFactory,
                    persistenceManager,
                    dateFactory::get,
                    configuration.cacheDurationInMillis,
                    configuration.logger
            )

    @Provides
    @Singleton
    override fun provideCacheManager(persistenceManager: PersistenceManager<E>,
                                     cacheMetadataManager: CacheMetadataManager<E>,
                                     dateFactory: Function1<Long?, Date>,
                                     emptyResponseFactory: EmptyResponseFactory<E>) =
            CacheManager(
                    persistenceManager,
                    cacheMetadataManager,
                    emptyResponseFactory,
                    dateFactory::get,
                    configuration.logger
            )

    @Provides
    @Singleton
    override fun provideErrorInterceptorFactory(dateFactory: Function1<Long?, Date>) =
            object : Function1<CacheToken, ErrorInterceptor<E>> {
                override fun get(t1: CacheToken) = ErrorInterceptor(
                        configuration.context,
                        configuration.errorFactory,
                        configuration.logger,
                        dateFactory::get,
                        t1
                )
            }

    @Provides
    @Singleton
    override fun provideNetworkInterceptorFactory(dateFactory: Function1<Long?, Date>) =
            object : Function3<ErrorInterceptor<E>, CacheToken, Long, NetworkInterceptor<E>> {
                override fun get(t1: ErrorInterceptor<E>, t2: CacheToken, t3: Long) = NetworkInterceptor(
                        configuration.context,
                        configuration.logger,
                        t1,
                        dateFactory::get,
                        t2,
                        t3,
                        configuration.connectivityTimeoutInMillis.toInt()
                )
            }

    @Provides
    @Singleton
    override fun provideCacheInterceptorFactory(dateFactory: Function1<Long?, Date>,
                                                cacheManager: CacheManager<E>) =
            object : Function3<ErrorInterceptor<E>, CacheToken, Long, CacheInterceptor<E>> {
                override fun get(t1: ErrorInterceptor<E>, t2: CacheToken, t3: Long) = CacheInterceptor(
                        t1,
                        cacheManager,
                        dateFactory::get,
                        configuration.isCacheEnabled,
                        t2,
                        t3
                )
            }

    @Provides
    @Singleton
    override fun provideResponseInterceptor(dateFactory: Function1<Long?, Date>,
                                            metadataSubject: PublishSubject<CacheMetadata<E>>,
                                            emptyResponseFactory: EmptyResponseFactory<E>) =
            object : Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<E>> {
                override fun get(t1: CacheToken,
                                 t2: Boolean,
                                 t3: Boolean,
                                 t4: Long) = ResponseInterceptor(
                        configuration.logger,
                        dateFactory::get,
                        emptyResponseFactory,
                        configuration,
                        metadataSubject,
                        t1,
                        t2,
                        t3,
                        t4,
                        configuration.mergeOnNextOnError
                )
            }

    @Provides
    @Singleton
    override fun provideDejaVuInterceptorFactory(hasher: Hasher,
                                                 dateFactory: Function1<Long?, Date>,
                                                 networkInterceptorFactory: Function3<ErrorInterceptor<E>, CacheToken, Long, NetworkInterceptor<E>>,
                                                 errorInterceptorFactory: Function1<CacheToken, ErrorInterceptor<E>>,
                                                 cacheInterceptorFactory: Function3<ErrorInterceptor<E>, CacheToken, Long, CacheInterceptor<E>>,
                                                 responseInterceptor: Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<E>>) =
            DejaVuInterceptor.Factory(
                    hasher,
                    dateFactory::get,
                    errorInterceptorFactory::get,
                    networkInterceptorFactory::get,
                    cacheInterceptorFactory::get,
                    responseInterceptor::get,
                    configuration
            )

    @Provides
    @Singleton
    override fun provideDefaultAdapterFactory() =
            RxJava2CallAdapterFactory.create()!!

    @Provides
    @Singleton
    override fun provideUriParser() = object : Function1<String, Uri> {
        override fun get(t1: String) = Uri.parse(t1)
    }

    @Provides
    @Singleton
    override fun provideRetrofitCallAdapterInnerFactory() =
            object : Function6<DejaVuInterceptor.Factory<E>, Logger, String, Class<*>, CacheInstruction?, CallAdapter<Any, Any>, RetrofitCallAdapter<E>> {
                override fun get(
                        t1: DejaVuInterceptor.Factory<E>,
                        t2: Logger,
                        t3: String,
                        t4: Class<*>,
                        t5: CacheInstruction?,
                        t6: CallAdapter<Any, Any>
                ) = RetrofitCallAdapter(
                        configuration,
                        t4,
                        t1,
                        CacheInstructionSerialiser(),
                        RequestBodyConverter(),
                        t2,
                        t3,
                        t5,
                        t6
                )
            }

    @Provides
    @Singleton
    override fun provideRetrofitCallAdapterFactory(dateFactory: Function1<Long?, Date>,
                                                   innerFactory: Function6<DejaVuInterceptor.Factory<E>, Logger, String, Class<*>, CacheInstruction?, CallAdapter<Any, Any>, RetrofitCallAdapter<E>>,
                                                   defaultAdapterFactory: RxJava2CallAdapterFactory,
                                                   dejaVuInterceptorFactory: DejaVuInterceptor.Factory<E>,
                                                   processingErrorAdapterFactory: ProcessingErrorAdapter.Factory<E>,
                                                   annotationProcessor: AnnotationProcessor<E>) =
            RetrofitCallAdapterFactory(
                    defaultAdapterFactory,
                    innerFactory::get,
                    dateFactory::get,
                    dejaVuInterceptorFactory,
                    annotationProcessor,
                    processingErrorAdapterFactory,
                    configuration.logger
            )

    @Provides
    @Singleton
    override fun provideProcessingErrorAdapterFactory(errorInterceptorFactory: Function1<CacheToken, ErrorInterceptor<E>>,
                                                      dateFactory: Function1<Long?, Date>,
                                                      responseInterceptorFactory: Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<E>>) =
            ProcessingErrorAdapter.Factory(
                    errorInterceptorFactory::get,
                    responseInterceptorFactory::get,
                    dateFactory::get
            )

    @Provides
    @Singleton
    override fun provideCacheMetadataSubject() =
            PublishSubject.create<CacheMetadata<E>>()

    @Provides
    @Singleton
    override fun provideCacheMetadataObservable(subject: PublishSubject<CacheMetadata<E>>) =
            subject.map { it }!!

    @Provides
    @Singleton
    override fun provideAnnotationProcessor() =
            AnnotationProcessor(configuration)

    @Provides
    @Singleton
    override fun provideEmptyResponseFactory() =
            EmptyResponseFactory(configuration.errorFactory)

}
